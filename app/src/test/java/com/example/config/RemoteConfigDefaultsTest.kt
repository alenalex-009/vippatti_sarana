package com.example.config

import com.example.ui.components.NavTabs
import com.example.viewmodel.ScreenTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REGRESSION GUARD for the first-launch "only 3 of 5 tabs" bug.
 *
 * The manager used to read Firebase synchronously right after the ASYNC
 * setDefaultsAsync, so on a fresh install (no activated values yet, unset
 * booleans read as false) it published featureRadarEnabled=false and
 * featureDispatchesEnabled=false — and the first composition rendered only
 * HOME / GUIDE / PROFILE until a later fetch republished the real values.
 *
 * The contract pinned here: the registered defaults enable both killable
 * modules, and a config built from those defaults (what the manager publishes
 * once defaults are applied) shows ALL FIVE tabs on the very first launch.
 */
class RemoteConfigDefaultsTest {

  @Test
  fun `firebase defaults cover every key the manager reads`() {
    assertEquals(
      setOf(
        "home_padding",
        "primary_color",
        "secondary_color",
        "emergency_banner_text",
        "emergency_banner_enabled",
        "feature_radar_enabled",
        "feature_dispatches_enabled",
        "app_logo_url"
      ),
      RemoteConfigDefaults.firebaseDefaults.keys
    )
  }

  @Test
  fun `both killable modules default to true with correct types`() {
    val defaults = RemoteConfigDefaults.firebaseDefaults
    val radar = defaults["feature_radar_enabled"]
    val dispatches = defaults["feature_dispatches_enabled"]
    assertTrue("feature_radar_enabled must be a Boolean", radar is Boolean)
    assertTrue("feature_radar_enabled must default to true", radar == true)
    assertTrue("feature_dispatches_enabled must be a Boolean", dispatches is Boolean)
    assertTrue("feature_dispatches_enabled must default to true", dispatches == true)
    // Sanity on the surrounding types updateState() expects:
    assertTrue(defaults["home_padding"] is Int)
    assertTrue(defaults["emergency_banner_enabled"] == false)
  }

  @Test
  fun `config built from the firebase defaults shows all five tabs on first launch`() {
    val defaults = RemoteConfigDefaults.firebaseDefaults
    val firstLaunchConfig = AppRemoteConfig(
      homePadding = (defaults["home_padding"] as Int),
      primaryColorHex = defaults["primary_color"] as String,
      secondaryColorHex = defaults["secondary_color"] as String,
      emergencyBannerText = defaults["emergency_banner_text"] as String,
      emergencyBannerEnabled = defaults["emergency_banner_enabled"] as Boolean,
      featureRadarEnabled = defaults["feature_radar_enabled"] as Boolean,
      featureDispatchesEnabled = defaults["feature_dispatches_enabled"] as Boolean,
      appLogoUrl = defaults["app_logo_url"] as String
    )
    assertEquals(
      listOf(
        ScreenTab.HOME,
        ScreenTab.RADAR_MAP,
        ScreenTab.NEWS_DISPATCHES,
        ScreenTab.INSTRUCTIONS,
        ScreenTab.PROFILE
      ),
      NavTabs.visible(firstLaunchConfig)
    )
  }

  @Test
  fun `default config object also shows all five tabs`() {
    // _configState is seeded with AppRemoteConfig() at construction, so every
    // frame before Firebase answers anything must already be fully visible.
    assertEquals(5, NavTabs.visible(AppRemoteConfig()).size)
  }
}
