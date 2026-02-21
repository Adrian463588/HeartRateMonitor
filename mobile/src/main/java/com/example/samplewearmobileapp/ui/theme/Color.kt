package com.example.samplewearmobileapp.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
//  Healthcare-Grade Semantic Color Palette
//  Designed for clinical data collection environments
// ============================================================

// === Light Theme Colors ===
val md_theme_light_primary = Color(0xFF1565C0)          // Clinical blue
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFD1E4FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001D36)

val md_theme_light_secondary = Color(0xFF2E7D32)        // Sensor green
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFC8E6C9)
val md_theme_light_onSecondaryContainer = Color(0xFF002106)

val md_theme_light_tertiary = Color(0xFF7B1FA2)         // Analysis purple
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFF3E5F5)
val md_theme_light_onTertiaryContainer = Color(0xFF270057)

val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)

val md_theme_light_background = Color(0xFFFAFAFA)
val md_theme_light_onBackground = Color(0xFF1A1C1E)
val md_theme_light_surface = Color(0xFFFAFAFA)
val md_theme_light_onSurface = Color(0xFF1A1C1E)
val md_theme_light_surfaceVariant = Color(0xFFE7E0EC)
val md_theme_light_onSurfaceVariant = Color(0xFF44474F)
val md_theme_light_outline = Color(0xFF74777F)

// === Dark Theme Colors (Clinical Low-Light) ===
val md_theme_dark_primary = Color(0xFF90CAF9)
val md_theme_dark_onPrimary = Color(0xFF003258)
val md_theme_dark_primaryContainer = Color(0xFF0D47A1)
val md_theme_dark_onPrimaryContainer = Color(0xFFD1E4FF)

val md_theme_dark_secondary = Color(0xFF66BB6A)
val md_theme_dark_onSecondary = Color(0xFF00390E)
val md_theme_dark_secondaryContainer = Color(0xFF1B5E20)
val md_theme_dark_onSecondaryContainer = Color(0xFFC8E6C9)

val md_theme_dark_tertiary = Color(0xFFCE93D8)
val md_theme_dark_onTertiary = Color(0xFF3A0057)
val md_theme_dark_tertiaryContainer = Color(0xFF6A1B9A)
val md_theme_dark_onTertiaryContainer = Color(0xFFF3E5F5)

val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_theme_dark_background = Color(0xFF121212)
val md_theme_dark_onBackground = Color(0xFFE3E2E6)
val md_theme_dark_surface = Color(0xFF121212)
val md_theme_dark_onSurface = Color(0xFFE3E2E6)
val md_theme_dark_surfaceVariant = Color(0xFF2C2C2C)
val md_theme_dark_onSurfaceVariant = Color(0xFFC4C6D0)
val md_theme_dark_outline = Color(0xFF8E9099)

// ============================================================
//  Sensor-Specific Semantic Colors
//  Used for real-time status indicators and graph traces
// ============================================================

object SensorColors {
    // --- Sensor states ---
    val activeLight = Color(0xFF1B7A2B)
    val activeDark = Color(0xFF66BB6A)
    val connectingLight = Color(0xFFF9A825)
    val connectingDark = Color(0xFFFFD54F)
    // error state uses MaterialTheme.colorScheme.error

    // --- Chart trace colors ---
    val ecgTraceLight = Color(0xFFC62828)
    val ecgTraceDark = Color(0xFFEF5350)

    val ppgGreenTraceLight = Color(0xFF2E7D32)
    val ppgGreenTraceDark = Color(0xFF66BB6A)

    val ppgIrTraceLight = Color(0xFF1565C0)
    val ppgIrTraceDark = Color(0xFF42A5F5)

    val ppgRedTraceLight = Color(0xFFC62828)
    val ppgRedTraceDark = Color(0xFFEF9A9A)

    // --- Card surfaces ---
    val cardSurfaceLight = Color(0xFFFFFFFF)
    val cardSurfaceDark = Color(0xFF1E1E1E)

    // --- Recording state ---
    val recordingPulse = Color(0xFFD32F2F)
    val recordingPulseDark = Color(0xFFEF5350)
    val pausedAmber = Color(0xFFF9A825)
    val idleGrey = Color(0xFF757575)
}
