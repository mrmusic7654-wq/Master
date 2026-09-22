package com.mastercontrol.app.feature.video

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mastercontrol.app.core.common.format.DateTimeFormat
import com.mastercontrol.app.core.common.format.Formatters
import com.mastercontrol.app.core.ui.component.CopyValueRow
import com.mastercontrol.app.core.ui.component.DataRow
import com.mastercontrol.app.core.ui.component.EmptyState
import com.mastercontrol.app.core.ui.component.LoadingState
import com.mastercontrol.app.core.ui.component.McAction
import com.mastercontrol.app.core.ui.component.McChoice
import com.mastercontrol.app.core.ui.component.McChoiceDialog
import com.mastercontrol.app.core.ui.component.McChipRow
import com.mastercontrol.app.core.ui.component.McConfirmDialog
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McSelectableChip
import com.mastercontrol.app.core.ui.component.McTone
import com.mastercontrol.app.core.ui.component.NoticeBar
import com.mastercontrol.app.core.ui.component.SectionCard
import com.mastercontrol.app.core.ui.component.SectionHint
import com.mastercontrol.app.core.ui.component.StatusChip
import com.mastercontrol.app.core.ui.component.TransferProgressBar
import com.mastercontrol.app.core.ui.theme.MonoLabelStyle
import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.MappingStatus
import com.mastercontrol.app.domain.model.TelegramMapping
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState
import com.mastercontrol.app.domain.model.Video
import com.mastercontrol.app.domain.model.VideoStatus
import com.mastercontrol.app.domain.usecase.DeleteVideoUseCase

private const val VIDEO_MIME_PATTERN = "video/*"
private const val IMAGE_MIME_PATTERN = "image/*"

/**
 * Video details.
 *
 * The permanent video ID is displayed first and is copyable: it is the key that
 * survives re-uploads and replacements. Everything else on this screen is either
 * local catalog metadata or a recorded Telegram mapping.
 */
@Composable
fun VideoRoute(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val replacementLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.onReplacementPicked(uri.toString()) }

    val posterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.onPosterPicked(uri.toString()) }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val message = state.errorMessage ?: state.infoMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onDismissMessage()
        }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onNavigateUp()
    }

    VideoScreen(
        state = state,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onEditClick = viewModel::onEditClick,
        onDismissEditor = viewModel::onDismissEditor,
        onEditorChange = viewModel::onEditorChange,
        onSaveEditor = viewModel::onSaveEditor,
        onQueueUploadClick = viewModel::onQueueUploadClick,
        onDismissUploadChannelPicker = viewModel::onDismissUploadChannelPicker,
        onUploadChannelSelected = viewModel::onUploadChannelSelected,
        onReplaceClick = { replacementLauncher.launch(arrayOf(VIDEO_MIME_PATTERN)) },
        onDismissReplacement = viewModel::onDismissReplacement,
        onReplacementChannelSelected = viewModel::onReplacementChannelSelected,
        onPosterClick = { posterLauncher.launch(arrayOf(IMAGE_MIME_PATTERN)) },
        onVerifyMappingClick = viewModel::onVerifyMappingClick,
        onDeleteClick = viewModel::onDeleteClick,
        onDismissDelete = viewModel::onDismissDelete,
        onDeleteModeChange = viewModel::onDeleteModeChange,
        onDeleteReview = viewModel::onDeleteReview,
        onConfirmDelete = viewModel::onConfirmDelete,
    )
}

