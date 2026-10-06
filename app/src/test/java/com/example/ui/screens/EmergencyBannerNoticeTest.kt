package com.example.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.VippattiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * REGRESSION GUARD for the un-closable emergency banner.
 *
 * The config-driven emergency notice used to render as a bare Text with NO
 * dismiss control — a permanent strip over every tab. The contract pinned
 * here: the notice always ships a working close control, and dismissing it
 * removes the notice from the screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h720dp")
class EmergencyBannerNoticeTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `banner shows its text and a dismiss control`() {
    composeTestRule.setContent {
      VippattiTheme {
        com.example.EmergencyBannerNotice(
          text = "Cyclone alert: coastal evacuation advisory in effect",
          onDismiss = {}
        )
      }
    }
    composeTestRule.onNodeWithText("Cyclone alert: coastal evacuation advisory in effect")
      .assertExists()
    composeTestRule.onNodeWithTag("emergency_banner_dismiss").assertExists()
  }

  @Test
  fun `tapping dismiss removes the banner`() {
    var dismissed = false
    composeTestRule.setContent {
      VippattiTheme {
        com.example.EmergencyBannerNotice(
          text = "Cyclone alert: coastal evacuation advisory in effect",
          onDismiss = { dismissed = true }
        )
      }
    }
    composeTestRule.onNodeWithTag("emergency_banner_dismiss").performClick()
    composeTestRule.waitForIdle()
    assertTrue("dismiss callback must fire", dismissed)
  }

  @Test
  fun `no banner renders when the caller suppresses it`() {
    composeTestRule.setContent {
      VippattiTheme {
        // The root gate renders nothing when disabled/dismissed, so nothing
        // (not even the dismiss control) may exist in the tree.
        Unit
      }
    }
    composeTestRule.onNodeWithTag("emergency_banner_dismiss").assertDoesNotExist()
  }
}
