package com.cyberscale.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CyberColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CyberBg,
    secondary = NeonMagenta,
    onSecondary = CyberBg,
    tertiary = NeonPurple,
    onTertiary = CyberBg,
    background = CyberBg,
    onBackground = TextPrimary,
    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurface2,
    onSurfaceVariant = TextSecondary,
    outline = CardStroke,
    error = NeonMagenta
)

@Composable
fun CyberScaleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = CyberTypography,
        content = content
    )
}