@Composable
internal fun VideoScreen(
    state: VideoUiState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onEditClick: () -> Unit = {},
    onDismissEditor: () -> Unit = {},
    onEditorChange: ((VideoEditor) -> VideoEditor) -> Unit = {},
    onSaveEditor: () -> Unit = {},
    onQueueUploadClick: () -> Unit = {},
    onDismissUploadChannelPicker: () -> Unit = {},
    onUploadChannelSelected: (Long) -> Unit = {},
    onReplaceClick: () -> Unit = {},
    onDismissReplacement: () -> Unit = {},
    onReplacementChannelSelected: (Long) -> Unit = {},
    onPosterClick: () -> Unit = {},
    onVerifyMappingClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onDismissDelete: () -> Unit = {},
    onDeleteModeChange: (DeleteVideoUseCase.Mode) -> Unit = {},
    onDeleteReview: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
) {
    if (state.loading) {
        LoadingState(message = "Loading ${state.videoId}…", modifier = modifier.fillMaxSize())
        return
    }

    val video = state.video
    if (video == null || state.notFound) {
        EmptyState(
            icon = Icons.Filled.VideoLibrary,
            title = "Video not found",
            message = "No catalog entry with the ID ${state.videoId} exists on this device. " +
                "It may have been deleted, or the catalog backup that contained it was not imported.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(McDimens.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd),
    ) {
        item(key = "header") { HeaderSection(video = video, state = state) }

        item(key = "actions") {
            ActionRow(
                state = state,
                onEditClick = onEditClick,
                onQueueUploadClick = onQueueUploadClick,
                onReplaceClick = onReplaceClick,
                onPosterClick = onPosterClick,
                onVerifyMappingClick = onVerifyMappingClick,
                onDeleteClick = onDeleteClick,
            )
        }

        item(key = "mapping") {
            MappingSection(
                video = video,
                mapping = state.mapping,
                state = state,
                onQueueUploadClick = onQueueUploadClick,
                onVerifyMappingClick = onVerifyMappingClick,
            )
        }

        if (state.tasks.isNotEmpty()) {
            item(key = "tasks") { UploadSection(tasks = state.tasks, state = state) }
        }

        item(key = "details") { DetailsSection(video = video) }

        if (state.history.isNotEmpty()) {
            item(key = "history") { HistorySection(history = state.history) }
        }
    }

    state.editor?.let { editor ->
        VideoEditDialog(
            editor = editor,
            state = state,
            onChange = onEditorChange,
            onSave = onSaveEditor,
            onDismiss = onDismissEditor,
        )
    }

    if (state.uploadChannelPickerOpen) {
        McChoiceDialog(
            title = "Upload to which channel?",
            supportingText = "Only channels that are enabled and grant posting rights are listed.",
            selectedKey = state.channels.firstOrNull { it.isDefault }?.id?.toString(),
            options = state.channels.filter { it.enabled && it.permissions.canUploadVideos }.map { channel ->
                McChoice(
                    key = channel.id.toString(),
                    label = channel.displayName,
                    description = buildString {
                        if (channel.isDefault) append("Default · ")
                        append("${state.history.count { it.channelId == channel.id }} recorded event(s)")
                    },
                )
            },
            onSelect = { choice -> choice.key.toLongOrNull()?.let(onUploadChannelSelected) },
            onDismiss = onDismissUploadChannelPicker,
        )
    }

    state.pendingReplacementUri?.let {
        McChoiceDialog(
            title = "Upload the replacement where?",
            supportingText = "${video.videoId} has no Telegram mapping yet, so pick the storage channel " +
                "for the new upload. The permanent ID does not change.",
            selectedKey = state.channels.firstOrNull { it.isDefault }?.id?.toString(),
            options = state.channels.filter { it.enabled && it.permissions.canUploadVideos }.map { channel ->
                McChoice(key = channel.id.toString(), label = channel.displayName)
            },
            onSelect = { choice -> choice.key.toLongOrNull()?.let(onReplacementChannelSelected) },
            onDismiss = onDismissReplacement,
        )
    }

    if (state.deleteDialogOpen && !state.confirmDelete) {
        DeleteModeDialog(
            video = video,
            state = state,
            onModeChange = onDeleteModeChange,
            onReview = onDeleteReview,
            onDismiss = onDismissDelete,
        )
    }

    if (state.deleteDialogOpen && state.confirmDelete) {
        val telegram = state.deleteMode == DeleteVideoUseCase.Mode.LOCAL_AND_TELEGRAM
        val mapping = state.mapping
        val channelLabel = if (mapping != null) {
            state.channelName(mapping.channelId) ?: "channel ${mapping.channelId}"
        } else {
            null
        }
        McConfirmDialog(
            title = if (telegram) "Delete ${video.videoId} everywhere?" else "Delete ${video.videoId} from this device?",
            message = buildString {
                append("The catalog row, its metadata and its local poster are removed. ")
                if (telegram && mapping != null && channelLabel != null) {
                    append("The Telegram message ${mapping.messageId} in \"$channelLabel\" is deleted first; ")
                    append("if Telegram refuses, nothing is removed locally. ")
                } else if (telegram) {
                    append("No Telegram mapping is recorded, so nothing is deleted on Telegram. ")
                } else {
                    append("Telegram media is left untouched. ")
                }
                append("The ID ${video.videoId} is retired and is never reused.")
            },
            confirmLabel = if (telegram) "Delete everywhere" else "Delete locally",
            dismissLabel = "Keep",
            destructive = true,
            acknowledgementLabel = if (telegram) {
                "I understand the Telegram message is deleted permanently and ${video.videoId} is retired"
            } else {
                "I understand ${video.videoId} is retired and never reused"
            },
            icon = Icons.Filled.Warning,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }
}

@Composable
private fun HeaderSection(video: Video, state: VideoUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd)) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(McDimens.ThumbnailAspectRatio)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(McDimens.IconSizeEmptyState),
            )
            val poster = video.posterUri ?: video.thumbnailUri
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = "Poster for ${video.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (video.status == VideoStatus.IMPORTING) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(McDimens.SpacingSm))
                        Text(
                            text = "Preparing poster and checksum",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingXs)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            CopyValueRow(label = "Permanent video ID", value = video.videoId)
            McChipRow {
                StatusChip(text = video.status.label(), tone = video.status.tone(), icon = video.status.icon())
                state.categoryName(video.categoryId)?.let { StatusChip(text = it, icon = Icons.Filled.Star) }
                state.folderName(video.folderId)?.let { StatusChip(text = it) }
                if (video.tags.isNotEmpty()) {
                    StatusChip(text = "${video.tags.size} tag(s)")
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    state: VideoUiState,
    onEditClick: () -> Unit,
    onQueueUploadClick: () -> Unit,
    onReplaceClick: () -> Unit,
    onPosterClick: () -> Unit,
    onVerifyMappingClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
            OutlinedButton(onClick = onEditClick, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Edit")
            }
            OutlinedButton(onClick = onPosterClick, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Poster")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
            OutlinedButton(
                onClick = onQueueUploadClick,
                enabled = !state.busy && state.canQueueUpload,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Upload")
            }
            OutlinedButton(
                onClick = onReplaceClick,
                enabled = !state.busy && state.canReplaceMedia,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Replace")
            }
            OutlinedButton(
                onClick = onVerifyMappingClick,
                enabled = !state.busy && !state.verifying && state.hasTelegramMedia,
                modifier = Modifier.weight(1f),
            ) {
                if (state.verifying) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Verify")
            }
        }
        OutlinedButton(
            onClick = onDeleteClick,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(McDimens.SpacingXs))
            Text("Delete video")
        }
        if (!state.canQueueUpload && state.activeTask == null) {
            SectionHint(
                "Uploading needs an enabled storage channel that grants posting rights. " +
                    "Configure one under Channels.",
            )
        }
    }
}

