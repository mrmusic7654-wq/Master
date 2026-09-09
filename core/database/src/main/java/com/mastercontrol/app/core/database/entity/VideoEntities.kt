package com.mastercontrol.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Catalog video row. [id] is an internal auto-increment key; [videoId] is the
 * permanent, immutable human-readable ID (VID-000001) used everywhere in the
 * domain and by the future Streamer. Video IDs are never reused after delete.
 */
@Entity(
    tableName = "videos",
    indices = [
        Index(value = ["videoId"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["folderId"]),
        Index(value = ["status"]),
        Index(value = ["sha256"]),
    ],
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["categoryId"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = FolderEntity::class, parentColumns = ["folderId"], childColumns = ["folderId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "videoId", collate = ColumnInfo.NOCASE) val videoId: String,
    val title: String,
    @ColumnInfo(name = "originalFileName") val originalFileName: String,
    val description: String = "",
    @ColumnInfo(name = "durationMs") val durationMs: Long? = null,
    @ColumnInfo(name = "fileSizeBytes") val fileSizeBytes: Long? = null,
    @ColumnInfo(name = "mimeType") val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @ColumnInfo(name = "frameRate") val frameRate: Double? = null,
    @ColumnInfo(name = "thumbnailUri") val thumbnailUri: String? = null,
    @ColumnInfo(name = "posterUri") val posterUri: String? = null,
    @ColumnInfo(name = "sourceUri") val sourceUri: String,
    @ColumnInfo(name = "releaseDate") val releaseDate: String? = null,
    val language: String? = null,
    val rating: Float? = null,
    val year: Int? = null,
    @ColumnInfo(name = "createdAtEpochMs") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updatedAtEpochMs") val updatedAtEpochMs: Long,
    val status: String,
    @ColumnInfo(name = "categoryId") val categoryId: Long? = null,
    @ColumnInfo(name = "folderId") val folderId: Long? = null,
    val sha256: String? = null,
    @ColumnInfo(name = "hashPending") val hashPending: Boolean = false,
)

/** The Video -> Telegram message mapping (exactly one active mapping per video). */
@Entity(
    tableName = "telegram_mappings",
    primaryKeys = ["videoId"],
    indices = [
        Index(value = ["channelId", "messageId"], unique = true),
        Index(value = ["channelId"]),
        Index(value = ["mappingStatus"]),
    ],
    foreignKeys = [
        ForeignKey(entity = VideoEntity::class, parentColumns = ["videoId"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE),
        // Note: deliberately no FK to channels — removing a channel from Master
        // Control must not cascade/block existing historical mappings.
    ],
)
data class TelegramMappingEntity(
    @ColumnInfo(name = "videoId") val videoId: String,
    @ColumnInfo(name = "channelId") val channelId: Long,
    @ColumnInfo(name = "messageId") val messageId: Long,
    @ColumnInfo(name = "telegramFileId") val telegramFileId: Int? = null,
    @ColumnInfo(name = "telegramRemoteFileId") val telegramRemoteFileId: String? = null,
    @ColumnInfo(name = "telegramUniqueFileId") val telegramUniqueFileId: String? = null,
    @ColumnInfo(name = "telegramLocalFilePath") val telegramLocalFilePath: String? = null,
    @ColumnInfo(name = "fileSizeBytes") val fileSizeBytes: Long? = null,
    @ColumnInfo(name = "mimeType") val mimeType: String? = null,
    @ColumnInfo(name = "uploadedAtEpochMs") val uploadedAtEpochMs: Long,
    @ColumnInfo(name = "updatedAtEpochMs") val updatedAtEpochMs: Long,
    @ColumnInfo(name = "mappingStatus") val mappingStatus: String,
)

/**
 * Normalized tags (no comma-separated strings). The tag is stored lower-cased;
 * the original casing of a tag is not part of identity.
 */
@Entity(
    tableName = "video_tags",
    primaryKeys = ["videoId", "tag"],
    indices = [Index(value = ["tag"])],
    foreignKeys = [
        ForeignKey(entity = VideoEntity::class, parentColumns = ["videoId"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class VideoTagEntity(
    @ColumnInfo(name = "videoId") val videoId: String,
    val tag: String,
)
