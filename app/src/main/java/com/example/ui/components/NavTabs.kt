package com.example.ui.components

import com.example.R
import com.example.config.AppRemoteConfig
import com.example.viewmodel.ScreenTab

/**
 * The five primary navigation destinations, in user-journey order: quiet safety
 * check -> map -> news -> guide -> profile.
 *
 * These are the app's core screens. Bottom-navigation visibility is NOT gated by
 * Remote Config, because a first-launch device with no Firebase answer (or a
 * config that returns false) must still show all five tabs: Home | Map | News |
 * Guide | Profile.
 *
 * Remote Config may still control functionality or content INSIDE Map and News,
 * but it must never remove those tabs from the primary bottom navigation.
 *
 * Deliberately pure and Android-free apart from resource ids, because it has to
 * be readable by both the bar and the content switch.
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
   * All five primary tabs are always visible. There is no Remote-Config kill switch
   * for core navigation. Home, Guide and Profile carry the offline survival manual
   * and the user's own saved data; Map and News are core pages, not optional modules.
   */
  fun isVisible(tab: ScreenTab, config: AppRemoteConfig): Boolean = when (tab) {
    ScreenTab.HOME,
    ScreenTab.RADAR_MAP,
    ScreenTab.NEWS_DISPATCHES,
    ScreenTab.INSTRUCTIONS,
    ScreenTab.PROFILE -> true
  }

  /** The tabs to draw, in order. Always all five. */
  fun visible(config: AppRemoteConfig): List<ScreenTab> = ordered

  /**
   * The tab to actually render. Every primary tab is always enabled, so resolve
   * is the identity function: a tab never gets redirected away from itself.
   */
  fun resolve(tab: ScreenTab, config: AppRemoteConfig): ScreenTab = tab

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