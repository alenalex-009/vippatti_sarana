package com.example.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.VippattiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render contracts for the welcome card of the onboarding carousel: brand
 * label, heading, honest body copy, and the hero emblem. No capability
 * overclaims may appear in the copy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class OnboardingWelcomeTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun render() {
    composeTestRule.setContent {
      VippattiTheme {
        OnboardingWelcomePage()
      }
    }
  }

  @Test
  fun `welcome copy matches the specified wording`() {
    render()

    composeTestRule.onNodeWithText("VIPPATTI SARANA").assertExists()
    composeTestRule.onNodeWithText("Preparedness Starts Before the Emergency.")
      .assertExists()
    composeTestRule.onNodeWithText(
      "Vippatti Sarana brings essential disaster information, safety guidance " +
        "and emergency tools together in one place."
    ).assertExists()
    composeTestRule.onNodeWithTag("onboarding_hero_logo").assertExists()
  }

  @Test
  fun `no protection or response guarantees are claimed`() {
    render()

    for (overclaim in listOf(
      "real-time protection", "guaranteed", "always-active",
      "automatic emergency response", "offline broadcast", "predict"
    )) {
      composeTestRule.onAllNodesWithText(overclaim, substring = true, ignoreCase = true)
        .assertCountEquals(0)
    }
  }
}
