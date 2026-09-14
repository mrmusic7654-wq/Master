package com.mastercontrol.app.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Confirmation dialog.
 *
 * Destructive operations that touch Telegram additionally require an explicit
 * acknowledgement checkbox: nothing remote is ever deleted by a single tap.
 */
@Composable
fun McConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    acknowledgementLabel: String? = null,
    icon: ImageVector? = null,
) {
    var acknowledged by remember(title, acknowledgementLabel) { mutableStateOf(false) }
    val confirmEnabled = acknowledgementLabel == null || acknowledged

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon?.let { { Icon(it, contentDescription = null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (acknowledgementLabel != null) {
                    Spacer(Modifier.height(McDimens.SpacingMd))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                        Text(
                            text = acknowledgementLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

/** One selectable option in [McChoiceDialog]. */
data class McChoice(
    val key: String,
    val label: String,
    val description: String? = null,
    val enabled: Boolean = true,
)

/** Single-select dialog (sort order, delete mode, channel target, …). */
@Composable
fun McChoiceDialog(
    title: String,
    options: List<McChoice>,
    selectedKey: String?,
    onSelect: (McChoice) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(McDimens.SpacingMd))
                }
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(options, key = { it.key }) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .mcClickableIf(option.enabled) { onSelect(option) }
                                .padding(vertical = McDimens.SpacingSm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option.key == selectedKey,
                                onClick = null,
                                enabled = option.enabled,
                            )
                            Spacer(Modifier.width(McDimens.SpacingSm))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (option.enabled) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                if (option.description != null) {
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Text entry dialog used for titles, labels, category/folder names and tags. */
@Composable
fun McInputDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "Save",
    supportingText: String? = null,
    errorText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    placeholder: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                McTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = label,
                    supportingText = supportingText,
                    errorText = errorText,
                    keyboardType = keyboardType,
                    singleLine = singleLine,
                    placeholder = placeholder,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = errorText == null) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Horizontal button row used inside sheets and wide dialogs. */
@Composable
fun McActionRow(
    actions: List<McAction>,
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(McDimens.SpacingSm, Alignment.End),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            if (action.emphasized) {
                Button(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    colors = if (action.destructive) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    action.icon?.let {
                        Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(McDimens.SpacingSm))
                    }
                    Text(action.label)
                }
            } else {
                TextButton(onClick = action.onClick, enabled = action.enabled) {
                    action.icon?.let {
                        Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(McDimens.SpacingSm))
                    }
                    Text(action.label)
                }
            }
        }
    }
}

private fun Modifier.mcClickableIf(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.then(androidx.compose.foundation.clickable(onClick = onClick)) else this
