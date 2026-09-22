package com.mastercontrol.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.AuthorizationState
import com.mastercontrol.app.domain.model.ConnectionState
import com.mastercontrol.app.domain.model.LibraryStatistics
import com.mastercontrol.app.domain.model.StorageChannel
import com.mastercontrol.app.domain.model.TelegramAccountInfo
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState
import com.mastercontrol.app.domain.repository.TelegramAccountRepository
import com.mastercontrol.app.domain.repository.TelegramChannelRepository
import com.mastercontrol.app.domain.repository.UploadTaskRepository
import com.mastercontrol.app.domain.usecase.GetLibraryStatisticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val statistics: LibraryStatistics = LibraryStatistics(),
    val account: TelegramAccountInfo? = null,
    val authorizationState: AuthorizationState = AuthorizationState.NeedsConfiguration,
    val connectionState: ConnectionState = ConnectionState.UNKNOWN,
    val defaultChannel: StorageChannel? = null,
    val channelCount: Int = 0,
    val activeUploads: List<UploadTask> = emptyList(),
    val failedUploads: List<UploadTask> = emptyList(),
    val queuedUploads: List<UploadTask> = emptyList(),
    val refreshing: Boolean = false,
    val errorMessage: String? = null,
    val tdlibVersion: String = "",
) {
    val isAuthorized: Boolean get() = authorizationState is AuthorizationState.Ready
    val channelReady: Boolean get() = defaultChannel?.permissions?.canUploadVideos == true
}

/**
 * Dashboard values are read from the catalog on every collection: counts, byte
 * totals and activity come from real rows, never from generated placeholders.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getLibraryStatistics: GetLibraryStatisticsUseCase,
    private val accountRepository: TelegramAccountRepository,
    private val channelRepository: TelegramChannelRepository,
    private val uploadTaskRepository: UploadTaskRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.observeAuthorizationState().collect { auth ->
                _uiState.update { it.copy(authorizationState = auth) }
                if (auth is AuthorizationState.Ready) loadAccount()
            }
        }
        viewModelScope.launch {
            accountRepository.observeConnectionState().collect { connection ->
                _uiState.update { it.copy(connectionState = connection) }
            }
        }
        viewModelScope.launch {
            channelRepository.observeDefaultChannel().collect { channel ->
                _uiState.update { it.copy(defaultChannel = channel) }
            }
        }
        viewModelScope.launch {
            channelRepository.observeChannels().collect { channels ->
                _uiState.update { it.copy(channelCount = channels.size) }
            }
        }
        viewModelScope.launch {
            uploadTaskRepository.observeAll().collect { tasks ->
                _uiState.update {
                    it.copy(
                        activeUploads = tasks.filter { task ->
                            task.state == UploadTaskState.UPLOADING ||
                                task.state == UploadTaskState.PREPARING ||
                                task.state == UploadTaskState.VERIFYING
                        },
                        queuedUploads = tasks.filter { task ->
                            task.state == UploadTaskState.QUEUED || task.state == UploadTaskState.RETRYING
                        },
                        failedUploads = tasks.filter { task -> task.state == UploadTaskState.FAILED },
                    )
                }
            }
        }
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true, errorMessage = null) }
            try {
                val statistics = getLibraryStatistics()
                val version = accountRepository.getTdlibVersion()
                _uiState.update {
                    it.copy(
                        statistics = statistics,
                        tdlibVersion = version,
                        loading = false,
                        refreshing = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = (t as? AppError)?.userMessage
                            ?: "Statistics could not be loaded from the local catalog.",
                    )
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    private suspend fun loadAccount() {
        val info = accountRepository.getAccountInfo()
        _uiState.update { it.copy(account = info) }
    }
}
