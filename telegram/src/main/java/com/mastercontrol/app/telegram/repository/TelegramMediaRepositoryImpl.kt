package com.mastercontrol.app.telegram.repository

import com.mastercontrol.app.domain.model.MessageVerification
import com.mastercontrol.app.domain.model.RemoteMessageRef
import com.mastercontrol.app.domain.repository.TelegramMediaRepository
import com.mastercontrol.app.telegram.tdlib.manager.TdLibManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramMediaRepositoryImpl @Inject constructor(
    manager: TdLibManager,
) : TelegramMediaRepository {

    private val messageManager = manager.messageManager

    override suspend fun verifyMessage(channelId: Long, messageId: Long): MessageVerification =
        messageManager.verifyMessage(channelId, messageId)

    override suspend fun deleteMessage(channelId: Long, messageId: Long) =
        messageManager.deleteMessage(channelId, messageId)

    override suspend fun fetchRecentMessages(
        channelId: Long,
        fromMessageId: Long,
        offset: Int,
        limit: Int,
    ): List<RemoteMessageRef> = messageManager.fetchRecentMessages(channelId, fromMessageId, offset, limit)
}
