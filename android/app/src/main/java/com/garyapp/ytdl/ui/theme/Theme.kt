package com.garyapp.ytdl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YtdlLightColors = lightColorScheme(
    primary = Color(0xFF2F5D50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE8D9),
    onPrimaryContainer = Color(0xFF11382E),
    secondary = Color(0xFF66577D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDF8),
    onSecondaryContainer = Color(0xFF281A3A),
    tertiary = Color(0xFF8A4D40),
    onTertiary = Color.White,
    background = Color(0xFFF8FAF6),
    onBackground = Color(0xFF191D1A),
    surface = Color(0xFFF8FAF6),
    onSurface = Color(0xFF191D1A),
    surfaceContainerHighest = Color(0xFFE4EAE2),
    onSurfaceVariant = Color(0xFF424940),
)

@Composable
fun YtdlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YtdlLightColors,
        content = content,
    )
}