@Composable
private fun MappingSection(
    video: Video,
    mapping: TelegramMapping?,
    state: VideoUiState,
    onQueueUploadClick: () -> Unit,
    onVerifyMappingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "Telegram mapping",
        subtitle = if (mapping == null) "Not uploaded yet" else "${mapping.mappingStatus.label()} mapping",
        leadingIcon = Icons.Filled.CloudDone,
        modifier = modifier,
    ) {
        if (mapping == null) {
            SectionHint(
                "${video.videoId} exists only in the local catalog. Once it is uploaded, the " +
                    "channel ID and message ID are recorded here and the permanent ID stays the same.",
            )
            Spacer(Modifier.height(McDimens.SpacingSm))
            OutlinedButton(onClick = onQueueUploadClick, enabled = state.canQueueUpload && !state.busy) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("Queue upload")
            }
            return@SectionCard
        }

        StatusChip(
            text = mapping.mappingStatus.label(),
            tone = mapping.mappingStatus.tone(),
            icon = mapping.mappingStatus.icon(),
        )
        Spacer(Modifier.height(McDimens.SpacingSm))
        DataRow(label = "Channel", value = state.channelName(mapping.channelId) ?: "Unknown channel")
        CopyValueRow(label = "Channel ID", value = mapping.channelId.toString())
        CopyValueRow(label = "Message ID", value = mapping.messageId.toString())
        mapping.telegramUniqueFileId?.let { CopyValueRow(label = "Unique file ID", value = it) }
        mapping.telegramRemoteFileId?.let { CopyValueRow(label = "Remote file ID", value = it) }
        mapping.telegramLocalFilePath?.let { DataRow(label = "Cached at", value = it) }
        DataRow(label = "Uploaded", value = DateTimeFormat.instant(mapping.uploadedAt))
        DataRow(label = "Recorded size", value = Formatters.bytes(mapping.fileSizeBytes))
        DataRow(label = "Format", value = Formatters.formatFromMime(mapping.mimeType))

        state.verification?.let { result ->
            Spacer(Modifier.height(McDimens.SpacingSm))
            NoticeBar(
                message = when {
                    !result.found -> "Telegram no longer has message ${mapping.messageId}."
                    !result.matchesVideo -> "The media behind message ${mapping.messageId} changed."
                    else -> "Telegram confirms message ${mapping.messageId} still holds this video."
                } + result.notes.joinToString(prefix = " ", separator = " ").takeIf { it.isNotBlank() }.orEmpty(),
                tone = if (result.found && result.matchesVideo) McTone.SUCCESS else McTone.DANGER,
                icon = if (result.found && result.matchesVideo) Icons.Filled.Verified else Icons.Filled.Error,
                action = McAction("Verify again", onVerifyMappingClick),
            )
        }

        if (mapping.mappingStatus != MappingStatus.ACTIVE) {
            Spacer(Modifier.height(McDimens.SpacingSm))
            NoticeBar(
                message = mapping.mappingStatus.explanation(),
                tone = McTone.WARNING,
                icon = Icons.Filled.Warning,
                action = McAction("Verify now", onVerifyMappingClick),
            )
        }
    }
}

