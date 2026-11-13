package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.preferences.ThemeModePreference

private val ObsidianDarkScheme = darkColorScheme(
    primary = ParchmentGold,
    onPrimary = ObsidianInk,
    primaryContainer = ObsidianElevated,
    onPrimaryContainer = TextIvory,
    secondary = CelesteAccent,
    onSecondary = ObsidianInk,
    secondaryContainer = Color(0xFF132D33),
    onSecondaryContainer = CelesteAccent,
    tertiary = LavenderMist,
    background = ObsidianInk,
    onBackground = TextIvory,
    surface = ObsidianSurface,
    onSurface = TextIvory,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSilver,
    error = CoralAlert,
    outline = Color(0xFF2B354C)
)

private val WarmPaperLightScheme = lightColorScheme(
    primary = BronzeAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DCC8),
    onPrimaryContainer = DeepInkPrimary,
    secondary = DeepTealSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E7E8),
    onSecondaryContainer = DeepTealSecondary,
    tertiary = Color(0xFF52489C),
    background = WarmPaperBg,
    onBackground = DeepInkPrimary,
    surface = WarmPaperSurface,
    onSurface = DeepInkPrimary,
    surfaceVariant = WarmPaperCard,
    onSurfaceVariant = Color(0xFF4A5365),
    error = Color(0xFFB3261E),
    outline = Color(0xFFD0C9B8)
)

@Composable
fun PaperFlowTheme(
    themeMode: ThemeModePreference = ThemeModePreference.DARK_PRIORITY,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeModePreference.DARK_PRIORITY -> true
        ThemeModePreference.LIGHT_PAPER -> false
        ThemeModePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) ObsidianDarkScheme else WarmPaperLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
