package com.mastercontrol.app.feature.channels

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mastercontrol.app.core.common.format.DateTimeFormat
import com.mastercontrol.app.core.common.format.Formatters
import com.mastercontrol.app.core.ui.component.CopyValueRow
import com.mastercontrol.app.core.ui.component.DataRow
import com.mastercontrol.app.core.ui.component.EmptyState
import com.mastercontrol.app.core.ui.component.LoadingState
import com.mastercontrol.app.core.ui.component.McConfirmDialog
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McIconAction
import com.mastercontrol.app.core.ui.component.McTextField
import com.mastercontrol.app.core.ui.component.McTone
import com.mastercontrol.app.core.ui.component.NoticeBar
import com.mastercontrol.app.core.ui.component.PermissionItem
import com.mastercontrol.app.core.ui.component.PermissionList
import com.mastercontrol.app.core.ui.component.SectionCard
import com.mastercontrol.app.core.ui.component.SectionHint
import com.mastercontrol.app.core.ui.component.StatusChip
import com.mastercontrol.app.core.ui.component.McAnimatedVisibility
import com.mastercontrol.app.core.ui.component.toneColor
import com.mastercontrol.app.domain.model.ChannelCandidate
import com.mastercontrol.app.domain.model.ChannelKind
import com.mastercontrol.app.domain.model.ConnectionState
import com.mastercontrol.app.domain.model.StorageChannel

/**
 * Storage channel management.
 *
 * Channel search and permission verification are live TDLib operations; the
 * stored rows are local bookkeeping. Nothing in this screen deletes Telegram
 * media — removal only forgets the channel inside Master Control.
 */
@Composable
fun ChannelsRoute(
    modifier: Modifier = Modifier,
    viewModel: ChannelsViewModel = hiltViewModel(),
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

    ChannelsScreen(
        state = state,
        modifier = modifier,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onAddChannel = viewModel::onAddChannel,
        onToggleExpand = viewModel::onToggleExpand,
        onVerifyClick = viewModel::onVerifyClick,
        onVerifyAllClick = viewModel::onVerifyAllClick,
        onSetDefaultClick = viewModel::onSetDefaultClick,
        onDismissDefault = viewModel::onDismissDefault,
        onConfirmSetDefault = viewModel::onConfirmSetDefault,
        onToggleEnabled = viewModel::onToggleEnabled,
        onRenameClick = viewModel::onRenameClick,
        onRenameChange = viewModel::onRenameChange,
        onDismissRename = viewModel::onDismissRename,
        onConfirmRename = viewModel::onConfirmRename,
        onResetRename = viewModel::onResetRename,
        onRemoveClick = viewModel::onRemoveClick,
        onDismissRemove = viewModel::onDismissRemove,
        onConfirmRemove = viewModel::onConfirmRemove,
    )
}

