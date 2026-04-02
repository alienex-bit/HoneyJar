package com.honeyjar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import com.honeyjar.app.R

val PlayfairDisplay = FontFamily(
    Font(R.font.playfair_display_black_italic, FontWeight.Black, FontStyle.Italic),
    Font(R.font.playfair_display_bold_italic, FontWeight.Bold, FontStyle.Italic)
)

val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold)
)

// --- HoneyJar Specific Tokens ---
@Immutable
data class HoneyJarColors(
    val accent: Color,
    val accentGlow: Color,
    val heroGradient: Brush,
    val glassBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val itemBg: Color,
    val heatmapRamp: List<Color>
)

val LocalHoneyJarColors = staticCompositionLocalOf {
    HoneyJarColors(
        accent = Color(0xFFFFB300),
        accentGlow = Color(0xFFFFB300).copy(alpha = 0.3f),
        heroGradient = Brush.radialGradient(listOf(Color(0xFFFFB300), Color(0xFFA64D00))),
        glassBorder = Color.White.copy(alpha = 0.15f),
        textPrimary = Color.White,
        textSecondary = Color(0xFFA0A0A0),
        itemBg = Color.White.copy(alpha = 0.08f),
        heatmapRamp = listOf(
            Color.White.copy(alpha = 0.08f),
            Color(0xFFFFB300).copy(alpha = 0.20f),
            Color(0xFFFFB300).copy(alpha = 0.45f),
            Color(0xFFFFB300).copy(alpha = 0.72f),
            Color(0xFFFFB300)
        )
    )
}

// --- Specific Color Definitions ---
val DarkBg = Color(0xFF0C0C0C)
val MidnightBg = Color(0xFF0C0D12)
val CreamBg = Color(0xFFFDFAF5)
val MinimalBg = Color(0xFFFFFFFF)

enum class HoneyJarThemeType {
    DarkHoney, Midnight, LightCream, LightMinimal
}

private val DarkHoneyColorScheme = darkColorScheme(
    primary = Color(0xFFFFB300),
    secondary = Color(0xFFA64D00),
    background = DarkBg,
    surface = Color(0xFF161616),
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val MidnightColorScheme = darkColorScheme(
    primary = Color(0xFFA57EFF),   // was 7C4DFF — lightened to pass WCAG AA (6.5:1 on bg)
    secondary = Color(0xFF6B3FA0), // was 311B92 — that was near-invisible (1.6:1); usable purple
    background = MidnightBg,
    surface = Color(0xFF1A1B24),
    onPrimary = Color.Black,       // dark text on lighter purple
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightCreamColorScheme = lightColorScheme(
    primary = Color(0xFF8B6700),   // was D49A00 — that failed at 2.4:1; now 5.0:1 AA ✓
    secondary = Color(0xFF7A5C00), // was A67C00 — darkened to match; 6.0:1 AA ✓
    background = CreamBg,
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,       // white on dark amber reads well
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A)
)

private val LightMinimalColorScheme = lightColorScheme(
    primary = Color(0xFF0062D6),   // was 0076FF — was borderline 4.2:1; now 5.6:1 AA ✓
    secondary = Color(0xFF1565C0), // was 42A5F5 — that failed at 2.6:1; now 5.7:1 AA ✓
    background = MinimalBg,
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1E21),
    onSurface = Color(0xFF1C1E21)
)

