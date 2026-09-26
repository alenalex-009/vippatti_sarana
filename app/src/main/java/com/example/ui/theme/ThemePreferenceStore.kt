package com.example.ui.theme

import android.content.SharedPreferences

/**
 * Persistence for the user's [ThemeMode] choice.
 *
 * Production uses [SharedPreferencesThemePreferenceStore], which is the same
 * local-storage mechanism the app already uses for the onboarding completion
 * flag and the auth session - no new storage dependency or architecture.
 *
 * The stored value is read once when the ViewModel is created and written on
 * every change, so the selection survives navigation, Activity recreation and
 * process death.
 */
interface ThemePreferenceStore {
  fun load(): ThemeMode
  fun save(mode: ThemeMode)

  /** The user-selected brand colour theme. */
  fun loadColorTheme(): ColorTheme
  fun saveColorTheme(theme: ColorTheme)
}

/** SharedPreferences-backed store used by the app. */
class SharedPreferencesThemePreferenceStore(
  private val prefs: SharedPreferences
) : ThemePreferenceStore {

  override fun load(): ThemeMode =
    ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, null))

  override fun save(mode: ThemeMode) {
    prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
  }

  override fun loadColorTheme(): ColorTheme =
    ColorTheme.fromStorage(prefs.getString(KEY_COLOR_THEME, null))

  override fun saveColorTheme(theme: ColorTheme) {
    prefs.edit().putString(KEY_COLOR_THEME, theme.name).apply()
  }

  companion object {
    const val PREFS_NAME = "vippatti_appearance"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_COLOR_THEME = "color_theme"
  }
}

/**
 * Non-persistent store used as the ViewModel default so plain JVM unit tests
 * need no Android Context. Behaves like a real store within one instance.
 */
class InMemoryThemePreferenceStore(
  initial: ThemeMode = ThemeMode.SYSTEM,
  initialColorTheme: ColorTheme = ColorTheme.DEFAULT
) : ThemePreferenceStore {

  private var current: ThemeMode = initial
  private var currentColorTheme: ColorTheme = initialColorTheme

  override fun load(): ThemeMode = current

  override fun save(mode: ThemeMode) {
    current = mode
  }

  override fun loadColorTheme(): ColorTheme = currentColorTheme

  override fun saveColorTheme(theme: ColorTheme) {
    currentColorTheme = theme
  }
}
