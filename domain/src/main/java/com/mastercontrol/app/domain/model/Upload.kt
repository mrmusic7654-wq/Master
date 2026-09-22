package com.mastercontrol.app.domain.model

import java.time.Instant

/**
 * Durable upload state machine.
 *
 * QUEUED -> PREPARING -> UPLOADING -> VERIFYING -> COMPLETED
 * any of QUEUED/PREPARING/UPLOADING -> FAILED -> (retry) -> RETRYING -> UPLOADING
 * QUEUED/PREPARING/UPLOADING -> CANCELLED (terminal)
 *
 * Permanent failures stop after the configured retry budget is exhausted.
 */
enum class UploadTaskState {
    QUEUED,
    PREPARING,
    UPLOADING,
    VERIFYING,
    RETRYING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

/** One durable upload task. */
data class UploadTask(
    val taskId: Long = 0L,
    val videoId: String,
    val sourceUri: String,
    val fileName: String,
    val channelId: Long? = null,
    val state: UploadTaskState = UploadTaskState.QUEUED,
    val bytesUploaded: Long = 0L,
    val totalBytes: Long? = null,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val progress: Float
        get() = when (state) {
            UploadTaskState.COMPLETED -> 1f
            UploadTaskState.QUEUED, UploadTaskState.PREPARING -> 0f
            UploadTaskState.FAILED, UploadTaskState.CANCELLED -> 0f
            else -> if ((totalBytes ?: 0L) > 0L) (bytesUploaded.toFloat() / totalBytes!!.toFloat()).coerceIn(0f, 1f) else 0f
        }
}

/** Bounded retry policy used by the upload engine. */
data class RetryPolicy(
    val maxAttempts: Int = 5,
    val baseDelayMs: Long = 30_000L,
    val maxDelayMs: Long = 3_600_000L,
) {
    /**
     * Bounded exponential backoff for a 1-indexed [attempt].
     *
     * attempt 1 is the first try and never waits; attempt 2 (the first retry)
     * waits [baseDelayMs], then doubles per attempt until [maxDelayMs].
     */
    fun delayForAttempt(attempt: Int): Long {
        if (attempt <= 1) return 0L
        val exponent = (attempt - 2).coerceIn(0, 10)
        return (baseDelayMs * (1L shl exponent)).coerceAtMost(maxDelayMs)
    }

    /** True when [attempt] has exhausted the retry budget (no further retries). */
    fun isExhausted(attempt: Int): Boolean = attempt >= maxAttempts
}

/** Input handed to the Telegram upload executor by the worker. */
data class UploadJob(
    val taskId: Long,
    val videoId: String,
    val sourceUri: String,
    val channelId: Long,
    val fileName: String,
    val mimeType: String? = null,
    val totalBytes: Long? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val thumbnailPath: String? = null,
    val caption: String? = null,
)

/** Evidence that the upload was verified against Telegram. */
data class UploadReceipt(
    val channelId: Long,
    val messageId: Long,
    val telegramFileId: Int,
    val telegramRemoteFileId: String,
    val telegramUniqueFileId: String,
    val fileSizeBytes: Long,
    val mimeType: String?,
)

/** Progress stream events emitted by the upload executor. */
sealed class UploadProgressEvent {
    data class Preparing(val totalBytes: Long?) : UploadProgressEvent()
    data class Transferring(val bytesUploaded: Long, val totalBytes: Long) : UploadProgressEvent()
    data class Verifying(val messageId: Long? = null) : UploadProgressEvent()
    data class Completed(val receipt: UploadReceipt) : UploadProgressEvent()
    data class Failed(val error: Throwable) : UploadProgressEvent()
}
