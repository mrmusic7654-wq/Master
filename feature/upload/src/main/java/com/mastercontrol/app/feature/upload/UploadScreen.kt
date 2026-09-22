package com.mastercontrol.app.feature.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mastercontrol.app.core.common.format.DateTimeFormat
import com.mastercontrol.app.core.common.format.Formatters
import com.mastercontrol.app.core.ui.component.EmptyState
import com.mastercontrol.app.core.ui.component.LoadingState
import com.mastercontrol.app.core.ui.component.McChipRow
import com.mastercontrol.app.core.ui.component.McConfirmDialog
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McSelectableChip
import com.mastercontrol.app.core.ui.component.McTone
import com.mastercontrol.app.core.ui.component.NoticeBar
import com.mastercontrol.app.core.ui.component.SectionHint
import com.mastercontrol.app.core.ui.component.StatusChip
import com.mastercontrol.app.core.ui.component.TransferProgressBar
import com.mastercontrol.app.core.ui.theme.MonoLabelStyle
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState

/** One row of the queue list: either a section header or a task card. */
private sealed interface UploadListEntry {
    data class Header(val title: String, val count: Int) : UploadListEntry
    data class Task(val task: UploadTask) : UploadListEntry
}

/**
 * Upload queue.
 *
 * Shows only real transfer state: byte counters persisted by the upload worker,
 * rates derived from those counters, and the task's stored error. Uploads
 * continue when the app is backgrounded, the device rotates, or the process is
 * killed and restarted — the queue is durable in Room, not in this screen.
 */
@Composable
fun UploadRoute(
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UploadViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val message = state.errorMessage ?: state.infoMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onDismissMessage()
        }
    }

    UploadScreen(
        state = state,
        modifier = modifier,
        onFilterChange = viewModel::onFilterChange,
        onOpenVideo = onOpenVideo,
        onCancelClick = viewModel::onCancelClick,
        onDismissCancel = viewModel::onDismissCancel,
        onConfirmCancel = viewModel::onConfirmCancel,
        onRetryClick = viewModel::onRetryClick,
        onRequeueClick = viewModel::onRequeueClick,
        onRetryAllClick = viewModel::onRetryAllClick,
        onClearFinishedClick = viewModel::onClearFinishedClick,
        onDismissClearFinished = viewModel::onDismissClearFinished,
        onConfirmClearFinished = viewModel::onConfirmClearFinished,
        onClearProblemsClick = viewModel::onClearProblemsClick,
        onDismissClearProblems = viewModel::onDismissClearProblems,
        onConfirmClearProblems = viewModel::onConfirmClearProblems,
    )
}

