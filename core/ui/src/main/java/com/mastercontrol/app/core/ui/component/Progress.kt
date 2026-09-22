package com.mastercontrol.app.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mastercontrol.app.core.common.format.Formatters

/**
 * Transfer progress row.
 *
 * [progress] is `null` while the transferred byte count is unknown — the bar then
 * renders indeterminate instead of pretending a percentage. Rate and ETA strings
 * come from [com.mastercontrol.app.core.common.transfer.TransferRateEstimator],
 * which derives them from observed counters only.
 */
@Composable
fun TransferProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    label: String? = null,
    rateText: String? = null,
    etaText: String? = null,
    detail: String? = null,
    tone: McTone = McTone.INFO,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null || rateText != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.width(McDimens.SpacingSm))
                if (rateText != null) {
                    Text(
                        text = rateText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(McDimens.SpacingXs))
        }
        if (progress == null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = toneColor(tone),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        } else {
            val clamped = progress.coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { clamped },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = toneColor(tone),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.width(McDimens.SpacingSm))
                Text(
                    text = Formatters.percent(clamped),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (detail != null || etaText != null) {
            Spacer(Modifier.height(McDimens.SpacingXs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = detail ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (etaText != null) {
                    Text(
                        text = etaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
