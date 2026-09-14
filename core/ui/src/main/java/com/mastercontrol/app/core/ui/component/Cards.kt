package com.mastercontrol.app.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mastercontrol.app.core.common.format.Formatters
import com.mastercontrol.app.core.ui.theme.MonoLabelStyle

/** Titled container used to group related facts on every screen. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(McDimens.SpacingLg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(McDimens.IconSize),
                    )
                    Spacer(Modifier.width(McDimens.SpacingSm))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(McDimens.SpacingMd))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(McDimens.SpacingMd))
            content()
        }
    }
}

/**
 * Dashboard metric tile. [value] is always rendered from real data; when the
 * value is unknown callers must pass an explicit placeholder such as "—".
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null,
    tone: McTone = McTone.NEUTRAL,
    onClick: (() -> Unit)? = null,
) {
    val tint = toneColor(tone)
    val cardModifier = if (onClick != null) {
        modifier.clickableCard(onClick)
    } else {
        modifier
    }
    Card(
        modifier = cardModifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(McDimens.SpacingLg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(McDimens.SpacingSm))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(McDimens.SpacingSm))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (caption != null) {
                Spacer(Modifier.height(McDimens.SpacingXs))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Label / value row used by detail screens. Values that identify Telegram state
 * (Video ID, channel ID, message ID) are rendered monospaced so they can be read
 * and compared without ambiguity.
 */
@Composable
fun DataRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    monospaced: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = McDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value ?: "—",
            style = if (monospaced) MonoLabelStyle.copy(color = valueColor) else MaterialTheme.typography.bodyMedium,
            color = if (monospaced) Color.Unspecified else valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.62f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(McDimens.SpacingSm))
            trailing()
        }
    }
}

/** Byte size row that renders "—" when the size is genuinely unknown. */
@Composable
fun DataSizeRow(label: String, sizeBytes: Long?, modifier: Modifier = Modifier) {
    DataRow(label = label, value = Formatters.bytes(sizeBytes), modifier = modifier)
}

/** Small caption line used under section headers. */
@Composable
fun SectionHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Tonal surface used for inline notices (offline banner, permission warning). */
@Composable
fun NoticeBar(
    message: String,
    modifier: Modifier = Modifier,
    tone: McTone = McTone.INFO,
    icon: ImageVector? = null,
    action: McAction? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = toneContainerColor(tone),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier.padding(horizontal = McDimens.SpacingMd, vertical = McDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = toneColor(tone),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(McDimens.SpacingSm))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                Spacer(Modifier.width(McDimens.SpacingSm))
                androidx.compose.material3.TextButton(onClick = action.onClick, enabled = action.enabled) {
                    Text(action.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun toneColor(tone: McTone): Color = when (tone) {
    McTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    McTone.INFO -> MaterialTheme.colorScheme.primary
    McTone.SUCCESS -> com.mastercontrol.app.core.ui.theme.McColors.success
    McTone.WARNING -> com.mastercontrol.app.core.ui.theme.McColors.warning
    McTone.DANGER -> MaterialTheme.colorScheme.error
}

@Composable
fun toneContainerColor(tone: McTone): Color = when (tone) {
    McTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    McTone.INFO -> MaterialTheme.colorScheme.primaryContainer
    McTone.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
    McTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
    McTone.DANGER -> MaterialTheme.colorScheme.errorContainer
}

/** Card that behaves as a button: 48dp minimum target is enforced by the padding above. */
private fun Modifier.clickableCard(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
