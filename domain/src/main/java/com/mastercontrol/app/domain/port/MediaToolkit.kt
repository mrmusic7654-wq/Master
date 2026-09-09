package com.mastercontrol.app.domain.port

/**
 * Device-side media operations needed by import. Implemented in the Android
 * data layer with MediaMetadataRetriever/MediaExtractor/ContentResolver.
 * Deliberately free of Android types so domain code stays testable.
 */
data class MediaSourceInfo(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val frameRate: Double?,
    val hasVideoTrack: Boolean,
)

data class GeneratedThumbnail(
    val fileUri: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
)

interface MediaToolkit {

    /**
     * Inspects a content URI without loading the file into memory.
     * Throws InvalidMediaError when the file is missing/unreadable/not video.
     */
    suspend fun inspect(contentUri: String): MediaSourceInfo

    /**
     * Generates a compressed JPEG/WebP thumbnail at [captureMs]; falls back to
     * [fallbackCaptureMs] when that frame cannot be decoded. Cancellation is
     * cooperative: the caller cancelling the coroutine aborts decoding.
     * The returned file is managed by the implementation's thumbnail store.
     */
    suspend fun generateThumbnail(
        contentUri: String,
        captureMs: Long,
        fallbackCaptureMs: Long,
    ): GeneratedThumbnail

    /** Streaming SHA-256; never loads the file into memory. */
    suspend fun computeSha256(contentUri: String, isActive: () -> Boolean = { true }): String
}
