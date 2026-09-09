package com.mastercontrol.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "categoryId") val categoryId: Long = 0L,
    val name: String,
    val description: String = "",
    val icon: String = "",
    @ColumnInfo(name = "sortOrder") val sortOrder: Int = 0,
    @ColumnInfo(name = "createdAtEpochMs") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updatedAtEpochMs") val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["parentFolderId"]),
    ],
    foreignKeys = [
        ForeignKey(entity = FolderEntity::class, parentColumns = ["folderId"], childColumns = ["parentFolderId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "folderId") val folderId: Long = 0L,
    val name: String,
    @ColumnInfo(name = "parentFolderId") val parentFolderId: Long? = null,
    val description: String = "",
    @ColumnInfo(name = "sortOrder") val sortOrder: Int = 0,
    @ColumnInfo(name = "createdAtEpochMs") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updatedAtEpochMs") val updatedAtEpochMs: Long,
)

/** A Telegram storage channel configured in Master Control (local row). */
@Entity(
    tableName = "channels",
    indices = [Index(value = ["isDefault"])],
)
data class ChannelEntity(
    @PrimaryKey @ColumnInfo(name = "channelId") val channelId: Long,
    val title: String,
    val username: String? = null,
    val kind: String,
    @ColumnInfo(name = "isCreator") val isCreator: Boolean = false,
    @ColumnInfo(name = "isAdministrator") val isAdministrator: Boolean = false,
    @ColumnInfo(name = "canPostMessages") val canPostMessages: Boolean = false,
    @ColumnInfo(name = "canEditMessages") val canEditMessages: Boolean = false,
    @ColumnInfo(name = "canDeleteMessages") val canDeleteMessages: Boolean = false,
    @ColumnInfo(name = "canInviteUsers") val canInviteUsers: Boolean = false,
    @ColumnInfo(name = "canChangeInfo") val canChangeInfo: Boolean = false,
    @ColumnInfo(name = "canPinMessages") val canPinMessages: Boolean = false,
    @ColumnInfo(name = "localLabel") val localLabel: String? = null,
    val enabled: Boolean = true,
    @ColumnInfo(name = "isDefault") val isDefault: Boolean = false,
    @ColumnInfo(name = "lastVerifiedAtEpochMs") val lastVerifiedAtEpochMs: Long? = null,
    @ColumnInfo(name = "lastSynchronizedAtEpochMs") val lastSynchronizedAtEpochMs: Long? = null,
    @ColumnInfo(name = "createdAtEpochMs") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updatedAtEpochMs") val updatedAtEpochMs: Long,
)

/** Durable upload queue row. */
@Entity(
    tableName = "upload_tasks",
    indices = [
        Index(value = ["videoId"]),
        Index(value = ["state"]),
        Index(value = ["channelId"]),
    ],
    foreignKeys = [
        ForeignKey(entity = VideoEntity::class, parentColumns = ["videoId"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class UploadTaskEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "taskId") val taskId: Long = 0L,
    @ColumnInfo(name = "videoId") val videoId: String,
    @ColumnInfo(name = "sourceUri") val sourceUri: String,
    @ColumnInfo(name = "fileName") val fileName: String,
    @ColumnInfo(name = "channelId") val channelId: Long? = null,
    val state: String,
    @ColumnInfo(name = "bytesUploaded") val bytesUploaded: Long = 0L,
    @ColumnInfo(name = "totalBytes") val totalBytes: Long? = null,
    @ColumnInfo(name = "attemptCount") val attemptCount: Int = 0,
    @ColumnInfo(name = "lastError") val lastError: String? = null,
    @ColumnInfo(name = "createdAtEpochMs") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updatedAtEpochMs") val updatedAtEpochMs: Long,
)

/** Local activity log row. */
@Entity(
    tableName = "activity_log",
    indices = [
        Index(value = ["type"]),
        Index(value = ["createdAtEpochMs"]),
    ],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "activityId") val activityId: Long = 0L,
    val type: String,
    val message: String,
    val details: String? = null,
    @ColumnInfo(name = "relatedVideoId") val relatedVideoId: String? = null,
    @ColumnInfo(name = "channelId") val channelId: Long? = null,
    @ColumnInfo(name = "createdAtEpochMs") val createdAtEpochMs: Long,
)

/**
 * Transactional counter backing the permanent Video ID allocator. A single
 * row is incremented inside the same Room transaction that inserts the video,
 * making allocation collision-safe.
 */
@Entity(tableName = "video_id_counter")
data class VideoIdCounterEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "nextSequence") val nextSequence: Long = 0L,
)
