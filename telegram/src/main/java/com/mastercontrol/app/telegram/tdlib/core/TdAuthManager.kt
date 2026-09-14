package com.mastercontrol.app.telegram.tdlib.core

import com.mastercontrol.app.domain.model.AuthorizationState
import com.mastercontrol.app.domain.model.CodeDeliveryType
import com.mastercontrol.app.domain.model.CodeInfo
import com.mastercontrol.app.domain.model.ConnectionState
import com.mastercontrol.app.domain.model.TelegramAccountInfo
import com.mastercontrol.app.telegram.tdlib.json.TdContentParser
import com.mastercontrol.app.telegram.tdlib.json.TdRequests
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Bridges updateAuthorizationState / updateConnectionState into the domain
 * model and executes authentication commands. Holds NO secrets.
 */
class TdAuthManager(private val client: TdClientCore) {

    private val _authorizationState = MutableStateFlow<AuthorizationState>(AuthorizationState.NeedsConfiguration)
    val authorizationState: StateFlow<AuthorizationState> = _authorizationState

    private val _connectionState = MutableStateFlow(ConnectionState.UNKNOWN)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    init {
        client.onUpdate("updateAuthorizationState") { update ->
            val state = update["authorization_state"] as? JsonObject ?: return@onUpdate
            _authorizationState.value = mapAuthorization(state)
        }
        client.onUpdate("updateConnectionState") { update ->
            val state = update["state"] as? JsonObject
            val type = state?.get("@type")?.jsonPrimitive?.contentOrNull ?: return@onUpdate
            _connectionState.value = when (type) {
                "connectionStateReady" -> ConnectionState.READY
                "connectionStateConnecting" -> ConnectionState.CONNECTING
                "connectionStateUpdating" -> ConnectionState.UPDATING
                "connectionStateWaitingForNetwork" -> ConnectionState.WAITING_FOR_NETWORK
                else -> ConnectionState.DISCONNECTED
            }
        }
    }

    suspend fun submitPhoneNumber(phoneNumber: String) {
        client.call(TdRequests.setAuthenticationPhoneNumber(phoneNumber))
    }

    suspend fun submitCode(code: String) {
        client.call(TdRequests.checkAuthenticationCode(code))
    }

    suspend fun submitPassword(password: String) {
        client.call(TdRequests.checkAuthenticationPassword(password))
    }

    suspend fun resendCode() {
        client.call(TdRequests.resendAuthenticationCode())
    }

    suspend fun logout() {
        runCatching { client.call(TdRequests.logOut()) }
    }

    suspend fun accountInfo(): TelegramAccountInfo? {
        val user = client.call(TdRequests.getMe())
        val id = user["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val first = user["first_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val last = user["last_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val usernames = (user["usernames"] as? JsonObject)
        val username = usernames?.get("active_usernames")
            ?.let { if (it is kotlinx.serialization.json.JsonArray) it.firstOrNull()?.jsonPrimitive?.contentOrNull else null }
        val phone = user["phone_number"]?.jsonPrimitive?.contentOrNull
        return TelegramAccountInfo(
            userId = id,
            displayName = (first + " " + last).trim().ifEmpty { "Telegram account" },
            username = username,
            phoneNumber = phone,
        )
    }

    private fun mapAuthorization(state: JsonObject): AuthorizationState {
        val type = state["@type"]?.jsonPrimitive?.contentOrNull ?: return AuthorizationState.Other("unknown")
        return when (type) {
            "authorizationStateWaitTdlibParameters" -> AuthorizationState.NeedsConfiguration
            "authorizationStateWaitPhoneNumber" -> AuthorizationState.WaitPhoneNumber
            "authorizationStateWaitCode" -> {
                val info = state["code_info"] as? JsonObject
                AuthorizationState.WaitCode(info?.let { mapCodeInfo(it) })
            }
            "authorizationStateWaitPassword" -> AuthorizationState.WaitPassword(
                passwordHint = TdContentParser.primitiveText(state, "password_hint"),
                hasRecoveryEmailAddress = TdContentParser.primitiveBool(state, "has_recovery_email_address"),
                recoveryEmailAddressPattern = TdContentParser.primitiveText(state, "recovery_email_address_pattern"),
            )
            "authorizationStateWaitRegistration" -> AuthorizationState.WaitRegistration
            "authorizationStateWaitEmailAddress" -> AuthorizationState.WaitEmailAddress
            "authorizationStateWaitEmailCode" -> AuthorizationState.WaitEmailCode
            "authorizationStateWaitOtherDeviceConfirmation" -> AuthorizationState.WaitOtherDeviceConfirmation(
                TdContentParser.primitiveText(state, "link"),
            )
            "authorizationStateWaitPremiumPurchase" -> AuthorizationState.WaitPremiumPurchase
            "authorizationStateReady" -> AuthorizationState.Ready()
            "authorizationStateLoggingOut" -> AuthorizationState.LoggingOut
            "authorizationStateClosing" -> AuthorizationState.Closing
            "authorizationStateClosed" -> AuthorizationState.Closed
            else -> AuthorizationState.Other(type)
        }
    }

    private fun mapCodeInfo(info: JsonObject): CodeInfo {
        val type = info["type"] as? JsonObject
        val typeName = type?.get("@type")?.jsonPrimitive?.contentOrNull
        val next = info["next_type"] as? JsonObject
        val nextName = next?.get("@type")?.jsonPrimitive?.contentOrNull
        return CodeInfo(
            phoneNumber = TdContentParser.primitiveText(info, "phone_number"),
            type = deliveryType(typeName),
            nextType = deliveryType(nextName),
            timeoutSeconds = TdContentParser.primitiveLong(info, "timeout")?.toInt() ?: 0,
        )
    }

    private fun deliveryType(name: String?): CodeDeliveryType = when (name) {
        "authenticationCodeTypeTelegramMessage" -> CodeDeliveryType.TelegramMessage
        "authenticationCodeTypeSms" -> CodeDeliveryType.Sms
        "authenticationCodeTypeSmsWord" -> CodeDeliveryType.SmsWord
        "authenticationCodeTypeSmsPhrase" -> CodeDeliveryType.SmsPhrase
        "authenticationCodeTypeCall" -> CodeDeliveryType.Call
        "authenticationCodeTypeMissedCall" -> CodeDeliveryType.MissedCall
        "authenticationCodeTypeFragment" -> CodeDeliveryType.Fragment
        "authenticationCodeTypeEmailAddress" -> CodeDeliveryType.EmailAddress
        "authenticationCodeTypeFirebase" -> CodeDeliveryType.Firebase
        else -> CodeDeliveryType.Unknown
    }
}
