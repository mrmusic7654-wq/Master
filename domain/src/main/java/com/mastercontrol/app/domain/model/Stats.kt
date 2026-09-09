package com.mastercontrol.app.domain.model

/**
 * Dashboard statistics computed from real data.
 *
 * Local storage statistics (media files present on this device) are kept
 * distinct from Telegram-referenced storage (bytes stored remotely in
 * Telegram). Master Control never fabricates Telegram quota numbers.
 */
data class LibraryStatistics(
    val totalVideos: Int = 0,
    val totalLocalMediaBytes: Long = 0L,
    val totalTelegramReferencedBytes: Long = 0L,
    val completedUploads: Int = 0,
    val pendingUploads: Int = 0,
    val failedUploads: Int = 0,
    val uploadsInProgress: Int = 0,
    val totalChannels: Int = 0,
    val totalCategories: Int = 0,
    val totalFolders: Int = 0,
    val totalTags: Int = 0,
    val totalLocalThumbnails: Int = 0,
    val pendingUploadBytes: Long = 0L,
    val completedMediaCount: Int = 0,
    val remoteDeletedCount: Int = 0,
    val recentActivity: List<ActivityLogEntry> = emptyList(),
)
