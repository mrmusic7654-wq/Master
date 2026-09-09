package com.mastercontrol.app.domain.model

import java.time.Instant

/** Lifecycle of a catalog video. Upload progress lives on the [UploadTask]. */
enum class VideoStatus {
    /** Metadata/thumbnail processing still running after import. */
    IMPORTING,

    /** Imported and ready; nothing queued yet. */
    READY,

    /** At least one upload task is queued or running. */
    UPLOADING,

    /** Upload finished and the Telegram mapping was verified and committed. */
    COMPLETE,

    /** The most recent upload attempt permanently failed. */
    FAILED,
}

/** Persistence status of the Video -> Telegram message mapping. */
enum class MappingStatus {
    NONE,
    PENDING_VERIFY,
    ACTIVE,
    STALE,
    REPLACED,
    REMOTE_DELETED,
}

/**
 * A catalog video.
 *
 * [videoId] is the permanent human-readable identifier (e.g. VID-000001) and never changes,
 * even when the underlying Telegram message is replaced.
 */
data class Video(
    val videoId: String,
    val title: String,
    val originalFileName: String,
    val description: String = "",
    val durationMs: Long? = null,
    val fileSizeBytes: Long? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val thumbnailUri: String? = null,
    val posterUri: String? = null,
    val sourceUri: String,
    val releaseDate: String? = null,
    val language: String? = null,
    val rating: Float? = null,
    val year: Int? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: VideoStatus,
    val categoryId: Long? = null,
    val folderId: Long? = null,
    val tags: List<String> = emptyList(),
    val sha256: String? = null,
    val hashPending: Boolean = false,
)

/** The Video -> Telegram message mapping. Exactly one active mapping per video at a time. */
data class TelegramMapping(
    val videoId: String,
    val channelId: Long,
    val messageId: Long,
    val telegramFileId: Int? = null,
    val telegramRemoteFileId: String? = null,
    val telegramUniqueFileId: String? = null,
    val telegramLocalFilePath: String? = null,
    val fileSizeBytes: Long? = null,
    val mimeType: String? = null,
    val uploadedAt: Instant,
    val updatedAt: Instant,
    val mappingStatus: MappingStatus,
)

data class Category(
    val categoryId: Long,
    val name: String,
    val description: String = "",
    val icon: String = "",
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Folder(
    val folderId: Long,
    val name: String,
    val parentFolderId: Long? = null,
    val description: String = "",
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)
