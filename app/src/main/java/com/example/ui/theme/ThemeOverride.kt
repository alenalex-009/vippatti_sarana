package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ============================================================================
 * REMOTE-CONFIG THEME OVERRIDE (pure, so it is unit-testable without Compose)
 * ============================================================================
 *
 * Remote config can hand us a brand hex. A colour is never a single value in
 * this app: [VippattiColors] pairs each accent with the text and container
 * colours that sit on it. Overriding only the accent — which is what the first
 * cut did — leaves `onPrimary` and the container roles on the base palette, so
 * a dark remote brand kept dark on-brand text and became unreadable.
 *
 * [apply] therefore re-derives the whole quartet for the accent it is given and
 * validates the incoming hex instead of trusting `Color.parseColor` not to throw.
 */
object ThemeOverride {

  /** Strict `#RRGGBB` / `#AARRGGBB`. Returns null for anything else. */
  fun parseHex(raw: String?): Color? {
    val s = raw?.trim()?.removePrefix("#") ?: return null
    if (s.isEmpty()) return null
    if (!(s.length == 6 || s.length == 8)) return null
    if (!s.all { it in "0123456789abcdefABCDEF" }) return null
    val rgb = s.substring(s.length - 6)
    val alpha = if (s.length == 8) s.substring(0, 2).toInt(16) / 255f else 1f
    return Color(
      red = rgb.substring(0, 2).toInt(16) / 255f,
      green = rgb.substring(2, 4).toInt(16) / 255f,
      blue = rgb.substring(4, 6).toInt(16) / 255f,
      alpha = alpha
    )
  }

  /** WCAG relative luminance of a colour, sRGB -> linear. */
  fun luminance(c: Color): Float {
    fun lin(v: Float) =
      if (v <= 0.04045f) v / 12.92f else Math.pow(((v + 0.055) / 1.055).toDouble(), 2.4).toFloat()
    return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
  }

  /** WCAG contrast ratio between two opaque colours. */
  fun contrast(a: Color, b: Color): Float {
    val la = luminance(a)
    val lb = luminance(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
  }

  /** Blend [tint] over [base] at [amount] (0..1) — a cheap tonal step. */
  fun blend(base: Color, tint: Color, amount: Float): Color = Color(
    red = base.red + (tint.red - base.red) * amount,
    green = base.green + (tint.green - base.green) * amount,
    blue = base.blue + (tint.blue - base.blue) * amount,
    alpha = 1f
  )

  /**
   * The most readable of [candidates] on [background]; ties favour the earlier
   * candidate so the house ink wins when both clear AA.
   */
  fun bestOn(background: Color, candidates: List<Color>): Color =
    candidates.maxByOrNull { contrast(it, background) } ?: candidates.first()

  /**
   * Returns a palette whose primary/secondary accents (and every role derived
   * from them) come from remote config. Null or malformed values fall back to
   * [base], so a bad push can never white-out the app.
   */
  fun apply(
    base: VippattiColors,
    primaryHex: String?,
    secondaryHex: String?,
    dark: Boolean
  ): VippattiColors {
    val inks =
      if (dark) listOf(base.tacticalOnSurface, Color(0xFF003822)) // light ink, dark ink
      else listOf(base.tacticalOnSurface, Color(0xFFFFFFFF))      // dark ink, light ink
    val primary = parseHex(primaryHex) ?: return applySecondary(base, secondaryHex, dark, inks)
    val primaryContainer =
      if (dark) blend(base.obsidianContainer, primary, 0.28f)
      else blend(base.obsidianContainerLow, primary, 0.22f)
    val base2 = base.copy(
      neonEmerald = primary,
      onNeonEmerald = bestOn(primary, inks),
      neonEmeraldContainer = primaryContainer,
      onNeonEmeraldContainer = bestOn(primaryContainer, inks)
    )
    return applySecondary(base2, secondaryHex, dark, inks)
  }

  private fun applySecondary(
    base: VippattiColors,
    secondaryHex: String?,
    dark: Boolean,
    inks: List<Color>
  ): VippattiColors {
    val secondary = parseHex(secondaryHex) ?: return base
    val container =
      if (dark) blend(base.obsidianContainer, secondary, 0.28f)
      else blend(base.obsidianContainerLow, secondary, 0.22f)
    return base.copy(
      tacticalCyan = secondary,
      onTacticalCyan = bestOn(secondary, inks),
      tacticalCyanContainer = container,
      onTacticalCyanContainer = bestOn(container, inks)
    )
  }
}