@Composable
fun HoneyJarTheme(
    themeType: HoneyJarThemeType = HoneyJarThemeType.DarkHoney,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeType) {
        HoneyJarThemeType.DarkHoney -> DarkHoneyColorScheme
        HoneyJarThemeType.Midnight -> MidnightColorScheme
        HoneyJarThemeType.LightCream -> LightCreamColorScheme
        HoneyJarThemeType.LightMinimal -> LightMinimalColorScheme
    }

    val customColors = when (themeType) {
        HoneyJarThemeType.DarkHoney -> HoneyJarColors(
            accent = Color(0xFFFFB300),
            accentGlow = Color(0xFFFFB300).copy(alpha = 0.3f),
            heroGradient = Brush.radialGradient(listOf(Color(0xFFFFB300), Color(0xFFA64D00))),
            glassBorder = Color.White.copy(alpha = 0.15f),   // was 10% — slightly more card definition
            textPrimary = Color.White,
            textSecondary = Color(0xFFA0A0A0),
            itemBg = Color.White.copy(alpha = 0.08f),
            heatmapRamp = listOf(
                Color.White.copy(alpha = 0.08f),
                Color(0xFFFFB300).copy(alpha = 0.20f),
                Color(0xFFFFB300).copy(alpha = 0.45f),
                Color(0xFFFFB300).copy(alpha = 0.72f),
                Color(0xFFFFB300)
            )
        )
        HoneyJarThemeType.Midnight -> HoneyJarColors(
            accent = Color(0xFFA57EFF),
            accentGlow = Color(0xFFA57EFF).copy(alpha = 0.3f),
            heroGradient = Brush.radialGradient(listOf(Color(0xFFA57EFF), Color(0xFF6B3FA0))),
            glassBorder = Color.White.copy(alpha = 0.12f),   // was purple 10% — near invisible; white is cleaner on dark
            textPrimary = Color.White,
            textSecondary = Color(0xFF9DA3AF),
            itemBg = Color.White.copy(alpha = 0.06f),        // was purple 12% — too heavy; neutral white tint
            heatmapRamp = listOf(
                Color(0xFFA57EFF).copy(alpha = 0.08f),
                Color(0xFFA57EFF).copy(alpha = 0.25f),
                Color(0xFFA57EFF).copy(alpha = 0.52f),
                Color(0xFFA57EFF).copy(alpha = 0.78f),
                Color(0xFFA57EFF)
            )
        )
        HoneyJarThemeType.LightCream -> HoneyJarColors(
            accent = Color(0xFF8B6700),
            accentGlow = Color(0xFF8B6700).copy(alpha = 0.12f),
            heroGradient = Brush.radialGradient(listOf(Color(0xFFFFF5E6), Color(0xFFFEF0D7))),
            glassBorder = Color.Black.copy(alpha = 0.07f),   // was 5% — slightly more card definition
            textPrimary = Color(0xFF1A1A1A),
            textSecondary = Color(0xFF6B635A),
            itemBg = Color(0xFFF5F2EC),                      // slightly lighter than EEE8DC to pass AA for primary on cards
            heatmapRamp = listOf(
                Color(0xFFE8E0D0),
                Color(0xFFD4A800).copy(alpha = 0.35f),
                Color(0xFFBF9000).copy(alpha = 0.60f),
                Color(0xFF8B6700).copy(alpha = 0.80f),
                Color(0xFF8B6700)
            )
        )
        HoneyJarThemeType.LightMinimal -> HoneyJarColors(
            accent = Color(0xFF0062D6),
            accentGlow = Color(0xFF0062D6).copy(alpha = 0.12f),
            heroGradient = Brush.radialGradient(listOf(Color(0xFFDCEEFF), Color(0xFFB8D9FF))),  // more visible than before
            glassBorder = Color.Black.copy(alpha = 0.06f),   // was 4% — slightly more card definition
            textPrimary = Color(0xFF1C1E21),
            textSecondary = Color(0xFF65676B),
            itemBg = Color(0xFFEBF0F7),                      // was F0F2F5 — slightly more blue tint to match new primary
            heatmapRamp = listOf(
                Color(0xFFE0EAF5),
                Color(0xFF90BAE8),
                Color(0xFF4D95D8),
                Color(0xFF0F6FC6),
                Color(0xFF0062D6)
            )
        )
    }

    CompositionLocalProvider(LocalHoneyJarColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
