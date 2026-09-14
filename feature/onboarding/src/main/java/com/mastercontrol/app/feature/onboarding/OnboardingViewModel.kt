package com.mastercontrol.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.core.common.text.SecretMasking
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.AuthorizationState
import com.mastercontrol.app.domain.model.ChannelCandidate
import com.mastercontrol.app.domain.model.ChannelVerification
import com.mastercontrol.app.domain.model.ConnectionState
import com.mastercontrol.app.domain.model.StorageChannel
import com.mastercontrol.app.domain.model.TelegramAccountInfo
import com.mastercontrol.app.domain.repository.TelegramAccountRepository
import com.mastercontrol.app.domain.repository.TelegramChannelRepository
import com.mastercontrol.app.domain.repository.TelegramCredentialsRepository
import com.mastercontrol.app.domain.usecase.AddStorageChannelUseCase
import com.mastercontrol.app.domain.usecase.ClearTelegramCredentials
import com.mastercontrol.app.domain.usecase.ConfigureTelegramCredentials
import com.mastercontrol.app.domain.usecase.ConnectTelegramUseCase
import com.mastercontrol.app.domain.usecase.GetOnboardingStageUseCase
import com.mastercontrol.app.domain.usecase.OnboardingStage
import com.mastercontrol.app.domain.usecase.ResendAuthenticationCodeUseCase
import com.mastercontrol.app.domain.usecase.SearchChannelsUseCase
import com.mastercontrol.app.domain.usecase.SetDefaultChannelUseCase
import com.mastercontrol.app.domain.usecase.SubmitAuthenticationCodeUseCase
import com.mastercontrol.app.domain.usecase.SubmitPhoneNumberUseCase
import com.mastercontrol.app.domain.usecase.SubmitTwoFactorPasswordUseCase
import com.mastercontrol.app.domain.usecase.VerifyChannelUseCase
import com.mastercontrol.app.domain.util.CredentialValidation
import com.mastercontrol.app.domain.util.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the operator is currently doing; drives the step indicator and the body. */
enum class OnboardingStep { WELCOME, CREDENTIALS, AUTHENTICATION, CHANNEL, COMPLETE }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val stage: OnboardingStage = OnboardingStage.NeedsCredentials,
    val authorizationState: AuthorizationState = AuthorizationState.NeedsConfiguration,
    val connectionState: ConnectionState = ConnectionState.UNKNOWN,
    val account: TelegramAccountInfo? = null,
    // ---- application credentials (BYOK) -------------------------------------
    val apiIdInput: String = "",
    val apiHashInput: String = "",
    val credentialsConfigured: Boolean = false,
    val maskedApiHash: String = "",
    val apiIdError: String? = null,
    val apiHashError: String? = null,
    // ---- Telegram account login ---------------------------------------------
    val phoneInput: String = "",
    val codeInput: String = "",
    val passwordInput: String = "",
    val phoneError: String? = null,
    val codeError: String? = null,
    val passwordError: String? = null,
    val passwordHint: String? = null,
    val codeTimeoutSeconds: Int = 0,
    // ---- storage channel ----------------------------------------------------
    val channelQuery: String = "",
    val channelResults: List<ChannelCandidate> = emptyList(),
    val searchingChannels: Boolean = false,
    val storedChannels: List<StorageChannel> = emptyList(),
    val verification: ChannelVerification? = null,
    // ---- shared -------------------------------------------------------------
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val canSaveCredentials: Boolean
        get() = apiIdInput.isNotBlank() && apiHashInput.isNotBlank() && apiIdError == null && apiHashError == null

    val isAuthenticated: Boolean
        get() = authorizationState is AuthorizationState.Ready
}

