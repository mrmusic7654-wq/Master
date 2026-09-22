package com.mastercontrol.app.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mastercontrol.app.core.ui.component.McChoice
import com.mastercontrol.app.core.ui.component.McChoiceDialog
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McTextField
import com.mastercontrol.app.core.ui.component.SectionHint

/** Which nested picker is open inside the metadata editor. */
private enum class EditorPicker { CATEGORY, FOLDER }

private const val NONE_KEY = "none"

/**
 * Metadata editor.
 *
 * Only local catalog fields are editable here. The permanent video ID, the file
 * name as imported, the Telegram mapping and the upload history are read-only —
 * they describe facts, not preferences.
 */
@Composable
internal fun VideoEditDialog(
    editor: VideoEditor,
    state: VideoUiState,
    onChange: ((VideoEditor) -> VideoEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picker by remember { mutableStateOf<EditorPicker?>(null) }

    AlertDialog(
        onDismissRequest = { if (!editor.saving) onDismiss() },
        title = { Text("Edit ${state.videoId}", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(McDimens.SpacingMd),
            ) {
                SectionHint(
                    "These fields describe the catalog entry. The permanent ID and the Telegram " +
                        "mapping are not affected by a metadata edit.",
                )
                McTextField(
                    value = editor.title,
                    onValueChange = { value -> onChange { it.copy(title = value, titleError = null) } },
                    label = "Title",
                    errorText = editor.titleError,
                    enabled = !editor.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                McTextField(
                    value = editor.description,
                    onValueChange = { value -> onChange { it.copy(description = value) } },
                    label = "Description",
                    singleLine = false,
                    enabled = !editor.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
                    McTextField(
                        value = editor.yearText,
                        onValueChange = { value -> onChange { it.copy(yearText = value.filter(Char::isDigit).take(4), yearError = null) } },
                        label = "Year",
                        placeholder = "2024",
                        errorText = editor.yearError,
                        keyboardType = KeyboardType.Number,
                        enabled = !editor.saving,
                        modifier = Modifier.weight(1f),
                    )
                    McTextField(
                        value = editor.ratingText,
                        onValueChange = { value -> onChange { it.copy(ratingText = value, ratingError = null) } },
                        label = "Rating (0–5)",
                        placeholder = "4.5",
                        errorText = editor.ratingError,
                        keyboardType = KeyboardType.Decimal,
                        enabled = !editor.saving,
                        modifier = Modifier.weight(1f),
                    )
                }
                McTextField(
                    value = editor.language,
                    onValueChange = { value -> onChange { it.copy(language = value) } },
                    label = "Language",
                    placeholder = "en",
                    supportingText = "A short code or name, as you prefer to read it in the library.",
                    enabled = !editor.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                McTextField(
                    value = editor.releaseDate,
                    onValueChange = { value -> onChange { it.copy(releaseDate = value) } },
                    label = "Release date",
                    placeholder = "2024-05-01",
                    supportingText = "Stored exactly as entered; ISO format sorts predictably.",
                    enabled = !editor.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                McTextField(
                    value = editor.tagsText,
                    onValueChange = { value -> onChange { it.copy(tagsText = value) } },
                    label = "Tags",
                    placeholder = "documentary, 4k, archive",
                    singleLine = false,
                    supportingText = "Separate tags with commas. Duplicates are collapsed on save.",
                    enabled = !editor.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingXs)) {
                    Text(
                        text = "Organization",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { picker = EditorPicker.CATEGORY },
                        enabled = !editor.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(McDimens.SpacingSm))
                        Text(state.categoryName(editor.categoryId) ?: "No category")
                    }
                    OutlinedButton(
                        onClick = { picker = EditorPicker.FOLDER },
                        enabled = !editor.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(McDimens.SpacingSm))
                        Text(state.folderName(editor.folderId) ?: "No folder")
                    }
                }
                Spacer(Modifier.height(McDimens.SpacingXs))
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !editor.saving && editor.title.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.saving) { Text("Cancel") }
        },
    )

    when (picker) {
        EditorPicker.CATEGORY -> McChoiceDialog(
            title = "Category",
            selectedKey = editor.categoryId?.toString(),
            options = listOf(McChoice(key = NONE_KEY, label = "No category")) +
                state.categories.map { McChoice(key = it.categoryId.toString(), label = it.name) },
            onSelect = { choice ->
                val id = if (choice.key == NONE_KEY) null else choice.key.toLongOrNull()
                onChange { it.copy(categoryId = id) }
                picker = null
            },
            onDismiss = { picker = null },
        )

        EditorPicker.FOLDER -> McChoiceDialog(
            title = "Folder",
            selectedKey = editor.folderId?.toString(),
            options = listOf(McChoice(key = NONE_KEY, label = "No folder")) +
                state.folders.map { McChoice(key = it.folderId.toString(), label = it.name) },
            onSelect = { choice ->
                val id = if (choice.key == NONE_KEY) null else choice.key.toLongOrNull()
                onChange { it.copy(folderId = id) }
                picker = null
            },
            onDismiss = { picker = null },
        )

        null -> Unit
    }
}
