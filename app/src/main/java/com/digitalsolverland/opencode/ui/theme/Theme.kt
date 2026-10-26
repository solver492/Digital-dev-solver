package com.digitalsolverland.opencode.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

private val Background = Color(0xFF0B0E14)
private val Surface = Color(0xFF10141C)
private val SurfaceVariant = Color(0xFF161B22)
private val Primary = Color(0xFF7C9BFF)
private val OnPrimary = Color(0xFF000000)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF9BA3AF)
private val Border = Color(0xFF30363D)
private val Accent = Color(0xFF3FB950)

private val OpenCodeDarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    tertiary = Accent,
)

@Composable
fun OpenCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenCodeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
