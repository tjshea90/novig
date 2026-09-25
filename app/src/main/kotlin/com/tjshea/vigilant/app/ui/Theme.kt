package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors with a meaning (edge up/down, stale) that Material's scheme has no slot for. */
@Immutable
data class EdgeColors(
    val positive: Color,
    val positiveContainer: Color,
    val negative: Color,
    val warning: Color,
    val muted: Color,
)

private val DarkEdge = EdgeColors(
    positive = Color(0xFF2BD98B),
    positiveContainer = Color(0xFF123A2A),
    negative = Color(0xFFFF6B6B),
    warning = Color(0xFFFFC857),
    muted = Color(0xFF8A96A8),
)

private val LightEdge = EdgeColors(
    positive = Color(0xFF0B8A55),
    positiveContainer = Color(0xFFD5F5E6),
    negative = Color(0xFFC62828),
    warning = Color(0xFF9A6700),
    muted = Color(0xFF5B6675),
)

val LocalEdgeColors = staticCompositionLocalOf { DarkEdge }

// Dark, high-contrast, green-accented — the look OddsJam users know, tuned for an OLED/LCD phone.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF2BD98B),
    onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF123A2A),
    onPrimaryContainer = Color(0xFFB7F5D6),
    secondary = Color(0xFF7CB7FF),
    background = Color(0xFF0A0E13),
    onBackground = Color(0xFFE6EBF2),
    surface = Color(0xFF0A0E13),
    onSurface = Color(0xFFE6EBF2),
    surfaceVariant = Color(0xFF1A222D),
    onSurfaceVariant = Color(0xFFA9B4C4),
    surfaceContainer = Color(0xFF121821),
    surfaceContainerHigh = Color(0xFF18202B),
    surfaceContainerHighest = Color(0xFF1F2835),
    surfaceContainerLow = Color(0xFF0F141B),
    outline = Color(0xFF3A4556),
    outlineVariant = Color(0xFF263040),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B8A55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5E6),
    onPrimaryContainer = Color(0xFF00391F),
    secondary = Color(0xFF1E5FB3),
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFF6F8FA),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F3F6),
    error = Color(0xFFC62828),
)

@Composable
fun VigilantTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalEdgeColors provides if (darkTheme) DarkEdge else LightEdge) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}

object Edge {
    val colors: EdgeColors
        @Composable get() = LocalEdgeColors.current
}
