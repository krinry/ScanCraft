package com.krinry.scancraft.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ---------------------------------------------------------------------------
// SCANCRAFT BRAND CONSTANTS (EXTRACTED FROM OFFICIAL LOGO)
// ---------------------------------------------------------------------------
val ScanCraftTeal = Color(0xFF076072)          // Signature Ocean Teal (Logo Dominant)
val ScanCraftTealLight = Color(0xFF0CA4C0)     // Bright Cyan / Active Accent
val ScanCraftTealDark = Color(0xFF044450)      // Deep Pressed Teal
val ScanCraftTealContainer = Color(0xFF0D2B32) // Subtle Dark Container Tint
val ScanCraftTealContainerLight = Color(0xFFDCF2F7) // Fresh Light Container Tint

// Aliases for unified global brand consistency
val ScannerGreen = ScanCraftTeal               // Global Brand Primary
val ScannerGreenLight = ScanCraftTealLight     // Active / Highlight
val ScannerGreenDark = ScanCraftTealDark       // Pressed / Deep Accent
val ScannerGreenContainer = ScanCraftTealContainer

// Supporting Functional Accents
val ScannerFolderYellow = Color(0xFFEAA838)   // Warm Amber / Folder
val ScannerBlueAccent = Color(0xFF0D7DFC)     // PDF / Export Accent
val ScannerGoldVip = Color(0xFFEDCC8B)        // Gold Accent
val ScannerRed = Color(0xFFEE3F3F)            // Delete / Alert Red

// Dark / Neutral Surface Tokens
val ScannerDarkBar = Color(0xFF141414)
val ScannerSlatePrimary = Color(0xFF1C2C30)
val ScannerSlateDark = Color(0xFF101C1F)
val ScannerNavyDark = Color(0xFF121212)
val ScannerBgLight = Color(0xFF000000)
val ScannerCardBg = Color(0xFF141718)
val ScannerBorderLight = Color(0xFF21272A)
val ScannerTextPrimary = Color(0xFFFFFFFF)
val ScannerTextSecondary = Color(0xFF8E9599)
val ScannerTextMuted = Color(0xFF636A6E)

// ---------------------------------------------------------------------------
// DYNAMIC COLOR DESIGN SYSTEM
// ---------------------------------------------------------------------------
@Stable
class ScannerColors(
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryContainer: Color,
    val folderYellow: Color,
    val isDark: Boolean
)

val DarkScannerColors = ScannerColors(
    background = Color(0xFF000000),         // AMOLED true black
    surface = Color(0xFF141718),            // Dark slate-graphite card
    surfaceSecondary = Color(0xFF1A1F21),   // Secondary elevated surface
    cardBorder = Color(0xFF222B2E),         // Subtle dark teal-graphite border
    textPrimary = Color(0xFFFFFFFF),        // High-contrast white text
    textSecondary = Color(0xFF8E9599),      // Neutral gray secondary text
    textMuted = Color(0xFF636A6E),          // Subtle muted text
    primary = ScanCraftTeal,                // ScanCraft Brand Teal
    primaryContainer = ScanCraftTealContainer, // Dark teal container
    folderYellow = ScannerFolderYellow,     // Manila folder yellow
    isDark = true
)

val LightScannerColors = ScannerColors(
    background = Color(0xFFF7F9FA),        // Soft paper light background
    surface = Color(0xFFFFFFFF),           // Pure white card surface
    surfaceSecondary = Color(0xFFF0F3F5),  // Soft gray secondary surface
    cardBorder = Color(0xFFE5EAEC),        // Clean card border
    textPrimary = Color(0xFF0D191D),       // Deep slate primary text
    textSecondary = Color(0xFF5A666B),     // Medium slate secondary text
    textMuted = Color(0xFF9CA7AC),         // Light muted text
    primary = ScanCraftTeal,                // ScanCraft Brand Teal
    primaryContainer = ScanCraftTealContainerLight, // Mint/cyan container tint
    folderYellow = ScannerFolderYellow,    // Manila folder yellow
    isDark = false
)

val LocalScannerColors = staticCompositionLocalOf { DarkScannerColors }

object ScannerTheme {
    val colors: ScannerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalScannerColors.current
}

// ---------------------------------------------------------------------------
// MATERIAL 3 COLOR SCHEMES
// ---------------------------------------------------------------------------
private val DarkColorScheme = darkColorScheme(
    primary = ScanCraftTeal,
    onPrimary = Color.White,
    primaryContainer = ScanCraftTealContainer,
    onPrimaryContainer = Color.White,
    secondary = ScanCraftTealLight,
    onSecondary = Color.White,
    background = Color(0xFF000000),
    surface = Color(0xFF141718),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1F21),
    onSurfaceVariant = Color(0xFF8E9599),
    outline = Color(0xFF222B2E)
)

private val LightColorScheme = lightColorScheme(
    primary = ScanCraftTeal,
    onPrimary = Color.White,
    primaryContainer = ScanCraftTealContainerLight,
    onPrimaryContainer = Color(0xFF044450),
    secondary = ScanCraftTealLight,
    onSecondary = Color.White,
    background = Color(0xFFF7F9FA),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0D191D),
    onSurface = Color(0xFF0D191D),
    surfaceVariant = Color(0xFFF0F3F5),
    onSurfaceVariant = Color(0xFF5A666B),
    outline = Color(0xFFE5EAEC)
)

@Composable
fun ScanCraftTheme(
    darkTheme: Boolean = ThemeManager.isDark(LocalContext.current),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val scannerColors = if (darkTheme) DarkScannerColors else LightScannerColors

    // Synchronize System Status Bar and Navigation Bar dynamically with Edge-to-Edge
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalScannerColors provides scannerColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}

// Backward compatibility alias
@Composable
fun OpenScannerTheme(
    darkTheme: Boolean = ThemeManager.isDark(LocalContext.current),
    content: @Composable () -> Unit
) = ScanCraftTheme(darkTheme = darkTheme, content = content)
