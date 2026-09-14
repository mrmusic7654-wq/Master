package com.mastercontrol.app.feature.folders

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mastercontrol.app.core.ui.component.DataRow
import com.mastercontrol.app.core.ui.component.EmptyState
import com.mastercontrol.app.core.ui.component.LoadingState
import com.mastercontrol.app.core.ui.component.McAction
import com.mastercontrol.app.core.ui.component.McChoice
import com.mastercontrol.app.core.ui.component.McChoiceDialog
import com.mastercontrol.app.core.ui.component.McChipRow
import com.mastercontrol.app.core.ui.component.McConfirmDialog
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McIconAction
import com.mastercontrol.app.core.ui.component.McSelectableChip
import com.mastercontrol.app.core.ui.component.McTextField
import com.mastercontrol.app.core.ui.component.McTone
import com.mastercontrol.app.core.ui.component.SectionHint
import com.mastercontrol.app.core.ui.component.StatusChip
import com.mastercontrol.app.core.ui.component.toneColor
import com.mastercontrol.app.domain.model.Folder

/**
 * Folder management.
 *
 * Folders organize the local catalog. Deleting one never deletes a video, its
 * permanent ID, or its Telegram media; the operator explicitly chooses whether
 * the contained videos are moved to another folder or left unfiled.
 */
@Composable
fun FoldersRoute(
    modifier: Modifier = Modifier,
    viewModel: FoldersViewModel = hiltViewModel(),
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

    FoldersScreen(
        state = state,
        modifier = modifier,
        onCreateClick = viewModel::onCreateClick,
        onEditClick = viewModel::onEditClick,
        onDeleteClick = viewModel::onDeleteClick,
        onDismissEditor = viewModel::onDismissEditor,
        onEditorNameChange = viewModel::onEditorNameChange,
        onEditorDescriptionChange = viewModel::onEditorDescriptionChange,
        onParentPickerOpen = viewModel::onParentPickerOpen,
        onParentPickerDismiss = viewModel::onParentPickerDismiss,
        onParentSelected = viewModel::onParentSelected,
        onSaveEditor = viewModel::onSaveEditor,
        onDeleteModeChange = viewModel::onDeleteModeChange,
        onReassignPickerOpen = viewModel::onReassignPickerOpen,
        onReassignPickerDismiss = viewModel::onReassignPickerDismiss,
        onReassignTargetSelected = viewModel::onReassignTargetSelected,
        onDeleteReview = viewModel::onDeleteReview,
        onDismissDelete = viewModel::onDismissDelete,
        onConfirmDelete = viewModel::onConfirmDelete,
    )
}

