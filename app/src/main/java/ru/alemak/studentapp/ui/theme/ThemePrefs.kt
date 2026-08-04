package ru.alemak.studentapp.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Persists dark/light for next cold start.
 * No activity-alias switching — LauncherLight/Dark caused "does not exist" crashes.
 */
object ThemePrefs {
    private const val PREFS = "splash_theme"
    private const val KEY_DARK = "dark_theme"

    fun isDark(context: Context): Boolean {
        val ctx = context.applicationContext ?: context
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, false)
    }

    fun setDark(context: Context, dark: Boolean) {
        val ctx = context.applicationContext ?: context
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, dark)
            .commit()
    }

    fun applyNightModeFromPrefs(context: Context) {
        val dark = isDark(context)
        val mode = if (dark) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
