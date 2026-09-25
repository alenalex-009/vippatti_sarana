package com.example.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.risk.HazardAnalysisService
import com.example.data.risk.PersonalRiskAssessment
import com.example.data.risk.RiskAssessmentEngine
import com.example.data.risk.RiskLevel
import com.example.data.model.DataProvenance
import com.example.data.routing.GeoPoint
import com.example.viewmodel.ScreenTab
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.VippattiUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UX contracts for the redesigned journey (user-centricity + simplicity):
 *  - the app LANDS on a calm Home that answers "is my area safe?" first;
 *  - every next step is one clear row with a label;
 *  - navigation rows actually navigate.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h720dp")
class HomeScreenUxTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun render(state: VippattiUiState, nav: (ScreenTab) -> Unit = {}) {
    composeTestRule.setContent {
      VippattiTheme {
        HomeScreen(
          uiState = state,
          onOpenRadar = { nav(ScreenTab.RADAR_MAP) },
          onOpenNews = { nav(ScreenTab.NEWS_DISPATCHES) },
          onOpenGuide = { nav(ScreenTab.INSTRUCTIONS) },
          onOpenProfile = { nav(ScreenTab.PROFILE) },
          onAssessTerrain = {},
          onGuidanceGo = {},
          onSearchTerrainHaven = {},
          onRouteToTerrainHaven = {},
          onGuidanceDismiss = {}
        )
      }
    }
  }

  @Test
  fun `default landing tab is HOME`() {
    assertEquals(ScreenTab.HOME, VippattiUiState().currentTab)
  }

  @Test
  fun `home leads with the single safety question and an honest pending state`() {
    render(VippattiUiState())
    composeTestRule.onNodeWithText("IS MY AREA SAFE RIGHT NOW?").assertExists()
    composeTestRule.onNodeWithText("ASSESSING YOUR AREA\u2026").assertExists()
    composeTestRule.onNodeWithTag("home_risk_hero").assertExists()
  }

  @Test
  fun `a calm assessment shows the green verdict and the map CTA navigates`() {
    var visited: ScreenTab? = null
    val calm = PersonalRiskAssessment(
      location = GeoPoint(10.0, 76.5),
      level = RiskLevel.GREEN,
      primaryHazard = null,
      affectingHazardCount = 0,
      explanation = "No active hazard affects your location right now.",
      trendSummary = "Stable",
      confidenceNote = "Live feeds + GPS"
    )
    render(VippattiUiState(personalRisk = calm), nav = { visited = it })
    composeTestRule.onNodeWithText("YOUR AREA LOOKS CALM").assertExists()
    composeTestRule.onNodeWithText("OPEN THE HAZARD MAP").performClick()
    assertEquals(ScreenTab.RADAR_MAP, visited)
  }

  @Test
  fun `home lists the four next steps with readable labels`() {
    var visited: ScreenTab? = null
    render(VippattiUiState(), nav = { visited = it })
    composeTestRule.onNodeWithText("Safe zones & evacuation routes").assertExists()
    composeTestRule.onNodeWithText("What to do during a disaster").assertExists()
    composeTestRule.onNodeWithText("Latest disaster news").assertExists()
    composeTestRule.onNodeWithText("CHECK MY TERRAIN").assertExists()
    // and each row navigates or acts
    composeTestRule.onNodeWithTag("home_action_guide").performClick()
    assertEquals(ScreenTab.INSTRUCTIONS, visited)
  }

  @Test
  fun `danger guidance surfaces on home without extra taps`() {
    val guidance = com.example.data.shelters.EmergencyGuidance.NoShelterKnown(
      headline = "DANGER NEAR YOU \u2014 no registered shelter in range",
      detail = "No shelter record exists for this area in this build. Move to higher ground and call 112."
    )
    render(
      VippattiUiState(
        personalRisk = PersonalRiskAssessment(
          GeoPoint(10.0, 76.5), RiskLevel.RED, null, 1,
          "Inside an active hazard.", "Worsening", ""
        ),
        emergencyGuidance = guidance
      )
    )
    composeTestRule.onNodeWithTag("home_guidance_card").assertExists()
    composeTestRule.onNodeWithText("call 112", substring = true).assertExists()
  }
}
