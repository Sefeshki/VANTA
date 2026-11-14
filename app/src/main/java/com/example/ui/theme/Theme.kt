package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VantaColors(
    val background: Color,
    val secondaryBackground: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderGlow: Color,
    val primary: Color,
    val primaryBright: Color,
    val primaryGlow: Color,
    val primaryDark: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color
)

private val MonochromeVantaColors = VantaColors(
    background = VantaBackground,
    secondaryBackground = VantaSecondaryBackground,
    surface = VantaSurface,
    surfaceElevated = VantaSurfaceElevated,
    border = VantaBorder,
    borderGlow = VantaBorderGlow,
    primary = MonochromePrimary,
    primaryBright = MonochromeBright,
    primaryGlow = MonochromeGlow,
    primaryDark = MonochromeDark,
    textPrimary = VantaTextPrimary,
    textSecondary = VantaTextSecondary,
    textMuted = VantaTextMuted,
    success = VantaSuccess,
    warning = VantaWarning,
    error = VantaError,
    info = VantaInfo
)

val LocalVantaColors = staticCompositionLocalOf {
    MonochromeVantaColors
}

fun getVantaColors(): VantaColors = MonochromeVantaColors

fun getMaterialColorScheme(colors: VantaColors = MonochromeVantaColors): ColorScheme {
    return darkColorScheme(
        primary = colors.primary,
        onPrimary = Color.Black,
        primaryContainer = colors.primaryDark,
        onPrimaryContainer = colors.primaryBright,
        secondary = colors.primaryGlow,
        onSecondary = Color.Black,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceElevated,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.border,
        error = colors.error,
        onError = Color.White
    )
}

@Composable
fun VantaTheme(
    content: @Composable () -> Unit
) {
    val vantaColors = MonochromeVantaColors
    val colorScheme = getMaterialColorScheme(vantaColors)

    CompositionLocalProvider(LocalVantaColors provides vantaColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
