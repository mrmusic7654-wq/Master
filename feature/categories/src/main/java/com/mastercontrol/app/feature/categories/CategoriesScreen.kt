package com.mastercontrol.app.feature.categories

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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mastercontrol.app.core.ui.component.EmptyState
import com.mastercontrol.app.core.ui.component.LoadingState
import com.mastercontrol.app.core.ui.component.McAction
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
import com.mastercontrol.app.domain.model.Category

/**
 * Category management.
 *
 * Categories are local catalog metadata only: deleting one never touches a
 * video's permanent ID, its Telegram mapping or the media itself.
 */
@Composable
fun CategoriesRoute(
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = hiltViewModel(),
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

    CategoriesScreen(
        state = state,
        modifier = modifier,
        onCreateClick = viewModel::onCreateClick,
        onEditClick = viewModel::onEditClick,
        onDeleteClick = viewModel::onDeleteClick,
        onDismissEditor = viewModel::onDismissEditor,
        onEditorNameChange = viewModel::onEditorNameChange,
        onEditorDescriptionChange = viewModel::onEditorDescriptionChange,
        onEditorIconChange = viewModel::onEditorIconChange,
        onSaveEditor = viewModel::onSaveEditor,
        onDismissDelete = viewModel::onDismissDelete,
        onConfirmDelete = viewModel::onConfirmDelete,
    )
}

@Composable
internal fun CategoriesScreen(
    state: CategoriesUiState,
    modifier: Modifier = Modifier,
    onCreateClick: () -> Unit = {},
    onEditClick: (Category) -> Unit = {},
    onDeleteClick: (Category) -> Unit = {},
    onDismissEditor: () -> Unit = {},
    onEditorNameChange: (String) -> Unit = {},
    onEditorDescriptionChange: (String) -> Unit = {},
    onEditorIconChange: (CategoryIcon) -> Unit = {},
    onSaveEditor: () -> Unit = {},
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
                    text = "${state.categories.size} ${if (state.categories.size == 1) "category" else "categories"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                SectionHint("Used to filter and organize your library.")
            }
            Button(onClick = onCreateClick, enabled = !state.busy) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(McDimens.SpacingXs))
                Text("New")
            }
        }

        when {
            state.loading -> LoadingState(message = "Loading categories…")

            state.categories.isEmpty() -> EmptyState(
                icon = Icons.Filled.Category,
                title = "No categories yet",
                message = "Create a category to group videos. Categories are stored on this device " +
                    "and are included in catalog exports.",
                actions = listOf(McAction("Create category", onCreateClick, Icons.Filled.Add, emphasized = true)),
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = McDimens.SpacingXxl),
            ) {
                items(state.categories, key = { it.categoryId }) { category ->
                    CategoryRow(
                        category = category,
                        videoCount = state.videoCounts[category.categoryId] ?: 0,
                        onEditClick = { onEditClick(category) },
                        onDeleteClick = { onDeleteClick(category) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    state.editor?.let { editor ->
        CategoryEditorDialog(
            editor = editor,
            onNameChange = onEditorNameChange,
            onDescriptionChange = onEditorDescriptionChange,
            onIconChange = onEditorIconChange,
            onSave = onSaveEditor,
            onDismiss = onDismissEditor,
        )
    }

    state.pendingDelete?.let { category ->
        val affected = state.videoCounts[category.categoryId] ?: 0
        McConfirmDialog(
            title = "Delete \"${category.name}\"?",
            message = buildString {
                append("The category is removed from this device. ")
                if (affected > 0) {
                    append("$affected video(s) keep their permanent IDs and Telegram mappings, ")
                    append("and become uncategorized.")
                } else {
                    append("No videos are assigned to it.")
                }
                append(" Nothing is deleted from Telegram.")
            },
            confirmLabel = "Delete category",
            dismissLabel = "Keep",
            destructive = true,
            acknowledgementLabel = if (affected > 0) "I understand $affected video(s) will be uncategorized" else null,
            icon = Icons.Filled.Warning,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    videoCount: Int,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = CategoryIcon.fromKey(category.icon)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = McDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon.vector(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(McDimens.IconSizeLarge).padding(McDimens.SpacingSm),
        )
        Spacer(Modifier.width(McDimens.SpacingMd))
        Column(Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (category.description.isNotBlank()) {
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(McDimens.SpacingXs))
            StatusChip(
                text = "$videoCount ${if (videoCount == 1) "video" else "videos"}",
                tone = if (videoCount > 0) McTone.INFO else McTone.NEUTRAL,
            )
        }
        McIconAction(icon = Icons.Filled.Edit, contentDescription = "Edit ${category.name}", onClick = onEditClick)
        McIconAction(
            icon = Icons.Filled.Delete,
            contentDescription = "Delete ${category.name}",
            onClick = onDeleteClick,
            tint = toneColor(McTone.DANGER),
        )
    }
}

@Composable
private fun CategoryEditorDialog(
    editor: CategoryEditor,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onIconChange: (CategoryIcon) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!editor.saving) onDismiss() },
        title = {
            Text(
                text = if (editor.isExisting) "Edit category" else "New category",
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
                    placeholder = "Documentaries",
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
                Column {
                    Text(
                        text = "Icon",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(McDimens.SpacingXs))
                    McChipRow {
                        CategoryIcon.entries.forEach { option ->
                            McSelectableChip(
                                selected = editor.icon == option,
                                label = option.label,
                                leadingIcon = option.vector(),
                                onClick = { onIconChange(option) },
                            )
                        }
                    }
                }
                SectionHint(
                    "The icon is stored as a stable key, so an exported catalog stays readable " +
                        "on another device or a future app version.",
                )
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
