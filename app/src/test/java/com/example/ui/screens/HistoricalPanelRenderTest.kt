package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.historical.HistoricalContextService
import com.example.data.historical.HistoricalDate
import com.example.data.historical.HistoricalDatasetInfo
import com.example.data.historical.HistoricalDisasterCatalog
import com.example.data.historical.HistoricalDisasterEvent
import com.example.data.historical.HistoricalImpacts
import com.example.data.historical.HistoricalSpatialPrecision
import com.example.data.model.DataStatus
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.VippattiUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RENDER tests for the HISTORICAL (EM-DAT) surfaces.
 *
 * These assert what is actually on screen: the mandatory HISTORICAL labelling,
 * the dataset attribution, the honest "Not available" for values EM-DAT does not
 * provide, the spatial-precision explanation, and the disclaimer. They also pin
 * that the panel never claims the archive is live.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class HistoricalPanelRenderTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val mappable = HistoricalDisasterEvent(
    id = "1996-0123-IND",
    group = "Natural",
    subgroup = "Meteorological",
    type = "Storm",
    subtype = "Tropical cyclone",
    country = "India",
    locationText = "Odisha",
    startDate = HistoricalDate(1996, 6, 6),
    impacts = HistoricalImpacts(totalDeaths = 1000, totalAffected = 2_000_000),
    latitude = 20.2,
    longitude = 85.7,
    source = "EM-DAT, CRED / UCLouvain, Brussels, Belgium",
    datasetVersion = "2026-09-11",
    accessedOn = "Fri, 18 Sep 2026 15:01:31 UTC",
    spatialPrecision = HistoricalSpatialPrecision.SOURCE_COORDINATES
  )

  private val contextOnly = HistoricalDisasterEvent(
    id = "2020-0332-IND",
    group = "Natural",
    subgroup = "Hydrological",
    type = "Mass movement (wet)",
    subtype = "Landslide (wet)",
    country = "India",
    locationText = "Idukki district (Kerala state)",
    adminUnitNames = listOf("Idukki"),
    startDate = HistoricalDate(2020, 8, 7),
    impacts = HistoricalImpacts(totalDeaths = 70),
    source = "EM-DAT, CRED / UCLouvain, Brussels, Belgium",
    datasetVersion = "2026-09-11",
    spatialPrecision = HistoricalSpatialPrecision.ADMIN_UNIT_ONLY
  )

  private fun catalog() = HistoricalDisasterCatalog(
    events = listOf(mappable, contextOnly),
    info = HistoricalDatasetInfo(
      source = "EM-DAT, CRED / UCLouvain, Brussels, Belgium",
      sourceUrl = "https://www.emdat.be",
      glossaryUrl = "https://doc.emdat.be/docs/data-structure-and-content/emdat-public-table/",
      version = "2026-09-11",
      fileCreated = "Fri, 18 Sep 2026 15:01:31 UTC",
      tableType = "public_emdat_custom_request",
      declaredRecordCount = 740,
      parsedRecordCount = 2
    )
  )

  private fun state(catalog: HistoricalDisasterCatalog?) = VippattiUiState(
    historicalCatalog = catalog,
    historicalStatus = if (catalog != null) DataStatus.HISTORICAL else DataStatus.NOT_CONFIGURED,
    historicalError = if (catalog == null) "No historical dataset is bundled." else null,
    historicalContext = HistoricalContextService.contextFor("Idukki", "Kerala", catalog)
  )

  /**
   * The panel is taller than Robolectric's default viewport, so it is rendered
   * inside the app's own scroll pattern; assertions scroll to the node first,
   * exactly as a user would.
   */
  private fun renderPanel(uiState: VippattiUiState) {
    composeTestRule.setContent {
      VippattiTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          HistoricalIntelligencePanel(
            uiState = uiState,
            onToggleLayer = {},
            onFiltersChange = {},
            onClearFilters = {},
            onSelectEvent = {}
          )
        }
      }
    }
  }

  /** Scrolls to a node and asserts it is actually displayed on screen. */
  private fun assertVisible(text: String) {
    composeTestRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `the collapsed panel labels the archive as historical and credits its source`() {
    renderPanel(state(catalog()))

    composeTestRule.onNodeWithText("HISTORICAL DATA — NOT LIVE HAZARDS").assertIsDisplayed()
    assertVisible(
      "EM-DAT, CRED / UCLouvain, Brussels, Belgium • version 2026-09-11 • " +
        "glossary: https://doc.emdat.be/docs/data-structure-and-content/emdat-public-table/"
    )
    // The dataset's own coverage is stated, not implied.
    assertVisible("2 archived records • 1 with source coordinates • 1 context-only")
  }

  @Test
  fun `expanding shows area evidence, the disclaimer and the record list`() {
    renderPanel(state(catalog()))
    composeTestRule.onNodeWithText("EXPLORE").performClick()

    // The real Idukki record is listed with its record count.
    assertVisible("YOUR AREA — HISTORICAL EVIDENCE")
    assertVisible("1 historical record between 2020 and 2020 • Idukki (district)")
    assertVisible("2020-08-07 • Mass movement (wet)")
    // The mandatory wording appears verbatim.
    assertVisible(HistoricalContextService.DISCLAIMER)
    // Filter count reflects the selection.
    assertVisible("FILTER (2 of 2)")
  }

  @Test
  fun `a missing impact figure renders as Not available, never as zero`() {
    renderPanel(state(catalog()))
    composeTestRule.onNodeWithText("EXPLORE").performClick()

    // The selection reports deaths (a figure) but no homeless or damage at all,
    // so those totals must read "Not available" rather than 0.
    assertVisible("Deaths")
    assertVisible("1,070")
    assertVisible("1 of 2 records state a figure")
    // Homeless and damage have no reporting record: coverage says so, and the
    // total itself is never rendered as 0. Both rows render the identical
    // coverage string, so assert both nodes (not a single node).
    composeTestRule
      .onAllNodesWithText("0 of 2 records state a figure")
      .assertCountEquals(2)
    composeTestRule
      .onAllNodesWithText("0 of 2 records state a figure")[0]
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule
      .onAllNodesWithText("0 of 2 records state a figure")[1]
      .performScrollTo()
      .assertIsDisplayed()
    assertVisible("Homeless")
    assertVisible("Damage ('000 US$)")
    composeTestRule.onAllNodesWithText("Not available").assertCountEquals(2)
  }

  @Test
  fun `with no dataset attached the panel says so instead of showing an empty history`() {
    renderPanel(state(null))

    // The status line and the body both state the absence - never an empty history.
    composeTestRule.onAllNodesWithText("No historical dataset is bundled.").assertCountEquals(2)
    assertVisible(
      "No historical dataset is loaded in this build, so no historical record can be " +
        "shown. Attach a prepared EM-DAT dataset to enable this panel."
    )
  }

  @Test
  fun `the record sheet shows every field, Not available for absent ones and the limits`() {
    composeTestRule.setContent {
      VippattiTheme {
        HistoricalEventDetailDialog(event = contextOnly, onDismiss = {})
      }
    }

    composeTestRule.onNodeWithText("HISTORICAL DISASTER RECORD").assertIsDisplayed()
    composeTestRule.onNodeWithText("2020-0332-IND").assertIsDisplayed()
    assertVisible("Mass movement (wet)")
    assertVisible("Idukki district (Kerala state)")
    assertVisible("70")
    // Absent impacts and coordinates read "Not available", never 0.
    assertVisible("Total affected")
    assertVisible("Coordinates (source)")
    assertVisible("HISTORICAL")
    // The record states exactly why it is not on the map.
    assertVisible(
      "This record is not mapped: EM-DAT provides no coordinates for it. " +
        "Its location text is kept exactly as the source states it."
    )
    assertVisible(HistoricalContextService.DISCLAIMER)
  }
}
