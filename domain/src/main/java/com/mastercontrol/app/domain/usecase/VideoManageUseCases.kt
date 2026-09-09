package com.mastercontrol.app.domain.usecase

import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import com.mastercontrol.app.domain.model.MappingStatus
import com.mastercontrol.app.domain.model.TelegramMapping
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState
import com.mastercontrol.app.domain.model.Video
import com.mastercontrol.app.domain.model.VideoStatus
import com.mastercontrol.app.domain.port.UploadWorkerScheduler
import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.TelegramMediaRepository
import com.mastercontrol.app.domain.repository.UploadTaskRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import java.time.Instant
import javax.inject.Inject

/** Persist metadata edits immediately through Room. */
class EditVideoMetadataUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val activityRepository: ActivityRepository,
) {
    suspend operator fun invoke(
        video: Video,
        title: String? = null,
        description: String? = null,
        categoryId: Long? = null,
        folderId: Long? = null,
        tags: List<String>? = null,
        year: Int? = null,
        language: String? = null,
        rating: Float? = null,
        releaseDate: String? = null,
        sortOrderNote: String? = null,
    ): Video {
        @Suppress("UNUSED_VARIABLE") val note = sortOrderNote
        val updated = video.copy(
            title = title ?: video.title,
            description = description ?: video.description,
            categoryId = categoryId ?: video.categoryId,
            folderId = folderId ?: video.folderId,
            tags = tags ?: video.tags,
            year = year ?: video.year,
            language = language ?: video.language,
            rating = rating ?: video.rating,
            releaseDate = releaseDate ?: video.releaseDate,
            updatedAt = Instant.now(),
        )
        videoRepository.updateVideo(updated)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.METADATA_EDITED,
                message = "${video.videoId} metadata updated",
                relatedVideoId = video.videoId,
                createdAt = updated.updatedAt,
            ),
        )
        return updated
    }
}

/** Assigns/relocates a video into a category/folder (or clears the assignment). */
class MoveVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(videoId: String, categoryId: Long?, folderId: Long?) {
        val video = videoRepository.getVideo(videoId) ?: return
        videoRepository.updateVideo(
            video.copy(
                categoryId = categoryId,
                folderId = folderId,
                updatedAt = Instant.now(),
            ),
        )
    }
}

/**
 * Replace video media without changing the permanent Video ID:
 * the replacement is uploaded as a *new* Telegram message; only after the new
 * message is verified and the mapping updated may the old message be deleted.
 */
class ReplaceVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val uploadTaskRepository: UploadTaskRepository,
    private val activityRepository: ActivityRepository,
    private val workerScheduler: UploadWorkerScheduler,
) {
    suspend operator fun invoke(videoId: String, replacementUri: String, channelId: Long?): UploadTask {
        val video = videoRepository.getVideo(videoId)
            ?: throw AppError.NotFoundError("Video $videoId does not exist.")
        val mapping = videoRepository.getMapping(videoId)
        uploadTaskRepository.getActiveTaskForVideo(videoId)?.let {
            throw AppError.DatabaseError("Video $videoId is already uploading.")
        }
        // The replacement is uploaded to the same channel the video currently
        // maps to (or an explicit channel). The old Telegram message is only
        // deleted by the worker after the new message is verified & committed.
        val targetChannel = channelId ?: mapping?.channelId
            ?: throw AppError.NotFoundError("Video $videoId has no Telegram mapping and no channel was given.")
        val now = Instant.now()
        val task = uploadTaskRepository.enqueue(
            UploadTask(
                videoId = videoId,
                sourceUri = replacementUri,
                fileName = video.originalFileName,
                channelId = targetChannel,
                state = UploadTaskState.QUEUED,
                totalBytes = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        videoRepository.setVideoStatus(videoId, VideoStatus.UPLOADING)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.VIDEO_REPLACED,
                message = "$videoId replacement queued (permanent ID unchanged)",
                relatedVideoId = videoId,
                channelId = targetChannel,
                createdAt = now,
            ),
        )
        workerScheduler.scheduleUploadQueueProcessing()
        return task
    }
}

/** Deletes a video. Telegram deletion is explicit and never silent. */
class DeleteVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val telegramMediaRepository: TelegramMediaRepository,
    private val uploadTaskRepository: UploadTaskRepository,
    private val activityRepository: ActivityRepository,
) {
    enum class Mode { LOCAL_ONLY, LOCAL_AND_TELEGRAM }

    suspend operator fun invoke(videoId: String, mode: Mode) {
        val mapping = videoRepository.getMapping(videoId)
        val video = videoRepository.getVideo(videoId)
        val now = Instant.now()

        if (mode == Mode.LOCAL_AND_TELEGRAM) {
            if (mapping != null && mapping.mappingStatus != MappingStatus.NONE) {
                // Deleting the remote message first: if this fails we abort so no
                // record is lost while remote media still exists.
                telegramMediaRepository.deleteMessage(mapping.channelId, mapping.messageId)
            }
        }

        uploadTaskRepository.getActiveTaskForVideo(videoId)?.let { active ->
            uploadTaskRepository.update(active.copy(state = UploadTaskState.CANCELLED, updatedAt = now))
        }
        videoRepository.deleteVideoLocally(videoId)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.VIDEO_DELETED,
                message = "$videoId deleted (${if (mode == Mode.LOCAL_AND_TELEGRAM) "local + Telegram" else "local only"})",
                details = video?.title,
                relatedVideoId = videoId,
                channelId = mapping?.channelId,
                createdAt = now,
            ),
        )
    }
}
