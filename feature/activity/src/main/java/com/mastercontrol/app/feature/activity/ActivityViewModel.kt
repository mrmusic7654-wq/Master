package com.mastercontrol.app.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import com.mastercontrol.app.domain.repository.ActivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filter groups over the local activity log. */
enum class ActivityFilter(val label: String) {
    ALL("All"),
    UPLOADS("Uploads"),
    LIBRARY("Library"),
    TELEGRAM("Telegram"),
    SYSTEM("System"),
}

data class ActivityUiState(
    val filter: ActivityFilter = ActivityFilter.ALL,
    val allEntries: List<ActivityLogEntry> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val confirmClear: Boolean = false,
) {
    val entries: List<ActivityLogEntry>
        get() = if (filter == ActivityFilter.ALL) allEntries else allEntries.filter { it.type.group() == filter }

    fun countFor(filter: ActivityFilter): Int =
        if (filter == ActivityFilter.ALL) allEntries.size else allEntries.count { it.type.group() == filter }
}

/**
 * Local operational log. Entries are written by the real subsystems (import,
 * upload worker, channel management, backup); nothing here is synthesized.
 */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                activityRepository.observeRecent(MAX_ENTRIES).collect { entries ->
                    _uiState.update { it.copy(allEntries = entries, loading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = (t as? AppError)?.userMessage ?: "The activity log could not be read.",
                    )
                }
            }
        }
    }

    fun onFilterChange(filter: ActivityFilter) = _uiState.update { it.copy(filter = filter) }

    fun onClearRequested() = _uiState.update { it.copy(confirmClear = true) }

    fun onDismissClear() = _uiState.update { it.copy(confirmClear = false) }

    fun onConfirmClear() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                activityRepository.clear()
                _uiState.update { it.copy(busy = false, confirmClear = false, infoMessage = "Activity log cleared.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        confirmClear = false,
                        errorMessage = (t as? AppError)?.userMessage ?: "The activity log could not be cleared.",
                    )
                }
            }
        }
    }

    fun onDismissMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}

/** Groups an activity type for filtering; derived from the real enum values. */
internal fun ActivityType.group(): ActivityFilter = when (this) {
    ActivityType.UPLOAD_QUEUED,
    ActivityType.UPLOAD_STARTED,
    ActivityType.UPLOAD_COMPLETED,
    ActivityType.UPLOAD_FAILED,
    ActivityType.UPLOAD_CANCELLED,
    ActivityType.UPLOAD_RETRYING,
    -> ActivityFilter.UPLOADS

    ActivityType.VIDEO_IMPORTED,
    ActivityType.METADATA_EDITED,
    ActivityType.THUMBNAIL_CHANGED,
    ActivityType.VIDEO_REPLACED,
    ActivityType.VIDEO_DELETED,
    -> ActivityFilter.LIBRARY

    ActivityType.CHANNEL_ADDED,
    ActivityType.CHANNEL_REMOVED,
    ActivityType.CHANNEL_DEFAULT_CHANGED,
    ActivityType.CHANNEL_VERIFIED,
    ActivityType.CHANNEL_ENABLED,
    ActivityType.CHANNEL_DISABLED,
    ActivityType.TELEGRAM_CONNECTED,
    ActivityType.TELEGRAM_CONNECTION_RESTORED,
    ActivityType.TELEGRAM_DISCONNECTED,
    ActivityType.MAPPING_VERIFIED,
    ActivityType.MAPPING_STALE,
    ActivityType.UNMANAGED_TELEGRAM_MEDIA_FOUND,
    ActivityType.RECONCILIATION_RAN,
    ActivityType.AUTHENTICATED,
    ActivityType.LOGGED_OUT,
    ActivityType.CREDENTIALS_UPDATED,
    -> ActivityFilter.TELEGRAM

    ActivityType.BACKUP_EXPORTED,
    ActivityType.BACKUP_IMPORTED,
    ActivityType.APP_LOCK_ENABLED,
    ActivityType.APP_LOCK_DISABLED,
    ActivityType.ERROR_RECOVERED,
    -> ActivityFilter.SYSTEM
}