@Composable
internal fun UploadScreen(
    state: UploadUiState,
    modifier: Modifier = Modifier,
    onFilterChange: (UploadFilter) -> Unit = {},
    onOpenVideo: (String) -> Unit = {},
    onCancelClick: (UploadTask) -> Unit = {},
    onDismissCancel: () -> Unit = {},
    onConfirmCancel: () -> Unit = {},
    onRetryClick: (UploadTask) -> Unit = {},
    onRequeueClick: (UploadTask) -> Unit = {},
    onRetryAllClick: () -> Unit = {},
    onClearFinishedClick: () -> Unit = {},
    onDismissClearFinished: () -> Unit = {},
    onConfirmClearFinished: () -> Unit = {},
    onClearProblemsClick: () -> Unit = {},
    onDismissClearProblems: () -> Unit = {},
    onConfirmClearProblems: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = McDimens.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd),
    ) {
        McChipRow(modifier = Modifier.fillMaxWidth()) {
            UploadFilter.entries.forEach { filter ->
                McSelectableChip(
                    selected = state.filter == filter,
                    label = "${filter.label} (${state.countFor(filter)})",
                    onClick = { onFilterChange(filter) },
                )
            }
        }

        val summary = buildString {
            if (state.transferring.isNotEmpty()) append("${state.transferring.size} transferring · ")
            if (state.waiting.isNotEmpty()) append("${state.waiting.size} waiting · ")
            append(Formatters.bytes(state.remainingBytes)).append(" left to send")
        }
        SectionHint(summary)
        if (state.hasUnknownSizes) {
            SectionHint(
                "Some tasks have no known file size yet, so their percentage and remaining time " +
                    "stay blank until the size is read from the file.",
            )
        }

        if (state.loading) {
            LoadingState(message = "Reading the upload queue…")
            return@Column
        }

        val entries = buildList {
            val groups = if (state.filter == UploadFilter.ALL) {
                listOf(
                    "Transferring" to state.transferring,
                    "Waiting" to state.waiting,
                    "Needs attention" to state.problems,
                    "Finished" to state.finished,
                )
            } else {
                listOf(state.filter.label to state.visibleTasks)
            }
            groups.filter { it.second.isNotEmpty() }.forEach { (title, tasks) ->
                add(UploadListEntry.Header(title, tasks.size))
                tasks.forEach { add(UploadListEntry.Task(it)) }
            }
        }

        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.CloudUpload,
                title = when (state.filter) {
                    UploadFilter.ACTIVE -> "Nothing is transferring"
                    UploadFilter.WAITING -> "Nothing is waiting"
                    UploadFilter.PROBLEMS -> "No failed or cancelled uploads"
                    UploadFilter.DONE -> "No finished uploads yet"
                    UploadFilter.ALL -> "The upload queue is empty"
                },
                message = "Queue a video from the library. Uploads run in a foreground worker, " +
                    "survive restarts and rotation, and are retried with bounded backoff.",
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = McDimens.SpacingXxl),
            verticalArrangement = Arrangement.spacedBy(McDimens.SpacingSm),
        ) {
            items(entries, key = { entry ->
                when (entry) {
                    is UploadListEntry.Header -> "header-${entry.title}"
                    is UploadListEntry.Task -> "task-${entry.task.taskId}"
                }
            }) { entry ->
                when (entry) {
                    is UploadListEntry.Header -> SectionHeader(title = entry.title, count = entry.count)
                    is UploadListEntry.Task -> UploadTaskCard(
                        task = entry.task,
                        reading = state.readings[entry.task.taskId],
                        busy = state.busy,
                        onOpenVideo = { onOpenVideo(entry.task.videoId) },
                        onCancelClick = { onCancelClick(entry.task) },
                        onRetryClick = { onRetryClick(entry.task) },
                        onRequeueClick = { onRequeueClick(entry.task) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm),
        ) {
            if (state.problems.isNotEmpty()) {
                OutlinedButton(onClick = onRetryAllClick, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(McDimens.SpacingXs))
                    Text("Requeue failed")
                }
                OutlinedButton(onClick = onClearProblemsClick, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(McDimens.SpacingXs))
                    Text("Clear")
                }
            }
            if (state.finished.isNotEmpty()) {
                OutlinedButton(onClick = onClearFinishedClick, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(McDimens.SpacingXs))
                    Text("Clear finished")
                }
            }
        }
    }

    state.pendingCancel?.let { task ->
        McConfirmDialog(
            title = "Cancel the upload of ${task.videoId}?",
            message = buildString {
                append("The transfer stops and ${Formatters.bytes(task.bytesUploaded)} already sent ")
                append("are discarded by Telegram. Your video, its permanent ID and any earlier ")
                append("mapping stay in the catalog. You can queue it again at any time.")
            },
            confirmLabel = "Cancel upload",
            dismissLabel = "Keep uploading",
            destructive = true,
            acknowledgementLabel = "I understand the partial upload is discarded",
            icon = Icons.Filled.Warning,
            onConfirm = onConfirmCancel,
            onDismiss = onDismissCancel,
        )
    }

    if (state.pendingClearFinished) {
        McConfirmDialog(
            title = "Clear ${state.finished.size} finished task(s)?",
            message = "The queue rows are removed from this device. Your videos, their permanent " +
                "IDs and their Telegram mappings are untouched.",
            confirmLabel = "Clear finished",
            dismissLabel = "Keep",
            destructive = true,
            icon = Icons.Filled.Delete,
            onConfirm = onConfirmClearFinished,
            onDismiss = onDismissClearFinished,
        )
    }

    if (state.pendingClearProblems) {
        McConfirmDialog(
            title = "Clear ${state.problems.size} failed or cancelled task(s)?",
            message = "The queue rows are removed from this device. The videos stay in your library " +
                "and can be queued again; nothing is deleted from Telegram.",
            confirmLabel = "Clear",
            dismissLabel = "Keep",
            destructive = true,
            acknowledgementLabel = "I understand these uploads will not be retried automatically",
            icon = Icons.Filled.Delete,
            onConfirm = onConfirmClearProblems,
            onDismiss = onDismissClearProblems,
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = McDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UploadTaskCard(
    task: UploadTask,
    reading: TransferReading?,
    busy: Boolean,
    onOpenVideo: () -> Unit,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    onRequeueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = task.state.tone()
    val total = task.totalBytes ?: 0L
    val knownSize = total > 0L

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(McDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(McDimens.SpacingSm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = task.videoId, style = MonoLabelStyle)
                }
                Spacer(Modifier.width(McDimens.SpacingSm))
                StatusChip(text = task.state.label(), tone = tone, icon = task.state.icon())
            }

            when (task.state) {
                UploadTaskState.UPLOADING, UploadTaskState.VERIFYING, UploadTaskState.PREPARING -> {
                    TransferProgressBar(
                        progress = if (knownSize) task.progress else null,
                        rateText = reading?.bytesPerSecond?.let { rate ->
                            if (rate > 0.0) Formatters.rate(rate) else "stalled"
                        },
                        etaText = reading?.remainingSeconds?.let { seconds -> Formatters.eta(seconds) },
                        detail = if (knownSize) {
                            "${Formatters.percent(task.progress)} · ${Formatters.bytes(task.bytesUploaded)} of ${Formatters.bytes(total)}"
                        } else {
                            "${Formatters.bytes(task.bytesUploaded)} sent"
                        },
                        tone = tone,
                    )
                }

                UploadTaskState.COMPLETED -> TransferProgressBar(
                    progress = 1f,
                    detail = if (knownSize) "${Formatters.bytes(total)} uploaded and committed" else "Uploaded and committed",
                    tone = McTone.SUCCESS,
                )

                UploadTaskState.RETRYING -> TransferProgressBar(
                    progress = null,
                    detail = if (knownSize) "${Formatters.bytes(task.bytesUploaded)} of ${Formatters.bytes(total)} sent so far" else null,
                    tone = McTone.WARNING,
                )

                UploadTaskState.QUEUED, UploadTaskState.FAILED, UploadTaskState.CANCELLED -> Unit
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Updated ${DateTimeFormat.relative(task.updatedAt.toEpochMilli())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (task.attemptCount > 0) {
                    Text(
                        text = "${task.attemptCount} attempt${if (task.attemptCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (task.lastError != null) {
                NoticeBar(
                    message = task.lastError,
                    tone = McTone.DANGER,
                    icon = Icons.Filled.Error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
                TextButton(onClick = onOpenVideo) { Text("Open video") }
                when (task.state) {
                    UploadTaskState.QUEUED,
                    UploadTaskState.PREPARING,
                    UploadTaskState.UPLOADING,
                    UploadTaskState.VERIFYING,
                    UploadTaskState.RETRYING,
                    -> TextButton(onClick = onCancelClick, enabled = !busy) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(McDimens.SpacingXs))
                        Text("Cancel")
                    }

                    UploadTaskState.FAILED -> TextButton(onClick = onRetryClick, enabled = !busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(McDimens.SpacingXs))
                        Text("Retry")
                    }

                    UploadTaskState.CANCELLED -> TextButton(onClick = onRequeueClick, enabled = !busy) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(McDimens.SpacingXs))
                        Text("Queue again")
                    }

                    UploadTaskState.COMPLETED -> Unit
                }
            }
        }
    }
}

