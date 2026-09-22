package com.mastercontrol.app.domain.usecase

import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import com.mastercontrol.app.domain.model.MappingStatus
import com.mastercontrol.app.domain.model.MessageVerification
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState
import com.mastercontrol.app.domain.model.Video
import com.mastercontrol.app.domain.model.VideoStatus
import com.mastercontrol.app.domain.port.DocumentStore
import com.mastercontrol.app.domain.port.UploadWorkerScheduler
import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.TelegramMediaRepository
import com.mastercontrol.app.domain.repository.UploadTaskRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import java.time.Instant
import javax.inject.Inject

/** Persist metadata edits immediately through Room. */
/**
 * Applies a metadata edit.
 *
 * Callers pass the *final* value for every field the editor owns; a field that
 * was not touched must be passed through unchanged from the current [Video].
 * That keeps "clear this value" and "keep this value" unambiguous: `null`
 * always means "no value", never "unchanged".
 *
 * The permanent [Video.videoId] and the Telegram mapping are never affected by
 * a metadata edit.
 */
class EditVideoMetadataUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val activityRepository: ActivityRepository,
) {
    suspend operator fun invoke(
        video: Video,
        title: String,
        description: String,
        categoryId: Long?,
        folderId: Long?,
        tags: List<String>,
        year: Int?,
        language: String?,
        rating: Float?,
        releaseDate: String?,
    ): Video {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            throw AppError.ValidationError("A title is required. The file name can be used if nothing else is known.")
        }
        year?.let { value ->
            if (value < MIN_YEAR || value > MAX_YEAR) {
                throw AppError.ValidationError("The year must be between $MIN_YEAR and $MAX_YEAR.")
            }
        }
        rating?.let { value ->
            if (value < 0f || value > MAX_RATING) {
                throw AppError.ValidationError("The rating must be between 0 and ${MAX_RATING.toInt()}.")
            }
        }
        val cleanedTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val updated = video.copy(
            title = trimmedTitle,
            description = description.trim(),
            categoryId = categoryId,
            folderId = folderId,
            tags = cleanedTags,
            year = year,
            language = language?.trim()?.ifEmpty { null },
            rating = rating,
            releaseDate = releaseDate?.trim()?.ifEmpty { null },
            updatedAt = Instant.now(),
        )
        videoRepository.updateVideo(updated)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.METADATA_EDITED,
                message = "${video.videoId} metadata updated",
                details = changedFields(video, updated).ifEmpty { null },
                relatedVideoId = video.videoId,
                createdAt = updated.updatedAt,
            ),
        )
        return updated
    }

    private fun changedFields(before: Video, after: Video): String = buildList {
        if (before.title != after.title) add("title")
        if (before.description != after.description) add("description")
        if (before.categoryId != after.categoryId) add("category")
        if (before.folderId != after.folderId) add("folder")
        if (before.tags != after.tags) add("tags")
        if (before.year != after.year) add("year")
        if (before.language != after.language) add("language")
        if (before.rating != after.rating) add("rating")
        if (before.releaseDate != after.releaseDate) add("release date")
    }.joinToString(", ")

    private companion object {
        const val MIN_YEAR = 1800
        const val MAX_YEAR = 2200
        const val MAX_RATING = 5f
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
    private val documentStore: DocumentStore,
) {
    suspend operator fun invoke(videoId: String, replacementUri: String, channelId: Long?): UploadTask {
        val video = videoRepository.getVideo(videoId)
            ?: throw AppError.NotFoundError("Video $videoId does not exist.")
        // The replacement is queued, not uploaded inline: the picker's grant must
        // be made durable or the worker may lose access before it runs.
        val durableAccess = documentStore.persistReadAccess(replacementUri)
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
                details = if (durableAccess) {
                    null
                } else {
                    "Read access to the replacement file could not be made durable; " +
                        "re-pick the file if the upload fails after a reboot."
                },
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
    private val documentStore: DocumentStore,
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
        // Nothing references the source file any more, so the persisted read
        // grant is given back instead of lingering for the app's lifetime.
        video?.sourceUri?.let { documentStore.releasePersistedAccess(it) }
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

/**
 * Verifies one video's Telegram mapping on demand.
 *
 * The permanent video ID is never changed here. Only the mapping status is
 * updated to reflect what Telegram actually reports right now: ACTIVE when the
 * message exists and still matches, STALE when the media behind it changed,
 * REMOTE_DELETED when the message is gone.
 */
class VerifyVideoMappingUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val telegramMediaRepository: TelegramMediaRepository,
    private val activityRepository: ActivityRepository,
) {
    suspend operator fun invoke(videoId: String): MessageVerification {
        val mapping = videoRepository.getMapping(videoId)
            ?: throw AppError.NotFoundError("Video $videoId has no Telegram mapping to verify.")
        if (mapping.mappingStatus == MappingStatus.NONE) {
            throw AppError.ValidationError("Video $videoId was never uploaded to Telegram.")
        }
        val now = Instant.now()
        val verification = telegramMediaRepository.verifyMessage(mapping.channelId, mapping.messageId)
        when {
            !verification.found -> {
                videoRepository.setMappingStatus(videoId, MappingStatus.REMOTE_DELETED)
                activityRepository.add(
                    ActivityLogEntry(
                        type = ActivityType.MAPPING_STALE,
                        message = "$videoId: Telegram message ${mapping.messageId} is missing",
                        relatedVideoId = videoId,
                        channelId = mapping.channelId,
                        createdAt = now,
                    ),
                )
            }

            !verification.matchesVideo -> {
                videoRepository.setMappingStatus(videoId, MappingStatus.STALE)
                activityRepository.add(
                    ActivityLogEntry(
                        type = ActivityType.MAPPING_STALE,
                        message = "$videoId: Telegram media changed (size or file mismatch)",
                        details = verification.notes.joinToString(" ").ifBlank { null },
                        relatedVideoId = videoId,
                        channelId = mapping.channelId,
                        createdAt = now,
                    ),
                )
            }

            else -> {
                videoRepository.setMappingStatus(videoId, MappingStatus.ACTIVE)
                activityRepository.add(
                    ActivityLogEntry(
                        type = ActivityType.MAPPING_VERIFIED,
                        message = "$videoId: Telegram message ${mapping.messageId} confirmed",
                        relatedVideoId = videoId,
                        channelId = mapping.channelId,
                        createdAt = now,
                    ),
                )
            }
        }
        return verification
    }
}
