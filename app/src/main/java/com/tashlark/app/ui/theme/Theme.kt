package com.tashlark.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tashlark.engine.Suit

/**
 * "Kora & neel": undyed khadi cloth by day, indigo by night, with turmeric, madder and
 * neem accents. Fixed brand palette (no dynamic colour) so cards always look the same.
 */
@Immutable
data class TashColors(
    val table: Color,
    val motif: Color,
    val panel: Color,
    val panelRaised: Color,
    val cardFace: Color,
    val cardEdge: Color,
    val cardBack: Color,
    val cardBackMotif: Color,
    val ink: Color,
    val inkMuted: Color,
    /** Ink used ON card faces and panels-on-cards (always dark, since faces stay paper). */
    val cardInk: Color,
    val haldi: Color,
    val onHaldi: Color,
    val madder: Color,
    val onMadder: Color,
    val neem: Color,
    val rule: Color,
    val isDark: Boolean,
)

val LightTash = TashColors(
    table = Color(0xFFD8CCB4),
    motif = Color(0xFFCDBFA3),
    panel = Color(0xFFF6F0E4),
    panelRaised = Color(0xFFFBF7EE),
    cardFace = Color(0xFFFBF7EE),
    cardEdge = Color(0xFFCDBFA6),
    cardBack = Color(0xFF2B3A67),
    cardBackMotif = Color(0xFFA63A2B),
    ink = Color(0xFF1F1A14),
    inkMuted = Color(0xFF5E554A),
    cardInk = Color(0xFF1F1A14),
    haldi = Color(0xFFD99A1E),
    onHaldi = Color(0xFF1F1A14),
    madder = Color(0xFFA63A2B),
    onMadder = Color(0xFFFBF7EE),
    neem = Color(0xFF3F6B4E),
    rule = Color(0xFFC4B597),
    isDark = false,
)

val DarkTash = TashColors(
    table = Color(0xFF18213A),
    motif = Color(0xFF1F2A48),
    panel = Color(0xFF222C47),
    panelRaised = Color(0xFF2A3554),
    cardFace = Color(0xFFEEE7D8),
    cardEdge = Color(0xFF0E1426),
    cardBack = Color(0xFF2B3A67),
    cardBackMotif = Color(0xFFD2604C),
    ink = Color(0xFFF2ECE0),
    inkMuted = Color(0xFFA9B0C4),
    cardInk = Color(0xFF1F1A14),
    haldi = Color(0xFFE8AE3A),
    onHaldi = Color(0xFF18213A),
    madder = Color(0xFFD2604C),
    onMadder = Color(0xFF18213A),
    neem = Color(0xFF7FB08F),
    rule = Color(0xFF34406A),
    isDark = true,
)

/** Suit inks on the paper card face (all ≥ 4.5:1 there). */
fun suitColor(suit: Suit, fourColour: Boolean): Color = when (suit) {
    Suit.SPADES -> Color(0xFF1F1A14)
    Suit.HEARTS -> Color(0xFFB8321F)
    Suit.DIAMONDS -> if (fourColour) Color(0xFF0B5FA5) else Color(0xFFB8321F)
    Suit.CLUBS -> if (fourColour) Color(0xFF00775A) else Color(0xFF1F1A14)
}

val LocalTash = staticCompositionLocalOf { LightTash }

object Tash {
    val colors: TashColors
        @Composable get() = LocalTash.current
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun TashlarkTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkTash else LightTash
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.haldi, onPrimary = c.onHaldi, secondary = c.madder, onSecondary = c.onMadder,
            background = c.table, onBackground = c.ink, surface = c.panel, onSurface = c.ink,
            surfaceVariant = c.panelRaised, onSurfaceVariant = c.inkMuted, outline = c.rule, outlineVariant = c.rule,
            error = c.madder, surfaceContainer = c.panel, surfaceContainerHigh = c.panelRaised,
            surfaceContainerLow = c.panel, surfaceContainerHighest = c.panelRaised, surfaceContainerLowest = c.table,
        )
    } else {
        lightColorScheme(
            primary = c.madder, onPrimary = c.onMadder, secondary = c.neem, onSecondary = c.panel,
            background = c.table, onBackground = c.ink, surface = c.panel, onSurface = c.ink,
            surfaceVariant = c.panelRaised, onSurfaceVariant = c.inkMuted, outline = c.rule, outlineVariant = c.rule,
            error = c.madder, surfaceContainer = c.panel, surfaceContainerHigh = c.panelRaised,
            surfaceContainerLow = c.panel, surfaceContainerHighest = c.panelRaised, surfaceContainerLowest = c.table,
        )
    }
    CompositionLocalProvider(LocalTash provides c) {
        MaterialTheme(colorScheme = scheme, typography = TashTypography, content = content)
    }
}
