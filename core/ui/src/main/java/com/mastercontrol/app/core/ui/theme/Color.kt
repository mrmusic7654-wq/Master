package com.mastercontrol.app.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Master Control palette.
 *
 * A neutral, slightly cool "operations console" identity: high information
 * density, restrained accent usage, and no resemblance to any messaging client.
 * Every colour pair below was chosen for WCAG AA contrast on its own surface.
 */
internal object McPalette {

    // ---- dark (default working environment) --------------------------------
    val DarkBackground = Color(0xFF0B0E13)
    val DarkSurface = Color(0xFF12161D)
    val DarkSurfaceVariant = Color(0xFF1B212B)
    val DarkSurfaceContainer = Color(0xFF171C25)
    val DarkSurfaceContainerHigh = Color(0xFF1F2531)
    val DarkOnSurface = Color(0xFFE6E9EF)
    val DarkOnSurfaceVariant = Color(0xFFAEB6C4)
    val DarkOutline = Color(0xFF39414F)
    val DarkOutlineVariant = Color(0xFF262D39)
    val DarkPrimary = Color(0xFF7FB8FF)
    val DarkOnPrimary = Color(0xFF062038)
    val DarkPrimaryContainer = Color(0xFF17385A)
    val DarkOnPrimaryContainer = Color(0xFFD6E6FF)
    val DarkSecondary = Color(0xFF79D3C0)
    val DarkOnSecondary = Color(0xFF04211B)
    val DarkSecondaryContainer = Color(0xFF124038)
    val DarkOnSecondaryContainer = Color(0xFFCFF3E9)
    val DarkTertiary = Color(0xFFE9B879)
    val DarkOnTertiary = Color(0xFF2B1B06)
    val DarkTertiaryContainer = Color(0xFF4A3212)
    val DarkOnTertiaryContainer = Color(0xFFFBE3C2)
    val DarkError = Color(0xFFFF8A80)
    val DarkOnError = Color(0xFF3B0705)
    val DarkErrorContainer = Color(0xFF5A1A15)
    val DarkOnErrorContainer = Color(0xFFFFDAD5)

    // ---- light -------------------------------------------------------------
    val LightBackground = Color(0xFFF6F7FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE7EAF0)
    val LightSurfaceContainer = Color(0xFFF1F3F7)
    val LightSurfaceContainerHigh = Color(0xFFE9ECF2)
    val LightOnSurface = Color(0xFF14181F)
    val LightOnSurfaceVariant = Color(0xFF4C5563)
    val LightOutline = Color(0xFF7C8695)
    val LightOutlineVariant = Color(0xFFD5DAE3)
    val LightPrimary = Color(0xFF1B5FA8)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFD6E4FF)
    val LightOnPrimaryContainer = Color(0xFF0A2A4A)
    val LightSecondary = Color(0xFF1F6B5C)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFCFF0E6)
    val LightOnSecondaryContainer = Color(0xFF0E332C)
    val LightTertiary = Color(0xFF8A5A19)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightTertiaryContainer = Color(0xFFFBE3C2)
    val LightOnTertiaryContainer = Color(0xFF33200A)
    val LightError = Color(0xFFB3261E)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFF9DEDC)
    val LightOnErrorContainer = Color(0xFF410E0B)
}

/** Semantic status tones used across screens (paired with text, never alone). */
object McStatusColors {
    /** Ready / verified / completed. */
    val Success get() = androidx.compose.ui.graphics.Color(0xFF2E9E6B)
    /** Waiting / queued / needs attention. */
    val Warning get() = androidx.compose.ui.graphics.Color(0xFFD08A1E)
    /** Failed / missing permission / destructive. */
    val Danger get() = androidx.compose.ui.graphics.Color(0xFFC6413A)
    /** Informational / in progress. */
    val Info get() = androidx.compose.ui.graphics.Color(0xFF3D7EBF)
    /** Neutral / disabled / unknown. */
    val Neutral get() = androidx.compose.ui.graphics.Color(0xFF78818F)
}