@Composable
private fun UploadSection(tasks: List<UploadTask>, state: VideoUiState, modifier: Modifier = Modifier) {
    SectionCard(
        title = "Upload tasks",
        subtitle = "${tasks.size} task(s) recorded for this video",
        leadingIcon = Icons.Filled.CloudUpload,
        modifier = modifier,
    ) {
        tasks.forEachIndexed { index, task ->
            Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingXs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Task ${task.taskId}",
                        style = MonoLabelStyle,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(text = task.state.label(), tone = task.state.tone(), icon = task.state.icon())
                }
                val total = task.totalBytes ?: 0L
                if (task.state == UploadTaskState.UPLOADING || task.state == UploadTaskState.VERIFYING) {
                    TransferProgressBar(
                        progress = if (total > 0L) task.progress else null,
                        detail = if (total > 0L) {
                            "${Formatters.percent(task.progress)} · ${Formatters.bytes(task.bytesUploaded)} of ${Formatters.bytes(total)}"
                        } else {
                            "${Formatters.bytes(task.bytesUploaded)} sent"
                        },
                        tone = McTone.INFO,
                    )
                } else if (task.state == UploadTaskState.COMPLETED) {
                    TransferProgressBar(progress = 1f, detail = "Committed to the catalog", tone = McTone.SUCCESS)
                }
                DataRow(label = "Channel", value = state.channelName(task.channelId) ?: "—")
                DataRow(label = "Attempts", value = task.attemptCount.toString())
                DataRow(label = "Updated", value = DateTimeFormat.relative(task.updatedAt.toEpochMilli()))
                if (task.lastError != null) {
                    NoticeBar(message = task.lastError, tone = McTone.DANGER, icon = Icons.Filled.Error)
                }
            }
            if (index != tasks.lastIndex) {
                Spacer(Modifier.height(McDimens.SpacingSm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(McDimens.SpacingSm))
            }
        }
    }
}

