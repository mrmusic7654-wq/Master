package com.mastercontrol.app.worker.upload

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mastercontrol.app.core.logging.Logger
import com.mastercontrol.app.core.logging.Tags
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import com.mastercontrol.app.domain.model.MappingStatus
import com.mastercontrol.app.domain.model.RetryPolicy
import com.mastercontrol.app.domain.model.TelegramMapping
import com.mastercontrol.app.domain.model.UploadJob
import com.mastercontrol.app.domain.model.UploadNetworkRule
import com.mastercontrol.app.domain.model.UploadProgressEvent
import com.mastercontrol.app.domain.model.UploadReceipt
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState
import com.mastercontrol.app.domain.model.Video
import com.mastercontrol.app.domain.model.VideoStatus
import com.mastercontrol.app.domain.port.TelegramUploadExecutor
import com.mastercontrol.app.domain.port.UploadWorkerScheduler
import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.SettingsRepository
import com.mastercontrol.app.domain.repository.UploadTaskRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The single durable upload-queue processor.
 *
 * Each run: reclaims tasks interrupted by process death, requeues RETRYING tasks
 * whose backoff has elapsed, then executes queued uploads through
 * [TelegramUploadExecutor]. Scheduling is idempotent — there is exactly one of
 * these workers (unique name), so concurrent runs can never double-upload.
 *
 * NOTE: a single WorkManager run is time-boxed (~10 minutes). Very large
 * transfers therefore need the foreground-service upgrade
 * (setForeground + notification); that wiring belongs in the app module.
 */
