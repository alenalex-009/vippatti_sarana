package com.example.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * Phase 5 information-hierarchy contracts (rule 22: primary visible first,
 * tertiary collapsed but never deleted):
 *  - hazard dialog opens with verdict + key facts VISIBLE and the technical
 *    sections COLLAPSED behind "Data & details"; expanding reveals them,
 *    including the honest "Data unavailable" state.
 *  - safe-zone dialog opens with the capacity RESULT and the SIMULATED state
 *    visible; the feasibility maths sit behind "Capacity details".
 *  - the simulated label appears ONLY for SIMULATED zones (honesty).
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

  private fun zoneDetail() = ZoneDetail(
    sections = listOf(
      ZoneDetailSection(
        heading = "FLOOD HAZARD DATA",
        fields = listOf(
          ZoneDetailField("Water level", "4.2 m"),
          ZoneDetailField("Rainfall (24h)", null)
        )
      )
    ),
    nearestSafeZone = null
  )

  private fun safeZone() = SafeZone(
    id = "sz-1", name = "DEMO Community Hall (simulated)",
    lat = 27.49, lon = 94.92, locationNote = "SIMULATED record",
    capacityTotal = 500, capacityCurrent = 230,
    waterAvailable = true, foodAvailable = true, electricityAvailable = true,
    sanitationAvailable = false, medicalSupport = true, accessibility = "Road",
    womenChildrenSuitability = true, operatingStatus = "OPEN",
    verificationStatus = "SIMULATED - not a verified shelter",
    elevationNote = "",
    provenance = DataProvenance(
      source = "test", classification = DataClassification.SIMULATED
    )
  )

  @Test
  fun `hazard dialog opens compact - expand reveals technical data`() {
    composeTestRule.setContent {
      VippattiTheme {
        HazardZoneDetailDialog(zone = hazardZone(true), detail = zoneDetail(), onDismiss = {})
      }
    }
    // PRIMARY visible immediately:
    composeTestRule.onNodeWithTag("hazard_verdict_box").assertIsDisplayed()
    composeTestRule.onNodeWithTag("hazard_stat_severity").assertIsDisplayed()
    composeTestRule.onNodeWithText("SIMULATED - demonstration only").assertIsDisplayed()
    // TERTIARY collapsed: the section fields are not composed at all.
    composeTestRule.onNodeWithText("Water level").assertDoesNotExist()
    composeTestRule.onNodeWithText("Data unavailable").assertDoesNotExist()
    // Expand reveals exactly what was hidden, incl. the honest unavailable:
    composeTestRule.onNodeWithTag("hazard_details_toggle").performClick()
    composeTestRule.onNodeWithText("Water level").assertExists()
    composeTestRule.onNodeWithText("Data unavailable").assertExists()
  }

  @Test
  fun `live hazard dialog carries no simulated label`() {
    composeTestRule.setContent {
      VippattiTheme {
        HazardZoneDetailDialog(zone = hazardZone(false), detail = zoneDetail(), onDismiss = {})
      }
    }
    composeTestRule.onNodeWithText("SIMULATED - demonstration only").assertDoesNotExist()
    composeTestRule.onNodeWithTag("hazard_verdict_box").assertIsDisplayed()
  }

  @Test
  fun `safe-zone dialog opens with result first - details collapsed`() {
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
    // PRIMARY result + state visible without wading through the maths:
    composeTestRule.onNodeWithText("270 spaces available of 500").assertExists()
    composeTestRule.onNodeWithText("SIMULATED - not a verified shelter").assertExists()
    // TERTIARY feasibility block hidden until expanded:
    composeTestRule.onNodeWithText("RELOCATION FEASIBILITY").assertDoesNotExist()
    // Expand reveals it (nothing deleted, only collapsed):
    composeTestRule.onNodeWithTag("safe_zone_details_toggle").performClick()
    composeTestRule.onNodeWithText("RELOCATION FEASIBILITY").assertExists()
    // The route action still exists:
    composeTestRule.onNodeWithTag("safe_zone_route_button").assertExists()
  }

  @Test
  fun `limiting-resource line never invents a reason when unassessed`() {
    composeTestRule.setContent {
      VippattiTheme {
        SafeZoneDetailDialog(
          zone = safeZone(), evaluation = null,
          onDismiss = {}, onSelectAndRoute = {}, capacityAssessment = null
        )
      }
    }
    // No assessment => no "Why:" line (never fabricate a reason):
    composeTestRule.onNodeWithTag("capacity_limiter_line").assertDoesNotExist()
  }
}
