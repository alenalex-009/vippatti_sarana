package com.example.data.disaster

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FIRMS LIVE-MODE CONTRACT (source-level, LocationPermissionUxTest pattern).
 *
 * Some guarantees live at the wiring sites, not in pure functions, so they
 * are pinned structurally against the real sources:
 *
 *  1. LIVE vs DEMO separation: the map receives real provider events ONLY
 *     when the demo overlay is hidden; DEMO keeps its labelled simulated
 *     network (and hides live pins) without ever mutating the stored events.
 *  2. No simulated fire data in LIVE mode: the FIRMS provider is the only
 *     producer of WILDFIRE events with source NASA_FIRMS and it fabricates
 *     nothing (no mock branch, no fallback rows).
 *  3. FIRMS detections never flow into hazard-zone conversion (they must not
 *     become fake fire polygons), while user reports still do.
 *  4. The FIRMS freshness chip is wired from the real provider state.
 */
class FirmsLiveModeContractTest {

  private val radarScreen: String by lazy {
    File(
      System.getProperty("user.dir"),
      "src/main/java/com/example/ui/screens/RadarMapScreen.kt"
    ).readText()
  }

  private val viewModel: String by lazy {
    File(
      System.getProperty("user.dir"),
      "src/main/java/com/example/viewmodel/VippattiViewModel.kt"
    ).readText()
  }

  private val firmsProvider: String by lazy {
    File(
      System.getProperty("user.dir"),
      "src/main/java/com/example/data/disaster/providers/FirmsFireProvider.kt"
    ).readText()
  }

  private val uiState: String by lazy {
    File(
      System.getProperty("user.dir"),
      "src/main/java/com/example/viewmodel/VippattiUiState.kt"
    ).readText()
  }

  @Test
  fun `the map gate hides live pins only while the demo overlay is visible`() {
    // The exact gate: demo ON -> empty list (labelled demo story), demo OFF
    // -> the real stored provider events. No other filtering of live data.
    assertTrue(
      "RadarMapScreen must gate live disaster events on isMockDataVisible",
      Regex(
        "disasterEvents\\s*=\\s*if \\(uiState\\.isMockDataVisible\\) emptyList\\(\\)\\s*else uiState\\.disasterEvents"
      ).containsMatchIn(radarScreen)
    )
  }

  @Test
  fun `the FIRMS freshness chip is wired from the real provider state`() {
    assertTrue(
      "the map must pass the FIRMS indicator derived from providerStates",
      "firmsLabel = uiState.firmsIndicatorLabel" in radarScreen &&
        "firmsStatus = uiState.firmsIndicatorStatus" in radarScreen
    )
    assertTrue(
      "the indicator label vocabulary must be LIVE/RECENT/UNAVAILABLE/SYNCING",
      listOf("NASA FIRMS \\u2022 LIVE", "NASA FIRMS \\u2022 RECENT",
        "NASA FIRMS \\u2022 UNAVAILABLE").all { it in uiState }
    )
  }

  @Test
  fun `FIRMS detections are excluded from hazard-zone conversion`() {
    // The intelligence pass must route events through liveZoneEvents (which
    // drops NASA_FIRMS observations) — never straight toHazardZones on the
    // raw disasterEvents list.
    assertTrue(
      "recomputeIntelligence must use liveZoneEvents for live zones",
      "toHazardZones(liveZoneEvents(state.disasterEvents, now))" in viewModel
    )
    assertFalse(
      "raw disasterEvents must not feed toHazardZones directly (fake fire circles)",
      Regex("toHazardZones\\(state\\.disasterEvents").containsMatchIn(viewModel)
    )
    val repository = File(
      System.getProperty("user.dir"),
      "src/main/java/com/example/data/disaster/DisasterDataRepository.kt"
    ).readText()
    assertTrue(
      "liveZoneEvents must filter NASA_FIRMS observations out of zone conversion",
      "it.source != DisasterSource.NASA_FIRMS" in repository
    )
  }

  @Test
  fun `the FIRMS provider has no simulated fallback - it can only emit real rows`() {
    // No mock/demo branch anywhere in the provider: every emitted event is
    // parsed from the actual HTTP response body.
    assertFalse(
      "FIRMS provider must not fabricate data",
      Regex("mock|fake|simulat", RegexOption.IGNORE_CASE).containsMatchIn(firmsProvider)
    )
    assertTrue(
      "FIRMS provider must parse the provider CSV body",
      "FirmsCsvParser.parse(body)" in firmsProvider
    )
  }

  @Test
  fun `cold start never leaves a never-cached provider unfetched`() {
    // Cache-first is for speed, not for hiding a provider that has no shard
    // yet (e.g. NASA FIRMS on a device that only ever cached USGS/IMD):
    // after applying the cached feed the ViewModel must check for uncovered
    // providers and refresh.
    assertTrue(
      "cold start must refresh when a provider has no cached shard",
      "hasMissingProvider(cached.providerStates)" in viewModel &&
        "if (disasterRepository.hasMissingProvider(cached.providerStates)) {" in viewModel
    )
  }

  @Test
  fun `demo separation lives only at the display gate - stored events are never mutated`() {
    // toggleMockData/setMockMode may flip visibility flags; they must not
    // clear or rewrite the real fetched events.
    listOf("fun setMockMode(", "fun toggleMockData(").forEach { fn ->
      val idx = viewModel.indexOf(fn)
      assertTrue("expected $fn in the ViewModel", idx >= 0)
      val body = viewModel.substring(idx, minOf(viewModel.length, idx + 900))
      assertFalse(
        "$fn must not clear disasterEvents",
        "disasterEvents = emptyList()" in body
      )
    }
  }
}
