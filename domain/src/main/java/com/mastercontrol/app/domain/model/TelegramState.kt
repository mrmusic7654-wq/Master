package com.mastercontrol.app.domain.model

/**
 * Application-facing Telegram authorization state. Maps 1:1 onto the TDLib
 * authorizationState* states but is deliberately free of TDLib types so the UI
 * never depends on the Telegram engine.
 */
sealed class AuthorizationState {
    /** No TDLib parameters configured yet. */
    object NeedsConfiguration : AuthorizationState()

    /** Waiting for the admin to enter a phone number. */
    object WaitPhoneNumber : AuthorizationState()

    /** Waiting for the verification code. */
    data class WaitCode(val codeInfo: CodeInfo?) : AuthorizationState()

    /** Waiting for the two-step verification password. */
    data class WaitPassword(
        val passwordHint: String? = null,
        val hasRecoveryEmailAddress: Boolean = false,
        val recoveryEmailAddressPattern: String? = null,
    ) : AuthorizationState()

    object WaitRegistration : AuthorizationState()

    object WaitEmailAddress : AuthorizationState()

    object WaitEmailCode : AuthorizationState()

    /** QR/other-device confirmation available. */
    data class WaitOtherDeviceConfirmation(val link: String? = null) : AuthorizationState()

    object WaitPremiumPurchase : AuthorizationState()

    /** Authorized and fully ready. */
    data class Ready(val displayName: String? = null, val username: String? = null) : AuthorizationState()

    object LoggingOut : AuthorizationState()

    object Closing : AuthorizationState()

    object Closed : AuthorizationState()

    /** Forward-compatible bucket for authorization states this build does not special-case. */
    data class Other(val rawType: String) : AuthorizationState()
}

/** Info about how the verification code is delivered. */
data class CodeInfo(
    val phoneNumber: String? = null,
    val type: CodeDeliveryType = CodeDeliveryType.Unknown,
    val nextType: CodeDeliveryType = CodeDeliveryType.Unknown,
    val timeoutSeconds: Int = 0,
)

enum class CodeDeliveryType {
    TelegramMessage,
    Sms,
    SmsWord,
    SmsPhrase,
    Call,
    MissedCall,
    Fragment,
    EmailAddress,
    Firebase,
    Unknown,
}

/** TDLib connection state (from updateConnectionState). */
enum class ConnectionState {
    WAITING_FOR_NETWORK,
    CONNECTING,
    UPDATING,
    READY,
    DISCONNECTED,
    UNKNOWN,
}

/** Snapshot of the authenticated Telegram account (never contains secrets). */
data class TelegramAccountInfo(
    val userId: Long,
    val displayName: String,
    val username: String? = null,
    val phoneNumber: String? = null,
)
