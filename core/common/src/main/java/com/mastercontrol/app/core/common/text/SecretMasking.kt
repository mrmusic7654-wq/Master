package com.mastercontrol.app.core.common.text

/**
 * Display masking for values that must never be shown, logged or exported in
 * full: Telegram API hashes, phone numbers, verification codes, session keys.
 *
 * Master Control stores these in the Android Keystore-backed secret store and
 * only ever renders a masked form in the UI (see SECURITY.md).
 */
object SecretMasking {

    private const val MASK = "•"

    /**
     * Reveals at most the last [visibleTail] characters: "ab3f…c9d2" style.
     * Short inputs are fully masked — a short secret has no safe tail.
     */
    fun maskSecret(value: String?, visibleTail: Int = 4): String {
        if (value.isNullOrEmpty()) return ""
        val trimmed = value.trim()
        if (trimmed.length <= visibleTail + 2) return MASK.repeat(trimmed.length.coerceAtLeast(4))
        return MASK.repeat(8) + trimmed.takeLast(visibleTail)
    }

    /** Masks a phone number while keeping the country code and last two digits. */
    fun maskPhoneNumber(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val digits = value.filter { it.isDigit() || it == '+' }
        if (digits.length < 6) return MASK.repeat(digits.length)
        val head = digits.take(3)
        val tail = digits.takeLast(2)
        return head + MASK.repeat((digits.length - 5).coerceIn(2, 8)) + tail
    }

    /** Verification codes and 2FA passwords are never rendered at all. */
    fun fullyMasked(length: Int): String = MASK.repeat(length.coerceIn(4, 12))

    /** True when [text] appears to contain a value that should not be logged. */
    fun looksLikeSecret(text: String?, knownSecrets: Collection<String>): Boolean {
        if (text.isNullOrEmpty()) return false
        return knownSecrets.any { it.length >= 8 && text.contains(it) }
    }
}
