package com.garyapp.ytdl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

enum class YtdlThemeMode {
    System,
    Light,
    Dark,
}

@Immutable
data class YtdlColorPreset(
    val id: String,
    val label: String,
    val source: String,
)

@Immutable
data class YtdlThemeConfig(
    val mode: YtdlThemeMode,
    val colorPreset: YtdlColorPreset,
)

fun ytdlColorPresets(): List<YtdlColorPreset> = listOf(
    YtdlColorPreset(
        id = "reference_v3",
        label = "基准图配色",
        source = "docs/android-gui-reference-v3.png",
    ),
    YtdlColorPreset(
        id = "codex",
        label = "Codex 风格",
        source = "Task 7/8 reserved appearance preset",
    ),
)

fun defaultYtdlThemeConfig(): YtdlThemeConfig = YtdlThemeConfig(
    mode = YtdlThemeMode.System,
    colorPreset = ytdlColorPresets().first { it.id == "reference_v3" },
)

private val YtdlReferenceV3LightColors = lightColorScheme(
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

private val YtdlReferenceV3DarkColors = darkColorScheme(
    primary = Color(0xFF9FD4BF),
    onPrimary = Color(0xFF08372A),
    primaryContainer = Color(0xFF1B4B3D),
    onPrimaryContainer = Color(0xFFCBE8D9),
    secondary = Color(0xFFD5C0ED),
    onSecondary = Color(0xFF37264E),
    secondaryContainer = Color(0xFF4E3E65),
    onSecondaryContainer = Color(0xFFE9DDF8),
    tertiary = Color(0xFFFFB4A4),
    onTertiary = Color(0xFF522016),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE1E4DE),
    surface = Color(0xFF111411),
    onSurface = Color(0xFFE1E4DE),
    surfaceContainerHighest = Color(0xFF333A34),
    onSurfaceVariant = Color(0xFFC3CBC0),
)

private val YtdlCodexLightColors = lightColorScheme(
    primary = Color(0xFF315C6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E7F3),
    onPrimaryContainer = Color(0xFF083541),
    secondary = Color(0xFF6A5641),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4DEC4),
    onSecondaryContainer = Color(0xFF281806),
    tertiary = Color(0xFF5D5D79),
    onTertiary = Color.White,
    background = Color(0xFFFAF8F2),
    onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFFAF8F2),
    onSurface = Color(0xFF1D1B16),
    surfaceContainerHighest = Color(0xFFE8E2D7),
    onSurfaceVariant = Color(0xFF4B463D),
)

private val YtdlCodexDarkColors = darkColorScheme(
    primary = Color(0xFFA7CDDA),
    onPrimary = Color(0xFF003642),
    primaryContainer = Color(0xFF174D5C),
    onPrimaryContainer = Color(0xFFC8E7F3),
    secondary = Color(0xFFE0C2A4),
    onSecondary = Color(0xFF3D2A15),
    secondaryContainer = Color(0xFF55402A),
    onSecondaryContainer = Color(0xFFF4DEC4),
    tertiary = Color(0xFFC7C4E8),
    onTertiary = Color(0xFF2F2F49),
    background = Color(0xFF15130E),
    onBackground = Color(0xFFE8E2D7),
    surface = Color(0xFF15130E),
    onSurface = Color(0xFFE8E2D7),
    surfaceContainerHighest = Color(0xFF383229),
    onSurfaceVariant = Color(0xFFCBC5BA),
)

@Composable
fun YtdlTheme(
    config: YtdlThemeConfig = defaultYtdlThemeConfig(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (config.mode) {
        YtdlThemeMode.System -> isSystemInDarkTheme()
        YtdlThemeMode.Light -> false
        YtdlThemeMode.Dark -> true
    }

    val colorScheme = when (config.colorPreset.id) {
        "codex" -> if (darkTheme) YtdlCodexDarkColors else YtdlCodexLightColors
        else -> if (darkTheme) YtdlReferenceV3DarkColors else YtdlReferenceV3LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
