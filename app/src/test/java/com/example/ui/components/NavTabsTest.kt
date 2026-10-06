package com.example.ui.components

import com.example.config.AppRemoteConfig
import com.example.viewmodel.ScreenTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRODUCTION CONTRACT: the bottom bar and the content switch both use NavTabs,
 * and the rule they agree on is simple — all five primary tabs are always visible.
 *
 * Map (ScreenTab.RADAR_MAP) and News (ScreenTab.NEWS_DISPATCHES) are CORE pages.
 * Remote Config may still control functionality or content INSIDE those screens,
 * but it must never remove those two tabs from the primary bottom navigation.
 *
 * The previous contract let Remote Config hide Map and News; that is what made a
 * first-launch device with no Firebase answer (or a config returning false) render
 * only Home | Guide | Profile. That is now forbidden.
 */
class NavTabsTest {

  @Test
  fun `all five primary tabs are always visible`() {
    // Spot-check with both flags on and both flags off, plus the default constructor.
    val allOn = AppRemoteConfig(featureRadarEnabled = true, featureDispatchesEnabled = true)
    val allOff = AppRemoteConfig(featureRadarEnabled = false, featureDispatchesEnabled = false)
    val default = AppRemoteConfig()

    val expected = listOf(
      ScreenTab.HOME,
      ScreenTab.RADAR_MAP,
      ScreenTab.NEWS_DISPATCHES,
      ScreenTab.INSTRUCTIONS,
      ScreenTab.PROFILE
    )

    assertEquals(expected, NavTabs.visible(allOn))
    assertEquals(expected, NavTabs.visible(allOff))
    assertEquals(expected, NavTabs.visible(default))
  }

  @Test
  fun `each primary tab is individually always visible`() {
    val config = AppRemoteConfig(featureRadarEnabled = false, featureDispatchesEnabled = false)
    for (tab in NavTabs.ordered) {
      assertTrue(
        "$tab must be visible even when both feature flags are false",
        NavTabs.isVisible(tab, config)
      )
    }
  }

  @Test
  fun `resolve is the identity for every primary tab`() {
    // No primary tab is ever redirected away from itself, so the bar and the
    // content switch can never disagree on which screen to render.
    val config = AppRemoteConfig(featureRadarEnabled = false, featureDispatchesEnabled = false)
    for (tab in NavTabs.ordered) {
      assertEquals(
        "[$tab] resolve must return the same tab",
        tab,
        NavTabs.resolve(tab, config)
      )
    }
  }

  @Test
  fun `visible list always has exactly five tabs`() {
    val configs = listOf(
      AppRemoteConfig(featureRadarEnabled = true, featureDispatchesEnabled = true),
      AppRemoteConfig(featureRadarEnabled = false, featureDispatchesEnabled = false),
      AppRemoteConfig(featureRadarEnabled = true, featureDispatchesEnabled = false),
      AppRemoteConfig(featureRadarEnabled = false, featureDispatchesEnabled = true),
      AppRemoteConfig()
    )
    for (config in configs) {
      assertEquals(5, NavTabs.visible(config).size)
    }
  }

  @Test
  fun `visible is independent of remote config flag values`() {
    // Changing the feature flags never changes the navigation list.
    val base = AppRemoteConfig()
    val varied = base.copy(featureRadarEnabled = false, featureDispatchesEnabled = false)
    val flipped = base.copy(featureRadarEnabled = true, featureDispatchesEnabled = true)
    assertEquals(NavTabs.visible(base), NavTabs.visible(varied))
    assertEquals(NavTabs.visible(base), NavTabs.visible(flipped))
  }

  @Test
  fun `every tab has a label and a stable test tag`() {
    for (tab in ScreenTab.values()) {
      assertTrue("$tab needs a resource id", NavTabs.labelRes(tab) != 0)
      assertTrue("$tab needs a test tag", NavTabs.testTag(tab).isNotBlank())
    }
    assertEquals(
      "Tag strings are instrumentation identifiers and must not drift",
      setOf("nav_home", "nav_radar_map", "nav_news", "nav_instructions", "nav_profile"),
      ScreenTab.values().map { NavTabs.testTag(it) }.toSet()
    )
  }
}
