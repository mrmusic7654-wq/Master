package com.mastercontrol.app.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mastercontrol.app.core.ui.theme.MonoLabelStyle
import kotlinx.coroutines.delay

/**
 * Identifier row with copy-to-clipboard (Video ID, channel ID, message ID).
 *
 * The value is selectable as well as copyable, and confirmation is inline text
 * rather than a toast so it is announced by TalkBack.
 */
@Composable
fun CopyValueRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    copyDescription: String = "Copy $label",
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(label, value) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(COPY_FEEDBACK_MS)
            copied = false
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = McDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.34f),
        )
        SelectionContainer(Modifier.weight(0.5f)) {
            Text(
                text = value ?: "—",
                style = MonoLabelStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(McDimens.SpacingXs))
        if (value != null) {
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(value))
                    copied = true
                },
                modifier = Modifier.size(McDimens.MinTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = copyDescription,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(McDimens.SpacingXs))
            Text(
                text = if (copied) "Copied" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val COPY_FEEDBACK_MS = 1_600L