private fun UploadTaskState.label(): String = when (this) {
    UploadTaskState.QUEUED -> "Queued"
    UploadTaskState.PREPARING -> "Preparing"
    UploadTaskState.UPLOADING -> "Uploading"
    UploadTaskState.VERIFYING -> "Verifying"
    UploadTaskState.RETRYING -> "Retrying"
    UploadTaskState.COMPLETED -> "Uploaded"
    UploadTaskState.FAILED -> "Failed"
    UploadTaskState.CANCELLED -> "Cancelled"
}

private fun UploadTaskState.tone(): McTone = when (this) {
    UploadTaskState.COMPLETED -> McTone.SUCCESS
    UploadTaskState.FAILED -> McTone.DANGER
    UploadTaskState.RETRYING -> McTone.WARNING
    UploadTaskState.CANCELLED -> McTone.NEUTRAL
    UploadTaskState.QUEUED -> McTone.NEUTRAL
    UploadTaskState.PREPARING, UploadTaskState.UPLOADING, UploadTaskState.VERIFYING -> McTone.INFO
}

private fun UploadTaskState.icon() = when (this) {
    UploadTaskState.COMPLETED -> Icons.Filled.CheckCircle
    UploadTaskState.FAILED -> Icons.Filled.Error
    UploadTaskState.RETRYING -> Icons.Filled.Refresh
    UploadTaskState.CANCELLED -> Icons.Filled.Cancel
    UploadTaskState.QUEUED -> Icons.Filled.Schedule
    UploadTaskState.PREPARING, UploadTaskState.UPLOADING, UploadTaskState.VERIFYING -> Icons.Filled.CloudUpload
}
