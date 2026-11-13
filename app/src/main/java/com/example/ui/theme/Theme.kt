package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.preferences.ThemeModePreference

private val SeamDarkColorScheme = darkColorScheme(
    primary = SeamWarmIvory,
    onPrimary = SeamNearBlack,
    primaryContainer = SeamElevatedSurface,
    onPrimaryContainer = SeamWarmIvory,
    secondary = SeamLavenderAccent,
    onSecondary = SeamNearBlack,
    secondaryContainer = SeamElevatedSurface,
    onSecondaryContainer = SeamSilver,
    tertiary = SeamSilver,
    onTertiary = SeamNearBlack,
    background = SeamNearBlack,
    onBackground = SeamWarmIvory,
    surface = SeamCharcoal,
    onSurface = SeamWarmIvory,
    surfaceVariant = SeamCharcoal,
    onSurfaceVariant = SeamMutedGrey,
    error = SeamErrorCoral,
    outline = SeamBorderSubtle,
    outlineVariant = SeamHighlightBorder
)

private val WarmPaperLightScheme = lightColorScheme(
    primary = DeepInkPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E0D5),
    onPrimaryContainer = DeepInkPrimary,
    secondary = DeepTealSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDD9E8),
    onSecondaryContainer = DeepInkPrimary,
    tertiary = BronzeAccent,
    background = WarmPaperBg,
    onBackground = DeepInkPrimary,
    surface = WarmPaperSurface,
    onSurface = DeepInkPrimary,
    surfaceVariant = WarmPaperCard,
    onSurfaceVariant = Color(0xFF565961),
    error = Color(0xFFB3261E),
    outline = Color(0xFFD5D1C5)
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

    val colorScheme = if (useDarkTheme) SeamDarkColorScheme else WarmPaperLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
