package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ============================================================================
 * SHAPE + SPACING TOKENS
 * ============================================================================
 * The repo shipped 16 distinct corner radii and 15 one-off padding values, so
 * nothing lined up. Six radii cover every surface the app actually draws; new
 * screens should reach for one of these instead of a fresh number.
 */
val VippattiShapes = Shapes(
  extraSmall = RoundedCornerShape(4.dp),   // chips, inline tags
  small = RoundedCornerShape(8.dp),        // buttons, status bands
  medium = RoundedCornerShape(10.dp),      // compact cards, list rows
  large = RoundedCornerShape(12.dp),       // cards, sheets, dialogs
  extraLarge = RoundedCornerShape(16.dp),  // hero / full-bleed surfaces
)

/** The full-rounded pill used by badges and selected nav pills. */
val VippattiPill = RoundedCornerShape(percent = 50)

/**
 * 4dp spacing scale. `Space.md` is the default gap between cards, which is what
 * most of the previously scattered 12/14/16dp paddings were reaching for.
 */
object VippattiSpace {
  val xxs: Dp = 2.dp
  val xs: Dp = 4.dp
  val sm: Dp = 8.dp
  val md: Dp = 12.dp
  val lg: Dp = 16.dp
  val xl: Dp = 24.dp
  val xxl: Dp = 32.dp

  /** WCAG 2.5.5 minimum touch target for anything the user has to hit. */
  val minTouchTarget: Dp = 48.dp
}