@Composable
internal fun ChannelsScreen(
    state: ChannelsUiState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onClearQuery: () -> Unit = {},
    onAddChannel: (ChannelCandidate, Boolean) -> Unit = { _, _ -> },
    onToggleExpand: (Long) -> Unit = {},
    onVerifyClick: (Long) -> Unit = {},
    onVerifyAllClick: () -> Unit = {},
    onSetDefaultClick: (StorageChannel) -> Unit = {},
    onDismissDefault: () -> Unit = {},
    onConfirmSetDefault: () -> Unit = {},
    onToggleEnabled: (StorageChannel, Boolean) -> Unit = { _, _ -> },
    onRenameClick: (StorageChannel) -> Unit = {},
    onRenameChange: (String) -> Unit = {},
    onDismissRename: () -> Unit = {},
    onConfirmRename: () -> Unit = {},
    onResetRename: () -> Unit = {},
    onRemoveClick: (StorageChannel) -> Unit = {},
    onDismissRemove: () -> Unit = {},
    onConfirmRemove: () -> Unit = {},
) {
    if (state.loading) {
        LoadingState(message = "Loading storage channels…", modifier = modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = McDimens.SpacingLg, vertical = McDimens.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd),
    ) {
        item(key = "search") {
            Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
                McTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = "Search your Telegram channels",
                    placeholder = "Name or @username",
                    leadingIcon = Icons.Filled.Search,
                    trailing = {
                        if (state.query.isNotEmpty()) {
                            McIconAction(
                                icon = Icons.Filled.Clear,
                                contentDescription = "Clear search",
                                onClick = onClearQuery,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!state.searchAvailable) {
                    NoticeBar(
                        message = "Telegram is ${state.connectionState.label().lowercase()}. Channel search and " +
                            "permission verification need an active connection.",
                        tone = McTone.WARNING,
                        icon = Icons.Filled.Warning,
                    )
                }
                if (state.searching) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(McDimens.SpacingSm))
                        Text("Searching Telegram…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (state.newCandidates.isNotEmpty()) {
            item(key = "candidates-header") {
                Text(
                    text = "Channels found on Telegram",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.newCandidates, key = { "candidate-${it.chatId}" }) { candidate ->
                CandidateCard(
                    candidate = candidate,
                    busy = state.busy,
                    hasDefault = state.defaultChannelId != null,
                    onAdd = { onAddChannel(candidate, false) },
                    onAddAsDefault = { onAddChannel(candidate, true) },
                )
            }
        }

        if (state.alreadyStoredCandidates.isNotEmpty() && state.query.isNotBlank()) {
            item(key = "already-stored") {
                SectionHint(
                    "${state.alreadyStoredCandidates.size} result(s) are already configured below.",
                )
            }
        }

        item(key = "stored-header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Configured storage channels",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (state.channels.isNotEmpty()) {
                    TextButton(onClick = onVerifyAllClick, enabled = !state.busy && state.searchAvailable) {
                        Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(McDimens.SpacingXs))
                        Text("Verify all")
                    }
                }
            }
        }

        if (state.channels.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Filled.CloudDone,
                    title = "No storage channel yet",
                    message = "Search for a channel you administer and add it. Master Control uploads " +
                        "media there and records the Telegram message IDs behind each permanent video ID.",
                )
            }
        } else {
            items(state.channels, key = { "channel-${it.id}" }) { channel ->
                ChannelCard(
                    channel = channel,
                    state = state,
                    onToggleExpand = { onToggleExpand(channel.id) },
                    onVerifyClick = { onVerifyClick(channel.id) },
                    onSetDefaultClick = { onSetDefaultClick(channel) },
                    onToggleEnabled = { enabled -> onToggleEnabled(channel, enabled) },
                    onRenameClick = { onRenameClick(channel) },
                    onRemoveClick = { onRemoveClick(channel) },
                )
            }
        }
    }

    state.pendingDefault?.let { channel ->
        McConfirmDialog(
            title = "Make \"${channel.displayName}\" the default?",
            message = "New uploads are sent to this channel unless another one is chosen while " +
                "queueing. Existing videos keep the channel they were uploaded to.",
            confirmLabel = "Set as default",
            dismissLabel = "Cancel",
            icon = Icons.Filled.Star,
            onConfirm = onConfirmSetDefault,
            onDismiss = onDismissDefault,
        )
    }

    state.renameTarget?.let { channel ->
        RenameDialog(
            channel = channel,
            value = state.renameValue,
            errorText = state.renameError,
            busy = state.busy,
            onValueChange = onRenameChange,
            onConfirm = onConfirmRename,
            onReset = onResetRename,
            onDismiss = onDismissRename,
        )
    }

    state.pendingRemove?.let { channel ->
        val affected = state.mappingCount(channel.id)
        McConfirmDialog(
            title = "Remove \"${channel.displayName}\"?",
            message = buildString {
                append("Master Control forgets this channel on this device. ")
                if (affected > 0) {
                    append("$affected video(s) keep their mapping rows pointing at it, so their ")
                    append("Telegram message IDs stay recorded. ")
                }
                append("Nothing is deleted from Telegram, and the media inside the channel stays there.")
            },
            confirmLabel = "Remove channel",
            dismissLabel = "Keep",
            destructive = true,
            acknowledgementLabel = "I understand the Telegram channel and its media are not deleted",
            icon = Icons.Filled.Warning,
            onConfirm = onConfirmRemove,
            onDismiss = onDismissRemove,
        )
    }
}

