package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Vippatti Sarana type scale.
 *
 * RULES (design-system pass):
 *  - the smallest text in the app is 12 sp so emergency information stays
 *    readable in rain, glare and at arm's length;
 *  - `display*` / `headlineSmall` carry the tactical screen headers;
 *  - `title*` carry card and section titles;
 *  - `body*` carry readable prose;
 *  - `label*` carry chips, tags and metric captions.
 *
 * Screens should read from `MaterialTheme.typography` (or the semantic helpers
 * in [VippattiText]) instead of the ad-hoc inline `fontSize = 11.sp` literals the
 * codebase accumulated.
 */
private val Sans = FontFamily.Default

val Typography = Typography(
  displayLarge = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Black,
    fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 0.sp
  ),
  displayMedium = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Black,
    fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.2.sp
  ),
  displaySmall = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Black,
    fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.4.sp
  ),
  headlineSmall = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Bold,
    fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.4.sp
  ),
  titleLarge = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Bold,
    fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp
  ),
  titleMedium = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Bold,
    fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp
  ),
  titleSmall = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp
  ),
  bodyLarge = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Normal,
    fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp
  ),
  bodyMedium = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Normal,
    fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp
  ),
  bodySmall = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Normal,
    fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp
  ),
  labelLarge = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Bold,
    fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp
  ),
  labelMedium = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Bold,
    fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
  ),
  /** Chip / metric caption / overline labels. Never below 12 sp. */
  labelSmall = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Bold,
    fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.5.sp
  )
)

/**
 * Semantic helpers for the recurring roles on the tactical screens, so no
 * screen hand-rolls a TextStyle for a section header or a metric caption.
 */
object VippattiText {
  /** Small uppercase overline above a value ("DESTINATION", "3-HR TREND"). */
  val overline: TextStyle
    get() = Typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)

  /** Secondary explanatory sentence under a heading. */
  val caption: TextStyle
    get() = Typography.bodySmall

  /** Metric value inside a tile. */
  val metric: TextStyle
    get() = Typography.labelLarge

  /** Uppercase button / CTA label. */
  val action: TextStyle
    get() = Typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 0.4.sp)

  /** Absolute floor for any text in the app. */
  const val MIN_FONT_SP = 12
}
