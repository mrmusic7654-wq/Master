package com.mastercontrol.app.telegram.tdlib.core

import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ChannelCandidate
import com.mastercontrol.app.domain.model.ChannelKind
import com.mastercontrol.app.domain.model.ChannelPermissions
import com.mastercontrol.app.telegram.tdlib.json.ChatDto
import com.mastercontrol.app.telegram.tdlib.json.ChatsDto
import com.mastercontrol.app.telegram.tdlib.json.SupergroupDto
import com.mastercontrol.app.telegram.tdlib.json.TdRequests
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Channel/supergroup discovery and permission verification through TDLib.
 * Only channels/groups the authenticated account can actually see are returned.
 */
class TdChatManager(private val client: TdClientCore) {

    suspend fun searchChannels(query: String): List<ChannelCandidate> {
        val chatIds = if (query.isBlank()) {
            val chats = client.call(TdRequests.getChats(limit = 200))
            decodeChats(chats)
        } else {
            val chats = client.call(TdRequests.searchChatsOnServer(query, limit = 50))
            decodeChats(chats)
        }
        val results = mutableListOf<ChannelCandidate>()
        for (chatId in chatIds) {
            if (results.size >= 60) break
            val candidate = runCatching { describeChannel(chatId) }.getOrNull() ?: continue
            if (candidate.kind == ChannelKind.SUPERGROUP || candidate.kind == ChannelKind.CHANNEL || candidate.kind == ChannelKind.BROADCAST_GROUP) {
                results += candidate
            }
        }
        return results
    }

    suspend fun verifyPermissions(chatId: Long): ChannelPermissions {
        val chat = chat(chatId) ?: throw AppError.ChannelNotFoundError()
        val supergroupId = chat.type?.supergroupId ?: return ChannelPermissions()
        val supergroup = supergroup(supergroupId) ?: return ChannelPermissions()
        return parsePermissions(supergroup)
    }

    /** Full candidate with member count and username (used before persisting). */
    suspend fun describeChannel(chatId: Long): ChannelCandidate {
        val chat = chat(chatId) ?: throw AppError.ChannelNotFoundError("Channel $chatId not found.")
        val type = chat.type ?: throw AppError.ChannelNotFoundError("Not a group/channel.")
        if (type.supergroupId == null) {
            throw AppError.ChannelNotFoundError("Only groups and channels can be storage locations.")
        }
        val kind = when {
            type.isChannel -> ChannelKind.CHANNEL
            type.supergroupId != null -> ChannelKind.SUPERGROUP
            else -> ChannelKind.SUPERGROUP
        }
        val sg = supergroup(type.supergroupId)
        return ChannelCandidate(
            chatId = chat.id,
            title = chat.title,
            username = sg?.usernames?.activeUsernames?.firstOrNull(),
            kind = if (sg?.isBroadcastGroup == true) ChannelKind.BROADCAST_GROUP else kind,
            permissions = parsePermissions(sg),
            memberCount = sg?.memberCount,
        )
    }

    private suspend fun chat(chatId: Long): ChatDto? {
        val obj = client.call(TdRequests.getChat(chatId))
        return runCatching {
            json().decodeFromJsonElement(ChatDto.serializer(), obj)
        }.getOrNull()
    }

    private suspend fun supergroup(id: Long): SupergroupDto? {
        val obj = client.call(TdRequests.getSupergroup(id))
        return runCatching {
            json().decodeFromJsonElement(SupergroupDto.serializer(), obj)
        }.getOrNull()
    }

    private fun decodeChats(obj: JsonObject): List<Long> {
        val chats = runCatching { json().decodeFromJsonElement(ChatsDto.serializer(), obj) }.getOrNull()
            ?: return emptyList()
        return chats.chatIds
    }

    private fun parsePermissions(supergroup: SupergroupDto?): ChannelPermissions {
        if (supergroup == null) return ChannelPermissions()
        val status = supergroup.status
        val type = status?.get("@type")?.jsonPrimitive?.contentOrNull ?: return ChannelPermissions()
        val isChannel = supergroup.isChannel
        return when (type) {
            "chatMemberStatusCreator" -> ChannelPermissions(isCreator = true, isAdministrator = true,
                canPostMessages = true, canEditMessages = true, canDeleteMessages = true,
                canInviteUsers = true, canChangeInfo = true, canPinMessages = true)
            "chatMemberStatusAdministrator" -> {
                val rights = status["rights"] as? JsonObject
                ChannelPermissions(
                    isAdministrator = true,
                    canPostMessages = bool(rights, "can_post_messages") || !isChannel,
                    canEditMessages = bool(rights, "can_edit_messages"),
                    canDeleteMessages = bool(rights, "can_delete_messages"),
                    canInviteUsers = bool(rights, "can_invite_users"),
                    canChangeInfo = bool(rights, "can_change_info"),
                    canPinMessages = bool(rights, "can_pin_messages"),
                )
            }
            else -> ChannelPermissions(canPostMessages = !isChannel)
        }
    }

    private fun bool(obj: JsonObject?, key: String): Boolean =
        (obj?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false

    private fun json() = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
}
