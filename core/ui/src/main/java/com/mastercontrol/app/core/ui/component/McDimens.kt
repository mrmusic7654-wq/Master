package com.mastercontrol.app.core.ui.component

import androidx.compose.ui.unit.dp

/** Shared spacing/sizing tokens so every screen has the same rhythm. */
object McDimens {
    val SpacingXxs = 2.dp
    val SpacingXs = 4.dp
    val SpacingSm = 8.dp
    val SpacingMd = 12.dp
    val SpacingLg = 16.dp
    val SpacingXl = 24.dp
    val SpacingXxl = 32.dp

    val CardCornerRadius = 12.dp
    val ThumbnailCornerRadius = 10.dp
    val IconSize = 20.dp
    val IconSizeLarge = 44.dp
    val IconSizeEmptyState = 56.dp

    /** Minimum accessible touch target (WCAG 2.5.8 / Material guidance). */
    val MinTouchTarget = 48.dp

    val ThumbnailAspectRatio = 16f / 9f
    val GridMinCellWidth = 164.dp
    val ListThumbnailWidth = 132.dp

    val SheetMaxWidth = 560.dp
    val DialogMaxWidth = 480.dp
}

/** A tappable label + value pair with an action slot (used across detail screens). */
enum class McTone { NEUTRAL, INFO, SUCCESS, WARNING, DANGER }
