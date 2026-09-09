package com.mastercontrol.app.domain.usecase

import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.id.VideoIdAllocator
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import com.mastercontrol.app.domain.model.Video
import com.mastercontrol.app.domain.model.VideoStatus
import com.mastercontrol.app.domain.port.MediaToolkit
import com.mastercontrol.app.domain.port.UploadWorkerScheduler
import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import java.time.Instant
import javax.inject.Inject

sealed class ImportOutcome {
    data class Imported(val video: Video) : ImportOutcome()
    data class Duplicate(val duplicateOf: Video, val reason: String) : ImportOutcome()
}

/**
 * Import step 1: validate the content URI, read metadata, guard against cheap
 * duplicates and create the catalog row in IMPORTING state. Heavy work
 * (thumbnail/hash) is delegated to [FinalizeVideoImportUseCase] in a worker so
 * the UI never blocks on decoding.
 */
class PrepareVideoImportUseCase @Inject constructor(
    private val mediaToolkit: MediaToolkit,
    private val videoIdAllocator: VideoIdAllocator,
    private val videoRepository: VideoRepository,
    private val activityRepository: ActivityRepository,
    private val workerScheduler: UploadWorkerScheduler,
) {
    suspend operator fun invoke(
        contentUri: String,
        requestedTitle: String? = null,
        categoryId: Long? = null,
        folderId: Long? = null,
        tags: List<String> = emptyList(),
    ): ImportOutcome {
        val info = mediaToolkit.inspect(contentUri)

        videoRepository.findDuplicateBySizeName(info.sizeBytes, info.displayName)?.let { existing ->
            return ImportOutcome.Duplicate(
                existing,
                "A video with the same file size and name already exists (${existing.videoId}).",
            )
        }

        val now = Instant.now()
        val video = Video(
            videoId = videoIdAllocator.allocate(),
            title = requestedTitle?.takeIf { it.isNotBlank() }
                ?: info.displayName.substringBeforeLast('.').ifBlank { info.displayName },
            originalFileName = info.displayName,
            description = "",
            durationMs = info.durationMs,
            fileSizeBytes = info.sizeBytes,
            mimeType = info.mimeType,
            width = info.width,
            height = info.height,
            frameRate = info.frameRate,
            sourceUri = contentUri,
            createdAt = now,
            updatedAt = now,
            status = VideoStatus.IMPORTING,
            categoryId = categoryId,
            folderId = folderId,
            tags = tags.distinct(),
            hashPending = true,
        )
        val created = videoRepository.createVideo(video)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.VIDEO_IMPORTED,
                message = "${created.videoId} imported (${info.displayName})",
                relatedVideoId = created.videoId,
                createdAt = now,
            ),
        )
        workerScheduler.scheduleImportFinalization(created.videoId)
        return ImportOutcome.Imported(created)
    }
}

/** Import step 2 (worker): hash + thumbnail generation; row flips to READY. */
class FinalizeVideoImportUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val activityRepository: ActivityRepository,
    private val mediaToolkit: MediaToolkit,
) {
    suspend operator fun invoke(videoId: String, hashingEnabled: Boolean, thumbnailCaptureMs: Long) {
        val video = videoRepository.getVideo(videoId) ?: return
        try {
            var current = video
            if (hashingEnabled && current.sha256 == null) {
                val hash = mediaToolkit.computeSha256(current.sourceUri)
                videoRepository.attachHash(videoId, hash)
                current = videoRepository.getVideo(videoId) ?: current
                videoRepository.findDuplicateByHash(hash)?.let { dup ->
                    if (dup.videoId != videoId) {
                        activityRepository.add(
                            ActivityLogEntry(
                                type = ActivityType.VIDEO_IMPORTED,
                                message = "$videoId: identical file already cataloged as ${dup.videoId}",
                                details = "Duplicate detected by SHA-256.",
                                relatedVideoId = videoId,
                                createdAt = Instant.now(),
                            ),
                        )
                    }
                }
            }

            if (current.thumbnailUri == null) {
                val generated = mediaToolkit.generateThumbnail(
                    contentUri = current.sourceUri,
                    captureMs = thumbnailCaptureMs.coerceAtLeast(500L),
                    fallbackCaptureMs = 0L,
                )
                videoRepository.setThumbnail(videoId, generated.fileUri)
            }
            videoRepository.setVideoStatus(videoId, VideoStatus.READY)
        } catch (t: AppError) {
            // The source file may have disappeared; keep the row browsable and surface a log.
            videoRepository.setVideoStatus(videoId, VideoStatus.READY)
            activityRepository.add(
                ActivityLogEntry(
                    type = ActivityType.VIDEO_IMPORTED,
                    message = "$videoId: background import step failed",
                    details = t.userMessage,
                    relatedVideoId = videoId,
                    createdAt = Instant.now(),
                ),
            )
        }
    }
}

/** Manual thumbnail replacement (e.g. an image picked by the administrator). */
class SetVideoThumbnailUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val activityRepository: ActivityRepository,
) {
    suspend operator fun invoke(videoId: String, thumbnailUri: String?) {
        videoRepository.setThumbnail(videoId, thumbnailUri)
        if (thumbnailUri != null) {
            activityRepository.add(
                ActivityLogEntry(
                    type = ActivityType.THUMBNAIL_CHANGED,
                    message = "$videoId thumbnail replaced",
                    relatedVideoId = videoId,
                    createdAt = Instant.now(),
                ),
            )
        }
    }
}
