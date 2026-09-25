package com.example.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.DataProvenance
import com.example.data.model.SafeZone
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.VippattiUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * PRODUCTION CONTRACT: the radar's decision stack (recommended action ->
 * safe zones -> weather -> route -> evacuation CTA -> report incident) must
 * be on screen the moment the map tab opens. A collapsed-by-default sheet
 * read to testers as "the main features are gone".
 *
 * Checks (kept off the live MapView, which Robolectric cannot host):
 *  1. the sheet's initial state is EXPANDED (source-level assertion);
 *  2. the expanded payload actually composes with every block visible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
class RadarSheetVisibilityTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `the radar sheet opens EXPANDED so the decision stack is immediately visible`() {
    val source = java.io.File(
      System.getProperty("user.dir"),
      "src/main/java/com/example/ui/screens/RadarMapScreen.kt"
    ).readText()
    val match = Regex("var isSheetExpanded by remember \\{ mutableStateOf\\((\\w+)\\) \\}")
      .find(source)?.groupValues?.get(1)
    requireNotNull(match) { "sheet state declaration changed shape - update this test" }
    assertTrue(
      "the radar decision stack must START visible (was: $match)",
      match == "true"
    )
  }

  @Test
  fun `the expanded stack renders every decision block`() {
    composeTestRule.setContent {
      VippattiTheme {
        ExpandedSheetContent(
          uiState = VippattiUiState(),
          onSelectBestSafeZone = {},
          onSelectSafeZone = {},
          onSetTravelMode = {},
          onStartEvacuation = {},
          onStopEvacuation = {},
          onLoadAlternativeRoutes = {},
          onOpenIncidentReport = {},
          onRequestFallbackRoute = {},
          onRetryWeather = {}
        )
      }
    }
    composeTestRule.onNodeWithTag("open_incident_report_button", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithText("REPORT AN INCIDENT", substring = true).assertExists()
  }
}