@Composable
private fun CandidateCard(
    candidate: ChannelCandidate,
    busy: Boolean,
    hasDefault: Boolean,
    onAdd: () -> Unit,
    onAddAsDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = candidate.title,
        subtitle = candidate.username?.let { "@$it" } ?: "Private ${candidate.kind.label().lowercase()}",
        leadingIcon = Icons.Filled.CloudDone,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(text = candidate.kind.label(), tone = McTone.NEUTRAL)
            Spacer(Modifier.width(McDimens.SpacingSm))
            StatusChip(
                text = if (candidate.permissions.canUploadVideos) "Can upload" else "Cannot upload",
                tone = if (candidate.permissions.canUploadVideos) McTone.SUCCESS else McTone.DANGER,
                icon = if (candidate.permissions.canUploadVideos) Icons.Filled.Verified else Icons.Filled.Cancel,
            )
            candidate.memberCount?.let { count ->
                Spacer(Modifier.width(McDimens.SpacingSm))
                StatusChip(text = "${Formatters.compactCount(count.toLong())} members", tone = McTone.NEUTRAL)
            }
        }
        if (!candidate.permissions.canUploadVideos) {
            Spacer(Modifier.height(McDimens.SpacingSm))
            NoticeBar(
                message = "This account lacks posting rights here, so uploads would fail. " +
                    "Missing: ${candidate.permissions.missingForUpload.joinToString(", ")}.",
                tone = McTone.WARNING,
                icon = Icons.Filled.Warning,
            )
        }
        Spacer(Modifier.height(McDimens.SpacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
            Button(onClick = onAdd, enabled = !busy && candidate.permissions.canUploadVideos) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Add")
            }
            OutlinedButton(
                onClick = onAddAsDefault,
                enabled = !busy && candidate.permissions.canUploadVideos && !hasDefault,
            ) {
                Text("Add as default")
            }
        }
        if (!hasDefault) {
            SectionHint("No default channel is set yet; the first one you add becomes the upload target.")
        }
    }
}

