package com.mastercontrol.app.domain.usecase

import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.repository.TelegramAccountRepository
import com.mastercontrol.app.domain.repository.TelegramCredentialsRepository
import com.mastercontrol.app.domain.util.CredentialValidation
import com.mastercontrol.app.domain.util.ValidationResult
import javax.inject.Inject

/** Validates and securely stores runtime api_id/api_hash, then starts the engine. */
class ConfigureTelegramCredentials @Inject constructor(
    private val credentialsRepository: TelegramCredentialsRepository,
    private val accountRepository: TelegramAccountRepository,
) {
    suspend operator fun invoke(apiId: String, apiHash: String) {
        when (val v = CredentialValidation.validateApiId(apiId)) {
            is ValidationResult.Error -> throw AppError.CredentialValidationError(v.reason)
            is ValidationResult.Success -> Unit
        }
        when (val v = CredentialValidation.validateApiHash(apiHash)) {
            is ValidationResult.Error -> throw AppError.CredentialValidationError(v.reason)
            is ValidationResult.Success -> Unit
        }
        credentialsRepository.save(apiId.trim().toLong(), apiHash.trim())
        accountRepository.start()
    }
}

class ClearTelegramCredentials @Inject constructor(
    private val credentialsRepository: TelegramCredentialsRepository,
    private val accountRepository: TelegramAccountRepository,
) {
    suspend operator fun invoke() {
        accountRepository.logout()
        credentialsRepository.clear()
    }
}

class ConnectTelegramUseCase @Inject constructor(
    private val accountRepository: TelegramAccountRepository,
) {
    suspend operator fun invoke() {
        if (accountRepository.getAccountInfo() == null) {
            accountRepository.restart()
        }
    }
}

class SubmitPhoneNumberUseCase @Inject constructor(
    private val accountRepository: TelegramAccountRepository,
) {
    suspend operator fun invoke(phoneNumber: String) {
        val trimmed = phoneNumber.trim()
        when (val v = CredentialValidation.validatePhoneNumber(trimmed)) {
            is ValidationResult.Error -> throw AppError.CredentialValidationError(v.reason)
            is ValidationResult.Success -> Unit
        }
        accountRepository.submitPhoneNumber(trimmed)
    }
}

class SubmitAuthenticationCodeUseCase @Inject constructor(
    private val accountRepository: TelegramAccountRepository,
) {
    suspend operator fun invoke(code: String) {
        when (val v = CredentialValidation.validateCode(code)) {
            is ValidationResult.Error -> throw AppError.CredentialValidationError(v.reason)
            is ValidationResult.Success -> Unit
        }
        accountRepository.submitAuthenticationCode(code.trim())
    }
}

class SubmitTwoFactorPasswordUseCase @Inject constructor(
    private val accountRepository: TelegramAccountRepository,
) {
    suspend operator fun invoke(password: String) {
        if (password.isEmpty()) throw AppError.CredentialValidationError("Enter your two-step verification password.")
        accountRepository.submitTwoFactorPassword(password)
    }
}

class ResendAuthenticationCodeUseCase @Inject constructor(
    private val accountRepository: TelegramAccountRepository,
) {
    suspend operator fun invoke() = accountRepository.resendCode()
}

class LogoutTelegramUseCase @Inject constructor(
    private val accountRepository: TelegramAccountRepository,
) {
    suspend operator fun invoke() = accountRepository.logout()
}
