package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VigilantGreen = Color(0xFF22D3A5)
private val VigilantRed = Color(0xFFEF4444)
private val VigilantDarkBackground = Color(0xFF0F172A)
private val VigilantDarkSurface = Color(0xFF1E293B)

private val DarkColors = darkColorScheme(
    primary = VigilantGreen,
    error = VigilantRed,
    background = VigilantDarkBackground,
    surface = VigilantDarkSurface,
)

private val LightColors = lightColorScheme(
    primary = VigilantGreen,
    error = VigilantRed,
)

@Composable
fun VigilantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
