package com.mastercontrol.app.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = McPalette.DarkPrimary,
    onPrimary = McPalette.DarkOnPrimary,
    primaryContainer = McPalette.DarkPrimaryContainer,
    onPrimaryContainer = McPalette.DarkOnPrimaryContainer,
    secondary = McPalette.DarkSecondary,
    onSecondary = McPalette.DarkOnSecondary,
    secondaryContainer = McPalette.DarkSecondaryContainer,
    onSecondaryContainer = McPalette.DarkOnSecondaryContainer,
    tertiary = McPalette.DarkTertiary,
    onTertiary = McPalette.DarkOnTertiary,
    tertiaryContainer = McPalette.DarkTertiaryContainer,
    onTertiaryContainer = McPalette.DarkOnTertiaryContainer,
    error = McPalette.DarkError,
    onError = McPalette.DarkOnError,
    errorContainer = McPalette.DarkErrorContainer,
    onErrorContainer = McPalette.DarkOnErrorContainer,
    background = McPalette.DarkBackground,
    onBackground = McPalette.DarkOnSurface,
    surface = McPalette.DarkSurface,
    onSurface = McPalette.DarkOnSurface,
    surfaceVariant = McPalette.DarkSurfaceVariant,
    onSurfaceVariant = McPalette.DarkOnSurfaceVariant,
    surfaceContainer = McPalette.DarkSurfaceContainer,
    surfaceContainerHigh = McPalette.DarkSurfaceContainerHigh,
    outline = McPalette.DarkOutline,
    outlineVariant = McPalette.DarkOutlineVariant,
)

private val LightColors = lightColorScheme(
    primary = McPalette.LightPrimary,
    onPrimary = McPalette.LightOnPrimary,
    primaryContainer = McPalette.LightPrimaryContainer,
    onPrimaryContainer = McPalette.LightOnPrimaryContainer,
    secondary = McPalette.LightSecondary,
    onSecondary = McPalette.LightOnSecondary,
    secondaryContainer = McPalette.LightSecondaryContainer,
    onSecondaryContainer = McPalette.LightOnSecondaryContainer,
    tertiary = McPalette.LightTertiary,
    onTertiary = McPalette.LightOnTertiary,
    tertiaryContainer = McPalette.LightTertiaryContainer,
    onTertiaryContainer = McPalette.LightOnTertiaryContainer,
    error = McPalette.LightError,
    onError = McPalette.LightOnError,
    errorContainer = McPalette.LightErrorContainer,
    onErrorContainer = McPalette.LightOnErrorContainer,
    background = McPalette.LightBackground,
    onBackground = McPalette.LightOnSurface,
    surface = McPalette.LightSurface,
    onSurface = McPalette.LightOnSurface,
    surfaceVariant = McPalette.LightSurfaceVariant,
    onSurfaceVariant = McPalette.LightOnSurfaceVariant,
    surfaceContainer = McPalette.LightSurfaceContainer,
    surfaceContainerHigh = McPalette.LightSurfaceContainerHigh,
    outline = McPalette.LightOutline,
    outlineVariant = McPalette.LightOutlineVariant,
)

/** True when the user asked the system to remove animations. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Master Control theme.
 *
 * [darkTheme] / [dynamicColor] come from the persisted appearance settings
 * (Settings → Appearance); dynamic colour is only honoured on Android 12+.
 */
@Composable
fun MasterControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = McTypography,
            shapes = McShapes,
            content = content,
        )
    }
}

/** Semantic status colours resolved against the active scheme. */
object McColors {
    val success: Color
        @Composable @ReadOnlyComposable get() = McStatusColors.Success

    val warning: Color
        @Composable @ReadOnlyComposable get() = McStatusColors.Warning

    val danger: Color
        @Composable @ReadOnlyComposable get() = McStatusColors.Danger

    val info: Color
        @Composable @ReadOnlyComposable get() = McStatusColors.Info

    val neutral: Color
        @Composable @ReadOnlyComposable get() = McStatusColors.Neutral
}
