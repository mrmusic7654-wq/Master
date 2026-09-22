@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mastercontrol.app.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Empty state. Every screen has one: an empty catalog must read as "nothing yet,
 * here is what to do", never as a broken UI.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: List<McAction> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(McDimens.SpacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(McDimens.SpacingXl),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(McDimens.SpacingLg)
                    .size(McDimens.IconSizeEmptyState),
            )
        }
        Spacer(Modifier.height(McDimens.SpacingLg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(McDimens.SpacingSm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(McDimens.SpacingXl))
            ActionRow(actions)
        }
    }
}

/** Error state with at least one recovery action. Raw exceptions are never shown. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
    actions: List<McAction> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(McDimens.SpacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(McDimens.SpacingSm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(McDimens.SpacingLg))
            ActionRow(actions)
        }
    }
}

/** Centred loading indicator; [message] explains what is actually happening. */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            if (message != null) {
                Spacer(Modifier.height(McDimens.SpacingMd))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(actions: List<McAction>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(McDimens.SpacingSm),
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
                OutlinedButton(onClick = action.onClick, enabled = action.enabled) {
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

/**
 * Full-bleed variant used while a screen's data is still loading for the first
 * time (as opposed to a refresh, which keeps the old content visible).
 */
@Composable
fun FullScreenState(
    state: ScreenLoadState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onRetry: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        when (state) {
            ScreenLoadState.Loading -> LoadingState()
            is ScreenLoadState.Error -> ErrorState(
                message = state.message,
                actions = listOfNotNull(
                    onRetry?.let { McAction(label = "Retry", onClick = it, emphasized = true) },
                ),
                modifier = Modifier.align(Alignment.Center),
            )
            ScreenLoadState.Ready -> content()
        }
    }
}

/** Three-valued screen state: still loading, failed, or ready to render. */
sealed interface ScreenLoadState {
    data object Loading : ScreenLoadState
    data class Error(val message: String) : ScreenLoadState
    data object Ready : ScreenLoadState
}

/** Scales content down slightly when it is disabled, without relying on colour alone. */
fun Modifier.mcDisabledScale(active: Boolean): Modifier =
    graphicsLayer { alpha = if (active) 1f else 0.55f }