/**
 * First-run experience: application credentials (BYOK) → Telegram account
 * authorization → storage channel selection and permission verification.
 *
 * Every step is driven by real TDLib state; there is no simulated success path.
 * Secrets (api_hash, verification code, 2FA password) exist only in this state
 * while the operator types them, are cleared after submission and are never
 * logged or exported.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val getOnboardingStage: GetOnboardingStageUseCase,
    private val credentialsRepository: TelegramCredentialsRepository,
    private val accountRepository: TelegramAccountRepository,
    private val channelRepository: TelegramChannelRepository,
    private val configureTelegramCredentials: ConfigureTelegramCredentials,
    private val clearTelegramCredentials: ClearTelegramCredentials,
    private val connectTelegramUseCase: ConnectTelegramUseCase,
    private val submitPhoneNumberUseCase: SubmitPhoneNumberUseCase,
    private val submitAuthenticationCodeUseCase: SubmitAuthenticationCodeUseCase,
    private val submitTwoFactorPasswordUseCase: SubmitTwoFactorPasswordUseCase,
    private val resendAuthenticationCodeUseCase: ResendAuthenticationCodeUseCase,
    private val searchChannelsUseCase: SearchChannelsUseCase,
    private val addStorageChannelUseCase: AddStorageChannelUseCase,
    private val setDefaultChannelUseCase: SetDefaultChannelUseCase,
    private val verifyChannelUseCase: VerifyChannelUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            getOnboardingStage.stage().collect { stage ->
                _uiState.update { it.copy(stage = stage, step = stage.toStep()) }
            }
        }
        viewModelScope.launch {
            accountRepository.observeAuthorizationState().collect { auth ->
                _uiState.update { current ->
                    current.copy(
                        authorizationState = auth,
                        codeTimeoutSeconds = (auth as? AuthorizationState.WaitCode)?.codeInfo?.timeoutSeconds ?: 0,
                        passwordHint = (auth as? AuthorizationState.WaitPassword)?.passwordHint,
                    )
                }
                if (auth is AuthorizationState.Ready) refreshAccount()
            }
        }
        viewModelScope.launch {
            accountRepository.observeConnectionState().collect { connection ->
                _uiState.update { it.copy(connectionState = connection) }
            }
        }
        viewModelScope.launch {
            credentialsRepository.observeConfigured().collect { configured ->
                val masked = if (configured) {
                    credentialsRepository.readCredentials()?.let { (apiId, apiHash) ->
                        "API ID $apiId · hash ${SecretMasking.maskSecret(apiHash)}"
                    } ?: "Credentials stored"
                } else {
                    ""
                }
                _uiState.update { it.copy(credentialsConfigured = configured, maskedApiHash = masked) }
            }
        }
        viewModelScope.launch {
            channelRepository.observeChannels().collect { channels ->
                _uiState.update { it.copy(storedChannels = channels) }
            }
        }
        viewModelScope.launch {
            channelRepository.observeDefaultChannel().collect { default ->
                if (default != null) reverifyQuietly(default.id)
            }
        }
    }

    // ---- application credentials -------------------------------------------

    fun onApiIdChange(value: String) =
        _uiState.update { it.copy(apiIdInput = value.filter(Char::isDigit).take(12), apiIdError = null) }

    fun onApiHashChange(value: String) =
        _uiState.update { it.copy(apiHashInput = value.trim().take(64), apiHashError = null) }

    fun onSaveCredentials() {
        val apiId = _uiState.value.apiIdInput
        val apiHash = _uiState.value.apiHashInput
        // Field-level validation happens before anything is stored or sent.
        when (val check = CredentialValidation.validateApiId(apiId)) {
            is ValidationResult.Error -> {
                _uiState.update { it.copy(apiIdError = check.reason) }
                return
            }
            ValidationResult.Success -> Unit
        }
        when (val check = CredentialValidation.validateApiHash(apiHash)) {
            is ValidationResult.Error -> {
                _uiState.update { it.copy(apiHashError = check.reason) }
                return
            }
            ValidationResult.Success -> Unit
        }
        runGuarded {
            configureTelegramCredentials(apiId, apiHash)
            // Inputs are dropped from UI state the moment they are stored.
            _uiState.update {
                it.copy(
                    apiIdInput = "",
                    apiHashInput = "",
                    infoMessage = "Application credentials saved to the Android Keystore.",
                )
            }
            accountRepository.start()
        }
    }

    fun onEditCredentials() =
        _uiState.update { it.copy(step = OnboardingStep.CREDENTIALS, errorMessage = null, infoMessage = null) }

    fun onClearCredentials() {
        runGuarded {
            clearTelegramCredentials()
            _uiState.update {
                it.copy(
                    apiIdInput = "",
                    apiHashInput = "",
                    maskedApiHash = "",
                    infoMessage = "Telegram credentials and session were cleared from this device.",
                )
            }
        }
    }

    fun onTestConnection() {
        runGuarded {
            connectTelegramUseCase()
            accountRepository.start()
            _uiState.update {
                it.copy(infoMessage = "Connection test started — the state below reflects live TDLib updates.")
            }
        }
    }

    // ---- Telegram account authorization ------------------------------------

    fun onPhoneChange(value: String) =
        _uiState.update { it.copy(phoneInput = value.take(20), phoneError = null) }

    fun onCodeChange(value: String) =
        _uiState.update { it.copy(codeInput = value.filter(Char::isDigit).take(12), codeError = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(passwordInput = value, passwordError = null) }

    fun onSubmitPhone() {
        val phone = _uiState.value.phoneInput
        when (val check = CredentialValidation.validatePhoneNumber(phone)) {
            is ValidationResult.Error -> {
                _uiState.update { it.copy(phoneError = check.reason) }
                return
            }
            ValidationResult.Success -> Unit
        }
        runGuarded {
            submitPhoneNumberUseCase(phone)
            _uiState.update { it.copy(phoneInput = "") }
        }
    }

    fun onSubmitCode() {
        val code = _uiState.value.codeInput
        when (val check = CredentialValidation.validateCode(code)) {
            is ValidationResult.Error -> {
                _uiState.update { it.copy(codeError = check.reason) }
                return
            }
            ValidationResult.Success -> Unit
        }
        runGuarded {
            submitAuthenticationCodeUseCase(code)
            _uiState.update { it.copy(codeInput = "") }
        }
    }

    fun onSubmitPassword() {
        val password = _uiState.value.passwordInput
        if (password.isEmpty()) {
            _uiState.update { it.copy(passwordError = "Enter your two-step verification password.") }
            return
        }
        runGuarded {
            submitTwoFactorPasswordUseCase(password)
            _uiState.update { it.copy(passwordInput = "") }
        }
    }

    fun onResendCode() {
        runGuarded {
            resendAuthenticationCodeUseCase()
            _uiState.update { it.copy(infoMessage = "A new verification code was requested.") }
        }
    }

    // ---- storage channel ---------------------------------------------------

    fun onChannelQueryChange(value: String) {
        _uiState.update { it.copy(channelQuery = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (value.isBlank()) {
                _uiState.update { it.copy(channelResults = emptyList(), searchingChannels = false) }
                return@launch
            }
            _uiState.update { it.copy(searchingChannels = true, errorMessage = null) }
            try {
                val results = searchChannelsUseCase(value)
                _uiState.update {
                    it.copy(
                        channelResults = results,
                        searchingChannels = false,
                        infoMessage = if (results.isEmpty()) {
                            "No channel matched '$value' for this account."
                        } else {
                            null
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update { it.copy(searchingChannels = false) }
                fail(t)
            }
        }
    }

    fun onSelectChannel(candidate: ChannelCandidate, makeDefault: Boolean) {
        runGuarded {
            val stored = addStorageChannelUseCase(candidate, makeDefault)
            val verification = verifyChannelUseCase(stored.id)
            _uiState.update {
                it.copy(
                    verification = verification,
                    channelQuery = "",
                    channelResults = emptyList(),
                    infoMessage = if (verification.permissions.canUploadVideos) {
                        "Channel '${stored.displayName}' is verified and ready for uploads."
                    } else {
                        "Channel '${stored.displayName}' was added but is missing: " +
                            verification.permissions.missingForUpload.joinToString(", ")
                    },
                )
            }
        }
    }

    fun onReverifyChannel(channelId: Long) = runGuarded {
        val verification = verifyChannelUseCase(channelId)
        _uiState.update { it.copy(verification = verification) }
    }

    fun onMakeDefault(channelId: Long) = runGuarded {
        setDefaultChannelUseCase(channelId)
        _uiState.update { it.copy(infoMessage = "Default storage channel updated.") }
    }

    fun onRefreshChannels() = runGuarded {
        accountRepository.start()
        _uiState.update { it.copy(infoMessage = "Channel list refreshed from Telegram.") }
    }

    fun onDismissMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    fun onBackToWelcome() = _uiState.update { it.copy(step = OnboardingStep.WELCOME) }

    fun onProceedFromWelcome() {
        val target = when (_uiState.value.stage) {
            OnboardingStage.NeedsCredentials -> OnboardingStep.CREDENTIALS
            is OnboardingStage.Authentication -> OnboardingStep.AUTHENTICATION
            OnboardingStage.ChooseChannel -> OnboardingStep.CHANNEL
            OnboardingStage.Complete -> OnboardingStep.COMPLETE
        }
        _uiState.update { it.copy(step = target) }
    }

    private suspend fun reverifyQuietly(channelId: Long) {
        try {
            val verification = verifyChannelUseCase(channelId)
            _uiState.update { it.copy(verification = verification) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            // A quiet refresh must not interrupt the operator; the channel screen
            // surfaces verification failures explicitly.
            fail(t)
        }
    }

    private suspend fun refreshAccount() {
        val info = accountRepository.getAccountInfo()
        _uiState.update { it.copy(account = info) }
    }

    /** Runs an action with the busy flag set and structured error mapping. */
    private fun runGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorMessage = null, infoMessage = null) }
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                fail(t)
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * Maps a failure onto the structured error system: the operator sees
     * [AppError.userMessage]; the technical detail stays in the exception and the
     * diagnostics log.
     */
    private fun fail(t: Throwable) {
        val message = when (t) {
            is AppError -> t.userMessage
            else -> "The operation could not be completed. Details are in Settings → Diagnostics."
        }
        _uiState.update { it.copy(errorMessage = message) }
    }
}

private fun OnboardingStage.toStep(): OnboardingStep = when (this) {
    OnboardingStage.NeedsCredentials -> OnboardingStep.CREDENTIALS
    is OnboardingStage.Authentication -> OnboardingStep.AUTHENTICATION
    OnboardingStage.ChooseChannel -> OnboardingStep.CHANNEL
    OnboardingStage.Complete -> OnboardingStep.COMPLETE
}
