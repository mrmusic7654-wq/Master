package com.mastercontrol.app.feature.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ChannelCandidate
import com.mastercontrol.app.domain.model.ChannelVerification
import com.mastercontrol.app.domain.model.ConnectionState
import com.mastercontrol.app.domain.model.StorageChannel
import com.mastercontrol.app.domain.repository.TelegramAccountRepository
import com.mastercontrol.app.domain.repository.TelegramChannelRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import com.mastercontrol.app.domain.usecase.AddStorageChannelUseCase
import com.mastercontrol.app.domain.usecase.RemoveStorageChannelUseCase
import com.mastercontrol.app.domain.usecase.RenameChannelUseCase
import com.mastercontrol.app.domain.usecase.SearchChannelsUseCase
import com.mastercontrol.app.domain.usecase.SetChannelEnabledUseCase
import com.mastercontrol.app.domain.usecase.SetDefaultChannelUseCase
import com.mastercontrol.app.domain.usecase.VerifyChannelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChannelsUiState(
    val channels: List<StorageChannel> = emptyList(),
    val defaultChannelId: Long? = null,
    val connectionState: ConnectionState = ConnectionState.UNKNOWN,
    val mappingCounts: Map<Long, Int> = emptyMap(),
    val mappingBytes: Map<Long, Long> = emptyMap(),
    val query: String = "",
    val candidates: List<ChannelCandidate> = emptyList(),
    val searching: Boolean = false,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val verifyingChannelId: Long? = null,
    val verifications: Map<Long, ChannelVerification> = emptyMap(),
    val expandedChannelId: Long? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingRemove: StorageChannel? = null,
    val pendingDefault: StorageChannel? = null,
    val renameTarget: StorageChannel? = null,
    val renameValue: String = "",
    val renameError: String? = null,
) {
    val storedIds: Set<Long> get() = channels.map { it.id }.toSet()

    /** Candidates that are not configured yet, so "Add" stays meaningful. */
    val newCandidates: List<ChannelCandidate>
        get() = candidates.filter { it.chatId !in storedIds }

    val alreadyStoredCandidates: List<ChannelCandidate>
        get() = candidates.filter { it.chatId in storedIds }

    fun mappingCount(channelId: Long): Int = mappingCounts[channelId] ?: 0

    fun mappingBytes(channelId: Long): Long = mappingBytes[channelId] ?: 0L

    val searchAvailable: Boolean get() = connectionState == ConnectionState.READY
}

/**
 * Storage channels: the Telegram destinations Master Control uploads into.
 *
 * Everything shown here is either a locally stored row or a live result from
 * TDLib (search, permission verification). Removing a channel only deletes the
 * local bookkeeping — Telegram and the media inside it are never touched, which
 * is why the confirmation dialog says so explicitly.
 */
