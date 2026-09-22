package com.mastercontrol.app.domain.usecase

import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import com.mastercontrol.app.domain.model.StorageChannel
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState
import com.mastercontrol.app.domain.model.VideoStatus
import com.mastercontrol.app.domain.port.UploadWorkerScheduler
import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.TelegramChannelRepository
import com.mastercontrol.app.domain.repository.UploadTaskRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import java.time.Instant
import javax.inject.Inject

/**
 * Queues a video for upload to the default (or given) channel and returns the
 * created task. Refuses to create a second concurrent task for the same video.
 */
class QueueUploadUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val uploadTaskRepository: UploadTaskRepository,
    private val channelRepository: TelegramChannelRepository,
    private val activityRepository: ActivityRepository,
    private val workerScheduler: UploadWorkerScheduler,
) {
    suspend operator fun invoke(videoId: String, channelId: Long? = null): UploadTask {
        val video = videoRepository.getVideo(videoId)
            ?: throw AppError.NotFoundError("Video $videoId does not exist.")
        val channel: StorageChannel = if (channelId != null) {
            channelRepository.getChannel(channelId)
                ?: throw AppError.ChannelNotFoundError()
        } else {
            channelRepository.getChannelByDefault()
                ?: throw AppError.NotFoundError("No storage channel is selected. Configure a channel first.")
        }
        if (!channel.enabled) {
            throw AppError.ValidationError(
                "\"${channel.displayName}\" is disabled for uploads. Enable it or choose another channel.",
            )
        }
        if (!channel.permissions.canUploadVideos) {
            throw AppError.TelegramPermissionError(
                missingPermission = "post messages",
                detail = "Channel '${channel.displayName}' does not grant posting rights to this account.",
            )
        }

        // Concurrency guard: only one active task per video at a time.
        uploadTaskRepository.getActiveTaskForVideo(videoId)?.let { active ->
            throw AppError.DatabaseError("Video $videoId already has an active upload task (task ${active.taskId}).")
        }

        val now = Instant.now()
        val task = uploadTaskRepository.enqueue(
            UploadTask(
                videoId = videoId,
                sourceUri = video.sourceUri,
                fileName = video.originalFileName,
                channelId = channel.id,
                state = UploadTaskState.QUEUED,
                totalBytes = video.fileSizeBytes,
                createdAt = now,
                updatedAt = now,
            ),
        )
        videoRepository.setVideoStatus(videoId, VideoStatus.UPLOADING)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.UPLOAD_QUEUED,
                message = "$videoId queued for upload to ${channel.displayName}",
                relatedVideoId = videoId,
                channelId = channel.id,
                createdAt = now,
            ),
        )
        workerScheduler.scheduleUploadQueueProcessing()
        return task
    }
}

/** Cancels a queued/in-flight task. */
class CancelUploadUseCase @Inject constructor(
    private val uploadTaskRepository: UploadTaskRepository,
    private val videoRepository: VideoRepository,
    private val activityRepository: ActivityRepository,
    private val workerScheduler: UploadWorkerScheduler,
) {
    suspend operator fun invoke(taskId: Long) {
        val task = uploadTaskRepository.getTask(taskId) ?: return
        if (task.state == UploadTaskState.COMPLETED || task.state == UploadTaskState.CANCELLED) return
        val now = Instant.now()
        uploadTaskRepository.update(task.copy(state = UploadTaskState.CANCELLED, updatedAt = now))
        videoRepository.setVideoStatus(task.videoId, VideoStatus.READY)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.UPLOAD_CANCELLED,
                message = "${task.videoId} upload cancelled",
                relatedVideoId = task.videoId,
                channelId = task.channelId,
                createdAt = now,
            ),
        )
        // Wake the queue so an in-flight task notices the cancellation promptly.
        workerScheduler.scheduleUploadQueueProcessing()
    }
}

/** Retries one failed task. */
class RetryUploadUseCase @Inject constructor(
    private val uploadTaskRepository: UploadTaskRepository,
    private val activityRepository: ActivityRepository,
    private val workerScheduler: UploadWorkerScheduler,
) {
    suspend operator fun invoke(taskId: Long) {
        val task = uploadTaskRepository.getTask(taskId) ?: return
        if (task.state != UploadTaskState.FAILED) return
        val now = Instant.now()
        uploadTaskRepository.update(
            task.copy(state = UploadTaskState.QUEUED, lastError = null, updatedAt = now),
        )
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.UPLOAD_RETRYING,
                message = "${task.videoId} retry queued",
                relatedVideoId = task.videoId,
                channelId = task.channelId,
                createdAt = now,
            ),
        )
        workerScheduler.scheduleUploadQueueProcessing()
    }
}

/** Queue-wide operations used by the upload screen. */
class UploadQueueActionsUseCase @Inject constructor(
    private val uploadTaskRepository: UploadTaskRepository,
    private val workerScheduler: UploadWorkerScheduler,
) {
    suspend fun retryAllFailed(): Int {
        val count = uploadTaskRepository.requeueAllFailed()
        if (count > 0) workerScheduler.scheduleUploadQueueProcessing()
        return count
    }

    suspend fun clearCompleted(): Int =
        uploadTaskRepository.deleteAll(onlyStates = setOf(UploadTaskState.COMPLETED))

    suspend fun clearFailed(): Int =
        uploadTaskRepository.deleteAll(onlyStates = setOf(UploadTaskState.FAILED, UploadTaskState.CANCELLED))
}
