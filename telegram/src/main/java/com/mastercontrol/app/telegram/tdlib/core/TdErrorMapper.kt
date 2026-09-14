package com.mastercontrol.app.telegram.tdlib.core

import com.mastercontrol.app.domain.error.AppError
import kotlinx.serialization.json.JsonObject

/**
 * Maps TDLib `error` objects to structured [AppError] types.
 *
 * code conventions (TDLib): 401 = auth problems, 403 = forbidden/permissions,
 * 429 / 420 = rate limits (FLOOD_WAIT_x or retry_after header), 400 = bad
 * request (message/file not found, invalid input), 502/501/0 = network.
 */
object TdErrorMapper {

    fun map(errorObj: JsonObject): AppError {
        val code = (errorObj["code"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: -1
        val message = (errorObj["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
        return map(code, message)
    }

    fun map(code: Int, message: String): AppError {
        val lower = message.lowercase()
        return when {
            code == 429 || code == 420 || lower.contains("flood_wait") -> {
                val wait = Regex("flood_wait_(\\d+)").find(lower)?.groupValues?.get(1)?.toLongOrNull()
                AppError.TelegramRateLimitError(retryAfterSeconds = wait, detail = message)
            }
            code == 401 || lower.contains("unauthorized") || lower.contains("auth_key") ->
                AppError.TelegramAuthenticationError(detail = message)
            code == 403 || lower.contains("forbidden") -> {
                AppError.TelegramPermissionError(detail = message)
            }
            code == 400 && (lower.contains("not found") || lower.contains("message_id_invalid")) ->
                AppError.TelegramMessageNotFoundError(detail = message)
            code == 400 && lower.contains("chat") ->
                AppError.ChannelNotFoundError(detail = message)
            code == 400 && lower.contains("file") ->
                AppError.TelegramFileError(detail = message)
            code == 400 && lower.contains("phone") ->
                AppError.CredentialValidationError("Invalid phone number: check the international format.")
            code == 400 && (lower.contains("code") || lower.contains("invalid")) ->
                AppError.CredentialValidationError("Invalid code or input. Check what you entered.")
            code == 502 || code == 501 || code == 0 || lower.contains("network") ->
                AppError.TelegramConnectionError(detail = message)
            else -> AppError.TelegramConnectionError(detail = "TDLib error $code: $message")
        }
    }
}
