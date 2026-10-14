package com.gridlark.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * "Ink & paper" palette. Fixed brand colours (no dynamic colour) so boards, region colours
 * and shared results look the same on every phone.
 */
@Immutable
data class GridColors(
    val paper: Color,
    val surface: Color,
    val sunken: Color,
    val rule: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val success: Color,
    val error: Color,
    val regions: List<Color>,
    val isDark: Boolean,
)

// Region palette: Paul Tol's "light" scheme (colour-blind distinct) plus three extensions.
private val RegionsLight = listOf(
    Color(0xFF77AADD), Color(0xFFEE8866), Color(0xFFEEDD88), Color(0xFFFFAABB), Color(0xFF99DDFF),
    Color(0xFF44BB99), Color(0xFFBBCC33), Color(0xFFAAAA00), Color(0xFFDDDDDD), Color(0xFFC2A5CF),
    Color(0xFFE0C2A2), Color(0xFF9DB7AE),
)
private val RegionsDark = listOf(
    Color(0xFF6289B1), Color(0xFFBE6F54), Color(0xFFBEB16E), Color(0xFFCC8996), Color(0xFF7CB1CB),
    Color(0xFF3A967C), Color(0xFF97A42C), Color(0xFF898904), Color(0xFFB1B1B1), Color(0xFF9C85A6),
    Color(0xFFB49C83), Color(0xFF7F938C),
)

val LightGridColors = GridColors(
    paper = Color(0xFFF5F1E8),
    surface = Color(0xFFFFFCF5),
    sunken = Color(0xFFECE6D9),
    rule = Color(0xFFD9D2C3),
    ink = Color(0xFF1C1B19),
    inkMuted = Color(0xFF5E5A52),
    inkFaint = Color(0xFF8E887C),
    accent = Color(0xFFE9A23B),
    onAccent = Color(0xFF1C1B19),
    accentText = Color(0xFF9A5B0C),
    success = Color(0xFF2F6B4F),
    error = Color(0xFFB3412E),
    regions = RegionsLight,
    isDark = false,
)

val DarkGridColors = GridColors(
    paper = Color(0xFF161513),
    surface = Color(0xFF1F1D1A),
    sunken = Color(0xFF2A2723),
    rule = Color(0xFF3A3631),
    ink = Color(0xFFEFEAE0),
    inkMuted = Color(0xFFADA79B),
    inkFaint = Color(0xFF7A756B),
    accent = Color(0xFFF0B254),
    onAccent = Color(0xFF161513),
    accentText = Color(0xFFF0B254),
    success = Color(0xFF7FC29B),
    error = Color(0xFFE88A73),
    regions = RegionsDark,
    isDark = true,
)

val LocalGridColors = staticCompositionLocalOf { LightGridColors }

/** Shortcut: `Grid.colors.ink`. */
object Grid {
    val colors: GridColors
        @Composable get() = LocalGridColors.current
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun GridlarkTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkGridColors else LightGridColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.ink, onSecondary = c.paper,
            background = c.paper, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.sunken, onSurfaceVariant = c.inkMuted, outline = c.rule, outlineVariant = c.rule,
            error = c.error, surfaceContainer = c.surface, surfaceContainerHigh = c.sunken,
            surfaceContainerLow = c.surface, surfaceContainerHighest = c.sunken, surfaceContainerLowest = c.paper,
        )
    } else {
        lightColorScheme(
            primary = c.ink, onPrimary = c.paper, secondary = c.accentText, onSecondary = c.paper,
            background = c.paper, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.sunken, onSurfaceVariant = c.inkMuted, outline = c.rule, outlineVariant = c.rule,
            error = c.error, surfaceContainer = c.surface, surfaceContainerHigh = c.sunken,
            surfaceContainerLow = c.surface, surfaceContainerHighest = c.sunken, surfaceContainerLowest = c.paper,
        )
    }
    CompositionLocalProvider(LocalGridColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = GridTypography, content = content)
    }
}
