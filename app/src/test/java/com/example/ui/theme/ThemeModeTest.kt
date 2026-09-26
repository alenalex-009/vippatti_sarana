package com.example.ui.theme

import com.example.viewmodel.VippattiViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Appearance preference: resolution, persistence and the no-duplicate-guard.
 *
 * These cover the previously-broken behaviour, where the theme toggle lived only
 * in Compose/ViewModel memory and silently reset to Light on every process
 * restart.
 */
class ThemeModeTest {

  // --- Resolution ------------------------------------------------------------

  @Test
  fun `explicit modes ignore the system setting`() {
    assertTrue(ThemeMode.DARK.isDark(systemPrefersDark = false))
    assertFalse(ThemeMode.LIGHT.isDark(systemPrefersDark = true))
  }

  @Test
  fun `system mode follows the device setting`() {
    assertTrue(ThemeMode.SYSTEM.isDark(systemPrefersDark = true))
    assertFalse(ThemeMode.SYSTEM.isDark(systemPrefersDark = false))
  }

  @Test
  fun `corrupt or absent stored value falls back to system instead of throwing`() {
    assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
    assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(""))
    assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("NOT_A_MODE"))
    assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("DARK"))
  }

  // --- Persistence -----------------------------------------------------------

  @Test
  fun `selection is written to the store and read back on the next cold start`() {
    val store = InMemoryThemePreferenceStore()
    val firstRun = VippattiViewModel(themePreferences = store)
    firstRun.setThemeMode(ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, firstRun.uiState.value.themeMode)

    // Simulate process death: a brand-new ViewModel over the SAME store, which
    // is what MainActivity does on the next launch.
    val secondRun = VippattiViewModel(themePreferences = store)
    assertEquals(ThemeMode.DARK, secondRun.uiState.value.themeMode)
    assertTrue(secondRun.uiState.value.isDark(systemPrefersDark = false))
  }

  @Test
  fun `light selection also survives a cold start`() {
    val store = InMemoryThemePreferenceStore()
    VippattiViewModel(themePreferences = store).setThemeMode(ThemeMode.LIGHT)
    val secondRun = VippattiViewModel(themePreferences = store)
    assertEquals(ThemeMode.LIGHT, secondRun.uiState.value.themeMode)
    assertFalse(secondRun.uiState.value.isDark(systemPrefersDark = true))
  }

  @Test
  fun `fresh install defaults to system rather than pinning a mode`() {
    val vm = VippattiViewModel(themePreferences = InMemoryThemePreferenceStore())
    assertEquals(ThemeMode.SYSTEM, vm.uiState.value.themeMode)
  }

  // --- Quick toggle parity ---------------------------------------------------

  @Test
  fun `quick toggle cycles system then light then dark and persists each step`() {
    val store = InMemoryThemePreferenceStore()
    val vm = VippattiViewModel(themePreferences = store)

    vm.toggleTheme()
    assertEquals(ThemeMode.LIGHT, vm.uiState.value.themeMode)
    vm.toggleTheme()
    assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
    vm.toggleTheme()
    assertEquals(ThemeMode.SYSTEM, vm.uiState.value.themeMode)

    // The cycle is persisted too, so a restart lands on the last choice.
    assertEquals(ThemeMode.SYSTEM, VippattiViewModel(themePreferences = store).uiState.value.themeMode)
  }

  // --- No duplicate theme architecture ---------------------------------------

  @Test
  fun `derived isDarkTheme reports the pinned mode without shadowing it`() {
    assertTrue(VippattiUiStateWith(ThemeMode.DARK).isDarkTheme)
    assertFalse(VippattiUiStateWith(ThemeMode.LIGHT).isDarkTheme)
    // Under SYSTEM nothing is PINNED, even though the device may be dark.
    assertFalse(VippattiUiStateWith(ThemeMode.SYSTEM).isDarkTheme)
  }

  private fun VippattiUiStateWith(mode: ThemeMode) =
    com.example.viewmodel.VippattiUiState(themeMode = mode)
}
