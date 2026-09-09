package com.mastercontrol.app.telegram.tdlib.json

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Request builders matching the pinned TDLib snapshot scheme. Fields are
 * snake_case exactly as TDLib's JSON interface expects them.
 */
object TdRequests {

    fun setTdlibParameters(
        databaseDirectory: String,
        filesDirectory: String,
        databaseEncryptionKeyB64: String,
        apiId: Long,
        apiHash: String,
        systemLanguageCode: String,
        deviceModel: String,
        systemVersion: String,
        applicationVersion: String,
        useTestDc: Boolean = false,
    ): JsonObject = buildJsonObject {
        put("@type", "setTdlibParameters")
        put("use_test_dc", useTestDc)
        put("database_directory", databaseDirectory)
        put("files_directory", filesDirectory)
        put("database_encryption_key", databaseEncryptionKeyB64)
        put("use_file_database", true)
        put("use_chat_info_database", true)
        put("use_message_database", false)
        put("use_secret_chats", false)
        put("api_id", apiId)
        put("api_hash", apiHash)
        put("system_language_code", systemLanguageCode)
        put("device_model", deviceModel)
        put("system_version", systemVersion)
        put("application_version", applicationVersion)
    }

    fun getAuthorizationState(): JsonObject = buildJsonObject { put("@type", "getAuthorizationState") }

    fun setAuthenticationPhoneNumber(phoneNumber: String): JsonObject = buildJsonObject {
        put("@type", "setAuthenticationPhoneNumber")
        put("phone_number", phoneNumber)
        put("settings", buildJsonObject {
            put("@type", "phoneNumberAuthenticationSettings")
            put("allow_flash_call", false)
            put("allow_missed_call", false)
            put("is_current_phone_number", false)
            put("has_unknown_phone_number", false)
            put("allow_sms_retriever_api", false)
            putJsonArray("authentication_tokens") {}
        })
    }

    fun checkAuthenticationCode(code: String): JsonObject = buildJsonObject {
        put("@type", "checkAuthenticationCode")
        put("code", code)
    }

    fun checkAuthenticationPassword(password: String): JsonObject = buildJsonObject {
        put("@type", "checkAuthenticationPassword")
        put("password", password)
    }

    fun resendAuthenticationCode(): JsonObject = buildJsonObject {
        put("@type", "resendAuthenticationCode")
    }

    fun getMe(): JsonObject = buildJsonObject { put("@type", "getMe") }

    fun logOut(): JsonObject = buildJsonObject { put("@type", "logOut") }

    fun close(): JsonObject = buildJsonObject { put("@type", "close") }

    fun destroy(): JsonObject = buildJsonObject { put("@type", "destroy") }

    fun getOption(name: String): JsonObject = buildJsonObject {
        put("@type", "getOption")
        put("name", name)
    }

    fun getChats(limit: Int): JsonObject = buildJsonObject {
        put("@type", "getChats")
        put("chat_list", buildJsonObject { put("@type", "chatListMain") })
        put("limit", limit)
    }

    fun searchChatsOnServer(query: String, limit: Int): JsonObject = buildJsonObject {
        put("@type", "searchChatsOnServer")
        put("query", query)
        put("type_filter", buildJsonObject { put("@type", "searchChatTypeFilterChannel") })
        put("limit", limit)
    }

    fun getChat(chatId: Long): JsonObject = buildJsonObject {
        put("@type", "getChat")
        put("chat_id", chatId)
    }

    fun getSupergroup(supergroupId: Long): JsonObject = buildJsonObject {
        put("@type", "getSupergroup")
        put("supergroup_id", supergroupId)
    }

    fun getChatHistory(
        chatId: Long,
        fromMessageId: Long,
        offset: Int,
        limit: Int,
    ): JsonObject = buildJsonObject {
        put("@type", "getChatHistory")
        put("chat_id", chatId)
        put("from_message_id", fromMessageId)
        put("offset", offset)
        put("limit", limit)
        put("only_local", false)
    }

    fun getMessage(chatId: Long, messageId: Long): JsonObject = buildJsonObject {
        put("@type", "getMessage")
        put("chat_id", chatId)
        put("message_id", messageId)
    }