@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val channelRepository: TelegramChannelRepository,
    private val accountRepository: TelegramAccountRepository,
    private val videoRepository: VideoRepository,
    private val searchChannels: SearchChannelsUseCase,
    private val addStorageChannel: AddStorageChannelUseCase,
    private val removeStorageChannel: RemoveStorageChannelUseCase,
    private val setDefaultChannel: SetDefaultChannelUseCase,
    private val setChannelEnabled: SetChannelEnabledUseCase,
    private val renameChannel: RenameChannelUseCase,
    private val verifyChannel: VerifyChannelUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            channelRepository.observeChannels().collect { channels ->
                _uiState.update { it.copy(channels = channels, loading = false) }
                refreshMappingStats()
            }
        }
        viewModelScope.launch {
            channelRepository.observeDefaultChannel().collect { channel ->
                _uiState.update { it.copy(defaultChannelId = channel?.id) }
            }
        }
        viewModelScope.launch {
            accountRepository.observeConnectionState().collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    private suspend fun refreshMappingStats() {
        val counts = runCatching { videoRepository.countActiveMappingsByChannel() }.getOrDefault(emptyMap())
        val bytes = runCatching { videoRepository.activeMappingBytesByChannel() }.getOrDefault(emptyMap())
        _uiState.update { it.copy(mappingCounts = counts, mappingBytes = bytes) }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(candidates = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(searching = true) }
            delay(SEARCH_DEBOUNCE_MS)
            try {
                val results = searchChannels(query.trim())
                _uiState.update { it.copy(candidates = results, searching = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        searching = false,
                        candidates = emptyList(),
                        errorMessage = (t as? AppError)?.userMessage ?: "Telegram channel search failed.",
                    )
                }
            }
        }
    }

    fun onClearQuery() {
        searchJob?.cancel()
        _uiState.update { it.copy(query = "", candidates = emptyList(), searching = false) }
    }

    fun onAddChannel(candidate: ChannelCandidate, makeDefault: Boolean) {
        runGuarded("The channel could not be added.") {
            val channel = addStorageChannel(candidate, makeDefault)
            _uiState.update {
                it.copy(
                    infoMessage = "\"${channel.displayName}\" was added as a storage channel" +
                        if (makeDefault) " and set as the default upload target." else ".",
                )
            }
        }
    }

    fun onToggleExpand(channelId: Long) = _uiState.update {
        it.copy(expandedChannelId = if (it.expandedChannelId == channelId) null else channelId)
    }

    fun onVerifyClick(channelId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(verifyingChannelId = channelId) }
            try {
                val verification = verifyChannel(channelId)
                _uiState.update {
                    it.copy(
                        verifyingChannelId = null,
                        verifications = it.verifications + (channelId to verification),
                        infoMessage = if (verification.reachable) {
                            "\"${verification.channel.displayName}\" is reachable; permissions refreshed."
                        } else {
                            "\"${verification.channel.displayName}\" could not be reached right now."
                        },
                    )
                }
                refreshMappingStats()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        verifyingChannelId = null,
                        errorMessage = (t as? AppError)?.userMessage ?: "Permission verification failed.",
                    )
                }
            }
        }
    }

    fun onVerifyAllClick() {
        val targets = _uiState.value.channels.filter { it.enabled }
        if (targets.isEmpty()) {
            _uiState.update { it.copy(infoMessage = "There is no enabled channel to verify.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            var reachable = 0
            var unreachable = 0
            targets.forEach { channel ->
                _uiState.update { it.copy(verifyingChannelId = channel.id) }
                try {
                    val verification = verifyChannel(channel.id)
                    _uiState.update { it.copy(verifications = it.verifications + (channel.id to verification)) }
                    if (verification.reachable) reachable += 1 else unreachable += 1
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    unreachable += 1
                }
            }
            _uiState.update {
                it.copy(
                    busy = false,
                    verifyingChannelId = null,
                    infoMessage = "Verified ${targets.size} channel(s): $reachable reachable, $unreachable not reachable.",
                )
            }
            refreshMappingStats()
        }
    }

    fun onSetDefaultClick(channel: StorageChannel) = _uiState.update { it.copy(pendingDefault = channel) }

    fun onDismissDefault() = _uiState.update { it.copy(pendingDefault = null) }

    fun onConfirmSetDefault() {
        val channel = _uiState.value.pendingDefault ?: return
        runGuarded("The default channel could not be changed.") {
            setDefaultChannel(channel.id)
            _uiState.update {
                it.copy(pendingDefault = null, infoMessage = "\"${channel.displayName}\" is now the default upload target.")
            }
        }
    }

    fun onToggleEnabled(channel: StorageChannel, enabled: Boolean) {
        runGuarded(if (enabled) "The channel could not be enabled." else "The channel could not be disabled.") {
            setChannelEnabled(channel.id, enabled)
            _uiState.update {
                it.copy(
                    infoMessage = "\"${channel.displayName}\" ${if (enabled) "can receive uploads again." else "will not receive new uploads."}",
                )
            }
        }
    }

    fun onRenameClick(channel: StorageChannel) = _uiState.update {
        it.copy(renameTarget = channel, renameValue = channel.localLabel ?: channel.title, renameError = null)
    }

    fun onRenameChange(value: String) = _uiState.update { it.copy(renameValue = value, renameError = null) }

    fun onDismissRename() = _uiState.update { it.copy(renameTarget = null, renameValue = "", renameError = null) }

    fun onConfirmRename() {
        val channel = _uiState.value.renameTarget ?: return
        val label = _uiState.value.renameValue.trim()
        if (label.isEmpty()) {
            _uiState.update { it.copy(renameError = "Enter a label, or clear it to use the Telegram title.") }
            return
        }
        runGuarded("The channel could not be renamed.") {
            renameChannel(channel.id, label)
            _uiState.update {
                it.copy(renameTarget = null, renameValue = "", infoMessage = "Local label set to \"$label\".")
            }
        }
    }

    fun onResetRename() {
        val channel = _uiState.value.renameTarget ?: return
        runGuarded("The label could not be reset.") {
            renameChannel(channel.id, null)
            _uiState.update {
                it.copy(
                    renameTarget = null,
                    renameValue = "",
                    infoMessage = "The Telegram title \"${channel.title}\" is used again.",
                )
            }
        }
    }

    fun onRemoveClick(channel: StorageChannel) = _uiState.update { it.copy(pendingRemove = channel) }

    fun onDismissRemove() = _uiState.update { it.copy(pendingRemove = null) }

    fun onConfirmRemove() {
        val channel = _uiState.value.pendingRemove ?: return
        runGuarded("The channel could not be removed.") {
            removeStorageChannel(channel.id)
            _uiState.update {
                it.copy(
                    pendingRemove = null,
                    verifications = it.verifications - channel.id,
                    expandedChannelId = if (it.expandedChannelId == channel.id) null else it.expandedChannelId,
                    infoMessage = "\"${channel.displayName}\" was removed from Master Control. Its Telegram media is untouched.",
                )
            }
            refreshMappingStats()
        }
    }

    fun onDismissMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    private fun runGuarded(failureMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                block()
                _uiState.update { it.copy(busy = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(busy = false, errorMessage = (t as? AppError)?.userMessage ?: failureMessage)
                }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 450L
        const val MIN_QUERY_LENGTH = 2
    }
}
