package com.mastercontrol.app.domain.error

/**
 * Structured error hierarchy for Master Control.
 *
 * Raw exceptions (TDLib errors, IOExceptions, SQLite exceptions ...) are never shown to the
 * user. Every subsystem maps failures into one of these types, and the UI renders [userMessage].
 * Technical details are available through the exception [message]/[cause] and are only emitted
 * into the local diagnostics log.
 */
sealed class AppError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Human-readable, non-technical description that can be shown in the UI. */
    open val userMessage: String = message ?: "An unexpected error occurred."

    // ---- Telegram ---------------------------------------------------------

    class TelegramAuthenticationError(
        detail: String = "Telegram authorization failed.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "Telegram authorization failed. Please re-authenticate."
    }

    class TelegramConnectionError(
        detail: String = "Could not reach Telegram.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "Could not reach Telegram. Check your connection and retry."
    }

    class TelegramPermissionError(
        val missingPermission: String? = null,
        detail: String = "The account lacks the required channel permissions.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = buildString {
            append("Telegram permission missing.")
            missingPermission?.let { append(" Missing: $it.") }
        }
    }

    class TelegramRateLimitError(
        val retryAfterSeconds: Long? = null,
        detail: String = "Telegram rate limit reached.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String =
            retryAfterSeconds?.let { "Telegram is rate limiting requests. Try again in ${it}s." }
                ?: "Telegram is rate limiting requests. Please wait before retrying."
    }

    class TelegramFileError(
        detail: String = "Telegram could not process the file.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "Telegram could not process the file."
    }

    class TelegramMessageNotFoundError(
        detail: String = "The Telegram message could not be found.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "The Telegram message could not be found. It may have been deleted."
    }

    // ---- Upload -----------------------------------------------------------

    class UploadFailedError(
        val canRetry: Boolean = true,
        detail: String = "Upload failed.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "Upload failed: ${detail.take(160)}"
    }

    class UploadCancelledError(
        detail: String = "Upload cancelled.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "Upload cancelled."
    }

    // ---- Storage / media --------------------------------------------------

    class StorageError(
        detail: String = "Local storage error.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "Local storage error: ${detail.take(160)}"
    }

    class DatabaseError(
        detail: String = "Local database error.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "The local catalog could not be updated."
    }

    class InvalidMediaError(
        detail: String = "The selected file is not a supported video.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "The selected file is not a supported video."
    }

    class MediaUnavailableError(
        detail: String = "The file is no longer available.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "The file is no longer accessible on this device."
    }

    // ---- Input / validation -----------------------------------------------

    class CredentialValidationError(
        override val userMessage: String,
        cause: Throwable? = null,
    ) : AppError(userMessage, cause)

    /**
     * An operation was refused because its preconditions are not met.
     *
     * Used for guard rails that protect the catalog (for example: a disabled
     * channel cannot be the default upload target). The message tells the
     * operator exactly what to do next.
     */
    class ValidationError(
        override val userMessage: String,
        cause: Throwable? = null,
    ) : AppError(userMessage, cause)

    class ChannelNotFoundError(
        detail: String = "Channel not found.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "The requested Telegram channel was not found."
    }

    class CatalogImportError(
        detail: String = "The catalog backup could not be imported.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "The catalog backup could not be imported: ${detail.take(160)}"
    }

    class CatalogExportError(
        detail: String = "The catalog backup could not be exported.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "The catalog backup could not be exported: ${detail.take(160)}"
    }

    class NotFoundError(
        detail: String = "The requested item does not exist.",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val userMessage: String = "The requested item does not exist."
    }
}
