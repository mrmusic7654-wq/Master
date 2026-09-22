package com.mastercontrol.app.feature.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercontrol.app.core.common.transfer.TransferRateEstimator
import com.mastercontrol.app.domain.error.AppError
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState
import com.mastercontrol.app.domain.repository.UploadTaskRepository
import com.mastercontrol.app.domain.usecase.CancelUploadUseCase
import com.mastercontrol.app.domain.usecase.QueueUploadUseCase
import com.mastercontrol.app.domain.usecase.RetryUploadUseCase
import com.mastercontrol.app.domain.usecase.UploadQueueActionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Queue views over the durable upload tasks. */
enum class UploadFilter(val label: String) {
    ACTIVE("Active"),
    WAITING("Waiting"),
    PROBLEMS("Needs attention"),
    DONE("Finished"),
    ALL("All"),
}

/** Per-task transfer readings derived from persisted byte counters. */
data class TransferReading(
    val bytesPerSecond: Double?,
    val remainingSeconds: Long?,
)

data class UploadUiState(
    val filter: UploadFilter = UploadFilter.ACTIVE,
    val tasks: List<UploadTask> = emptyList(),
    val readings: Map<Long, TransferReading> = emptyMap(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingCancel: UploadTask? = null,
    val pendingClearFinished: Boolean = false,
    val pendingClearProblems: Boolean = false,
) {
    val visibleTasks: List<UploadTask>
        get() = when (filter) {
            UploadFilter.ACTIVE -> tasks.filter { it.state in ACTIVE_STATES }
            UploadFilter.WAITING -> tasks.filter { it.state == UploadTaskState.QUEUED || it.state == UploadTaskState.PREPARING }
            UploadFilter.PROBLEMS -> tasks.filter { it.state == UploadTaskState.FAILED || it.state == UploadTaskState.CANCELLED }
            UploadFilter.DONE -> tasks.filter { it.state == UploadTaskState.COMPLETED }
            UploadFilter.ALL -> tasks
        }

    fun countFor(filter: UploadFilter): Int = when (filter) {
        UploadFilter.ACTIVE -> tasks.count { it.state in ACTIVE_STATES }
        UploadFilter.WAITING -> tasks.count { it.state == UploadTaskState.QUEUED || it.state == UploadTaskState.PREPARING }
        UploadFilter.PROBLEMS -> tasks.count { it.state == UploadTaskState.FAILED || it.state == UploadTaskState.CANCELLED }
        UploadFilter.DONE -> tasks.count { it.state == UploadTaskState.COMPLETED }
        UploadFilter.ALL -> tasks.size
    }

    val transferring: List<UploadTask> get() = tasks.filter { it.state == UploadTaskState.UPLOADING || it.state == UploadTaskState.VERIFYING }
    val waiting: List<UploadTask> get() = tasks.filter { it.state == UploadTaskState.QUEUED || it.state == UploadTaskState.PREPARING || it.state == UploadTaskState.RETRYING }
    val problems: List<UploadTask> get() = tasks.filter { it.state == UploadTaskState.FAILED || it.state == UploadTaskState.CANCELLED }
    val finished: List<UploadTask> get() = tasks.filter { it.state == UploadTaskState.COMPLETED }

    /** Bytes still to transfer across queued/active tasks (real sizes only). */
    val remainingBytes: Long
        get() = tasks.filter { it.state in ACTIVE_STATES || it.state == UploadTaskState.QUEUED }
            .sumOf { task ->
                val total = task.totalBytes ?: 0L
                if (total > 0L) (total - task.bytesUploaded).coerceAtLeast(0L) else 0L
            }

    val hasUnknownSizes: Boolean
        get() = tasks.any { (it.state in ACTIVE_STATES || it.state == UploadTaskState.QUEUED) && (it.totalBytes ?: 0L) <= 0L }

    companion object {
        val ACTIVE_STATES = setOf(
            UploadTaskState.UPLOADING,
            UploadTaskState.PREPARING,
            UploadTaskState.VERIFYING,
            UploadTaskState.RETRYING,
        )
    }
}

/**
 * Upload queue screen state.
 *
 * Progress and rates come only from `bytesUploaded` counters the upload worker
 * persists from real TDLib `updateFile` events. TDLib cannot pause a file
 * transfer mid-stream, so the honest controls are cancel and requeue; there is
 * no pause button that would pretend otherwise.
 */
@HiltViewModel
class UploadViewModel @Inject constructor(
    private val uploadTaskRepository: UploadTaskRepository,
    private val cancelUpload: CancelUploadUseCase,
    private val retryUpload: RetryUploadUseCase,
    private val queueUpload: QueueUploadUseCase,
    private val queueActions: UploadQueueActionsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    /** One estimator per task, confined to the collector coroutine. */
    private val estimators = mutableMapOf<Long, TransferRateEstimator>()

    init {
        viewModelScope.launch {
            try {
                uploadTaskRepository.observeAll().collect { tasks ->
                    val readings = mutableMapOf<Long, TransferReading>()
                    val liveIds = mutableSetOf<Long>()
                    tasks.forEach { task ->
                        if (task.state in UploadUiState.ACTIVE_STATES || task.state == UploadTaskState.COMPLETED) {
                            liveIds += task.taskId
                            val estimator = estimators.getOrPut(task.taskId) { TransferRateEstimator() }
                            estimator.record(task.bytesUploaded)
                            readings[task.taskId] = TransferReading(
                                bytesPerSecond = if (task.state == UploadTaskState.COMPLETED) null else estimator.bytesPerSecond(),
                                remainingSeconds = if (task.state == UploadTaskState.COMPLETED) {
                                    null
                                } else {
                                    estimator.remainingSeconds(task.bytesUploaded, task.totalBytes)
                                },
                            )
                        }
                    }
                    // Drop windows for tasks that are gone or no longer moving.
                    estimators.keys.filter { it !in liveIds }.forEach { estimators.remove(it)?.reset() }

                    val defaultFilter = _uiState.value.filter
                    _uiState.update {
                        it.copy(
                            tasks = tasks,
                            readings = readings,
                            loading = false,
                            filter = if (it.loading && defaultFilter == UploadFilter.ACTIVE && tasks.none { t -> t.state in UploadUiState.ACTIVE_STATES }) {
                                UploadFilter.ALL
                            } else {
                                defaultFilter
                            },
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = (t as? AppError)?.userMessage ?: "The upload queue could not be read.",
                    )
                }
            }
        }
    }

    fun onFilterChange(filter: UploadFilter) = _uiState.update { it.copy(filter = filter) }

    fun onCancelClick(task: UploadTask) = _uiState.update { it.copy(pendingCancel = task) }

    fun onDismissCancel() = _uiState.update { it.copy(pendingCancel = null) }

    fun onConfirmCancel() {
        val task = _uiState.value.pendingCancel ?: return
        runGuarded("The upload could not be cancelled.") {
            cancelUpload(task.taskId)
            estimators.remove(task.taskId)?.reset()
            _uiState.update {
                it.copy(pendingCancel = null, infoMessage = "${task.videoId} upload cancelled. The video stays in your library.")
            }
        }
    }

    fun onRetryClick(task: UploadTask) {
        runGuarded("The upload could not be requeued.") {
            retryUpload(task.taskId)
            estimators[task.taskId]?.reset()
            _uiState.update { it.copy(infoMessage = "${task.videoId} is queued again.") }
        }
    }

    /**
     * Queues a cancelled task again.
     *
     * This creates a *new* task rather than resurrecting the cancelled row: TDLib
     * cannot resume a partially sent file, so the upload genuinely starts over and
     * the queue history stays truthful.
     */
    fun onRequeueClick(task: UploadTask) {
        runGuarded("The upload could not be queued again.") {
            queueUpload(task.videoId, task.channelId)
            _uiState.update { it.copy(infoMessage = "${task.videoId} is queued again and starts from the beginning.") }
        }
    }

    fun onRetryAllClick() {
        runGuarded("Failed uploads could not be requeued.") {
            val count = queueActions.retryAllFailed()
            _uiState.update {
                it.copy(infoMessage = if (count > 0) "$count upload(s) requeued." else "No failed uploads to requeue.")
            }
        }
    }

    fun onClearFinishedClick() = _uiState.update { it.copy(pendingClearFinished = true) }

    fun onDismissClearFinished() = _uiState.update { it.copy(pendingClearFinished = false) }

    fun onConfirmClearFinished() {
        runGuarded("Finished uploads could not be cleared.") {
            val count = queueActions.clearCompleted()
            _uiState.update {
                it.copy(pendingClearFinished = false, infoMessage = "$count finished task(s) removed from the queue list.")
            }
        }
    }

    fun onClearProblemsClick() = _uiState.update { it.copy(pendingClearProblems = true) }

    fun onDismissClearProblems() = _uiState.update { it.copy(pendingClearProblems = false) }

    fun onConfirmClearProblems() {
        runGuarded("Failed uploads could not be cleared.") {
            val count = queueActions.clearFailed()
            _uiState.update {
                it.copy(pendingClearProblems = false, infoMessage = "$count failed or cancelled task(s) removed from the queue list.")
            }
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
                    it.copy(
                        busy = false,
                        errorMessage = (t as? AppError)?.userMessage ?: failureMessage,
                    )
                }
            }
        }
    }
}