@HiltWorker
class UploadQueueWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploadTaskRepository: UploadTaskRepository,
    private val videoRepository: VideoRepository,
    private val activityRepository: ActivityRepository,
    private val settingsRepository: SettingsRepository,
    private val executor: TelegramUploadExecutor,
    private val scheduler: UploadWorkerScheduler,
    private val logger: Logger,
) : CoroutineWorker(context, params) {

    private val appContext: Context get() = applicationContext

    override suspend fun doWork(): Result {
        return try {
            if (!gatePassed()) {
                return Result.retry()
            }
            processQueue()
            if (uploadTaskRepository.countByState(UploadTaskState.QUEUED) > 0 ||
                uploadTaskRepository.countByState(UploadTaskState.RETRYING) > 0
            ) {
                scheduler.scheduleUploadQueueProcessing()
            }
            Result.success()
        } catch (t: CancellationException) {
            // Stopped by WorkManager (constraint loss / run window). In-flight
            // rows are reclaimed by the next run; make sure one is scheduled.
            runCatching { scheduler.scheduleUploadQueueProcessing() }
            throw t
        } catch (t: Throwable) {
            logger.error(Tags.WORKER, "upload queue run crashed", t)
            Result.retry()
        }
    }

    // ---- queue pass --------------------------------------------------------

    private suspend fun processQueue() {
        val settings = settingsRepository.getSettings()

        // 1) Reclaim work interrupted by process death.
        val reclaimed = uploadTaskRepository.reclaimInFlightTasksOlderThan(RECLAIM_GRACE_MS)
        if (reclaimed > 0) logger.info(Tags.WORKER, "reclaimed $reclaimed interrupted task(s)")

        // 2) Requeue RETRYING tasks whose backoff elapsed.
        requeueDueRetries()

        // 3) Execute queued tasks. A single worker processes serially; raising
        //    maxConcurrentUploads beyond 1 would need per-task workers and is
        //    out of scope for the single-queue design.
        val budget = settings.maxConcurrentUploads.coerceIn(1, 4)
        var handled = 0
        while (handled < budget) {
            currentCoroutineContext().ensureActive()
            val task = uploadTaskRepository.nextQueued() ?: break
            handled++
            runTask(task)
        }
    }

    // ---- single task -------------------------------------------------------

    private suspend fun runTask(task: UploadTask) {
        val current = uploadTaskRepository.getTask(task.taskId)
        if (current == null || current.state != UploadTaskState.QUEUED) return

        val video = videoRepository.getVideo(task.videoId)
        if (video == null || task.channelId == null) {
            failPermanently(task, "Source video or destination channel no longer exists.")
            return
        }

        val settings = settingsRepository.getSettings()
        val now = Instant.now()
        val attempt = task.attemptCount + 1

        // Claim: QUEUED -> PREPARING (an attempt always increments).
        uploadTaskRepository.update(
            task.copy(
                state = UploadTaskState.PREPARING,
                attemptCount = attempt,
                lastError = null,
                updatedAt = now,
            ),
        )
        // The operator may have cancelled between the claim read and this write.
        if (uploadTaskRepository.getTask(task.taskId)?.state == UploadTaskState.CANCELLED) {
            executor.cancel(task.taskId)
            return
        }
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.UPLOAD_STARTED,
                message = "${task.videoId} upload started (attempt $attempt)",
                relatedVideoId = task.videoId,
                channelId = task.channelId,
                createdAt = now,
            ),
        )

        val job = UploadJob(
            taskId = task.taskId,
            videoId = task.videoId,
            sourceUri = task.sourceUri,
            channelId = task.channelId,
            fileName = task.fileName,
            mimeType = video.mimeType,
            totalBytes = video.fileSizeBytes ?: task.totalBytes,
            durationMs = video.durationMs,
            width = video.width,
            height = video.height,
            thumbnailPath = video.thumbnailUri,
            caption = buildCaption(video, settings.captionIncludesVideoId),
        )

        val outcome = executeUpload(task, job)
        when (outcome) {
            is Outcome.Completed -> logger.info(Tags.WORKER, "upload ${task.taskId} completed")
            is Outcome.CancelledWhileRunning -> logger.info(Tags.WORKER, "upload ${task.taskId} cancelled while running")
            is Outcome.Failed -> onFailure(task, outcome.error)
        }
    }

    /**
     * Runs [job] to its terminal event. While it runs, a watcher polls the row
     * so an operator cancellation reaches the in-flight transfer promptly.
     */
    private suspend fun executeUpload(task: UploadTask, job: UploadJob): Outcome {
        var terminal: UploadProgressEvent? = null
        coroutineScope {
            val watcher = launch {
                while (isActive) {
                    delay(CANCEL_POLL_MS)
                    val row = uploadTaskRepository.getTask(task.taskId)
                    if (row?.state == UploadTaskState.CANCELLED) executor.cancel(task.taskId)
                }
            }
            try {
                executor.upload(job).collect { event ->
                    handleEvent(task, event)
                    if (event is UploadProgressEvent.Completed || event is UploadProgressEvent.Failed) {
                        terminal = event
                    }
                }
            } finally {
                watcher.cancel()
            }
        }
        return when (val t = terminal) {
            is UploadProgressEvent.Completed -> {
                val row = uploadTaskRepository.getTask(task.taskId)
                if (row?.state == UploadTaskState.CANCELLED) {
                    // Cancel won the race; Telegram may already hold the message.
                    // Do not map it locally — reconciliation will surface it.
                    Outcome.CancelledWhileRunning
                } else {
                    commitReceipt(task, t.receipt)
                    Outcome.Completed
                }
            }
            is UploadProgressEvent.Failed -> Outcome.Failed(t.error)
            else -> Outcome.Completed // flow ended without a terminal event (stopped mid-run)
        }
    }

    private suspend fun handleEvent(task: UploadTask, event: UploadProgressEvent) {
        when (event) {
            is UploadProgressEvent.Preparing -> Unit // row already claims PREPARING
            is UploadProgressEvent.Transferring -> touch(
                task.taskId,
                UploadTaskState.UPLOADING,
                bytesUploaded = event.bytesUploaded,
                totalBytes = event.totalBytes,
            )
            is UploadProgressEvent.Verifying -> touch(task.taskId, UploadTaskState.VERIFYING)
            is UploadProgressEvent.Completed, is UploadProgressEvent.Failed -> Unit
        }
    }

    /** Progress write; never downgrades a terminal or cancelled row. */
    private suspend fun touch(
        taskId: Long,
        state: UploadTaskState,
        bytesUploaded: Long? = null,
        totalBytes: Long? = null,
    ) {
        val row = uploadTaskRepository.getTask(taskId) ?: return
        if (row.state == UploadTaskState.CANCELLED || row.state == UploadTaskState.COMPLETED) return
        uploadTaskRepository.update(
            row.copy(
                state = state,
                bytesUploaded = bytesUploaded ?: row.bytesUploaded,
                totalBytes = totalBytes ?: row.totalBytes,
                updatedAt = Instant.now(),
            ),
        )
    }

    private suspend fun commitReceipt(task: UploadTask, receipt: UploadReceipt) {
        val now = Instant.now()
        val mapping = TelegramMapping(
            videoId = task.videoId,
            channelId = receipt.channelId,
            messageId = receipt.messageId,
            telegramFileId = receipt.telegramFileId,
            telegramRemoteFileId = receipt.telegramRemoteFileId,
            telegramUniqueFileId = receipt.telegramUniqueFileId,
            fileSizeBytes = receipt.fileSizeBytes,
            mimeType = receipt.mimeType,
            uploadedAt = now,
            updatedAt = now,
            mappingStatus = MappingStatus.ACTIVE,
        )
        // Atomic Room transaction: mapping upsert + video COMPLETE + task
        // COMPLETED (see VideoRepositoryImpl.commitCompletedUpload).
        videoRepository.commitCompletedUpload(task.videoId, mapping, task.taskId)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.UPLOAD_COMPLETED,
                message = "${task.videoId} uploaded to channel ${receipt.channelId} (message ${receipt.messageId})",
                relatedVideoId = task.videoId,
                channelId = task.channelId,
                createdAt = now,
            ),
        )
        logger.info(Tags.WORKER, "upload ${task.taskId} committed: message ${receipt.messageId}")
    }

    // ---- failure handling --------------------------------------------------

    private suspend fun onFailure(task: UploadTask, error: Throwable) {
        val row = uploadTaskRepository.getTask(task.taskId) ?: return
        if (row.state == UploadTaskState.CANCELLED) return

        val policy = RetryPolicy()
        val retryable = when (error) {
            is AppError.UploadCancelledError -> false
            is AppError.UploadFailedError -> error.canRetry
            is AppError.TelegramAuthenticationError,
            is AppError.TelegramPermissionError,
            is AppError.TelegramMessageNotFoundError,
            is AppError.MediaUnavailableError,
            is AppError.InvalidMediaError,
            is AppError.ChannelNotFoundError,
            is AppError.NotFoundError,
            -> false
            is AppError.TelegramRateLimitError,
            is AppError.TelegramConnectionError,
            is AppError.StorageError,
            is AppError.DatabaseError,
            -> true
            else -> true
        }
        if (!retryable || row.attemptCount >= policy.maxAttempts) {
            failPermanently(row, error.userMessageText())
            return
        }

        // Retryable: park in RETRYING (kept out of nextQueued) and schedule the
        // processor after this attempt's backoff delay.
        val now = Instant.now()
        uploadTaskRepository.update(
            row.copy(
                state = UploadTaskState.RETRYING,
                lastError = error.userMessageText(),
                updatedAt = now,
            ),
        )
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.UPLOAD_RETRYING,
                message = "${row.videoId} upload failed, retrying (attempt ${row.attemptCount}/${policy.maxAttempts})",
                details = error.userMessageText(),
                relatedVideoId = row.videoId,
                channelId = row.channelId,
                createdAt = now,
            ),
        )
        val backoff = policy.delayForAttempt(row.attemptCount)
        scheduler.scheduleUploadQueueProcessing(backoff)
        logger.warn(Tags.WORKER, "upload ${row.taskId} will retry in ${backoff}ms: ${error.userMessageText()}")
    }

    private suspend fun failPermanently(task: UploadTask, reason: String) {
        val now = Instant.now()
        uploadTaskRepository.update(
            task.copy(state = UploadTaskState.FAILED, lastError = reason, updatedAt = now),
        )
        videoRepository.setVideoStatus(task.videoId, VideoStatus.FAILED)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.UPLOAD_FAILED,
                message = "${task.videoId} upload failed permanently",
                details = reason,
                relatedVideoId = task.videoId,
                channelId = task.channelId,
                createdAt = now,
            ),
        )
        logger.warn(Tags.WORKER, "upload ${task.taskId} permanently failed: $reason")
    }

    private suspend fun requeueDueRetries() {
        val policy = RetryPolicy()
        val nowEpochMs = Instant.now().toEpochMilli()
        val due = uploadTaskRepository.observeByState(UploadTaskState.RETRYING).first()
            .filter { row ->
                val waited = nowEpochMs - row.updatedAt.toEpochMilli()
                waited >= policy.delayForAttempt(row.attemptCount.coerceAtLeast(1))
            }
        due.forEach { row ->
            uploadTaskRepository.update(
                row.copy(state = UploadTaskState.QUEUED, lastError = null, updatedAt = Instant.now()),
            )
            logger.info(Tags.WORKER, "requeued retry of ${row.taskId} (attempt ${row.attemptCount})")
        }
    }

    // ---- environment gates -------------------------------------------------

    /**
     * Applies the operator's network/charging upload rules. When false the run
     * defers; WorkManager backoff paces the retry.
     */
    private suspend fun gatePassed(): Boolean {
        val settings = settingsRepository.getSettings()
        if (settings.uploadNetworkRule == UploadNetworkRule.WIFI_ONLY && !isUnmeteredNetwork()) {
            logger.debug(Tags.WORKER, "queue run deferred: WIFI_ONLY and network is metered")
            return false
        }
        if (settings.allowUploadsOnlyWhileCharging && !isCharging()) {
            logger.debug(Tags.WORKER, "queue run deferred: waiting for charger")
            return false
        }
        return true
    }

    private fun isUnmeteredNetwork(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun isCharging(): Boolean {
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return false
        return bm.isCharging
    }

    private fun buildCaption(video: Video, includeVideoId: Boolean): String {
        val parts = buildList {
            if (includeVideoId) add(video.videoId)
            if (video.description.isNotBlank()) add(video.description)
        }
        return parts.joinToString(" — ")
    }

    private fun Throwable.userMessageText(): String =
        (this as? AppError)?.userMessage ?: (message?.take(200) ?: "Unknown error")

    private sealed class Outcome {
        object Completed : Outcome()
        object CancelledWhileRunning : Outcome()
        data class Failed(val error: Throwable) : Outcome()
    }

    companion object {
        /** In-flight rows older than this are reclaimed after process death. */
        const val RECLAIM_GRACE_MS = 30 * 60_000L

        /** How often the worker checks whether the operator cancelled the task. */
        const val CANCEL_POLL_MS = 2_000L
    }
}
