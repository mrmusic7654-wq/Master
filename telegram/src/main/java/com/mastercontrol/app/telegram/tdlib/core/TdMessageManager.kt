package com.mastercontrol.app.telegram.tdlib.core

import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.MessageVerification
import com.mastercontrol.app.domain.model.RemoteMessageRef
import com.mastercontrol.app.telegram.tdlib.json.MessageDto
import com.mastercontrol.app.telegram.tdlib.json.MessagesDto
import com.mastercontrol.app.telegram.tdlib.json.TdContentParser
import com.mastercontrol.app.telegram.tdlib.json.TdRequests
import kotlinx.serialization.json.Json

/** Message-level operations: verification, deletion, history scans, sending. */
class TdMessageManager(private val client: TdClientCore) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun verifyMessage(channelId: Long, messageId: Long): MessageVerification {
        val message = try {
            val obj = client.call(TdRequests.getMessage(channelId, messageId))
            decodeMessage(obj)
        } catch (t: AppError.TelegramMessageNotFoundError) {
            return MessageVerification(channelId, messageId, found = false)
        } catch (t: AppError) {
            if (t is AppError.ChannelNotFoundError) {
                return MessageVerification(channelId, messageId, found = false)
            }
            throw t
        }
        val media = TdContentParser.parseVideoMessageContent(message.content)
        return MessageVerification(
            channelId = channelId,
            messageId = messageId,
            found = true,
            matchesVideo = media.isVideoMessage && media.remoteId != null,
            ref = RemoteMessageRef(
                channelId = channelId,
                messageId = message.id,
                dateEpochSeconds = message.date,
                isVideo = media.isVideoMessage,
                fileSizeBytes = media.size,
                mimeType = media.mimeType,
                remoteFileId = media.remoteId,
                uniqueFileId = media.uniqueId,
                captionText = media.caption,
                telegramFileId = media.fileId,
            ),
        )
    }

    suspend fun deleteMessage(channelId: Long, messageId: Long) {
        client.call(TdRequests.deleteMessages(channelId, listOf(messageId)))
    }

    suspend fun fetchRecentMessages(
        channelId: Long,
        fromMessageId: Long,
        offset: Int,
        limit: Int,
    ): List<RemoteMessageRef> {
        val obj = client.call(
            TdRequests.getChatHistory(
                chatId = channelId,
                fromMessageId = fromMessageId,
                offset = offset,
                limit = limit.coerceIn(1, 100),
            ),
        )
        val messages = runCatching { json.decodeFromJsonElement(MessagesDto.serializer(), obj) }.getOrNull()
            ?: return emptyList()
        return messages.messages.mapNotNull { message ->
            val media = TdContentParser.parseVideoMessageContent(message.content)
            if (!media.isVideoMessage) return@mapNotNull null
            RemoteMessageRef(
                channelId = message.chatId,
                messageId = message.id,
                dateEpochSeconds = message.date,
                isVideo = true,
                fileSizeBytes = media.size,
                mimeType = media.mimeType,
                remoteFileId = media.remoteId,
                uniqueFileId = media.uniqueId,
                captionText = media.caption,
                telegramFileId = media.fileId,
            )
        }
    }

    private fun decodeMessage(obj: kotlinx.serialization.json.JsonObject): MessageDto =
        json.decodeFromJsonElement(MessageDto.serializer(), obj)
}
