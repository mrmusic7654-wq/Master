package com.mastercontrol.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.domain.model.AppSettings
import com.mastercontrol.app.domain.usecase.OnboardingStage
import com.mastercontrol.app.domain.repository.SettingsRepository
import com.mastercontrol.app.domain.repository.TelegramAccountRepository
import com.mastercontrol.app.domain.repository.TelegramCredentialsRepository
import com.mastercontrol.app.domain.usecase.GetOnboardingStageUseCase
import com.mastercontrol.app.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppStartState(
    val startDestination: String? = null,
    val stage: OnboardingStage? = null,
    val settings: AppSettings = AppSettings(),
    val engineStartAttempted: Boolean = false,
) {
    val onboardingNeeded: Boolean get() = stage != null && stage != OnboardingStage.Complete
}

/**
 * Decides what the app opens on, and resumes the Telegram engine after a cold
 * start.
 *
 * The gate is derived from real state (stored credentials, TDLib authorization,
 * default storage channel) rather than a "first launch" flag, so signing out or
 * clearing secrets returns the operator to onboarding automatically.
 */
@HiltViewModel
class AppStartViewModel @Inject constructor(
    private val getOnboardingStage: GetOnboardingStageUseCase,
    private val settingsRepository: SettingsRepository,
    private val credentialsRepository: TelegramCredentialsRepository,
    private val accountRepository: TelegramAccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppStartState())
    val state: StateFlow<AppStartState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            getOnboardingStage.stage().collect { stage ->
                _state.update {
                    it.copy(
                        stage = stage,
                        startDestination = if (stage == OnboardingStage.Complete) {
                            Destinations.DASHBOARD
                        } else {
                            Destinations.ONBOARDING
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            credentialsRepository.observeConfigured().collect { configured ->
                if (configured && !_state.value.engineStartAttempted) {
                    _state.update { it.copy(engineStartAttempted = true) }
                    // start() is a no-op when the engine is already running; this
                    // resumes the stored session after process death.
                    runCatching { accountRepository.start() }
                }
            }
        }
    }
}
