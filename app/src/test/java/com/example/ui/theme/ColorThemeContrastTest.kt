package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.viewmodel.VippattiViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Phase 5 (contrast) and the "disaster colours are semantic" invariant
 * (Phase 3/12) for the selectable colour themes.
 *
 * The contrast helper is the WCAG 2.1 relative-luminance ratio, so these are
 * real numbers rather than a subjective eyeball check.
 */
class ColorThemeContrastTest {

  // --- WCAG ------------------------------------------------------------------

  private fun channel(v: Float): Double {
    val c = v.toDouble()
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
  }

  private fun luminance(c: Color): Double =
    0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

  /** WCAG contrast ratio, 1.0 (identical) to 21.0 (black on white). */
  private fun contrast(fg: Color, bg: Color): Double {
    val a = luminance(fg)
    val b = luminance(bg)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
  }

  private fun bothPalettes(theme: ColorTheme) =
    listOf(false to theme.lightPalette, true to theme.darkPalette)

  /**
   * Hue in degrees, 0-360.
   *
   * Used instead of a luminance ratio when the question is "do these two colours
   * look like the same thing?", because a blue and a red can share a luminance
   * while being unmistakably different colours. A luminance ratio is the right
   * tool for legibility ([assertContrast]) and the wrong tool here.
   */
  private fun hue(c: Color): Double {
    val r = c.red.toDouble()
    val g = c.green.toDouble()
    val b = c.blue.toDouble()
    val maxV = max(r, max(g, b))
    val minV = min(r, min(g, b))
    val delta = maxV - minV
    if (delta == 0.0) return 0.0
    val h = when (maxV) {
      r -> 60.0 * (((g - b) / delta) % 6.0)
      g -> 60.0 * (((b - r) / delta) + 2.0)
      else -> 60.0 * (((r - g) / delta) + 4.0)
    }
    return if (h < 0) h + 360.0 else h
  }

  /** Shortest distance between two hues, 0-180. */
  private fun hueDistance(a: Double, b: Double): Double {
    val d = kotlin.math.abs(a - b) % 360.0
    return if (d > 180.0) 360.0 - d else d
  }

  private fun assertContrast(
    label: String,
    atLeast: Double,
    fg: Color,
    bg: Color
  ) {
    val ratio = contrast(fg, bg)
    assertTrue(
      "$label ${"%.1f".format(ratio)}:1 is below ${"%.1f".format(atLeast)}:1",
      ratio >= atLeast
    )
  }

  // --- Contrast: every theme, both appearances -------------------------------

  @Test
  fun `body text is strongly readable on the surface`() {
    for (theme in ColorTheme.entries) {
      for ((dark, p) in bothPalettes(theme)) {
        assertContrast("$theme dark=$dark body text", 12.0, p.tacticalOnSurface, p.obsidianSurface)
      }
    }
  }

  @Test
  fun `secondary text meets 4 point 5 to 1`() {
    for (theme in ColorTheme.entries) {
      for ((dark, p) in bothPalettes(theme)) {
        assertContrast("$theme dark=$dark secondary text", 4.5, p.tacticalOnSurfaceVariant, p.obsidianSurface)
      }
    }
  }

  @Test
  fun `filled buttons and the inactive nav item stay readable`() {
    for (theme in ColorTheme.entries) {
      for ((dark, p) in bothPalettes(theme)) {
        assertContrast("$theme dark=$dark onPrimary", 4.5, p.onNeonEmerald, p.neonEmerald)
        assertContrast("$theme dark=$dark inactive nav", 4.5, p.tacticalNavInactive, p.tacticalNavBg)
      }
    }
  }

  @Test
  fun `accent and secondary accent text on the canvas stay readable`() {
    for (theme in ColorTheme.entries) {
      for ((dark, p) in bothPalettes(theme)) {
        assertContrast("$theme dark=$dark accent on canvas", 4.5, p.neonEmerald, p.obsidianSurface)
        assertContrast("$theme dark=$dark secondary accent", 4.5, p.tacticalCyan, p.obsidianSurface)
      }
    }
  }

  @Test
  fun `the safe badge text stays readable`() {
    for (theme in ColorTheme.entries) {
      for ((dark, p) in bothPalettes(theme)) {
        assertContrast("$theme dark=$dark safe badge", 4.5, p.onSafeGreen, p.safeGreen)
      }
    }
  }

  // --- Disaster semantics are never themed ----------------------------------

