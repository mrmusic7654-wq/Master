package com.mastercontrol.app.worker.import

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.mastercontrol.app.core.logging.Logger
import com.mastercontrol.app.core.logging.Tags
import com.mastercontrol.app.domain.repository.SettingsRepository
import com.mastercontrol.app.domain.usecase.FinalizeVideoImportUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Background import step 2: SHA-256 hashing (when enabled) and thumbnail
 * extraction, then flips the video to READY. Scheduled right after the catalog
 * row is created so the UI never blocks on media decoding.
 */
@HiltWorker
class ImportFinalizeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val finalizeImport: FinalizeVideoImportUseCase,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val videoId = inputData.getString(KEY_VIDEO_ID)
            ?: return Result.failure()
        return try {
            val settings = settingsRepository.getSettings()
            finalizeImport(
                videoId = videoId,
                hashingEnabled = settings.mediaHashingEnabled,
                thumbnailCaptureMs = settings.thumbnailCaptureMs,
            )
            logger.info(Tags.IMPORT, "finalized import of $videoId")
            Result.success()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            // The use case itself degrades gracefully per-video; anything
            // escaping it is unexpected and must not wedge the queue.
            logger.error(Tags.IMPORT, "import finalization of $videoId crashed", t)
            Result.retry()
        }
    }

    companion object {
        const val KEY_VIDEO_ID = "video_id"

        fun inputData(videoId: String): Data =
            Data.Builder().putString(KEY_VIDEO_ID, videoId).build()
    }
}
