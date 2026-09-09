package com.mastercontrol.app.domain.model

import java.time.Instant

/** Local operational activity log entries. */
enum class ActivityType {
    VIDEO_IMPORTED,
    UPLOAD_QUEUED,
    UPLOAD_STARTED,
    UPLOAD_COMPLETED,
    UPLOAD_FAILED,
    UPLOAD_CANCELLED,
    UPLOAD_RETRYING,
    METADATA_EDITED,
    THUMBNAIL_CHANGED,
    VIDEO_REPLACED,
    VIDEO_DELETED,
    CHANNEL_ADDED,
    CHANNEL_REMOVED,
    CHANNEL_DEFAULT_CHANGED,
    CHANNEL_VERIFIED,
    TELEGRAM_CONNECTED,
    TELEGRAM_CONNECTION_RESTORED,
    TELEGRAM_DISCONNECTED,
    MAPPING_VERIFIED,
    MAPPING_STALE,
    UNMANAGED_TELEGRAM_MEDIA_FOUND,
    RECONCILIATION_RAN,
    BACKUP_EXPORTED,
    BACKUP_IMPORTED,
    APP_LOCK_ENABLED,
    APP_LOCK_DISABLED,
    CREDENTIALS_UPDATED,
    AUTHENTICATED,
    LOGGED_OUT,
    ERROR_RECOVERED,
}

data class ActivityLogEntry(
    val activityId: Long = 0L,
    val type: ActivityType,
    val message: String,
    val details: String? = null,
    val relatedVideoId: String? = null,
    val channelId: Long? = null,
    val createdAt: Instant,
)
