package com.mastercontrol.app.lock

import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.domain.repository.AppLockMode
import com.mastercontrol.app.domain.repository.AppLockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface LockState {
    data object Checking : LockState
    data object Unlocked : LockState

    data class Locked(
        val mode: AppLockMode,
        val biometricAvailable: Boolean,
        val pin: String = "",
        val error: String? = null,
        val failedAttempts: Int = 0,
        val verifying: Boolean = false,
        val promptVisible: Boolean = false,
    ) : LockState
}

/**
 * App lock.
 *
 * PIN verification is a hash comparison inside the Keystore-backed secret store.
 * Biometric and device-credential unlock are delegated to Android's
 * `BiometricPrompt`; Master Control stores no biometric data and never
 * implements its own crypto.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appLockRepository: AppLockRepository,
    private val session: AppLockSession,
) : ViewModel() {

    private val _state = MutableStateFlow<LockState>(LockState.Checking)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var biometricPrompt: BiometricPrompt? = null

    init {
        viewModelScope.launch {
            combine(appLockRepository.observeLockEnabled(), appLockRepository.observeLockMode(), session.unlocked) { enabled, mode, unlocked ->
                Triple(enabled, mode, unlocked)
            }.collect { (enabled, mode, unlocked) ->
                _state.value = when {
                    !enabled || unlocked -> LockState.Unlocked
                    else -> LockState.Locked(mode = mode, biometricAvailable = false)
                }
            }
        }
    }

    fun onPinChange(pin: String) {
        val current = _state.value as? LockState.Locked ?: return
        _state.value = current.copy(pin = pin.filter { it.isDigit() }.take(MAX_PIN_LENGTH), error = null)
    }

    fun onSubmitPin() {
        val current = _state.value as? LockState.Locked ?: return
        if (current.pin.length < MIN_PIN_LENGTH) {
            _state.value = current.copy(error = "Enter at least $MIN_PIN_LENGTH digits.")
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(verifying = true, error = null)
            val ok = runCatching { appLockRepository.verifyPin(current.pin) }.getOrDefault(false)
            val locked = _state.value as? LockState.Locked ?: current
            if (ok) {
                session.unlock()
            } else {
                _state.value = locked.copy(
                    pin = "",
                    verifying = false,
                    failedAttempts = locked.failedAttempts + 1,
                    error = "That PIN is not correct.",
                )
            }
        }
    }

    /**
     * Reports whether the platform can authenticate with [mode] on this device.
     * Called from the gate with the hosting activity, because `BiometricManager`
     * needs a `Context`.
     */
    fun onBiometricAvailability(activity: FragmentActivity, mode: AppLockMode) {
        val current = _state.value as? LockState.Locked ?: return
        val authenticators = authenticatorsFor(mode)
        val result = BiometricManager.from(activity).canAuthenticate(authenticators)
        _state.value = current.copy(biometricAvailable = result == BiometricManager.BIOMETRIC_SUCCESS)
    }

    fun onShowBiometricPrompt(activity: FragmentActivity) {
        val current = _state.value as? LockState.Locked ?: return
        if (current.mode == AppLockMode.PIN || current.promptVisible) return
        val authenticators = authenticatorsFor(current.mode)
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            _state.value = current.copy(
                biometricAvailable = false,
                error = "This device cannot use ${current.mode.label()}. Set a PIN in Settings to keep the catalog protected.",
            )
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricPrompt = null
                    session.unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricPrompt = null
                    val locked = _state.value as? LockState.Locked ?: return
                    _state.value = locked.copy(
                        promptVisible = false,
                        error = when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED,
                            -> null

                            else -> errString.toString()
                        },
                    )
                }

                override fun onAuthenticationFailed() {
                    val locked = _state.value as? LockState.Locked ?: return
                    _state.value = locked.copy(failedAttempts = locked.failedAttempts + 1)
                }
            },
        )
        biometricPrompt = prompt
        _state.value = current.copy(promptVisible = true, error = null)
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Master Control")
                .setSubtitle("Confirm your identity to open the media catalog")
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(false)
                .build(),
        )
    }

    fun onDismissPrompt() {
        biometricPrompt?.cancelAuthentication()
        biometricPrompt = null
        val current = _state.value as? LockState.Locked ?: return
        _state.value = current.copy(promptVisible = false)
    }

    /** Manual lock from the app bar; the process keeps running. */
    fun onLockNow() {
        session.lock()
    }

    private fun authenticatorsFor(mode: AppLockMode): Int = when (mode) {
        // Biometric unlock always offers the device credential as a way in, so a
        // broken or unenrolled sensor can never lock the operator out.
        AppLockMode.BIOMETRIC -> BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        AppLockMode.DEVICE_CREDENTIAL -> DEVICE_CREDENTIAL
        AppLockMode.PIN -> DEVICE_CREDENTIAL
    }

    private fun AppLockMode.label(): String = when (this) {
        AppLockMode.PIN -> "PIN unlock"
        AppLockMode.BIOMETRIC -> "biometric unlock"
        AppLockMode.DEVICE_CREDENTIAL -> "device unlock"
    }

    private companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 12
    }
}
