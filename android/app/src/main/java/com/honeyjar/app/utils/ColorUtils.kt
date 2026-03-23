package com.honeyjar.app.utils

import androidx.compose.ui.graphics.Color
import android.graphics.Color as AndroidColor

object ColorUtils {
    /**
     * Parses a hex color string into a Compose Color.
     * If parsing fails, returns Color.Transparent or a specified fallback.
     */
    fun parseHexColor(hex: String, fallback: Color = Color.Gray): Color {
        return try {
            Color(AndroidColor.parseColor(hex))
        } catch (e: Exception) {
            fallback
        }
    }
}
