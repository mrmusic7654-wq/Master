package com.mastercontrol.app.domain.repository

import com.mastercontrol.app.domain.model.MessageVerification
import com.mastercontrol.app.domain.model.RemoteMessageRef

/**
 * Low-level Telegram media/message operations used by upload, delete,
 * replace and reconciliation. Implemented by the telegram module over TDLib.
 */
interface TelegramMediaRepository {

    /** Confirms a message still exists and describes its media (never fabricates results). */
    suspend fun verifyMessage(channelId: Long, messageId: Long): MessageVerification

    /** Permanently deletes a message owned by the account. */
    suspend fun deleteMessage(channelId: Long, messageId: Long)

    /**
     * Reads a window of messages from a channel (newest-first). Used by the
     * reconciliation engine to find "Unmanaged Telegram Media Found".
     */
    suspend fun fetchRecentMessages(
        channelId: Long,
        fromMessageId: Long,
        offset: Int,
        limit: Int,
    ): List<RemoteMessageRef>
}
