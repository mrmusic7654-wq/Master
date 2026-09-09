package com.mastercontrol.app.domain.model

/** UI appearance. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Layout preference of the library screen. */
enum class LibraryLayout { GRID, LIST, COMPACT }

/** Sort keys offered by the library. */
enum class LibrarySort {
    DATE_ADDED_DESC,
    DATE_ADDED_ASC,
    TITLE_ASC,
    TITLE_DESC,
    DURATION_DESC,
    SIZE_DESC,
    YEAR_DESC,
    STATUS_ASC,
}

/** Where uploads are allowed to run. */
enum class UploadNetworkRule {
    ANY_NETWORK,
    WIFI_ONLY,
}

/** Network state as observed by the connectivity monitor. */
enum class NetworkStatus { OFFLINE, WIFI, METERED }

/** Thumbnail generation quality. */
enum class ThumbnailQuality { STANDARD, HIGH }

/**
 * Non-sensitive application settings persisted with DataStore.
 * Sensitive values (API hash, TDLib key, PIN) are never stored here.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val librarySort: LibrarySort = LibrarySort.DATE_ADDED_DESC,
    val uploadNetworkRule: UploadNetworkRule = UploadNetworkRule.ANY_NETWORK,
    val allowUploadsOnlyWhileCharging: Boolean = false,
    val maxConcurrentUploads: Int = 1,
    val thumbnailQuality: ThumbnailQuality = ThumbnailQuality.STANDARD,
    val thumbnailCaptureMs: Long = 1_000L,
    val captionIncludesVideoId: Boolean = true,
    val reconciliationEnabled: Boolean = true,
    val reconciliationIntervalHours: Int = 12,
    val mediaHashingEnabled: Boolean = true,
    val autoDeleteLocalCopyAfterUpload: Boolean = false,
    val diagnosticsEnabled: Boolean = true,
)
