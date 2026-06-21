package com.garyapp.ytdl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import com.garyapp.ytdl.core.settings.AppSettings
import com.garyapp.ytdl.core.settings.AppearanceSettings

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

@Immutable
data class YtdlAppPalette(
    val appBackground: Color,
    val bottomBarBackground: Color,
    val cardBackground: Color,
    val mutedCardBackground: Color,
    val borderColor: Color,
    val downloadAccent: Color,
    val formatAccent: Color,
    val queueAccent: Color,
    val historyAccent: Color,
    val settingsAccent: Color,
    val successGreen: Color,
    val softText: Color,
    val titleText: Color,
    val segmentedBackground: Color,
    val neutralText: Color,
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

fun themeConfigForSettings(settings: AppSettings): YtdlThemeConfig {
    val mode = when (AppearanceSettings.normalizeThemeModeId(settings.themeModeId)) {
        AppearanceSettings.ThemeModeLight -> YtdlThemeMode.Light
        AppearanceSettings.ThemeModeDark -> YtdlThemeMode.Dark
        else -> YtdlThemeMode.System
    }
    val presetId = AppearanceSettings.normalizeColorPresetId(settings.colorPresetId)
    val preset = ytdlColorPresets().firstOrNull { it.id == presetId }
        ?: ytdlColorPresets().first { it.id == AppearanceSettings.ColorPresetReferenceV3 }
    return YtdlThemeConfig(mode = mode, colorPreset = preset)
}

fun ytdlAppPaletteForPreset(
    presetId: String,
    darkTheme: Boolean = false,
): YtdlAppPalette {
    return when (AppearanceSettings.normalizeColorPresetId(presetId)) {
        AppearanceSettings.ColorPresetCodex -> if (darkTheme) YtdlCodexDarkPalette else YtdlCodexLightPalette
        else -> if (darkTheme) YtdlReferenceV3DarkPalette else YtdlReferenceV3LightPalette
    }
}

val LocalYtdlAppPalette = staticCompositionLocalOf {
    ytdlAppPaletteForPreset(AppearanceSettings.ColorPresetReferenceV3)
}

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

private val YtdlReferenceV3LightPalette = YtdlAppPalette(
    appBackground = Color(0xFFFFFCF7),
    bottomBarBackground = Color(0xFFF8F0FB),
    cardBackground = Color(0xFFFFFFFF),
    mutedCardBackground = Color(0xFFF6F5F0),
    borderColor = Color(0xFFE8E2DA),
    downloadAccent = Color(0xFFFF5B55),
    formatAccent = Color(0xFF138F88),
    queueAccent = Color(0xFFFF7A1A),
    historyAccent = Color(0xFF7357C8),
    settingsAccent = Color(0xFF2E86DE),
    successGreen = Color(0xFF18A15F),
    softText = Color(0xFF5E625C),
    titleText = Color(0xFF181B17),
    segmentedBackground = Color(0xFFF1EEE8),
    neutralText = Color(0xFF263447),
)

private val YtdlReferenceV3DarkPalette = YtdlAppPalette(
    appBackground = Color(0xFF111411),
    bottomBarBackground = Color(0xFF211B25),
    cardBackground = Color(0xFF191D1A),
    mutedCardBackground = Color(0xFF252B26),
    borderColor = Color(0xFF333A34),
    downloadAccent = Color(0xFFFFB4A4),
    formatAccent = Color(0xFF9FD4BF),
    queueAccent = Color(0xFFFFB77A),
    historyAccent = Color(0xFFD5C0ED),
    settingsAccent = Color(0xFFA9C7FF),
    successGreen = Color(0xFF7DDCA5),
    softText = Color(0xFFC3CBC0),
    titleText = Color(0xFFE1E4DE),
    segmentedBackground = Color(0xFF2C312C),
    neutralText = Color(0xFFE1E4DE),
)

private val YtdlCodexLightPalette = YtdlAppPalette(
    appBackground = Color(0xFFFAF8F2),
    bottomBarBackground = Color(0xFFEFEDE7),
    cardBackground = Color(0xFFFFFFFF),
    mutedCardBackground = Color(0xFFF1EDE5),
    borderColor = Color(0xFFE2DCD0),
    downloadAccent = Color(0xFF315C6B),
    formatAccent = Color(0xFF7C705E),
    queueAccent = Color(0xFFA26E35),
    historyAccent = Color(0xFF5D5D79),
    settingsAccent = Color(0xFF2F6D80),
    successGreen = Color(0xFF3C8158),
    softText = Color(0xFF615B50),
    titleText = Color(0xFF1D1B16),
    segmentedBackground = Color(0xFFE8E2D7),
    neutralText = Color(0xFF30352F),
)

private val YtdlCodexDarkPalette = YtdlAppPalette(
    appBackground = Color(0xFF15130E),
    bottomBarBackground = Color(0xFF221E18),
    cardBackground = Color(0xFF201D16),
    mutedCardBackground = Color(0xFF2D281F),
    borderColor = Color(0xFF413A2D),
    downloadAccent = Color(0xFFA7CDDA),
    formatAccent = Color(0xFFE0C2A4),
    queueAccent = Color(0xFFFFC27E),
    historyAccent = Color(0xFFC7C4E8),
    settingsAccent = Color(0xFF9AD4E6),
    successGreen = Color(0xFF8FCAA2),
    softText = Color(0xFFCBC5BA),
    titleText = Color(0xFFE8E2D7),
    segmentedBackground = Color(0xFF383229),
    neutralText = Color(0xFFE8E2D7),
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

    CompositionLocalProvider(
        LocalYtdlAppPalette provides ytdlAppPaletteForPreset(config.colorPreset.id, darkTheme),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
