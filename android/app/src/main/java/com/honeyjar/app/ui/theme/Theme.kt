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
        glassBorder = Color.White.copy(alpha = 0.1f),
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
    primary = Color(0xFF7C4DFF),
    secondary = Color(0xFF311B92),
    background = MidnightBg,
    surface = Color(0xFF1A1B24),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightCreamColorScheme = lightColorScheme(
    primary = Color(0xFFD49A00),
    secondary = Color(0xFFA67C00),
    background = CreamBg,
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.Black,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A)
)

private val LightMinimalColorScheme = lightColorScheme(
    primary = Color(0xFF0076FF),
    secondary = Color(0xFF42A5F5),
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
            glassBorder = Color.White.copy(alpha = 0.1f),
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
            accent = Color(0xFF7C4DFF),
            accentGlow = Color(0xFF7C4DFF).copy(alpha = 0.3f),
            heroGradient = Brush.radialGradient(listOf(Color(0xFF7C4DFF), Color(0xFF311B92))),
            glassBorder = Color(0xFF7C4DFF).copy(alpha = 0.1f),
            textPrimary = Color.White,
            textSecondary = Color(0xFF9DA3AF),
            itemBg = Color(0xFF7C4DFF).copy(alpha = 0.12f),
            heatmapRamp = listOf(
                Color(0xFF7C4DFF).copy(alpha = 0.10f),
                Color(0xFF7C4DFF).copy(alpha = 0.25f),
                Color(0xFF7C4DFF).copy(alpha = 0.50f),
                Color(0xFF7C4DFF).copy(alpha = 0.78f),
                Color(0xFF7C4DFF)
            )
        )
        HoneyJarThemeType.LightCream -> HoneyJarColors(
            accent = Color(0xFFD49A00),
            accentGlow = Color(0xFFD49A00).copy(alpha = 0.1f),
            heroGradient = Brush.radialGradient(listOf(Color(0xFFFFF5E6), Color(0xFFFEF0D7))),
            glassBorder = Color.Black.copy(alpha = 0.05f),
            textPrimary = Color(0xFF1A1A1A),
            textSecondary = Color(0xFF6B635A),
            itemBg = Color(0xFFF5F0E8),
            heatmapRamp = listOf(
                Color(0xFFEDE8DF),
                Color(0xFFFFE082),
                Color(0xFFFFCA28),
                Color(0xFFD49A00),
                Color(0xFFA67C00)
            )
        )
        HoneyJarThemeType.LightMinimal -> HoneyJarColors(
            accent = Color(0xFF0076FF),
            accentGlow = Color(0xFF0076FF).copy(alpha = 0.1f),
            heroGradient = Brush.radialGradient(listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))),
            glassBorder = Color.Black.copy(alpha = 0.04f),
            textPrimary = Color(0xFF1C1E21),
            textSecondary = Color(0xFF65676B),
            itemBg = Color(0xFFF0F2F5),
            heatmapRamp = listOf(
                Color(0xFFE8EDF2),
                Color(0xFFBBDEFB),
                Color(0xFF64B5F6),
                Color(0xFF1E88E5),
                Color(0xFF0076FF)
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
