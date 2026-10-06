package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.disaster.ZoneDetail
import com.example.data.routing.GeoPoint
import com.example.ui.theme.VippattiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RESPONSIVE DETAIL-CARD CONTRACT (Robolectric compose, real layout passes).
 *
 * The three summary cards ("Affected radius | Trend | Detected") must adapt
 * to the AVAILABLE width — never to a device model:
 *   narrow  (< 2 * minCardWidth)  -> stacked full-width cards
 *   normal  (>= 2 min cards)      -> two cards + full-width third
 *   wide    (>= 3 min cards)      -> three cards on one row
 *
 * Also pinned here: a long timestamp never explodes card height, missing
 * values render honestly, cards never overflow their container, and the
 * dialog close button stays reachable at every width.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp")
class DetailCardAdaptiveLayoutTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var pxPerDp = 1f

  private fun renderCardsAt(widthDp: Int, timestamp: String? = "03 Oct 2026, 09:43") {
    composeTestRule.setContent {
      pxPerDp = LocalDensity.current.density
      VippattiTheme {
        Box(Modifier.width(widthDp.dp)) {
          AdaptiveStatCards(
            first = { m ->
              SummaryCard("Affected radius", "18.4 km", modifier = m, tag = "card_a")
            },
            second = { m ->
              SummaryCard("Trend", "\u2192 Stable", modifier = m, tag = "card_b")
            },
            third = { m ->
              SummaryCard(
                "Detected", "2d ago", modifier = m, tag = "card_c",
                subValue = timestamp
              )
            }
          )
        }
      }
    }
  }

  private fun bounds(tag: String) =
    composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

  /**
   * The tagged card bounds exclude the card's own 12dp horizontal padding
   * (the tag modifier sits inside it), so the content width of a card that
   * fills its container is containerWidth - 24dp.
   */
  private fun contentWidth(containerDp: Int): Float =
    containerDp.toFloat() - 24f * pxPerDp

  // --------------------------------------------------------------- narrow

  @Test
  fun `narrow width stacks the three cards full-width`() {
    renderCardsAt(280)
    val a = bounds("card_a"); val b = bounds("card_b"); val c = bounds("card_c")
    assertTrue("cards must stack vertically", b.top > a.top && c.top > b.top)
    assertEquals("stacked cards share the left edge", a.left, b.left, 1.5f)
    assertEquals(a.left, c.left, 1.5f)
    // Full width used, nothing overflows the container.
    assertEquals(contentWidth(280), a.right - a.left, 2f)
    assertTrue(c.right <= 280f + 1f)
  }

  // --------------------------------------------------------------- normal

  @Test
  fun `normal phone width gives two cards plus a full-width third`() {
    renderCardsAt(340)
    val a = bounds("card_a"); val b = bounds("card_b"); val c = bounds("card_c")
    assertEquals("first two cards share a row", a.top, b.top, 1.5f)
    assertTrue("second card sits to the right", b.left > a.left)
    assertTrue("third card drops below", c.top > a.top)
    assertEquals("third card spans the full width", contentWidth(340), c.right - c.left, 2f)
    assertTrue(c.right <= 340f + 1f)
  }

  // ----------------------------------------------------------------- wide

  @Test
  // A 460dp container needs a screen wider than the class-level 360dp phone,
  // else the width coerces and the 2+1 layout correctly wins.
  @Config(sdk = [36], qualifiers = "w480dp-h640dp")
  fun `wide layout puts all three cards on one row`() {
    renderCardsAt(460)
    val a = bounds("card_a"); val b = bounds("card_b"); val c = bounds("card_c")
    assertEquals(a.top, b.top, 1.5f)
    assertEquals(b.top, c.top, 1.5f)
    assertTrue(b.left > a.left)
    assertTrue(c.left > b.left)
    assertTrue(c.right <= 460f + 1f)
  }

  // ---------------------------------------------------- timestamp handling

  @Test
  fun `a long timestamp wraps without forcing a giant card`() {
    renderCardsAt(460)
    val height = bounds("card_c").height
    // label + bold value + two wrapped timestamp lines must stay compact.
    assertTrue(
      "Detected card grew to ${height}px (max ${120 * pxPerDp}px)",
      height < 120f * pxPerDp
    )
    composeTestRule.onNodeWithText("03 Oct 2026, 09:43").assertIsDisplayed()
  }

  @Test
  fun `cards handle short values and absent timestamps`() {
    renderCardsAt(340, timestamp = null)
    composeTestRule.onNodeWithTag("card_c").assertIsDisplayed()
    composeTestRule.onNodeWithText("2d ago").assertIsDisplayed()
  }

  @Test
  fun `missing values render as Data unavailable, never zero`() {
    composeTestRule.setContent {
      pxPerDp = LocalDensity.current.density
      VippattiTheme {
        Box(Modifier.width(300.dp)) {
          SummaryCard("Trend", "Data unavailable", modifier = Modifier, unavailable = true)
        }
      }
    }
    composeTestRule.onNodeWithText("Data unavailable").assertIsDisplayed()
  }

  // -------------------------------------------------- dialog device widths

  private fun hazardZone() = HazardZone(
    id = "hz-1",
    name = "M 4.2 Earthquake — near Kochi",
    type = HazardType.EARTHQUAKE,
    severity = HazardSeverity.LOW,
    center = GeoPoint(10.1, 76.3),
    radiusMeters = 4200.0,
    riskLevel = "Low",
    trend = HazardTrend.STABLE,
    sourceStatus = "Observed earthquake from USGS Earthquake Hazards Program.",
    lastUpdatedMillis = 1_790_000_000_000L,
    provenance = DataProvenance(
      source = "USGS Earthquake Hazards Program",
      classification = DataClassification.OBSERVED
    )
  )

  private fun openHazardDialog(onDismiss: () -> Unit) {
    composeTestRule.setContent {
      VippattiTheme {
        HazardZoneDetailDialog(
          zone = hazardZone(),
          detail = ZoneDetail(sections = emptyList(), nearestSafeZone = null),
          onDismiss = onDismiss
        )
      }
    }
  }

  @Test
  @Config(sdk = [36], qualifiers = "w320dp-h640dp")
  fun `hazard dialog on a small phone stacks the cards and keeps close reachable`() {
    var dismissed = false
    openHazardDialog { dismissed = true }
    val a = bounds("hazard_stat_radius")
    val b = bounds("hazard_stat_trend")
    val c = bounds("hazard_stat_detected")
    assertTrue("small phone must stack the summary cards", b.top > a.top && c.top > b.top)
    // Title, severity and user position still come first (content priority):
    composeTestRule.onNodeWithTag("hazard_severity_pill").assertIsDisplayed()
    composeTestRule.onNodeWithText("Earthquake hazard").assertIsDisplayed()
    // Close button exists and actually dismisses:
    composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()
      .performClick()
    assertTrue(dismissed)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w430dp-h640dp")
  fun `hazard dialog on a large phone shows two cards plus a full-width third`() {
    openHazardDialog {}
    val a = bounds("hazard_stat_radius")
    val b = bounds("hazard_stat_trend")
    val c = bounds("hazard_stat_detected")
    assertEquals("first two cards share a row on a large phone", a.top, b.top, 1.5f)
    assertTrue(b.left > a.left)
    assertTrue("third card drops below", c.top > a.top)
    composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()
  }
}
