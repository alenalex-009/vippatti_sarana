package com.example.ui.components

import com.example.config.AppRemoteConfig
import com.example.viewmodel.ScreenTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRODUCTION CONTRACT: the bottom bar and the content switch agree on which
 * modules remote config has enabled, and a module killed mid-session cannot
 * leave the user stranded.
 *
 * Visibility used to be encoded twice - `if (showRadar)` in the bar and an inline
 * `when` in MainActivity - and those two copies are how a killed feature ends up
 * with a hidden tab that still renders. NavTabs is now the single source, so it
 * is worth pinning directly rather than only through Compose rendering.
 */
class NavTabsTest {

  private val allOn = AppRemoteConfig(featureRadarEnabled = true, featureDispatchesEnabled = true)
  private val allOff = AppRemoteConfig(featureRadarEnabled = false, featureDispatchesEnabled = false)

  @Test
  fun `every tab is present when nothing is disabled`() {
    assertEquals(
      listOf(ScreenTab.HOME, ScreenTab.RADAR_MAP, ScreenTab.NEWS_DISPATCHES,
        ScreenTab.INSTRUCTIONS, ScreenTab.PROFILE),
      NavTabs.visible(allOn)
    )
  }

  @Test
  fun `disabling radar removes the map tab and nothing else`() {
    val tabs = NavTabs.visible(allOff.copy(featureDispatchesEnabled = true))
    assertFalse(ScreenTab.RADAR_MAP in tabs)
    assertEquals(listOf(ScreenTab.HOME, ScreenTab.NEWS_DISPATCHES, ScreenTab.INSTRUCTIONS,
      ScreenTab.PROFILE), tabs)
  }

  @Test
  fun `disabling news removes the news tab and nothing else`() {
    val tabs = NavTabs.visible(allOff.copy(featureRadarEnabled = true))
    assertFalse(ScreenTab.NEWS_DISPATCHES in tabs)
    assertEquals(listOf(ScreenTab.HOME, ScreenTab.RADAR_MAP, ScreenTab.INSTRUCTIONS,
      ScreenTab.PROFILE), tabs)
  }

  @Test
  fun `the manual, profile and home cannot be switched off`() {
    // These carry the offline survival manual and the user's own saved data.
    // A server-side mistake must not be able to hide the guidance a user is in the
    // middle of reading, so these three are unconditional by design.
    for (tab in listOf(ScreenTab.HOME, ScreenTab.INSTRUCTIONS, ScreenTab.PROFILE)) {
      assertTrue("$tab must survive every flag being off", NavTabs.isVisible(tab, allOff))
    }
    assertEquals(listOf(ScreenTab.HOME, ScreenTab.INSTRUCTIONS, ScreenTab.PROFILE),
      NavTabs.visible(allOff))
  }

  @Test
  fun `a tab disabled while the user is standing in it falls back to home`() {
    assertEquals(ScreenTab.HOME, NavTabs.resolve(ScreenTab.RADAR_MAP, allOff))
    assertEquals(ScreenTab.HOME, NavTabs.resolve(ScreenTab.NEWS_DISPATCHES, allOff))
    // Enabled, or not remotely killable, resolves to itself.
    assertEquals(ScreenTab.RADAR_MAP, NavTabs.resolve(ScreenTab.RADAR_MAP, allOn))
    assertEquals(ScreenTab.INSTRUCTIONS, NavTabs.resolve(ScreenTab.INSTRUCTIONS, allOff))
    assertEquals(ScreenTab.HOME, NavTabs.resolve(ScreenTab.HOME, allOff))
  }

  @Test
  fun `whatever the bar offers is always renderable`() {
    // The property that keeps the two copies of the rule from ever disagreeing:
    // for every flag combination, no visible tab may resolve away to something else.
    for (radar in listOf(true, false)) {
      for (news in listOf(true, false)) {
        val cfg = AppRemoteConfig(featureRadarEnabled = radar, featureDispatchesEnabled = news)
        for (tab in NavTabs.visible(cfg)) {
          assertEquals("[$cfg] visible tab $tab must render itself", tab, NavTabs.resolve(tab, cfg))
        }
      }
    }
  }

  @Test
  fun `with no remote config available every module stays visible`() {
    // Firebase absent leaves the StateFlow at its data-class default. If those
    // defaults ever flip to false, a device that cannot reach Firebase loses the
    // map and the feed entirely, so the safe-by-default behaviour is pinned here.
    val untouched = AppRemoteConfig()
    assertEquals(5, NavTabs.visible(untouched).size)
    assertEquals(ScreenTab.RADAR_MAP, NavTabs.resolve(ScreenTab.RADAR_MAP, untouched))
  }

  @Test
  fun `every tab has a label and a stable test tag`() {
    for (tab in ScreenTab.values()) {
      assertTrue("$tab needs a resource id", NavTabs.labelRes(tab) != 0)
      assertTrue("$tab needs a test tag", NavTabs.testTag(tab).isNotBlank())
    }
    assertEquals("Tag strings are instrumentation identifiers and must not drift",
      setOf("nav_home", "nav_radar_map", "nav_news", "nav_instructions", "nav_profile"),
      ScreenTab.values().map { NavTabs.testTag(it) }.toSet())
  }
}
