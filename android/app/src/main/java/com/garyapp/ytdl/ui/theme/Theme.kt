package com.garyapp.ytdl.ui.theme

import androidx.compose.material3.MaterialTheme
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.garyapp.ytdl.core.settings.AppLanguage
import com.garyapp.ytdl.ui.LocalAppLanguage
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
    val primaryActionText: Color = Color.White,
    val controlActionText: Color = Color.White,
    val actionContainer: Color = segmentedBackground,
)

fun ytdlColorPresets(): List<YtdlColorPreset> = listOf(
    YtdlColorPreset(
        id = AppearanceSettings.ColorPresetMorandi,
        label = "莫兰迪",
        source = "docs/design/2026-11-05-download-revision/approved-a-tokens.json",
    ),
    YtdlColorPreset(
        id = "codex",
        label = "Codex 风格",
        source = "Task 7/8 reserved appearance preset",
    ),
)

fun defaultYtdlThemeConfig(): YtdlThemeConfig = YtdlThemeConfig(
    mode = YtdlThemeMode.System,
    colorPreset = ytdlColorPresets().first { it.id == AppearanceSettings.ColorPresetMorandi },
)

fun themeConfigForSettings(settings: AppSettings): YtdlThemeConfig {
    val mode = when (AppearanceSettings.normalizeThemeModeId(settings.themeModeId)) {
        AppearanceSettings.ThemeModeLight -> YtdlThemeMode.Light
        AppearanceSettings.ThemeModeDark -> YtdlThemeMode.Dark
        else -> YtdlThemeMode.System
    }
    val presetId = AppearanceSettings.normalizeColorPresetId(settings.colorPresetId)
    val preset = ytdlColorPresets().firstOrNull { it.id == presetId }
        ?: ytdlColorPresets().first { it.id == AppearanceSettings.ColorPresetMorandi }
    return YtdlThemeConfig(mode = mode, colorPreset = preset)
}

fun ytdlAppPaletteForPreset(
    presetId: String,
    darkTheme: Boolean = false,
): YtdlAppPalette {
    return when (AppearanceSettings.normalizeColorPresetId(presetId)) {
        AppearanceSettings.ColorPresetCodex -> if (darkTheme) YtdlCodexDarkPalette else YtdlCodexLightPalette
        else -> if (darkTheme) MorandiDarkPalette else MorandiLightPalette
    }
}

val LocalYtdlAppPalette = staticCompositionLocalOf {
    ytdlAppPaletteForPreset(AppearanceSettings.ColorPresetMorandi)
}

private val YtdlCompactTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)

internal fun ytdlCompactTypographyForUiTest(): Typography = YtdlCompactTypography

private val MorandiLightPalette = YtdlAppPalette(
    appBackground = Color(0xFFF3F0EB),
    bottomBarBackground = Color(0xFFFCFAF6),
    cardBackground = Color(0xFFFCFAF6),
    mutedCardBackground = Color(0xFFE0E7E4),
    borderColor = Color(0xFFD8D9D1),
    downloadAccent = Color(0xFF8B5860),
    formatAccent = Color(0xFF536F73),
    queueAccent = Color(0xFF8B5860),
    historyAccent = Color(0xFF536F73),
    settingsAccent = Color(0xFF8B5860),
    successGreen = Color(0xFF536F73),
    softText = Color(0xFF637071),
    titleText = Color(0xFF323F40),
    segmentedBackground = Color(0xFFE0E7E4),
    neutralText = Color(0xFF323F40),
    primaryActionText = Color(0xFFFFFDF7),
    actionContainer = Color(0xFFF0E2E2),
    controlActionText = Color(0xFFFFFDF7),
)

private val MorandiLightColors = with(MorandiLightPalette) {
    lightColorScheme(
        primary = downloadAccent,
        onPrimary = primaryActionText,
        primaryContainer = actionContainer,
        onPrimaryContainer = titleText,
        secondary = formatAccent,
        onSecondary = controlActionText,
        secondaryContainer = segmentedBackground,
        onSecondaryContainer = titleText,
        background = appBackground,
        onBackground = titleText,
        surface = cardBackground,
        onSurface = titleText,
        surfaceVariant = mutedCardBackground,
        onSurfaceVariant = softText,
        outline = borderColor,
        error = Color(0xFF9B4D4A),
    )
}

private val MorandiDarkPalette = YtdlAppPalette(
    appBackground = Color(0xFF232C30),
    bottomBarBackground = Color(0xFF2B373B),
    cardBackground = Color(0xFF2B373B),
    mutedCardBackground = Color(0xFF3A4E50),
    borderColor = Color(0xFF465458),
    downloadAccent = Color(0xFFD7B2B7),
    formatAccent = Color(0xFFB0C8C5),
    queueAccent = Color(0xFFD7B2B7),
    historyAccent = Color(0xFFB0C8C5),
    settingsAccent = Color(0xFFD7B2B7),
    successGreen = Color(0xFFB0C8C5),
    softText = Color(0xFFB3BFBC),
    titleText = Color(0xFFE4E6DF),
    segmentedBackground = Color(0xFF3A4E50),
    neutralText = Color(0xFFE4E6DF),
    primaryActionText = Color(0xFF30292D),
    actionContainer = Color(0xFF493B42),
    controlActionText = Color(0xFF232C30),
)

private val MorandiDarkColors = with(MorandiDarkPalette) {
    darkColorScheme(
        primary = downloadAccent,
        onPrimary = primaryActionText,
        primaryContainer = actionContainer,
        onPrimaryContainer = titleText,
        secondary = formatAccent,
        onSecondary = controlActionText,
        secondaryContainer = segmentedBackground,
        onSecondaryContainer = titleText,
        background = appBackground,
        onBackground = titleText,
        surface = cardBackground,
        onSurface = titleText,
        surfaceVariant = mutedCardBackground,
        onSurfaceVariant = softText,
        outline = borderColor,
        error = Color(0xFFE5ADA4),
    )
}

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
    primaryActionText = Color(0xFF15130E),
    controlActionText = Color(0xFF15130E),
)

@Composable
fun YtdlTheme(
    config: YtdlThemeConfig = defaultYtdlThemeConfig(),
    appLanguage: AppLanguage = AppLanguage.Chinese,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (config.mode) {
        YtdlThemeMode.System -> isSystemInDarkTheme()
        YtdlThemeMode.Light -> false
        YtdlThemeMode.Dark -> true
    }

    val colorScheme = when (config.colorPreset.id) {
        "codex" -> if (darkTheme) YtdlCodexDarkColors else YtdlCodexLightColors
        else -> if (darkTheme) MorandiDarkColors else MorandiLightColors
    }

    val view = LocalView.current
    val window = view.context.findActivity()?.window
    SideEffect {
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    val square = RoundedCornerShape(0)
    val palette = ytdlAppPaletteForPreset(config.colorPreset.id, darkTheme)
    CompositionLocalProvider(
        LocalYtdlAppPalette provides palette,
        LocalAppLanguage provides appLanguage,
    ) {
        MaterialTheme(
            colorScheme = colorScheme.copy(surfaceContainerHigh = palette.cardBackground),
            typography = YtdlCompactTypography,
            shapes = Shapes(square, square, square, square, square),
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
