package com.honeyjar.app.data

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.honeyjar.app.ui.theme.HoneyJarThemeType

object ThemePrefs {
    private const val PREFS_NAME = "honeyjar_theme_prefs"
    private const val KEY_THEME = "active_theme"
    
    private val _theme = mutableStateOf(HoneyJarThemeType.DarkHoney)
    val theme: State<HoneyJarThemeType> = _theme
    
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(KEY_THEME, HoneyJarThemeType.DarkHoney.name)
        _theme.value = try {
            HoneyJarThemeType.valueOf(savedTheme ?: HoneyJarThemeType.DarkHoney.name)
        } catch (e: Exception) {
            HoneyJarThemeType.DarkHoney
        }
    }
    
    fun setTheme(context: Context, newTheme: HoneyJarThemeType) {
        _theme.value = newTheme
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, newTheme.name).apply()
    }
}
