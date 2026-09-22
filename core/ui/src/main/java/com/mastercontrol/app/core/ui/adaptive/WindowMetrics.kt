package com.mastercontrol.app.core.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Minimal window-size classification.
 *
 * Deliberately not the material3-window-size-class artifact: Master Control
 * needs exactly three breakpoints and deriving them from the available width
 * keeps the dependency surface small.
 */
enum class McWindowClass {
    /** Phones, portrait. */
    COMPACT,

    /** Large phones landscape / small tablets / foldables unfolded. */
    MEDIUM,

    /** Tablets, desktop-mode windows. */
    EXPANDED,
    ;

    val isCompact: Boolean get() = this == COMPACT
    val atLeastMedium: Boolean get() = this != COMPACT
}

/** Classifies the current window from its available width. */
fun windowClassFor(width: Dp): McWindowClass = when {
    width < 600.dp -> McWindowClass.COMPACT
    width < 840.dp -> McWindowClass.MEDIUM
    else -> McWindowClass.EXPANDED
}

/** Classifies the enclosing constraints inside a [BoxWithConstraintsScope]. */
@Composable
fun BoxWithConstraintsScope.rememberWindowClass(): McWindowClass = windowClassFor(maxWidth)

/**
 * Column count for the library grid: driven by the window class and the
 * operator's density preference, never by hard-coded device assumptions.
 */
fun gridColumnsFor(windowClass: McWindowClass, minCellWidth: Dp = 160.dp, availableWidth: Dp): Int {
    val computed = (availableWidth / minCellWidth).toInt().coerceAtLeast(1)
    val ceiling = when (windowClass) {
        McWindowClass.COMPACT -> 4
        McWindowClass.MEDIUM -> 6
        McWindowClass.EXPANDED -> 8
    }
    return computed.coerceAtMost(ceiling)
}
