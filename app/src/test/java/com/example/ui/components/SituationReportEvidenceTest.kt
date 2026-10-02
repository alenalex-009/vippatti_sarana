package com.example.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.ui.theme.VippattiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Report-my-situation evidence row: the gallery attach and the LIVE camera
 * capture must both be present as separate affordances (user ask: "add a
 * feature for take pic from camera ... like attach photo evidence").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SituationReportEvidenceTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `the empty report offers gallery attach AND live camera side by side`() {
    composeTestRule.setContent {
      VippattiTheme {
        SituationReportDialog(
          reporterName = "Test Reporter",
          locationLabel = "Test location",
          batteryLabel = "80%",
          isSubmitting = false,
          onDismiss = {},
          onSubmit = { _, _ -> }
        )
      }
    }
    // The dialog content is taller than the test viewport; scroll to each
    // affordance first (what a user does), then assert it renders visibly.
    composeTestRule.onNodeWithTag("situation_photo_button")
      .performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("situation_camera_button")
      .performScrollTo().assertIsDisplayed()
    // English labels (the app's default locale in tests). Spec section 27
    // renamed the cramped all-caps labels to clear action verbs.
    composeTestRule.onNodeWithText("Choose from Gallery", substring = true)
      .performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Take Photo", substring = true)
      .performScrollTo().assertIsDisplayed()
  }
}
