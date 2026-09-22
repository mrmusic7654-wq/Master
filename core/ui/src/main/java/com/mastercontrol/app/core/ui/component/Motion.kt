package com.mastercontrol.app.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mastercontrol.app.core.ui.theme.LocalReducedMotion

/**
 * True when the operator asked the system to remove animations
 * (Settings → Accessibility → Remove animations), read once by the Activity and
 * provided through [LocalReducedMotion].
 */
@Composable
fun rememberReducedMotion(): Boolean = LocalReducedMotion.current

private const val MOTION_MS = 180

/** Visibility transition that collapses to an instant switch under reduced motion. */
@Composable
fun McAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (rememberReducedMotion()) {
        if (visible) content()
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(MOTION_MS)),
        exit = fadeOut(tween(MOTION_MS)),
        content = { content() },
    )
}

/** Cross-fade between screen states (loading → content → error). */
@Composable
fun <T> McCrossfade(
    target: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    if (rememberReducedMotion()) {
        content(target)
        return
    }
    Crossfade(targetState = target, modifier = modifier, animationSpec = tween(MOTION_MS), label = "mcCrossfade") {
        content(it)
    }
}
