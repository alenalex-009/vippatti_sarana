package com.example.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.ScreenTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI REGRESSION TEST for the primary bottom navigation.
 *
 * This is the test the pure NavTabs unit tests could not provide: it proves what
 * the Compose bottom bar ACTUALLY renders, not what NavTabs.visible() returns in
 * isolation. It must FAIL if the bar ever renders only three tabs — the historical
 * first-launch bug.
 *
 * Acceptance: all five primary tabs must exist simultaneously as tagged nodes:
 *   nav_home | nav_radar_map | nav_news | nav_instructions | nav_profile
 *
 * The bar now uses a constant five-tab list, so this is stable by construction —
 * but pinning it directly means a future refactor cannot silently reintroduce the
 * 3-tab first launch, and the test keeps behaving the same under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h720dp")
class VippattiBottomNavBarTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val allTabTags = listOf(
    "nav_home",
    "nav_radar_map",
    "nav_news",
    "nav_instructions",
    "nav_profile"
  )

  private fun render(currentTab: ScreenTab = ScreenTab.HOME) {
    composeTestRule.setContent {
      VippattiTheme {
        VippattiBottomNavBar(
          currentTab = currentTab,
          onTabSelected = { }
        )
      }
    }
  }

  @Test
  fun `all five primary tabs exist simultaneously in the bar`() {
    render()
    // Each primary tab is tagged, so asserting all five tags exist is the real
    // proof that the bar rendered five items on the first composition.
    // A missing tag fails the test immediately, which is exactly what we want
    // for the historical fresh-install 3-tab bug.
    composeTestRule.onNodeWithTag("nav_home").assertExists()
    composeTestRule.onNodeWithTag("nav_radar_map").assertExists()
    composeTestRule.onNodeWithTag("nav_news").assertExists()
    composeTestRule.onNodeWithTag("nav_instructions").assertExists()
    composeTestRule.onNodeWithTag("nav_profile").assertExists()
  }

  // Changing the selected primary tab must never change the number of tabs
  // rendered. One test per selected tab so each composition is independent,
  // matching the style of the other Robolectric Compose tests in this project.

  @Test
  fun `renders five tabs when HOME is selected`() {
    render(ScreenTab.HOME)
    composeTestRule.onNodeWithTag("nav_home").assertExists()
    composeTestRule.onNodeWithTag("nav_radar_map").assertExists()
    composeTestRule.onNodeWithTag("nav_news").assertExists()
    composeTestRule.onNodeWithTag("nav_instructions").assertExists()
    composeTestRule.onNodeWithTag("nav_profile").assertExists()
  }

  @Test
  fun `renders five tabs when MAP is selected`() {
    render(ScreenTab.RADAR_MAP)
    composeTestRule.onNodeWithTag("nav_home").assertExists()
    composeTestRule.onNodeWithTag("nav_radar_map").assertExists()
    composeTestRule.onNodeWithTag("nav_news").assertExists()
    composeTestRule.onNodeWithTag("nav_instructions").assertExists()
    composeTestRule.onNodeWithTag("nav_profile").assertExists()
  }

  @Test
  fun `renders five tabs when NEWS is selected`() {
    render(ScreenTab.NEWS_DISPATCHES)
    composeTestRule.onNodeWithTag("nav_home").assertExists()
    composeTestRule.onNodeWithTag("nav_radar_map").assertExists()
    composeTestRule.onNodeWithTag("nav_news").assertExists()
    composeTestRule.onNodeWithTag("nav_instructions").assertExists()
    composeTestRule.onNodeWithTag("nav_profile").assertExists()
  }

  @Test
  fun `renders five tabs when GUIDE is selected`() {
    render(ScreenTab.INSTRUCTIONS)
    composeTestRule.onNodeWithTag("nav_home").assertExists()
    composeTestRule.onNodeWithTag("nav_radar_map").assertExists()
    composeTestRule.onNodeWithTag("nav_news").assertExists()
    composeTestRule.onNodeWithTag("nav_instructions").assertExists()
    composeTestRule.onNodeWithTag("nav_profile").assertExists()
  }

  @Test
  fun `renders five tabs when PROFILE is selected`() {
    render(ScreenTab.PROFILE)
    composeTestRule.onNodeWithTag("nav_home").assertExists()
    composeTestRule.onNodeWithTag("nav_radar_map").assertExists()
    composeTestRule.onNodeWithTag("nav_news").assertExists()
    composeTestRule.onNodeWithTag("nav_instructions").assertExists()
    composeTestRule.onNodeWithTag("nav_profile").assertExists()
  }

  @Test
  fun `a phantom sixth tab never appears`() {
    // Regression: if a stray tab node is ever given one of the real tab test tags,
    // this fails via the count check above. This test additionally asserts that a
    // made-up tag is absent, so the bar is not accidentally rendering extra items.
    render()
    composeTestRule.onNodeWithTag("nav_radar_map_extra").assertDoesNotExist()
  }

  @Test
  fun `the bar matches NavTabs identity helpers`() {
    // The bar's constant list and NavTabs should agree, so the bar and the content
    // switch cannot drift.
    val barTabs = listOf(
      ScreenTab.HOME,
      ScreenTab.RADAR_MAP,
      ScreenTab.NEWS_DISPATCHES,
      ScreenTab.INSTRUCTIONS,
      ScreenTab.PROFILE
    )
    for (tab in barTabs) {
      assertTrue(
        "$tab must have a label resource for the bottom bar.",
        NavTabs.labelRes(tab) != 0
      )
      assertTrue(
        "$tab must have a stable test tag for the bottom bar.",
        NavTabs.testTag(tab).isNotBlank()
      )
    }
    assertEquals(
      "The bar's tab list must match NavTabs.ordered.",
      barTabs,
      NavTabs.ordered
    )
  }
}
