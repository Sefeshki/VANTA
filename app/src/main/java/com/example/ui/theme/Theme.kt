package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class VantaThemeId(val displayName: String, val primaryColorHex: Long) {
    PURPLE("Purple", 0xFFA855F7),
    RED("Red", 0xFFEF4444),
    BLUE("Blue", 0xFF3B82F6),
    CYAN("Cyan", 0xFF06B6D4)
}

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

val LocalVantaColors = staticCompositionLocalOf {
    getVantaColors(VantaThemeId.PURPLE)
}

fun getVantaColors(themeId: VantaThemeId): VantaColors {
    val (primary, bright, glow, dark) = when (themeId) {
        VantaThemeId.PURPLE -> Quad(PurplePrimary, PurpleBright, PurpleGlow, PurpleDark)
        VantaThemeId.RED -> Quad(RedPrimary, RedBright, RedGlow, RedDark)
        VantaThemeId.BLUE -> Quad(BluePrimary, BlueBright, BlueGlow, BlueDark)
        VantaThemeId.CYAN -> Quad(CyanPrimary, CyanBright, CyanGlow, CyanDark)
    }
    return VantaColors(
        background = VantaBackground,
        secondaryBackground = VantaSecondaryBackground,
        surface = VantaSurface,
        surfaceElevated = VantaSurfaceElevated,
        border = VantaBorder,
        borderGlow = VantaBorderGlow,
        primary = primary,
        primaryBright = bright,
        primaryGlow = glow,
        primaryDark = dark,
        textPrimary = VantaTextPrimary,
        textSecondary = VantaTextSecondary,
        textMuted = VantaTextMuted,
        success = VantaSuccess,
        warning = VantaWarning,
        error = VantaError,
        info = VantaInfo
    )
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

fun getMaterialColorScheme(colors: VantaColors): ColorScheme {
    return darkColorScheme(
        primary = colors.primary,
        onPrimary = Color.Black,
        primaryContainer = colors.primaryDark,
        onPrimaryContainer = colors.primaryBright,
        secondary = colors.primaryBright,
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
fun MyApplicationTheme(
    themeId: VantaThemeId = VantaThemeId.PURPLE,
    content: @Composable () -> Unit
) {
    val vantaColors = getVantaColors(themeId)
    val colorScheme = getMaterialColorScheme(vantaColors)

    CompositionLocalProvider(LocalVantaColors provides vantaColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
