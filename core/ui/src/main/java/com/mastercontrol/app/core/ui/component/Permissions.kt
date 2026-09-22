package com.mastercontrol.app.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A single Telegram right the account holds (or does not hold) in a channel. */
data class PermissionItem(
    val label: String,
    val granted: Boolean,
    /** Master Control needs this right for the operations it performs. */
    val required: Boolean = true,
)

/**
 * Permission line.
 *
 * The grant state is expressed by a word *and* an icon, never by colour alone,
 * so the screen stays readable for colour-blind operators and in TalkBack.
 */
@Composable
fun PermissionRow(
    item: PermissionItem,
    modifier: Modifier = Modifier,
) {
    val (icon, tone, stateLabel) = when {
        item.granted -> Triple(Icons.Filled.CheckCircle, McTone.SUCCESS, "Granted")
        item.required -> Triple(Icons.Filled.Cancel, McTone.DANGER, "Missing")
        else -> Triple(Icons.Filled.RemoveCircleOutline, McTone.NEUTRAL, "Not required")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = McDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = toneColor(tone),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(McDimens.SpacingSm))
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stateLabel,
            style = MaterialTheme.typography.labelSmall,
            color = toneColor(tone),
        )
    }
}

/**
 * Permission block for the channel screens, including the explicit explanation
 * of what is missing when the channel is not usable for uploads.
 */
@Composable
fun PermissionList(
    items: List<PermissionItem>,
    modifier: Modifier = Modifier,
    notes: List<String> = emptyList(),
    title: String = "Permissions",
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(McDimens.SpacingXs))
        Column(verticalArrangement = Arrangement.spacedBy(McDimens.SpacingXxs)) {
            items.forEach { PermissionRow(it) }
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(McDimens.SpacingSm))
            notes.forEach { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = McDimens.SpacingXxs),
                )
            }
        }
    }
}
