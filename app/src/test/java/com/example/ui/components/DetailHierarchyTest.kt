package com.example.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.disaster.NearestViableSafeZone
import com.example.data.disaster.ZoneDetail
import com.example.data.disaster.ZoneDetailField
import com.example.data.disaster.ZoneDetailSection
import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.ui.theme.VippattiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Detail-card design contracts (approved reference redesign):
 *  - hazard sheet opens COMPACT (icon / "{type} hazard" / severity pill / three
 *    summary cards / nearest-safe-zone / area centre); technical sections and
 *    provenance live behind the expandable details toggle;
 *  - the compact view never repeats demo/simulation labels (the global demo
 *    pill owns that signal); classification appears once, when expanded;
 *  - missing values render "Data unavailable" / "Not assessed" - never 0 or
 *    fabricated text;
 *  - the nearest-safe-zone card navigates to the EXACT record id;
 *  - safe-zone sheet: capacity gauge + quick status + limiting-factor row +
 *    route CTA visible in the compact state; key info / resources /
 *    feasibility / methodology only after "Show full details".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp")
class DetailHierarchyTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun hazardZone(simulated: Boolean) = HazardZone(
    id = "hz-1",
    name = "Brahmaputra Flood Pocket",
    type = HazardType.FLOOD,
    severity = HazardSeverity.EXTREME,
    center = GeoPoint(27.48, 94.91),
    radiusMeters = 1800.0,
    riskLevel = "Extreme",
    trend = HazardTrend.WORSENING,
    sourceStatus = if (simulated) "DEMO DATA - simulated" else "LIVE - IMD CAP",
    lastUpdatedMillis = 0L,
    provenance = DataProvenance(
      source = "test",
      classification = if (simulated) DataClassification.SIMULATED
        else DataClassification.OBSERVED
    )
  )

  private fun zoneDetail(nearest: NearestViableSafeZone? = null) = ZoneDetail(
    sections = listOf(
      ZoneDetailSection(
        heading = "FLOOD HAZARD DATA",
        fields = listOf(
          ZoneDetailField("Water level", "4.2 m"),
          ZoneDetailField("Rainfall (24h)", null)
        )
      )
    ),
    nearestSafeZone = nearest
  )

  private fun safeZone() = SafeZone(
    id = "sz-1", name = "Community Hall",
    lat = 27.49, lon = 94.92, locationNote = "SIMULATED record",
    capacityTotal = 500, capacityCurrent = 230,
    waterAvailable = true, foodAvailable = true, electricityAvailable = true,
    sanitationAvailable = false, medicalSupport = true, accessibility = "Road",
    womenChildrenSuitability = true, operatingStatus = "OPEN",
    verificationStatus = "SIMULATED - not a verified shelter", // model field stays SIMULATED
    elevationNote = "",
    provenance = DataProvenance(
      source = "test", classification = DataClassification.SIMULATED
    )
  )

  @Test
  fun `hazard sheet opens compact - expand reveals technical data`() {
    composeTestRule.setContent {
      VippattiTheme {
        HazardZoneDetailDialog(zone = hazardZone(true), detail = zoneDetail(), onDismiss = {})
      }
    }
    // Compact summary cards are visible immediately (real record values):
    composeTestRule.onNodeWithTag("hazard_stat_radius").assertIsDisplayed()
    composeTestRule.onNodeWithText("1.8 km").assertIsDisplayed()
    composeTestRule.onNodeWithTag("hazard_stat_trend").assertIsDisplayed()
    // The Detected tile has no timestamp in the record -> honest unavailable,
    // never a fabricated age:
    composeTestRule.onNodeWithTag("hazard_stat_detected")
      .assertExists() // tag on card; content read below
    composeTestRule.onNodeWithText("Data unavailable").assertExists()
    // TERTIARY collapsed: technical fields and severity row not composed yet:
    composeTestRule.onNodeWithText("Water level").assertDoesNotExist()
    composeTestRule.onNodeWithTag("hazard_stat_severity").assertDoesNotExist()
    // Expand reveals exactly what was hidden:
    composeTestRule.onNodeWithTag("hazard_details_toggle").performClick()
    composeTestRule.onNodeWithText("Water level").assertExists()
    composeTestRule.onNodeWithText("Rainfall (24h)").assertExists()
    composeTestRule.onNodeWithTag("hazard_stat_severity").assertExists()
  }

  @Test
  fun `compact hazard view never repeats demo labels`() {
    composeTestRule.setContent {
      VippattiTheme {
        HazardZoneDetailDialog(zone = hazardZone(true), detail = zoneDetail(), onDismiss = {})
      }
    }
    // Spec 20: one global demo signal - no DEMO/simulated wording in the
    // compact hazard card at all.
    composeTestRule.onNodeWithText("DEMO - demonstration only").assertDoesNotExist()
    composeTestRule.onNodeWithText("Demo data").assertDoesNotExist()
    composeTestRule.onNodeWithText("Simulated", substring = true).assertDoesNotExist()
    // Provenance appears exactly once, only inside expanded details:
    composeTestRule.onNodeWithTag("hazard_details_toggle").performClick()
    composeTestRule.onNodeWithText("SOURCE & DETAILS").assertExists()
  }

  @Test
  fun `nearest safe-zone card navigates to the exact record id`() {
    var opened: String? = null
    composeTestRule.setContent {
      VippattiTheme {
        HazardZoneDetailDialog(
          zone = hazardZone(false),
          detail = zoneDetail(
            nearest = NearestViableSafeZone(
              name = "Community Hall",
              distanceText = "1.4 km",
              capacityText = "270 spaces free",
              id = "sz-1"
            )
          ),
          onDismiss = {},
          onOpenSafeZone = { opened = it }
        )
      }
    }
    composeTestRule.onNodeWithText("Community Hall").assertExists()
    composeTestRule.onNodeWithText("1.4 km \u2022 270 spaces free").assertExists()
    composeTestRule.onNodeWithTag("nearest_safe_zone_card").performClick()
    // The callback receives THIS record's id - never a stand-in zone.
    org.junit.Assert.assertEquals("sz-1", opened)
  }

  @Test
  fun `safe-zone sheet opens with result-first compact view`() {
    composeTestRule.setContent {
      VippattiTheme {
        SafeZoneDetailDialog(
          zone = safeZone(),
          evaluation = null,
          onDismiss = {},
          onSelectAndRoute = {}
        )
      }
    }
    // PRIMARY capacity result + one concise provenance pill:
    composeTestRule.onNodeWithText("270 / 500").assertIsDisplayed()
    composeTestRule.onNodeWithText("54% available").assertIsDisplayed()
    composeTestRule.onNodeWithText("Demo data").assertIsDisplayed()
    // Quick status: distance unknown (no evaluation) => honest unavailable.
    composeTestRule.onNodeWithTag("safe_zone_stat_distance").assertExists()
    composeTestRule.onNodeWithText("Open").assertExists()
    // No assessment => no fabricated limiting factor (tag absent, text honest):
    composeTestRule.onNodeWithTag("capacity_limiter_line").assertDoesNotExist()
    composeTestRule.onNodeWithTag("limiting_factor_row").assertIsDisplayed()
    // TERTIARY hidden until expanded:
    composeTestRule.onNodeWithText("KEY INFORMATION").assertDoesNotExist()
    composeTestRule.onNodeWithText("FACILITY RESOURCES").assertDoesNotExist()
    composeTestRule.onNodeWithText("RELOCATION FEASIBILITY").assertDoesNotExist()
    // Expand reveals it (nothing deleted, only collapsed):
    composeTestRule.onNodeWithTag("safe_zone_details_toggle").performClick()
    composeTestRule.onNodeWithText("KEY INFORMATION").assertExists()
    composeTestRule.onNodeWithText("FACILITY RESOURCES").assertExists()
    composeTestRule.onNodeWithText("RELOCATION FEASIBILITY").assertExists()
    composeTestRule.onNodeWithText("DATA & METHODOLOGY").assertExists()
    // Route action still exists (exact destination wiring is a VM contract):
    composeTestRule.onNodeWithTag("safe_zone_route_button").assertExists()
  }

  @Test
  fun `resource flags are never shown as verified quantities`() {
    composeTestRule.setContent {
      VippattiTheme {
        SafeZoneDetailDialog(
          zone = safeZone(), evaluation = null,
          onDismiss = {}, onSelectAndRoute = {}
        )
      }
    }
    composeTestRule.onNodeWithTag("safe_zone_details_toggle").performClick()
    // The test zone flags water ON but carries no litres/day figure => the
    // row must say the quantity is not provided, not imply verified supply.
    composeTestRule.onNodeWithText("Water supply").assertExists()
    composeTestRule.onNodeWithText("Food supply").assertExists()
    // A flagged resource with no quantity must carry the honest qualifier...
    composeTestRule.onAllNodesWithText(
      "Available — quantity not provided", substring = true
    ).assertCountEquals(5) // water/food/power/medical/women flagged, no figures
    // ...and a false flag renders "Not available", never 0:
    composeTestRule.onAllNodesWithText("Not available").assertCountEquals(1)
    // No row may present a bare unqualified "Available" as verified:
    composeTestRule.onAllNodesWithText("Available").assertCountEquals(0)
  }
}
