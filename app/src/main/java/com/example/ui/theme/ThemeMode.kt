package com.example.ui.theme

/**
 * User-selectable appearance mode.
 *
 * The project already has a full light + dark theme architecture (see
 * [VippattiTheme] and the two palettes in Color.kt). This enum only describes
 * WHICH of those existing palettes the user wants - it does not introduce a
 * second theming system.
 *
 * - [SYSTEM] follows the device setting.
 * - [LIGHT] and [DARK] pin the palette regardless of the device setting.
 *
 * Disaster / hazard severity colours are NOT part of this choice: they keep
 * their fixed semantic meaning in every mode (see Color.kt).
 */
enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK;

  /**
   * Resolves the effective dark flag.
   *
   * @param systemPrefersDark the device's current dark-mode setting; only
   *   consulted for [SYSTEM].
   */
  fun isDark(systemPrefersDark: Boolean): Boolean = when (this) {
    DARK -> true
    LIGHT -> false
    SYSTEM -> systemPrefersDark
  }

  companion object {
    /**
     * Tolerant parser for a persisted value. Anything unrecognised (an older
     * build, a hand-edited preference) falls back to [SYSTEM] rather than
     * throwing, so a corrupt preference can never block app start.
     */
    fun fromStorage(raw: String?): ThemeMode =
      entries.firstOrNull { it.name == raw } ?: SYSTEM
  }
}