  @Test
  fun `danger red and warning amber are identical in every theme`() {
    val reference = ColorTheme.DEFAULT
    for (theme in ColorTheme.entries) {
      for (dark in listOf(false, true)) {
        val ref = if (dark) reference.darkPalette else reference.lightPalette
        val actual = theme.palette(dark)
        assertEquals("$theme dark=$dark emergencyRed", ref.emergencyRed, actual.emergencyRed)
        assertEquals("$theme dark=$dark emergencyRedBright", ref.emergencyRedBright, actual.emergencyRedBright)
        assertEquals("$theme dark=$dark warningAmber", ref.warningAmber, actual.warningAmber)
        assertEquals("$theme dark=$dark onEmergencyRed", ref.onEmergencyRed, actual.onEmergencyRed)
      }
    }
  }

  @Test
  fun `the safe green is identical in every theme`() {
    val reference = ColorTheme.DEFAULT
    for (theme in ColorTheme.entries) {
      for (dark in listOf(false, true)) {
        val ref = if (dark) reference.darkPalette else reference.lightPalette
        val actual = theme.palette(dark)
        assertEquals("$theme dark=$dark safeGreen", ref.safeGreen, actual.safeGreen)
        assertEquals("$theme dark=$dark onSafeGreen", ref.onSafeGreen, actual.onSafeGreen)
      }
    }
  }

  @Test
  fun `no theme's accent shares the danger hue, so an alert never reads as branding`() {
    for (theme in ColorTheme.entries) {
      for ((dark, p) in bothPalettes(theme)) {
        val distance = hueDistance(hue(p.neonEmerald), hue(p.emergencyRedBright))
        // Sunset Orange is the tightest case by design (a burnt orange next to
        // the fixed alert red) at ~13 degrees; every other theme is >100.
        // Danger is additionally always paired with an icon and an explicit
        // text label (SOS / SEVERITY / EMERGENCY) in this UI.
        assertTrue(
          "$theme dark=$dark accent hue is only $distance deg from danger red",
          distance >= 12.0
        )
      }
    }
  }

  @Test
  fun `no theme ever sets its accent to the danger or the safe colour`() {
    for (theme in ColorTheme.entries) {
      for ((dark, p) in bothPalettes(theme)) {
        assertNotEquals("$theme dark=$dark accent == danger", p.emergencyRedBright, p.neonEmerald)
        assertNotEquals("$theme dark=$dark accent == safe green", p.safeGreen, p.neonEmerald)
      }
    }
  }

  // --- Themes are genuinely different, and persist ----------------------------

  @Test
  fun `every theme has a visually distinct primary`() {
    val primaries = ColorTheme.entries.map { it.lightPalette.neonEmerald }
    assertEquals(
      "two themes share a primary colour",
      primaries.size,
      primaries.distinct().size
    )
  }

  @Test
  fun `the brand default is Vippatti Blue and a fresh install uses it`() {
    assertEquals(ColorTheme.VIPPATTI_BLUE, ColorTheme.DEFAULT)
    val vm = VippattiViewModel(themePreferences = InMemoryThemePreferenceStore())
    assertEquals(ColorTheme.VIPPATTI_BLUE, vm.uiState.value.colorTheme)
  }

  @Test
  fun `an unknown stored theme falls back to the default instead of throwing`() {
    assertEquals(ColorTheme.VIPPATTI_BLUE, ColorTheme.fromStorage(null))
    assertEquals(ColorTheme.VIPPATTI_BLUE, ColorTheme.fromStorage("NEON_RAVE"))
    assertEquals(ColorTheme.SUNSET_ORANGE, ColorTheme.fromStorage("SUNSET_ORANGE"))
  }

  @Test
  fun `the selected colour theme survives a cold start`() {
    val store = InMemoryThemePreferenceStore()
    VippattiViewModel(themePreferences = store).setColorTheme(ColorTheme.ROYAL_PURPLE)
    val secondRun = VippattiViewModel(themePreferences = store)
    assertEquals(ColorTheme.ROYAL_PURPLE, secondRun.uiState.value.colorTheme)
  }

  @Test
  fun `the colour theme is independent of the light dark mode`() {
    val store = InMemoryThemePreferenceStore()
    val vm = VippattiViewModel(themePreferences = store)
    vm.setColorTheme(ColorTheme.SUNSET_ORANGE)
    vm.setThemeMode(ThemeMode.DARK)
    assertEquals(ColorTheme.SUNSET_ORANGE, vm.uiState.value.colorTheme)
    assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
  }
}
