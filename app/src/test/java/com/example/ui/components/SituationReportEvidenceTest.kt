package com.example.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
    composeTestRule.onNodeWithTag("situation_photo_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("situation_camera_button").assertIsDisplayed()
    // English labels (the app's default locale in tests).
    composeTestRule.onNodeWithText("ATTACH PHOTO EVIDENCE", substring = true)
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("TAKE PHOTO WITH CAMERA", substring = true)
      .assertIsDisplayed()
  }
}
