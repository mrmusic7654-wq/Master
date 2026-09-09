package com.mastercontrol.app.telegram.tdlib.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Strongly-typed views over the TDLib JSON responses that Master Control
 * consumes. Field names and shapes mirror the pinned TDLib snapshot
 * (third_party/tdlib, td/generate/scheme/td_api.tl) exactly.
 *
 * Requests are built as JsonObject literals (see TdRequests.kt); responses are
 * decoded through these models. Unknown fields are ignored defensively.
 */

@Serializable
data class FileDto(
    @SerialName("id") val id: Int,
    @SerialName("size") val size: Long = 0L,
    @SerialName("expected_size") val expectedSize: Long = 0L,
    @SerialName("local") val local: LocalFileDto? = null,
    @SerialName("remote") val remote: RemoteFileDto? = null,
)

@Serializable
data class LocalFileDto(
    @SerialName("path") val path: String? = null,
    @SerialName("can_be_downloaded") val canBeDownloaded: Boolean = false,
    @SerialName("can_be_deleted") val canBeDeleted: Boolean = false,
    @SerialName("is_downloading_active") val isDownloadingActive: Boolean = false,
    @SerialName("is_downloading_completed") val isDownloadingCompleted: Boolean = false,
)

@Serializable
data class RemoteFileDto(
    @SerialName("id") val id: String? = null,
    @SerialName("unique_id") val uniqueId: String? = null,
    @SerialName("is_uploading_active") val isUploadingActive: Boolean = false,
    @SerialName("is_uploading_completed") val isUploadingCompleted: Boolean = false,
    @SerialName("uploaded_size") val uploadedSize: Long = 0L,
)

@Serializable
data class ChatsDto(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("chat_ids") val chatIds: List<Long> = emptyList(),
)

@Serializable
data class ChatDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("type") val type: ChatTypeDto? = null,
)

@Serializable
data class ChatTypeDto(
    @SerialName("@type") val type: String,
    @SerialName("supergroup_id") val supergroupId: Long? = null,
    @SerialName("is_channel") val isChannel: Boolean = false,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("basic_group_id") val basicGroupId: Long? = null,
)

@Serializable
data class SupergroupDto(
    @SerialName("id") val id: Long,
    @SerialName("status") val status: JsonObject? = null,
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("usernames") val usernames: UsernamesDto? = null,
    @SerialName("is_channel") val isChannel: Boolean = false,
    @SerialName("is_broadcast_group") val isBroadcastGroup: Boolean = false,
)

@Serializable
data class UsernamesDto(
    @SerialName("active_usernames") val activeUsernames: List<String> = emptyList(),
)

@Serializable
data class MessageDto(
    @SerialName("id") val id: Long,
    @SerialName("chat_id") val chatId: Long,
    @SerialName("date") val date: Long,
    @SerialName("content") val content: JsonObject? = null,
)

@Serializable
data class MessagesDto(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("messages") val messages: List<MessageDto> = emptyList(),
)

@Serializable
data class OkDto(@SerialName("@type") val type: String = "ok")

@Serializable
data class UserDto(
    @SerialName("id") val id: Long,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    @SerialName("username") val username: String? = null,
    @SerialName("usernames") val usernames: UsernamesDto? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("type") val type: JsonObject? = null,
)

@Serializable
data class ErrorDto(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String,
)

/** Envelope used to route incoming JSON (both updates and request responses). */
@Serializable
data class TdEnvelope(
    @SerialName("@type") val type: String,
    @SerialName("@extra") val extra: String? = null,
)

/** Video media payload extracted defensively from a message content object. */
data class VideoMediaSnapshot(
    val isVideoMessage: Boolean,
    val fileId: Int? = null,
    val remoteId: String? = null,
    val uniqueId: String? = null,
    val size: Long? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val fileName: String? = null,
    val caption: String? = null,
)

object TdContentParser {

    /** Extracts media info from a message content JsonObject (video messages only). */
    fun parseVideoMessageContent(content: JsonObject?): VideoMediaSnapshot {
        if (content == null) return VideoMediaSnapshot(isVideoMessage = false)
        val type = content["@type"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null } ?: ""
        if (type != "messageVideo") return VideoMediaSnapshot(isVideoMessage = false)
        val video = content["video"] as? JsonObject ?: return VideoMediaSnapshot(isVideoMessage = true)
        val file = video["video"] as? JsonObject
        val remote = file?.get("remote") as? JsonObject
        val local = file?.get("local") as? JsonObject
        return VideoMediaSnapshot(
            isVideoMessage = true,
            fileId = (file?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull(),
            remoteId = (remote?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.content,
            uniqueId = (remote?.get("unique_id") as? kotlinx.serialization.json.JsonPrimitive)?.content,
            size = (file?.get("size") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull(),
            durationMs = ((video["duration"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull())?.times(1000L),
            width = (video["width"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull(),
            height = (video["height"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull(),
            mimeType = (video["mime_type"] as? kotlinx.serialization.json.JsonPrimitive)?.content,
            fileName = (video["file_name"] as? kotlinx.serialization.json.JsonPrimitive)?.content,
            caption = (content["caption"] as? JsonObject)?.get("text")
                ?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null },
        )
    }

    fun primitiveText(obj: JsonObject?, key: String): String? =
        (obj?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content

    fun primitiveLong(obj: JsonObject?, key: String): Long? =
        primitiveText(obj, key)?.toLongOrNull()

    fun primitiveBool(obj: JsonObject?, key: String): Boolean =
        primitiveText(obj, key)?.toBooleanStrictOrNull() ?: false
}
