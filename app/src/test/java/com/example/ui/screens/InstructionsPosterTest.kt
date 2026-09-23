package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.instructions.DisasterInstructions
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.ScreenTab
import com.example.viewmodel.VippattiUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Instructions-module image integration tests.
 *
 * Covers, without touching any other feature: the disaster -> poster wiring,
 * the aspect ratios measured from the supplied InstructionImages artwork, the
 * localized content description on every poster, the tap-through to the zoom
 * reader and the reader's own close action.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class InstructionsPosterTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Every disaster in the existing data model must resolve to its own poster. */
  @Test
  fun everyDisasterCategory_hasItsOwnPoster() {
    DisasterInstructions.categories.forEach { category ->
      assertNotNull("No poster wired for '${category.id}'", disasterPosterRes(category.id))
    }
    val drawables = DisasterInstructions.categories.map { disasterPosterRes(it.id)!! }
    assertEquals("Posters must not be shared between disasters", drawables.size, drawables.distinct().size)
  }

  @Test
  fun unknownCategory_hasNoPoster() {
    assertNull(disasterPosterRes("tsunami"))
    assertNull(disasterPosterRes(""))
  }

  /** Portrait, and exactly the ratios measured from the shipped PNGs. */
  @Test
  fun posterAspectRatios_matchTheSuppliedArtwork() {
    assertEquals(1024f / 1536f, disasterPosterAspectRatio("flood")!!, 0.0001f)
    assertEquals(1024f / 1536f, disasterPosterAspectRatio("earthquake")!!, 0.0001f)
    assertEquals(941f / 1672f, disasterPosterAspectRatio("landslide")!!, 0.0001f)
    assertEquals(941f / 1672f, disasterPosterAspectRatio("fire")!!, 0.0001f)
    DisasterInstructions.categories.forEach { category ->
      val ratio = disasterPosterAspectRatio(category.id)!!
      assertTrue("${category.id} poster must stay portrait", ratio > 0.1f && ratio < 1f)
    }
  }

  @Test
  @Config(qualifiers = "w360dp-h720dp")
  fun everyPoster_rendersWithALocalizedDescription() {
    composeTestRule.setContent {
      VippattiTheme {
        Column {
          DisasterInstructions.categories.forEach { category ->
            DisasterInstructionPoster(
              categoryId = category.id,
              disasterTitle = category.title,
              onOpen = {}
            )
          }
        }
      }
    }
    DisasterInstructions.categories.forEach { category ->
      composeTestRule
        .onNodeWithTag(PosterTags.image(category.id))
        .assertExists()
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
    }
  }

  @Test
  @Config(qualifiers = "w360dp-h720dp")
  fun tappingAPoster_requestsTheZoomReader() {
    var openRequests = 0
    composeTestRule.setContent {
      VippattiTheme {
        DisasterInstructionPoster(
          categoryId = "flood",
          disasterTitle = "Flood",
          onOpen = { openRequests++ }
        )
      }
    }
    composeTestRule.onNodeWithTag(PosterTags.image("flood")).performClick()
    assertEquals(1, openRequests)
  }

  @Test
  @Config(qualifiers = "w360dp-h720dp")
  fun zoomReader_showsThePosterAndCloses() {
    var dismissals = 0
    composeTestRule.setContent {
      VippattiTheme {
        InstructionPosterZoomOverlay(
          categoryId = "earthquake",
          disasterTitle = "Earthquake",
          onDismiss = { dismissals++ }
        )
      }
    }
    composeTestRule.onNodeWithTag(PosterTags.VIEWER).assertExists()
    composeTestRule
      .onNodeWithTag(PosterTags.VIEWER_IMAGE)
      .assertExists()
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
    composeTestRule.onNodeWithTag(PosterTags.CLOSE).performClick()
    assertEquals(1, dismissals)
  }

  /**
   * Integration check on the real Instructions screen: the home is the
   * disaster chooser (no poster mounted), the poster appears only after
   * choosing a disaster — and back returns to the chooser in one step.
   */
  @Test
  @Config(qualifiers = "w360dp-h720dp")
  fun instructionsHome_showsOnlyTheSelectedDisasterPoster() {
    composeTestRule.setContent {
      VippattiTheme {
        InstructionsScreen(
          uiState = VippattiUiState(currentTab = ScreenTab.INSTRUCTIONS),
          onToggleTheme = {},
          onToggleOfflineAccess = { _ -> },
          onOpenInteractiveBag = {},
          onToggleFlashlight = {},
          onToggleSiren = {}
        )
      }
    }
    // Home = the four-disaster chooser: no poster is mounted yet.
    composeTestRule.onNodeWithTag(PosterTags.image("flood")).assertDoesNotExist()
    composeTestRule.onNodeWithTag("disaster_card_flood").assertExists()
    composeTestRule.onNodeWithTag("disaster_card_fire").assertExists()

    // Choosing a disaster mounts ITS poster (and only that one).
    composeTestRule.onNodeWithTag("disaster_card_fire").performClick()
    composeTestRule.onNodeWithTag(PosterTags.image("fire")).assertExists()
    composeTestRule.onNodeWithTag(PosterTags.image("flood")).assertDoesNotExist()
    // The written guidance is still mounted next to the poster.
    composeTestRule.onNodeWithTag("phase_during_tab").assertExists()

    // Back is predictable: one step back to the chooser, poster unmounted.
    composeTestRule.onNodeWithTag("instructions_back_button").performClick()
    composeTestRule.onNodeWithTag("disaster_card_fire").assertExists()
    composeTestRule.onNodeWithTag(PosterTags.image("fire")).assertDoesNotExist()
  }

  @Test
  @Config(qualifiers = "w360dp-h720dp")
  fun unknownDisaster_rendersNoPosterAndNoReader() {
    composeTestRule.setContent {
      VippattiTheme {
        Column {
          Text("marker")
          DisasterInstructionPoster(categoryId = "tsunami", disasterTitle = "Tsunami", onOpen = {})
          InstructionPosterZoomOverlay(categoryId = "tsunami", disasterTitle = "Tsunami", onDismiss = {})
        }
      }
    }
    composeTestRule.onNodeWithText("marker").assertExists()
    composeTestRule.onNodeWithTag(PosterTags.VIEWER).assertDoesNotExist()
  }
}
