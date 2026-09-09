package com.mastercontrol.app.domain.model

/** Snapshot of a Telegram message as seen remotely (used by verification/reconciliation). */
data class RemoteMessageRef(
    val channelId: Long,
    val messageId: Long,
    val dateEpochSeconds: Long,
    val isVideo: Boolean,
    val fileSizeBytes: Long? = null,
    val mimeType: String? = null,
    val remoteFileId: String? = null,
    val uniqueFileId: String? = null,
    val captionText: String? = null,
    val telegramFileId: Int? = null,
)

/** Result of verifying that a mapping's message still exists with expected media. */
data class MessageVerification(
    val channelId: Long,
    val messageId: Long,
    val found: Boolean,
    val matchesVideo: Boolean = false,
    val ref: RemoteMessageRef? = null,
    val notes: List<String> = emptyList(),
)

/** Outcome of a reconciliation run. */
data class ReconciliationReport(
    val mappingsChecked: Int = 0,
    val mappingsOk: Int = 0,
    val mappingsStale: Int = 0,
    val mappingsRemoteMissing: Int = 0,
    val unmanagedFound: Int = 0,
    val unmanagedItems: List<RemoteMessageRef> = emptyList(),
)