    fun deleteMessages(chatId: Long, messageIds: List<Long>): JsonObject = buildJsonObject {
        put("@type", "deleteMessages")
        put("chat_id", chatId)
        putJsonArray("message_ids") { messageIds.forEach { add(it) } }
        put("revoke", true)
    }

    fun sendMessageVideo(
        chatId: Long,
        uploadedFileId: Int,
        caption: String,
        durationSeconds: Long?,
        width: Int?,
        height: Int?,
        supportsStreaming: Boolean = true,
        thumbnailFileId: Int? = null,
    ): JsonObject = buildJsonObject {
        put("@type", "sendMessage")
        put("chat_id", chatId)
        put("topic_id", kotlinx.serialization.json.JsonNull)
        put("reply_to", kotlinx.serialization.json.JsonNull)
        put("options", kotlinx.serialization.json.JsonNull)
        put("reply_markup", kotlinx.serialization.json.JsonNull)
        putJsonObject("input_message_content") {
            put("@type", "inputMessageVideo")
            putJsonObject("video") {
                put("@type", "inputVideo")
                putJsonObject("video") {
                    put("@type", "inputFileId")
                    put("id", uploadedFileId)
                }
                if (thumbnailFileId != null) {
                    putJsonObject("thumbnail") {
                        put("@type", "inputThumbnail")
                        putJsonObject("thumbnail") {
                            put("@type", "inputFileId")
                            put("id", thumbnailFileId)
                        }
                        put("width", 320)
                        put("height", 180)
                    }
                } else {
                    put("thumbnail", kotlinx.serialization.json.JsonNull)
                }
                put("cover", kotlinx.serialization.json.JsonNull)
                put("start_timestamp", 0)
                putJsonArray("added_sticker_file_ids") {}
                put("duration", (durationSeconds ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()))
                put("width", width ?: 0)
                put("height", height ?: 0)
                put("supports_streaming", supportsStreaming)
            }
            putJsonObject("caption") {
                put("@type", "formattedText")
                put("text", caption)
                putJsonArray("entities") {}
            }
            put("show_caption_above_media", false)
            put("self_destruct_type", kotlinx.serialization.json.JsonNull)
            put("has_spoiler", false)
        }
    }

    fun preliminaryUploadFile(
        originalPath: String,
        expectedSize: Long,
        conversion: String = "master_control_v1",
    ): JsonObject = buildJsonObject {
        put("@type", "preliminaryUploadFile")
        putJsonObject("file") {
            put("@type", "inputFileGenerated")
            put("original_path", originalPath)
            put("conversion", conversion)
            put("expected_size", expectedSize)
        }
        putJsonObject("file_type") { put("@type", "fileTypeVideo") }
        put("priority", 1)
    }

    fun cancelPreliminaryUploadFile(fileId: Int): JsonObject = buildJsonObject {
        put("@type", "cancelPreliminaryUploadFile")
        put("file_id", fileId)
    }

    fun writeGeneratedFilePart(generationId: Long, offset: Long, dataBase64: String): JsonObject = buildJsonObject {
        put("@type", "writeGeneratedFilePart")
        put("generation_id", generationId)
        put("offset", offset)
        put("data", dataBase64)
    }

    fun setFileGenerationProgress(generationId: Long, expectedSize: Long, localPrefixSize: Long): JsonObject = buildJsonObject {
        put("@type", "setFileGenerationProgress")
        put("generation_id", generationId)
        put("expected_size", expectedSize)
        put("local_prefix_size", localPrefixSize)
    }

    fun finishFileGeneration(generationId: Long, withError: Boolean = false): JsonObject = buildJsonObject {
        put("@type", "finishFileGeneration")
        put("generation_id", generationId)
        if (withError) {
            putJsonObject("error") {
                put("@type", "error")
                put("code", 400)
                put("message", "File generation cancelled by the application")
            }
        } else {
            put("error", kotlinx.serialization.json.JsonNull)
        }
    }

    fun setLogVerbosityLevel(level: Int): JsonObject = buildJsonObject {
        put("@type", "setLogVerbosityLevel")
        put("new_verbosity_level", level)
    }
}
