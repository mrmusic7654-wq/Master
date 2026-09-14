package com.mastercontrol.app.core.ui.component

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A user-facing action rendered as a button.
 *
 * Screens pass actions rather than raw lambdas so error/empty states always get
 * a real recovery affordance (Retry / Reconnect / Open settings / …).
 */
data class McAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val emphasized: Boolean = false,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)
