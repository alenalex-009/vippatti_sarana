package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRODUCTION CONTRACT: a remote-config brand colour replaces the whole colour
 * role group it belongs to, or nothing at all.
 *
 * The first cut overrode only `primary`/`secondary` and left `onPrimary` and the
 * container roles on the base palette, so pushing a dark brand hex kept dark
 * on-brand text and produced unreadable buttons. These tests pin the behaviour
 * that the four roles move together and that malformed input degrades to base.
 */
class ThemeOverrideTest {

  private val light = LightVippattiColors
  private val dark = DarkVippattiColors

  @Test
  fun `a malformed or blank hex leaves the palette untouched`() {
    for (bad in listOf("", "   ", "red", "#12345", "gggggg", "#1234567", "0xRRGGBB")) {
      assertEquals("blank/garbage must fall back to base palette",
        light, ThemeOverride.apply(light, bad, bad, dark = false))
      assertEquals(dark, ThemeOverride.apply(dark, bad, bad, dark = true))
    }
  }

  @Test
  fun `a valid hex moves the whole primary role group, not just the accent`() {
    val out = ThemeOverride.apply(light, "#7B1FA2", "", dark = false)
    assertEquals(0xFF7B1FA2.toInt() and 0xFFFFFF, out.neonEmerald.toRgbHex())
    // The roles that were previously left behind must now have followed.
    assertNotEquals(light.onNeonEmerald, out.onNeonEmerald)
    assertNotEquals(light.neonEmeraldContainer, out.neonEmeraldContainer)
    assertNotEquals(light.onNeonEmeraldContainer, out.onNeonEmeraldContainer)
    // And roles belonging to other groups are untouched.
    assertEquals(light.tacticalCyan, out.tacticalCyan)
    assertEquals(light.emergencyRed, out.emergencyRed)
  }

  @Test
  fun `onPrimary is chosen for readability against the pushed brand colour`() {
    // A light brand needs dark ink; a dark brand needs light ink. The old code
    // kept the base ink either way, which is exactly what made buttons unreadable.
    val lightBrand = ThemeOverride.apply(light, "#F3E5F5", "", dark = false)
    val darkBrand = ThemeOverride.apply(light, "#1A237E", "", dark = false)
    assertTrue("light brand must get dark ink",
      ThemeOverride.contrast(lightBrand.onNeonEmerald, lightBrand.neonEmerald) >= 4.5f)
    assertTrue("dark brand must get light ink",
      ThemeOverride.contrast(darkBrand.onNeonEmerald, darkBrand.neonEmerald) >= 4.5f)
    // The base pairing would have failed the light-brand case.
    assertTrue("sanity: the base ink really was wrong for a light brand",
      ThemeOverride.contrast(light.onNeonEmerald, lightBrand.neonEmerald) < 4.5f)
  }

  @Test
  fun `secondary is applied independently of primary`() {
    val onlySecondary = ThemeOverride.apply(light, "", "#0288D1", dark = false)
    assertEquals(0xFF0288D1.toInt() and 0xFFFFFF, onlySecondary.tacticalCyan.toRgbHex())
    assertEquals("primary group must not move", light.neonEmerald, onlySecondary.neonEmerald)
    assertNotEquals("its on/container roles must move", light.onTacticalCyan, onlySecondary.onTacticalCyan)
  }

  @Test
  fun `contrast helper matches the WCAG reference values`() {
    assertEquals(21f, ThemeOverride.contrast(Color.Black, Color.White), 0.01f)
    assertEquals(1f, ThemeOverride.contrast(Color.Red, Color.Red), 0.001f)
    // Mid-grey on white is a well-known ~4.6:1 borderline case.
    val grey = Color(0xFF767676)
    assertTrue("grey on white must sit just above AA", ThemeOverride.contrast(grey, Color.White) >= 4.5f)
  }

  /** Compare on the 24-bit RGB channel, ignoring alpha rounding noise. */
  private fun Color.toRgbHex(): Int = toArgb() and 0x00FFFFFF
}
