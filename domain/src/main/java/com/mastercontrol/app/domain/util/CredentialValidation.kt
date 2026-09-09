package com.mastercontrol.app.domain.util

/**
 * Pure validation for runtime-provided Telegram configuration and login input.
 * Returns structured reasons rather than throwing; used by onboarding UI.
 */
object CredentialValidation {

    fun validateApiId(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ValidationResult.Error("Enter your API ID.")
        val value = trimmed.toLongOrNull()
            ?: return ValidationResult.Error("API ID must be a whole number.")
        if (value <= 0L || value > 999_999_999L) {
            return ValidationResult.Error("API ID looks malformed. It is a numeric id from my.telegram.org.")
        }
        return ValidationResult.Success
    }

    fun validateApiHash(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ValidationResult.Error("Enter your API hash.")
        if (!Regex("^[0-9a-fA-F]{16,64}$").matches(trimmed)) {
            return ValidationResult.Error("API hash must be a hexadecimal string (16–64 characters).")
        }
        return ValidationResult.Success
    }

    fun validatePhoneNumber(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ValidationResult.Error("Enter your phone number in international format.")
        val digits = trimmed.filter(Char::isDigit)
        if (digits.length < 7 || digits.length > 15) {
            return ValidationResult.Error("Phone number should be in international format, e.g. +15551234567.")
        }
        return ValidationResult.Success
    }

    fun validateCode(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.length < 4 || trimmed.length > 12) {
            return ValidationResult.Error("The verification code looks incorrect.")
        }
        return ValidationResult.Success
    }

    fun validatePin(input: String): ValidationResult {
        if (input.length < 4 || input.length > 64) {
            return ValidationResult.Error("The PIN must be between 4 and 64 characters.")
        }
        if (!Regex("^[0-9]+$").matches(input)) {
            return ValidationResult.Error("The PIN may only contain digits.")
        }
        return ValidationResult.Success
    }
}

sealed class ValidationResult {
    data object Success : ValidationResult()
    data class Error(val reason: String) : ValidationResult()
}
