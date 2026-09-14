package com.mastercontrol.app.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mastercontrol.app.core.common.format.DateTimeFormat
import com.mastercontrol.app.core.ui.component.EmptyState
import com.mastercontrol.app.core.ui.component.LoadingState
import com.mastercontrol.app.core.ui.component.McChipRow
import com.mastercontrol.app.core.ui.component.McConfirmDialog
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McIconAction
import com.mastercontrol.app.core.ui.component.McSelectableChip
import com.mastercontrol.app.core.ui.component.McTone
import com.mastercontrol.app.core.ui.component.StatusChip
import com.mastercontrol.app.core.ui.component.toneColor
import com.mastercontrol.app.core.ui.theme.MonoLabelStyle
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType

/**
 * Activity log screen.
 *
 * [onOpenVideo] lets an operator jump from an entry to the video it refers to;
 * the video ID relationship is the core invariant of Master Control, so it is
 * always rendered as real text rather than only as decoration.
 */
@Composable
fun ActivityRoute(
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = hiltViewModel(),
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

    ActivityScreen(
        state = state,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onFilterChange = viewModel::onFilterChange,
        onOpenVideo = onOpenVideo,
        onClearRequested = viewModel::onClearRequested,
        onDismissClear = viewModel::onDismissClear,
        onConfirmClear = viewModel::onConfirmClear,
    )
}

@Composable
internal fun ActivityScreen(
    state: ActivityUiState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onFilterChange: (ActivityFilter) -> Unit = {},
    onOpenVideo: (String) -> Unit = {},
    onClearRequested: () -> Unit = {},
    onDismissClear: () -> Unit = {},
    onConfirmClear: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = McDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            McChipRow(modifier = Modifier.weight(1f)) {
                ActivityFilter.entries.forEach { filter ->
                    McSelectableChip(
                        selected = state.filter == filter,
                        label = "${filter.label} (${state.countFor(filter)})",
                        onClick = { onFilterChange(filter) },
                    )
                }
            }
            McIconAction(
                icon = Icons.Filled.Delete,
                contentDescription = "Clear activity log",
                onClick = onClearRequested,
                enabled = state.allEntries.isNotEmpty() && !state.busy,
            )
        }

        if (state.loading) {
            LoadingState(message = "Reading the activity log…")
            return@Column
        }

        if (state.entries.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                title = if (state.filter == ActivityFilter.ALL) "No activity recorded yet" else "No ${state.filter.label.lowercase()} activity",
                message = "Imports, uploads, metadata edits, channel changes and backups are " +
                    "written to this log as they really happen.",
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = McDimens.SpacingLg, vertical = McDimens.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(McDimens.SpacingXs),
        ) {
            items(state.entries, key = { it.activityId }) { entry ->
                ActivityRow(entry = entry, onOpenVideo = onOpenVideo)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    if (state.confirmClear) {
        McConfirmDialog(
            title = "Clear the activity log?",
            message = "All ${state.allEntries.size} recorded event(s) are deleted from this device. " +
                "Your catalog, mappings and Telegram media are not affected.",
            confirmLabel = "Clear log",
            dismissLabel = "Keep",
            destructive = true,
            acknowledgementLabel = "I understand this history cannot be restored",
            icon = Icons.Filled.Warning,
            onConfirm = onConfirmClear,
            onDismiss = onDismissClear,
        )
    }
}

@Composable
private fun ActivityRow(entry: ActivityLogEntry, onOpenVideo: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = McDimens.SpacingSm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = entry.type.icon(),
            contentDescription = null,
            tint = toneColor(entry.type.tone()),
            modifier = Modifier.size(18.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(McDimens.SpacingMd))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (entry.details != null) {
                Text(
                    text = entry.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(McDimens.SpacingXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DateTimeFormat.relative(entry.createdAt.toEpochMilli()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.relatedVideoId != null) {
                    Spacer(Modifier.width(McDimens.SpacingSm))
                    TextButton(onClick = { onOpenVideo(entry.relatedVideoId) }) {
                        Text(entry.relatedVideoId, style = MonoLabelStyle)
                    }
                }
                if (entry.channelId != null) {
                    Spacer(Modifier.width(McDimens.SpacingSm))
                    StatusChip(text = "channel ${entry.channelId}", tone = McTone.NEUTRAL)
                }
            }
        }
    }
}

private fun ActivityType.icon(): ImageVector = when (group()) {
    ActivityFilter.UPLOADS -> Icons.Filled.CloudUpload
    ActivityFilter.LIBRARY -> Icons.Filled.VideoLibrary
    ActivityFilter.TELEGRAM -> Icons.Filled.Sync
    ActivityFilter.SYSTEM -> Icons.Filled.Settings
}

private fun ActivityType.tone(): McTone = when (this) {
    ActivityType.UPLOAD_FAILED,
    ActivityType.MAPPING_STALE,
    ActivityType.UNMANAGED_TELEGRAM_MEDIA_FOUND,
    ActivityType.VIDEO_DELETED,
    ActivityType.TELEGRAM_DISCONNECTED,
    -> McTone.DANGER

    ActivityType.UPLOAD_COMPLETED,
    ActivityType.MAPPING_VERIFIED,
    ActivityType.TELEGRAM_CONNECTED,
    ActivityType.TELEGRAM_CONNECTION_RESTORED,
    ActivityType.AUTHENTICATED,
    ActivityType.CHANNEL_VERIFIED,
    -> McTone.SUCCESS

    ActivityType.UPLOAD_RETRYING,
    ActivityType.ERROR_RECOVERED,
    ActivityType.CHANNEL_DEFAULT_CHANGED,
    -> McTone.WARNING

    else -> McTone.INFO
}
