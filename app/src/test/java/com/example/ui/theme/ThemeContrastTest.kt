package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRODUCTION CONTRACT: the data-status pill and the bottom navigation are
 * readable by everyone, in both themes, on every surface they can land on.
 *
 * The status colours used to be borrowed from the map-overlay accents, which are
 * tuned for large areas and glows, never for 12sp bold text. Measured against the
 * pill they actually paint into, light mode shipped HISTORICAL at 1.19:1 and STALE
 * at 2.84:1 — effectively invisible to a low-vision user trying to tell live data
 * from archived data during an emergency.
 *
 * These assertions read the real [DarkVippattiColors] / [LightVippattiColors]
 * objects, so a future hex edit that breaks contrast fails the build instead of
 * shipping. `ContrastRatio` is deliberately local rather than reusing
 * [ThemeOverride.contrast], so the checker cannot share a bug with the thing it checks.
 */
class ThemeContrastTest {

  /** WCAG 2.x contrast ratio, computed independently of the app's own helper. */
  private fun contrast(a: Color, b: Color): Double {
    fun lin(v: Float): Double {
      val x = v.toDouble()
      return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
    }
    fun lum(c: Color): Double =
      0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    val la = lum(a)
    val lb = lum(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
  }

  /** `Color(alpha = 0.9f)` over an opaque background, as Compose would composite it. */
  private fun over(fg: Color, bg: Color, alpha: Float): Color = Color(
    red = (alpha * fg.red + (1 - alpha) * bg.red),
    green = (alpha * fg.green + (1 - alpha) * bg.green),
    blue = (alpha * fg.blue + (1 - alpha) * bg.blue),
    alpha = 1f
  )

  private fun assertAtLeast(label: String, ratio: Double, floor: Double) {
    assertTrue("$label needs >=$floor:1 for AA but measured ${"%.2f".format(ratio)}:1", ratio >= floor)
  }

  /** Every surface a status pill can plausibly be drawn on, worst case wins. */
  private fun cardBackgrounds(p: VippattiColors): List<Pair<String, Color>> = listOf(
    "obsidianSurface" to p.obsidianSurface,
    "containerLowest" to p.obsidianContainerLowest,
    "containerLow" to p.obsidianContainerLow,
    "containerHigh" to p.obsidianContainerHigh,
    "containerHighest" to p.obsidianContainerHighest,
    "obsidianBright" to p.obsidianBright,
  )

  private fun checkStatusPills(name: String, p: VippattiColors) {
    val tokens = listOf(
      "statusSuccessText" to p.statusSuccessText,
      "statusStaleText" to p.statusStaleText,
      "statusErrorText" to p.statusErrorText,
      "statusHistoricalText" to p.statusHistoricalText,
      "tacticalOnSurfaceVariant (LOADING/EMPTY)" to p.tacticalOnSurfaceVariant,
    )
    for ((tokenName, ink) in tokens) {
      var worst = Double.MAX_VALUE
      var worstOn = ""
      for ((bgName, bg) in cardBackgrounds(p)) {
        // Provenance.StatusBadge paints ObsidianContainer at 90% under the text.
        val pill = over(p.obsidianContainer, bg, 0.9f)
        val r = contrast(ink, pill)
        if (r < worst) { worst = r; worstOn = bgName }
      }
      assertAtLeast("$name .$tokenName on status pill (worst: $worstOn)", worst, 4.5)
    }
  }

  @Test
  fun `dark status pill text meets WCAG AA on every surface`() =
    checkStatusPills("DarkVippattiColors", DarkVippattiColors)

  @Test
  fun `light status pill text meets WCAG AA on every surface`() =
    checkStatusPills("LightVippattiColors", LightVippattiColors)

  @Test
  fun `bottom nav labels meet WCAG AA in both themes`() {
    assertAtLeast(
      "DarkVippattiColors.tacticalNavInactive on nav bar",
      contrast(DarkVippattiColors.tacticalNavInactive, DarkVippattiColors.tacticalNavBg), 4.5
    )
    assertAtLeast(
      "LightVippattiColors.tacticalNavInactive on nav bar",
      contrast(LightVippattiColors.tacticalNavInactive, LightVippattiColors.tacticalNavBg), 4.5
    )
    assertAtLeast(
      "DarkVippattiColors selected label on nav bar",
      contrast(DarkVippattiColors.tacticalOnSurface, DarkVippattiColors.tacticalNavBg), 4.5
    )
    assertAtLeast(
      "LightVippattiColors selected label on nav bar",
      contrast(LightVippattiColors.tacticalOnSurface, LightVippattiColors.tacticalNavBg), 4.5
    )
  }

  @Test
  fun `the previously shipped colours really did fail - this test has teeth`() {
    // Regression proof: these are the exact hex values that shipped before the fix,
    // measured on the same pill they were drawn on. If a future edit "restores" them,
    // the AA assertions above will fail again.
    val pillLight = over(Color(0xFFEDF3EF), Color(0xFFF2F7F4), 0.9f)
    assertTrue("HISTORICAL outlineVariant must still be a contrast failure",
      contrast(Color(0xFFD3E2D9), pillLight) < 3.0)
    assertTrue("STALE warningAmber must still be a contrast failure",
      contrast(Color(0xFFD97706), pillLight) < 4.5)
    val pillDark = over(Color(0xFF1E2023), Color(0xFF111316), 0.9f)
    assertTrue("HISTORICAL dark outlineVariant must still fail",
      contrast(Color(0xFF3B4A41), pillDark) < 3.0)
    assertTrue("light nav inactive #5F7A6C must still fail on the nav bar",
      contrast(Color(0xFF5F7A6C), Color(0xFFF6FAF7)) < 4.5)
  }
}
