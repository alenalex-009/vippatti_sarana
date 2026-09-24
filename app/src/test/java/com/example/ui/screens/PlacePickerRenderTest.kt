package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.location.PlaceCandidate
import com.example.data.location.PlaceSearchResult
import com.example.data.location.PlaceSearcher
import com.example.data.routing.GeoPoint
import com.example.ui.components.PlacePickerCard
import com.example.ui.components.PlaceViewBanner
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.MainDispatcherRule
import com.example.viewmodel.VippattiViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RENDER contracts for the place picker (the "choose a state/city" flow):
 *  - the dialog states honestly that the view is NOT your location;
 *  - a picked candidate fires the callback with that place;
 *  - the chosen-place banner labels the mode and offers BACK TO ME;
 *  - end-to-end: picking through the real VM produces the banner state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h720dp")
class PlacePickerRenderTest {

  @get:Rule val composeTestRule = createComposeRule()
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val vizag = PlaceCandidate(
    name = "Visakhapatnam",
    displayName = "Visakhapatnam, Visakhapatnam, Andhra Pradesh, India",
    point = GeoPoint(17.6935, 83.2921),
    kind = "city"
  )

  @Test
  fun `dialog lists candidates and picking one fires the callback`() {
    var picked: PlaceCandidate? = null
    composeTestRule.setContent {
      VippattiTheme {
        PlacePickerCard(
          query = "vizag",
          isSearching = false,
          candidates = listOf(vizag),
          error = null,
          onQueryChange = {},
          onPick = { picked = it },
          onDismiss = {}
        )
      }
    }
    composeTestRule.onNodeWithText("LOOK AT ANOTHER PLACE").assertExists()
    composeTestRule.onNodeWithText("It is NOT your real location.", substring = true).assertExists()
    composeTestRule.onNodeWithText("Visakhapatnam").assertExists()
    composeTestRule.onNodeWithTag("place_candidate_visakhapatnam").performClick()
    assertEquals(vizag, picked)
  }

  @Test
  fun `inline error renders when no place matches`() {
    composeTestRule.setContent {
      VippattiTheme {
        PlacePickerCard(
          query = "xyz",
          isSearching = false,
          candidates = emptyList(),
          error = "No place in India matched \"xyz\".",
          onQueryChange = {},
          onPick = {},
          onDismiss = {}
        )
      }
    }
    composeTestRule.onNodeWithTag("place_search_error")
      .assertTextEquals("No place in India matched \"xyz\".")
  }

  @Test
  fun `banner labels the chosen place and BACK TO ME exits`() {
    var exited = 0
    composeTestRule.setContent {
      VippattiTheme {
        Box(Modifier.fillMaxSize()) {
          PlaceViewBanner(
            label = "Visakhapatnam, Visakhapatnam, Andhra Pradesh, India",
            onExit = { exited++ }
          )
        }
      }
    }
    composeTestRule.onNodeWithTag("place_view_banner").assertExists()
    composeTestRule.onNodeWithText("VIEWING: Visakhapatnam — not your location", substring = true)
      .assertExists()
    composeTestRule.onNodeWithTag("place_view_exit").performClick()
    assertEquals(1, exited)
  }

  @Test
  fun `end-to-end through the real VM, pick shows the Vizag banner state`() {
    val searcher = object : PlaceSearcher {
      override suspend fun search(query: String) =
        PlaceSearchResult.Found(listOf(vizag))
    }
    val vm = VippattiViewModel(placeSearcher = searcher)
    var renderedBannerLabel: String? = null
    composeTestRule.setContent {
      VippattiTheme {
        val state = vm.uiState.collectAsState().value
        if (state.isViewingChosenPlace && state.viewedPlaceLabel != null) {
          renderedBannerLabel = state.viewedPlaceLabel
          PlaceViewBanner(label = state.viewedPlaceLabel, onExit = { vm.exitPlaceView() })
        }
      }
    }
    composeTestRule.runOnIdle { vm.viewChosenPlace(vizag) }
    composeTestRule.waitForIdle()
    assertTrue(renderedBannerLabel?.contains("Andhra Pradesh") == true)
    composeTestRule.onNodeWithTag("place_view_exit").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle {
      assertTrue(!vm.uiState.value.isViewingChosenPlace)
    }
  }
}