@Composable
internal fun FoldersScreen(
    state: FoldersUiState,
    modifier: Modifier = Modifier,
    onCreateClick: () -> Unit = {},
    onEditClick: (Folder) -> Unit = {},
    onDeleteClick: (Folder) -> Unit = {},
    onDismissEditor: () -> Unit = {},
    onEditorNameChange: (String) -> Unit = {},
    onEditorDescriptionChange: (String) -> Unit = {},
    onParentPickerOpen: () -> Unit = {},
    onParentPickerDismiss: () -> Unit = {},
    onParentSelected: (Long?) -> Unit = {},
    onSaveEditor: () -> Unit = {},
    onDeleteModeChange: (FolderDeleteMode) -> Unit = {},
    onReassignPickerOpen: () -> Unit = {},
    onReassignPickerDismiss: () -> Unit = {},
    onReassignTargetSelected: (Long) -> Unit = {},
    onDeleteReview: () -> Unit = {},
    onDismissDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = McDimens.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${state.folders.size} ${if (state.folders.size == 1) "folder" else "folders"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                SectionHint("Folders can be nested to mirror how you organize media on this device.")
            }
            Button(onClick = onCreateClick, enabled = !state.busy) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("New")
            }
        }

        when {
            state.loading -> LoadingState(message = "Loading folders…")

            state.folders.isEmpty() -> EmptyState(
                icon = Icons.Filled.CreateNewFolder,
                title = "No folders yet",
                message = "Create a folder to file videos. Folders are catalog metadata stored on " +
                    "this device; they never change a video's permanent ID or its Telegram mapping.",
                actions = listOf(McAction("Create folder", onCreateClick, Icons.Filled.Add, emphasized = true)),
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = McDimens.SpacingXxl),
            ) {
                items(state.displayOrder(), key = { it.folderId }) { folder ->
                    FolderRow(
                        folder = folder,
                        state = state,
                        onEditClick = { onEditClick(folder) },
                        onDeleteClick = { onDeleteClick(folder) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    state.editor?.let { editor ->
        FolderEditorDialog(
            editor = editor,
            state = state,
            onNameChange = onEditorNameChange,
            onDescriptionChange = onEditorDescriptionChange,
            onParentPickerOpen = onParentPickerOpen,
            onSave = onSaveEditor,
            onDismiss = onDismissEditor,
        )
    }

    state.parentPickerFor?.let { editor ->
        McChoiceDialog(
            title = "Parent folder",
            supportingText = "A folder cannot contain itself, so its own descendants are not offered.",
            selectedKey = editor.parentFolderId?.toString(),
            options = listOf(McChoice(key = NONE_KEY, label = "No parent (top level)")) +
                state.parentOptions(editor.folderId).map { folder ->
                    McChoice(
                        key = folder.folderId.toString(),
                        label = folder.name,
                        description = state.parentName(folder.parentFolderId)?.let { "inside $it" },
                    )
                },
            onSelect = { choice ->
                onParentSelected(if (choice.key == NONE_KEY) null else choice.key.toLongOrNull())
            },
            onDismiss = onParentPickerDismiss,
        )
    }

    state.pendingDelete?.let { folder ->
        if (!state.confirmDelete) {
            FolderDeleteDialog(
                folder = folder,
                state = state,
                onModeChange = onDeleteModeChange,
                onReassignPickerOpen = onReassignPickerOpen,
                onReview = onDeleteReview,
                onDismiss = onDismissDelete,
            )
        } else {
            val affected = state.videoCount(folder.folderId)
            val children = state.childCount(folder.folderId)
            val targetName = if (state.deleteMode == FolderDeleteMode.MOVE) state.parentName(state.reassignTargetId) else null
            McConfirmDialog(
                title = "Delete \"${folder.name}\"?",
                message = buildString {
                    if (affected > 0) {
                        append("$affected video(s) are in this folder. ")
                        append(
                            if (targetName != null) {
                                "They are moved to \"$targetName\"."
                            } else {
                                "They are left unfiled."
                            },
                        )
                        append(" Their permanent IDs and Telegram mappings are untouched. ")
                    } else {
                        append("No videos are in this folder. ")
                    }
                    if (children > 0) append("$children sub-folder(s) become top-level folders. ")
                    append("Nothing is deleted from Telegram.")
                },
                confirmLabel = "Delete folder",
                dismissLabel = "Keep",
                destructive = true,
                acknowledgementLabel = if (affected > 0 || children > 0) {
                    "I understand the videos and Telegram media are kept"
                } else {
                    null
                },
                icon = Icons.Filled.Warning,
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDelete,
            )
        }

        if (state.reassignPickerOpen) {
            McChoiceDialog(
                title = "Move videos to",
                supportingText = "Pick the folder that should receive the ${state.videoCount(folder.folderId)} video(s).",
                selectedKey = state.reassignTargetId?.toString(),
                options = state.reassignOptions(folder.folderId).map { candidate ->
                    McChoice(
                        key = candidate.folderId.toString(),
                        label = candidate.name,
                        description = "${state.videoCount(candidate.folderId)} video(s) already inside",
                    )
                },
                onSelect = { choice -> choice.key.toLongOrNull()?.let(onReassignTargetSelected) },
                onDismiss = onReassignPickerDismiss,
            )
        }
    }
}

@Composable
private fun FolderRow(
    folder: Folder,
    state: FoldersUiState,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val depth = state.depthOf(folder.folderId)
    val videos = state.videoCount(folder.folderId)
    val children = state.childCount(folder.folderId)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (depth * INDENT_DP).dp, end = 0.dp)
            .padding(vertical = McDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (children > 0) Icons.Filled.CreateNewFolder else Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(McDimens.IconSizeLarge).padding(McDimens.SpacingSm),
        )
        Spacer(Modifier.width(McDimens.SpacingMd))
        Column(Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (folder.description.isNotBlank()) {
                Text(
                    text = folder.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.parentName(folder.parentFolderId)?.let { parent ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.SubdirectoryArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(McDimens.SpacingXs))
                    Text(
                        text = "inside $parent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(McDimens.SpacingXs))
            McChipRow {
                StatusChip(
                    text = "$videos ${if (videos == 1) "video" else "videos"}",
                    tone = if (videos > 0) McTone.INFO else McTone.NEUTRAL,
                )
                if (children > 0) {
                    StatusChip(
                        text = "$children sub-${if (children == 1) "folder" else "folders"}",
                        icon = Icons.Filled.Folder,
                    )
                }
            }
        }
        McIconAction(icon = Icons.Filled.Edit, contentDescription = "Edit ${folder.name}", onClick = onEditClick)
        McIconAction(
            icon = Icons.Filled.Delete,
            contentDescription = "Delete ${folder.name}",
            onClick = onDeleteClick,
            tint = toneColor(McTone.DANGER),
        )
    }
}

@Composable
private fun FolderEditorDialog(
    editor: FolderEditor,
    state: FoldersUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onParentPickerOpen: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val parentLabel = if (editor.parentFolderId == null) "No parent (top level)" else state.parentName(editor.parentFolderId) ?: "—"
    AlertDialog(
        onDismissRequest = { if (!editor.saving) onDismiss() },
        title = {
            Text(
                text = if (editor.isExisting) "Edit folder" else "New folder",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd),
            ) {
                McTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    label = "Name",
                    placeholder = "Concerts",
                    errorText = editor.nameError,
                    enabled = !editor.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                McTextField(
                    value = editor.description,
                    onValueChange = onDescriptionChange,
                    label = "Description (optional)",
                    singleLine = false,
                    enabled = !editor.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingXs)) {
                    Text(
                        text = "Parent folder",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onParentPickerOpen, enabled = !editor.saving, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(McDimens.SpacingSm))
                        Text(parentLabel)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !editor.saving && editor.name.isNotBlank()) {
                Text(if (editor.isExisting) "Save changes" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.saving) { Text("Cancel") }
        },
    )
}

@Composable
private fun FolderDeleteDialog(
    folder: Folder,
    state: FoldersUiState,
    onModeChange: (FolderDeleteMode) -> Unit,
    onReassignPickerOpen: () -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    val videos = state.videoCount(folder.folderId)
    val children = state.childCount(folder.folderId)
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete \"${folder.name}\"?", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd)) {
                SectionHint(
                    "The folder is removed from this device. Videos keep their permanent IDs and " +
                        "their Telegram mappings; nothing is deleted from Telegram.",
                )
                if (videos > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingXs)) {
                        Text(
                            text = "$videos video(s) are in this folder",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        McChipRow {
                            FolderDeleteMode.entries.forEach { mode ->
                                McSelectableChip(
                                    selected = state.deleteMode == mode,
                                    label = mode.label,
                                    onClick = { onModeChange(mode) },
                                )
                            }
                        }
                        Text(
                            text = state.deleteMode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.deleteMode == FolderDeleteMode.MOVE) {
                            DataRow(
                                label = "Destination",
                                value = state.parentName(state.reassignTargetId),
                                trailing = {
                                    TextButton(onClick = onReassignPickerOpen) {
                                        Text(if (state.reassignTargetId == null) "Choose" else "Change")
                                    }
                                },
                            )
                        }
                    }
                }
                if (children > 0) {
                    SectionHint("$children sub-folder(s) become top-level folders.")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onReview,
                enabled = videos == 0 || state.deleteMode == FolderDeleteMode.UNFILE || state.reassignTargetId != null,
            ) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Keep folder") } },
    )
}

private const val NONE_KEY = "none"
private const val INDENT_DP = 18
