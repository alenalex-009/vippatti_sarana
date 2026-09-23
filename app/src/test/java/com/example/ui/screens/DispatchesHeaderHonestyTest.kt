package com.example.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.data.news.NewsArticle
import com.example.data.news.NewsCategory
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsScope
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.VippattiUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * STAGE 7 — workflow-honesty render contracts.
 *
 * A cached, failed or never-synced news feed must never wear the LIVE label:
 * the section header follows the feed's real [newsStatus], exactly like the
 * sync banner. The SOS hero states its local-only nature for the same reason
 * the state layer does (RELAY_CHANNEL is local-only).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class DispatchesHeaderHonestyTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun article() = NewsArticle(
    id = "1",
    title = "Test dispatch",
    description = "Test description",
    content = "",
    url = "https://example.invalid/1",
    imageUrl = null,
    publishedAtIso = "2026-09-14T04:44:31Z",
    publishedAtMillis = 1_700_000_000_000L,
    language = "en",
    sourceName = "Test wire",
    sourceUrl = "https://example.invalid",
    scope = NewsScope.MY_AREA,
    category = NewsCategory.GENERAL
  )

  private fun renderDispatches(state: VippattiUiState) {
    composeTestRule.setContent {
      VippattiTheme {
        DispatchesScreen(
          uiState = state,
          onSync = {},
          onToggleAudio = {},
          onSelectCategory = {},
          onNavigateToEvacRoute = {},
          onNavigateTab = {},
          onToggleHistoricalLayer = {},
          onHistoricalFiltersChange = {},
          onClearHistoricalFilters = {},
          onSelectHistoricalEvent = {}
        )
      }
    }
  }

  /** The feed header sits below the fold in a LazyColumn: swipe until composed. */
  private fun scrollToFeedHeader() {
    repeat(12) {
      try {
        composeTestRule.onNodeWithTag("feed_dispatches_header").assertExists()
        return
      } catch (_: AssertionError) {
        composeTestRule.onRoot().performTouchInput { swipeUp() }
      }
    }
    composeTestRule.onNodeWithTag("feed_dispatches_header").assertExists()
  }

  @Test
  fun `a never-synced feed says NOT SYNCED, never LIVE`() {
    renderDispatches(VippattiUiState())
    scrollToFeedHeader()

    composeTestRule.onNodeWithText("FEED DISPATCHES").assertExists()
    composeTestRule.onNodeWithText("NOT SYNCED").assertExists()
    composeTestRule.onAllNodesWithText("LIVE VIA GNEWS", substring = true).assertCountEquals(0)
    composeTestRule.onAllNodesWithText("LIVE FEED DISPATCHES", substring = true).assertCountEquals(0)
  }

  @Test
  fun `a failed feed says FEED UNREACHABLE, never LIVE`() {
    renderDispatches(
      VippattiUiState(
        newsError = NewsError(NewsErrorKind.NETWORK, "No connection to the news service.")
      )
    )
    scrollToFeedHeader()

    composeTestRule.onNodeWithText("FEED UNREACHABLE").assertExists()
    composeTestRule.onAllNodesWithText("LIVE VIA GNEWS", substring = true).assertCountEquals(0)
  }

  @Test
  fun `a cached feed says CACHED FEED, never LIVE`() {
    renderDispatches(
      VippattiUiState(
        newsArticles = listOf(article()),
        isNewsFromCache = true
      )
    )
    scrollToFeedHeader()

    composeTestRule.onNodeWithText("CACHED FEED").assertExists()
    composeTestRule.onAllNodesWithText("LIVE VIA GNEWS", substring = true).assertCountEquals(0)
  }

  @Test
  fun `a live feed keeps the LIVE VIA GNEWS label`() {
    renderDispatches(
      VippattiUiState(
        newsArticles = listOf(article()),
        isNewsFromCache = false
      )
    )
    scrollToFeedHeader()

    composeTestRule.onNodeWithText("LIVE VIA GNEWS").assertExists()
  }

  @Test
  fun `the SOS hero states what it relays, never a live relay claim`() {
    // The Profile SOS center is the current SOS hero surface. It honestly
    // describes what it relays and must never claim a live authority relay.
    composeTestRule.setContent {
      VippattiTheme {
        SosCenterCard(
          onBroadcastSos = {},
          onOpenSituationReport = {}
        )
      }
    }

    composeTestRule.onNodeWithText("SOS Center").assertExists()
    composeTestRule.onNodeWithText("Relays live GPS, battery level and medical tag")
      .assertExists()
    composeTestRule.onNodeWithTag("broadcast_sos_hero_button").assertExists()
    composeTestRule.onAllNodesWithText("LIVE RELAY ACTIVE", substring = true).assertCountEquals(0)
  }
}
