package com.example.ui.components

import com.example.R
import com.example.config.AppRemoteConfig
import com.example.viewmodel.ScreenTab

/**
 * Which destinations exist, and what becomes of the one you are standing on when
 * remote config switches its module off.
 *
 * Deliberately pure and Android-free apart from resource ids, because it has to
 * be readable by both the bar and the content switch. It used to live twice: an
 * `if (showRadar)` in the nav bar and an inline `when` in `MainActivity`. Two
 * copies of one rule is exactly how a killed module keeps a hidden tab that still
 * renders its screen.
 */
object NavTabs {

  /** Destinations in user-journey order: quiet safety check -> map -> news -> guide -> profile. */
  val ordered: List<ScreenTab> = listOf(
    ScreenTab.HOME,
    ScreenTab.RADAR_MAP,
    ScreenTab.NEWS_DISPATCHES,
    ScreenTab.INSTRUCTIONS,
    ScreenTab.PROFILE
  )

  /**
   * HOME, GUIDE and PROFILE always exist: they carry the offline survival manual
   * and the user's own saved data, so no server switch may strand them. RADAR_MAP
   * and NEWS_DISPATCHES are the only remotely killable modules.
   */
  fun isVisible(tab: ScreenTab, config: AppRemoteConfig): Boolean = when (tab) {
    ScreenTab.RADAR_MAP -> config.featureRadarEnabled
    ScreenTab.NEWS_DISPATCHES -> config.featureDispatchesEnabled
    ScreenTab.HOME, ScreenTab.INSTRUCTIONS, ScreenTab.PROFILE -> true
  }

  /** The tabs to draw, in order. */
  fun visible(config: AppRemoteConfig): List<ScreenTab> = ordered.filter { isVisible(it, config) }

  /**
   * The tab to actually render. A module switched off mid-session while the user
   * is standing inside it falls back to HOME rather than leaving them on a screen
   * with no entry point.
   */
  fun resolve(tab: ScreenTab, config: AppRemoteConfig): ScreenTab =
    if (isVisible(tab, config)) tab else ScreenTab.HOME

  fun labelRes(tab: ScreenTab): Int = when (tab) {
    ScreenTab.HOME -> R.string.nav_home
    ScreenTab.RADAR_MAP -> R.string.nav_map
    ScreenTab.NEWS_DISPATCHES -> R.string.nav_news
    ScreenTab.INSTRUCTIONS -> R.string.nav_guide
    ScreenTab.PROFILE -> R.string.nav_profile
  }

  /** Test tags are stable identifiers, not prose: instrumentation depends on them. */
  fun testTag(tab: ScreenTab): String = when (tab) {
    ScreenTab.HOME -> "nav_home"
    ScreenTab.RADAR_MAP -> "nav_radar_map"
    ScreenTab.NEWS_DISPATCHES -> "nav_news"
    ScreenTab.INSTRUCTIONS -> "nav_instructions"
    ScreenTab.PROFILE -> "nav_profile"
  }
}