@Composable
private fun DetailsSection(video: Video, modifier: Modifier = Modifier) {
    SectionCard(
        title = "Catalog details",
        subtitle = "Stored on this device",
        leadingIcon = Icons.Filled.Info,
        modifier = modifier,
    ) {
        DataRow(label = "File name", value = video.originalFileName)
        DataRow(label = "Size", value = Formatters.bytes(video.fileSizeBytes))
        DataRow(label = "Duration", value = Formatters.duration(video.durationMs))
        DataRow(label = "Resolution", value = Formatters.resolutionLabel(video.width, video.height))
        DataRow(label = "Frame rate", value = Formatters.frameRate(video.frameRate))
        DataRow(label = "Format", value = Formatters.formatFromMime(video.mimeType))
        DataRow(label = "Year", value = video.year?.toString())
        DataRow(label = "Language", value = video.language)
        DataRow(label = "Rating", value = video.rating?.let { "${it} / 5" })
        DataRow(label = "Release date", value = video.releaseDate)
        if (video.description.isNotBlank()) {
            Spacer(Modifier.height(McDimens.SpacingXs))
            Text(
                text = video.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (video.tags.isNotEmpty()) {
            Spacer(Modifier.height(McDimens.SpacingSm))
            McChipRow {
                video.tags.forEach { tag -> StatusChip(text = tag) }
            }
        }
        Spacer(Modifier.height(McDimens.SpacingSm))
        CopyValueRow(label = "Source URI", value = video.sourceUri)
        if (video.sha256 != null) {
            CopyValueRow(label = "SHA-256", value = video.sha256)
        } else if (video.hashPending) {
            NoticeBar(
                message = "The checksum is still being computed in the background.",
                tone = McTone.INFO,
                icon = Icons.Filled.Schedule,
            )
        } else {
            DataRow(label = "SHA-256", value = "Not computed")
        }
        DataRow(label = "Added", value = DateTimeFormat.instant(video.createdAt))
        DataRow(label = "Updated", value = DateTimeFormat.instant(video.updatedAt))
    }
}

@Composable
private fun HistorySection(history: List<ActivityLogEntry>, modifier: Modifier = Modifier) {
    SectionCard(
        title = "History",
        subtitle = "${history.size} recorded event(s) for this video",
        leadingIcon = Icons.Filled.History,
        modifier = modifier,
    ) {
        history.forEachIndexed { index, entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = McDimens.SpacingXs)) {
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
                }
                Spacer(Modifier.width(McDimens.SpacingSm))
                Text(
                    text = DateTimeFormat.relative(entry.createdAt.toEpochMilli()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index != history.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun DeleteModeDialog(
    video: Video,
    state: VideoUiState,
    onModeChange: (DeleteVideoUseCase.Mode) -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasTelegram = state.hasTelegramMedia
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete ${video.videoId}?", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd)) {
                SectionHint(
                    "The permanent ID ${video.videoId} is retired and is never reused, so the " +
                        "catalog stays unambiguous even after deletion.",
                )
                McChipRow {
                    McSelectableChip(
                        selected = state.deleteMode == DeleteVideoUseCase.Mode.LOCAL_ONLY,
                        label = "This device only",
                        onClick = { onModeChange(DeleteVideoUseCase.Mode.LOCAL_ONLY) },
                    )
                    McSelectableChip(
                        selected = state.deleteMode == DeleteVideoUseCase.Mode.LOCAL_AND_TELEGRAM,
                        label = "Device and Telegram",
                        enabled = hasTelegram,
                        onClick = { onModeChange(DeleteVideoUseCase.Mode.LOCAL_AND_TELEGRAM) },
                    )
                }
                SectionHint(
                    if (state.deleteMode == DeleteVideoUseCase.Mode.LOCAL_AND_TELEGRAM) {
                        "The Telegram message is deleted first. If Telegram refuses, nothing is " +
                            "removed locally so the record is never lost while the media still exists."
                    } else if (hasTelegram) {
                        "The Telegram message stays where it is; only the local catalog row is removed."
                    } else {
                        "No Telegram media is recorded for this video, so only the local catalog row is removed."
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onReview, enabled = !state.busy) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Keep video") } },
    )
}

internal fun VideoStatus.label(): String = when (this) {
    VideoStatus.IMPORTING -> "Importing"
    VideoStatus.READY -> "Ready"
    VideoStatus.UPLOADING -> "Uploading"
    VideoStatus.COMPLETE -> "In Telegram"
    VideoStatus.FAILED -> "Failed"
}

internal fun VideoStatus.tone(): McTone = when (this) {
    VideoStatus.IMPORTING -> McTone.INFO
    VideoStatus.READY -> McTone.NEUTRAL
    VideoStatus.UPLOADING -> McTone.INFO
    VideoStatus.COMPLETE -> McTone.SUCCESS
    VideoStatus.FAILED -> McTone.DANGER
}

internal fun VideoStatus.icon() = when (this) {
    VideoStatus.IMPORTING -> Icons.Filled.Schedule
    VideoStatus.READY -> Icons.Filled.VideoLibrary
    VideoStatus.UPLOADING -> Icons.Filled.CloudUpload
    VideoStatus.COMPLETE -> Icons.Filled.CloudDone
    VideoStatus.FAILED -> Icons.Filled.Error
}

internal fun MappingStatus.label(): String = when (this) {
    MappingStatus.NONE -> "No mapping"
    MappingStatus.PENDING_VERIFY -> "Pending verification"
    MappingStatus.ACTIVE -> "Active"
    MappingStatus.STALE -> "Stale"
    MappingStatus.REPLACED -> "Replaced"
    MappingStatus.REMOTE_DELETED -> "Deleted on Telegram"
}

internal fun MappingStatus.tone(): McTone = when (this) {
    MappingStatus.NONE -> McTone.NEUTRAL
    MappingStatus.PENDING_VERIFY -> McTone.WARNING
    MappingStatus.ACTIVE -> McTone.SUCCESS
    MappingStatus.STALE -> McTone.WARNING
    MappingStatus.REPLACED -> McTone.INFO
    MappingStatus.REMOTE_DELETED -> McTone.DANGER
}

internal fun MappingStatus.icon() = when (this) {
    MappingStatus.NONE -> Icons.Filled.Info
    MappingStatus.PENDING_VERIFY -> Icons.Filled.Schedule
    MappingStatus.ACTIVE -> Icons.Filled.Verified
    MappingStatus.STALE -> Icons.Filled.Warning
    MappingStatus.REPLACED -> Icons.Filled.Refresh
    MappingStatus.REMOTE_DELETED -> Icons.Filled.Error
}

internal fun MappingStatus.explanation(): String = when (this) {
    MappingStatus.NONE -> "No Telegram media is recorded for this video."
    MappingStatus.PENDING_VERIFY -> "The upload was committed but Telegram has not confirmed the message yet."
    MappingStatus.ACTIVE -> "Telegram confirms this mapping."
    MappingStatus.STALE -> "The media behind the recorded message changed. Verify to see what Telegram reports."
    MappingStatus.REPLACED -> "This mapping was superseded by a newer upload of the same permanent ID."
    MappingStatus.REMOTE_DELETED -> "The recorded Telegram message no longer exists. Re-upload to restore the mapping."
}

internal fun UploadTaskState.label(): String = when (this) {
    UploadTaskState.QUEUED -> "Queued"
    UploadTaskState.PREPARING -> "Preparing"
    UploadTaskState.UPLOADING -> "Uploading"
    UploadTaskState.VERIFYING -> "Verifying"
    UploadTaskState.RETRYING -> "Retrying"
    UploadTaskState.COMPLETED -> "Uploaded"
    UploadTaskState.FAILED -> "Failed"
    UploadTaskState.CANCELLED -> "Cancelled"
}

internal fun UploadTaskState.tone(): McTone = when (this) {
    UploadTaskState.COMPLETED -> McTone.SUCCESS
    UploadTaskState.FAILED -> McTone.DANGER
    UploadTaskState.RETRYING -> McTone.WARNING
    UploadTaskState.CANCELLED, UploadTaskState.QUEUED -> McTone.NEUTRAL
    UploadTaskState.PREPARING, UploadTaskState.UPLOADING, UploadTaskState.VERIFYING -> McTone.INFO
}

internal fun UploadTaskState.icon() = when (this) {
    UploadTaskState.COMPLETED -> Icons.Filled.Verified
    UploadTaskState.FAILED -> Icons.Filled.Error
    UploadTaskState.RETRYING -> Icons.Filled.Refresh
    UploadTaskState.CANCELLED -> Icons.Filled.Delete
    UploadTaskState.QUEUED -> Icons.Filled.Schedule
    UploadTaskState.PREPARING, UploadTaskState.UPLOADING, UploadTaskState.VERIFYING -> Icons.Filled.CloudUpload
}
