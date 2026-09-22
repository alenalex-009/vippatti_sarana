package com.example.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.VippattiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Navigation contracts for the five-card swipeable onboarding carousel:
 * initial page, forward/back movement, live pagination, and finish
 * semantics. The finish callback must fire exactly for skip / log-in /
 * final-page completion — never merely for changing pages.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class OnboardingFlowTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var finished = 0

  private fun render() {
    finished = 0
    composeTestRule.setContent {
      VippattiTheme {
        OnboardingFlowScreen(onFinish = { finished++ })
      }
    }
  }

  /** Advances from card 1 through [clicks] primary actions. */
  private fun advance(clicks: Int) {
    composeTestRule.onNodeWithTag("onboarding_get_started").performClick()
    repeat(clicks - 1) {
      composeTestRule.onNodeWithTag("onboarding_primary").performClick()
    }
  }

  private fun advanceToNext() {
    composeTestRule.onNodeWithTag("onboarding_primary").performClick()
  }

  @Test
  fun `card 1 is displayed initially as Step 1 of 5`() {
    render()

    composeTestRule.onNodeWithText("VIPPATTI SARANA").assertExists()
    composeTestRule.onNodeWithText("Preparedness Starts Before the Emergency.")
      .assertExists()
    composeTestRule.onNodeWithContentDescription("Step 1 of 5").assertExists()
    assertEquals(0, finished)
  }

  @Test
  fun `card 1 advances to card 2`() {
    render()

    composeTestRule.onNodeWithTag("onboarding_get_started").performClick()

    composeTestRule.onNodeWithText("See Risk Around You.").assertExists()
    composeTestRule.onNodeWithText("Step 2 of 5").assertExists()
    assertEquals(0, finished)
  }

  @Test
  fun `every card can be reached in order`() {
    render()

    advance(1)
    composeTestRule.onNodeWithText("See Risk Around You.").assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("Stay Informed. Stay Ready.").assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("When It Matters, Know What To Do.")
      .assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("Your Safety Tools. One Place.").assertExists()
    composeTestRule.onNodeWithTag("onboarding_ready_status").assertExists()
    assertEquals(0, finished)
  }

  @Test
  fun `the pagination indicator reflects the current card`() {
    render()

    composeTestRule.onNodeWithContentDescription("Step 1 of 5").assertExists()
    advance(1)
    composeTestRule.onNodeWithText("Step 2 of 5").assertExists()
    composeTestRule.onNodeWithContentDescription("Step 2 of 5").assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("Step 3 of 5").assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("Step 4 of 5").assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("Step 5 of 5").assertExists()
  }

  @Test
  fun `back navigation returns to the previous card`() {
    render()
    advance(1)
    composeTestRule.onNodeWithText("See Risk Around You.").assertExists()

    composeTestRule.onNodeWithTag("onboarding_back").performClick()
    composeTestRule.onNodeWithText("Preparedness Starts Before the Emergency.")
      .assertExists()

    // Deeper: card 4 back lands on card 3.
    advance(1)
    advanceToNext()
    advanceToNext()
    composeTestRule.onNodeWithText("When It Matters, Know What To Do.")
      .assertExists()
    composeTestRule.onNodeWithTag("onboarding_back").performClick()
    composeTestRule.onNodeWithText("Stay Informed. Stay Ready.").assertExists()
    assertEquals(0, finished)
  }

  @Test
  fun `skip finishes onboarding without visiting every card`() {
    render()
    advance(1)
    advanceToNext()

    composeTestRule.onNodeWithTag("onboarding_skip").performClick()

    assertEquals(1, finished)
  }

  @Test
  fun `skip also works from the first card`() {
    render()

    composeTestRule.onNodeWithTag("onboarding_skip").performClick()

    assertEquals(1, finished)
  }

  @Test
  fun `completing card 5 finishes onboarding`() {
    render()
    advance(1)
    advanceToNext()
    advanceToNext()
    advanceToNext()
    composeTestRule.onNodeWithText("Your Safety Tools. One Place.").assertExists()
    composeTestRule.onNodeWithText("Continue to Sign Up").assertExists()

    composeTestRule.onNodeWithTag("onboarding_primary").performClick()

    assertEquals(1, finished)
  }

  @Test
  fun `log in on card 5 finishes onboarding into the auth flow`() {
    render()
    advance(1)
    advanceToNext()
    advanceToNext()
    advanceToNext()

    composeTestRule.onNodeWithTag("onboarding_log_in").performClick()

    assertEquals(1, finished)
  }
}
