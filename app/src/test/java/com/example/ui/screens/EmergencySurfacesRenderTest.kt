package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.CapacityStatus
import com.example.data.routing.GeoPoint
import com.example.data.model.SafeZone
import com.example.data.shelters.EmergencyGuidance
import com.example.data.shelters.SafeZoneEvaluation
import com.example.data.shelters.ShelterCapacityService
import com.example.data.suitability.ElevationGrid
import com.example.data.suitability.TerrainSuitabilityEngine
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.TerrainSelfAssessment
import com.example.viewmodel.VippattiUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RENDER contracts for the new emergency surfaces on a small phone:
 *  - the guidance card shows the actionable suggestion + GO and nothing when
 *    calm;
 *  - the honest no-shelter state shows the 112 instruction and the terrain
 *    search button;
 *  - the terrain self-assessment chip never shows a verdict before the user
 *    taps, and renders the honest UNASSESSED state for a service failure;
 *  - the Authority Console renders the prioritization rows with their tier
 *    labels and SIMULATED badges.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h720dp")
class EmergencySurfacesRenderTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun zone(id: String = "sz", lat: Double = 10.01, lon: Double = 76.5) = SafeZone(
    id = id, name = "Govt School Hall $id", lat = lat, lon = lon,
    locationNote = "t", capacityTotal = 300, capacityCurrent = 0,
    waterAvailable = true, foodAvailable = true, electricityAvailable = true,
    sanitationAvailable = true, medicalSupport = false, accessibility = "Road",
    womenChildrenSuitability = false, operatingStatus = "OPEN",
    verificationStatus = "FIELD RECORD", elevationNote = "",
    provenance = com.example.data.model.DataProvenance(source = "test")
  )

  private fun evaluation(z: SafeZone) = SafeZoneEvaluation(
    zone = z, distanceMeters = 1_110.0, isFeasible = true, rejectionReason = null,
    score = 80, reasons = emptyList(), hazardExposureCount = 0,
    capacityReport = ShelterCapacityService.report(z)
  )

  @Test
  fun `calm guidance state renders no card`() {
    composeTestRule.setContent {
      VippattiTheme {
        EmergencyGuidanceCard(
          guidance = EmergencyGuidance.None, haven = null, isSearchingHaven = false,
          onGo = {}, onSearchHaven = {}, onRouteToHaven = {}, onDismiss = {}
        )
      }
    }
    composeTestRule.onNodeWithText("Head to the nearest safe zone", substring = true)
      .assertDoesNotExist()
  }

  @Test
  fun `SuggestShelter renders the nearest-zone headline and GO fires the callback`() {
    var goes = 0
    val suggestion = EmergencyGuidance.forSituation(
      riskLevel = com.example.data.risk.RiskLevel.RED,
      evaluations = listOf(evaluation(zone())),
      hasActiveDestination = false
    ) as EmergencyGuidance.SuggestShelter
    composeTestRule.setContent {
      VippattiTheme {
        EmergencyGuidanceCard(
          guidance = suggestion, haven = null, isSearchingHaven = false,
          onGo = { goes++ }, onSearchHaven = {}, onRouteToHaven = {}, onDismiss = {}
        )
      }
    }
    composeTestRule.onNodeWithText("nearest safe zone", substring = true).assertExists()
    composeTestRule.onNodeWithText("1.1 km", substring = true).assertExists()
    composeTestRule.onNodeWithText("GO").performClick()
    assertEquals(1, goes)
  }

  @Test
  fun `no-shelter danger state shows the honest headline, 112 guidance and terrain search`() {
    var searches = 0
    composeTestRule.setContent {
      VippattiTheme {
        EmergencyGuidanceCard(
          guidance = EmergencyGuidance.NoShelterKnown(
            headline = "DANGER NEAR YOU — no registered shelter in range",
            detail = "No shelter record exists for this area in this build. Move to higher ground away from watercourses and riverbanks and call 112."
          ),
          haven = null, isSearchingHaven = false,
          onGo = {}, onSearchHaven = { searches++ }, onRouteToHaven = {}, onDismiss = {}
        )
      }
    }
    composeTestRule.onNodeWithText("call 112", substring = true).assertExists()
    composeTestRule.onNodeWithText("FIND SAFE TERRAIN").performClick()
    assertEquals(1, searches)
  }

  @Test
  fun `terrain chip hides the verdict until asked and shows the honest unavailable state`() {
    composeTestRule.setContent {
      VippattiTheme {
        Box(Modifier.fillMaxSize()) {
          TerrainSelfAssessmentChip(
            assessment = TerrainSelfAssessment.Unavailable("HTTP 500"),
            isAssessing = false,
            onAssess = {}, onDismiss = {}
          )
        }
      }
    }
    composeTestRule.onNodeWithText("RE-CHECK MY TERRAIN").assertExists()
    composeTestRule.onNodeWithText("TERRAIN NOT ASSESSED").assertExists()
    composeTestRule.onNodeWithText("Nothing was assumed", substring = true).assertExists()
  }

  @Test
  fun `terrain chip renders a red-zone verdict with the disclaimer and reason lines`() {
    val steep = TerrainSuitabilityEngine.evaluate(
      ElevationGrid(900.0, 930.0, 899.0, 901.0, 900.0, 30.0), rainfallMm24h = null, nearCoast = false
    )
    composeTestRule.setContent {
      VippattiTheme {
        Box(Modifier.fillMaxSize()) {
          TerrainSelfAssessmentChip(
            assessment = TerrainSelfAssessment.Result(steep, coastKnown = false),
            isAssessing = false,
            onAssess = {}, onDismiss = {}
          )
        }
      }
    }
    composeTestRule.onNodeWithText("RED ZONE", substring = true).assertExists()
    composeTestRule.onNodeWithText("NOT an official", substring = true).assertExists()
    composeTestRule.onNodeWithText("SRTM", substring = true).assertExists()
  }

  @Test
  fun `authority console renders ranked rows with tier labels and simulated badges`() {
    val priorities = com.example.data.habitations.HabitationPriorityEngine.rank(
      listOf(
        com.example.data.habitations.Habitation(
          id = "demo-x", name = "Test settlement",
          point = GeoPoint(10.0, 76.5),
          population = com.example.data.habitations.PopulationInput(
            300, com.example.data.model.DataClassification.SIMULATED, "demo"
          ),
          vulnerableShare = 0.3f
        )
      ),
      emptyList(),
      listOf(zone())
    )
    composeTestRule.setContent {
      VippattiTheme {
        AuthorityConsoleScreen(
          uiState = VippattiUiState(relocationPriorities = priorities),
          onBack = {},
          onSaveShelter = {}, onDeleteShelter = {},
          onSaveHabitation = {}, onDeleteHabitation = {},
          onRerank = {}
        )
      }
    }
    composeTestRule.onNodeWithText("AUTHORITY CONSOLE").assertExists()
    composeTestRule.onNodeWithText("Test settlement").assertExists()
    composeTestRule.onNodeWithText("SIMULATED demo record").assertExists()
  }
}