@Composable
private fun ChannelCard(
    channel: StorageChannel,
    state: ChannelsUiState,
    onToggleExpand: () -> Unit,
    onVerifyClick: () -> Unit,
    onSetDefaultClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRenameClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = state.expandedChannelId == channel.id
    val verification = state.verifications[channel.id]
    val permissions = verification?.permissions ?: channel.permissions
    val verifying = state.verifyingChannelId == channel.id
    val videos = state.mappingCount(channel.id)

    SectionCard(
        title = channel.displayName,
        subtitle = channel.username?.let { "@$it" } ?: "Private ${channel.kind.label().lowercase()}",
        leadingIcon = if (channel.isDefault) Icons.Filled.Star else Icons.Filled.CloudDone,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(text = channel.kind.label(), tone = McTone.NEUTRAL)
            Spacer(Modifier.width(McDimens.SpacingSm))
            if (channel.isDefault) {
                StatusChip(text = "Default", tone = McTone.INFO, icon = Icons.Filled.Star)
                Spacer(Modifier.width(McDimens.SpacingSm))
            }
            StatusChip(
                text = if (channel.enabled) "Enabled" else "Disabled",
                tone = if (channel.enabled) McTone.SUCCESS else McTone.NEUTRAL,
            )
            Spacer(Modifier.width(McDimens.SpacingSm))
            StatusChip(
                text = if (permissions.canUploadVideos) "Can upload" else "Cannot upload",
                tone = if (permissions.canUploadVideos) McTone.SUCCESS else McTone.DANGER,
                icon = if (permissions.canUploadVideos) Icons.Filled.Verified else Icons.Filled.Cancel,
            )
        }

        Spacer(Modifier.height(McDimens.SpacingMd))
        DataRow(label = "Channel ID", value = channel.id.toString(), monospaced = true)
        DataRow(
            label = "Videos stored",
            value = "$videos (${Formatters.bytes(state.mappingBytes(channel.id))})",
        )
        DataRow(
            label = "Permissions checked",
            value = channel.lastVerifiedAt?.let { DateTimeFormat.relative(it.toEpochMilli()) } ?: "Never",
        )
        if (channel.localLabel != null) {
            DataRow(label = "Local label", value = channel.localLabel)
        }

        verification?.let { result ->
            Spacer(Modifier.height(McDimens.SpacingSm))
            NoticeBar(
                message = if (result.reachable) {
                    "Reachable now. ${result.notes.joinToString(" ").ifBlank { "No issues reported." }}"
                } else {
                    "Not reachable right now. ${result.notes.joinToString(" ").ifBlank { "Permissions could not be confirmed." }}"
                },
                tone = if (result.reachable) McTone.SUCCESS else McTone.WARNING,
                icon = if (result.reachable) Icons.Filled.Verified else Icons.Filled.Warning,
            )
        }

        McAnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = McDimens.SpacingMd)) {
                CopyValueRow(label = "Telegram chat ID", value = channel.id.toString())
                PermissionList(
                    title = "Rights of this account in the channel",
                    items = listOf(
                        PermissionItem("Creator", permissions.isCreator, required = false),
                        PermissionItem("Administrator", permissions.isAdministrator, required = false),
                        PermissionItem("Post messages", permissions.canPostMessages, required = true),
                        PermissionItem("Edit messages", permissions.canEditMessages, required = true),
                        PermissionItem("Delete messages", permissions.canDeleteMessages, required = true),
                        PermissionItem("Change channel info", permissions.canChangeInfo, required = false),
                    ),
                    notes = listOf(
                        "Uploads need posting rights. Edit and delete rights are used for replacement and cleanup.",
                        "Creator status implies every right.",
                    ),
                )
            }
        }

        Spacer(Modifier.height(McDimens.SpacingSm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggleExpand) {
                Text(if (expanded) "Hide details" else "Details")
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            TextButton(
                onClick = onVerifyClick,
                enabled = !verifying && state.searchAvailable && !state.busy,
            ) {
                if (verifying) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(McDimens.SpacingXs))
                } else {
                    Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(McDimens.SpacingXs))
                }
                Text(if (verifying) "Verifying" else "Verify")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Uploads",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(McDimens.SpacingSm))
            Switch(
                checked = channel.enabled,
                onCheckedChange = onToggleEnabled,
                enabled = !state.busy,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
            if (!channel.isDefault) {
                OutlinedButton(onClick = onSetDefaultClick, enabled = !state.busy && channel.enabled) {
                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(McDimens.SpacingXs))
                    Text("Make default")
                }
            }
            OutlinedButton(onClick = onRenameClick, enabled = !state.busy) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Label")
            }
            Spacer(Modifier.weight(1f))
            McIconAction(
                icon = Icons.Filled.Delete,
                contentDescription = "Remove ${channel.displayName}",
                onClick = onRemoveClick,
                enabled = !state.busy,
                tint = toneColor(McTone.DANGER),
            )
        }
    }
}

@Composable
private fun RenameDialog(
    channel: StorageChannel,
    value: String,
    errorText: String?,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Local label", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
                SectionHint(
                    "A label is stored only on this device to help you tell channels apart. " +
                        "The Telegram title stays \"${channel.title}\".",
                )
                McTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = "Label",
                    errorText = errorText,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy && value.isNotBlank()) { Text("Save label") }
        },
        dismissButton = {
            Row {
                if (channel.localLabel != null) {
                    TextButton(onClick = onReset, enabled = !busy) { Text("Use Telegram title") }
                }
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
            }
        },
    )
}

private fun ChannelKind.label(): String = when (this) {
    ChannelKind.CHANNEL -> "Channel"
    ChannelKind.BROADCAST_GROUP -> "Broadcast group"
    ChannelKind.SUPERGROUP -> "Supergroup"
}

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.READY -> "Connected"
    ConnectionState.CONNECTING -> "Connecting"
    ConnectionState.UPDATING -> "Updating"
    ConnectionState.WAITING_FOR_NETWORK -> "Waiting for network"
    ConnectionState.DISCONNECTED -> "Disconnected"
    ConnectionState.UNKNOWN -> "Not started"
}
