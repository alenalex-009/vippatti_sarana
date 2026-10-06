package com.example.data.disaster

import com.example.data.model.DataStatus
import com.example.data.model.HazardSeverity
import com.example.viewmodel.VippattiUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIRMS INDICATOR + LIVE/DEMO SEPARATION.
 *
 *  - The compact map chip must read the REAL freshness of the FIRMS shard:
 *    NASA FIRMS • LIVE / RECENT / UNAVAILABLE (with an explicit SYNCING state
 *    before any result exists) — never a guess.
 *  - FIRMS fire detections are observations: they must NOT be converted into
 *    hazard zones (no fake fire polygons), while citizen fire reports and the
 *    other live providers keep their zone semantics.
 *  - The Active Fires layer is on by default so real hotspots are actually
 *    drawn (the original bug: fetched, parsed, never visible).
 */
class FirmsIndicatorAndLiveModeTest {

  private val now = 1_800_000_000_000L

  private fun firmsState(
    live: Boolean = false,
    fromCache: Boolean = false,
    kind: ProviderFailureKind? = null,
    message: String? = null,
    count: Int = 12
  ) = ProviderState(
    source = DisasterSource.NASA_FIRMS,
    eventCount = count,
    fetchedAtMillis = if (fromCache) now - 3_600_000L else now,
    isFromCache = fromCache,
    isLive = live,
    statusMessage = message,
    failureKind = kind
  )

  private fun stateWith(
    providerStates: List<ProviderState> = emptyList(),
    syncing: Boolean = false
  ) = VippattiUiState(
    providerStates = providerStates,
    isDisasterSyncing = syncing,
    disasterLastSyncMillis = now
  )

  // ------------------------------------------------------------- indicator

  @Test
  fun `a fresh FIRMS fetch reads LIVE`() {
    val ui = stateWith(listOf(firmsState(live = true)))
    assertEquals("NASA FIRMS \u2022 LIVE", ui.firmsIndicatorLabel)
    assertEquals(DataStatus.SUCCESS, ui.firmsIndicatorStatus)
  }

  @Test
  fun `a usable cached shard reads RECENT, never LIVE`() {
    val ui = stateWith(listOf(firmsState(fromCache = true)))
    assertEquals("NASA FIRMS \u2022 RECENT", ui.firmsIndicatorLabel)
    assertEquals(DataStatus.STALE, ui.firmsIndicatorStatus)
  }

  @Test
  fun `a cached shard kept through a failure still reads RECENT, not UNAVAILABLE`() {
    // The events ARE on screen (from cache); the chip must describe the data
    // shown, and the failure reason travels separately in provider states.
    val ui = stateWith(
      listOf(firmsState(fromCache = true, kind = ProviderFailureKind.FAILED, message = "offline"))
    )
    assertEquals("NASA FIRMS \u2022 RECENT", ui.firmsIndicatorLabel)
  }

  @Test
  fun `a rejected credential, a runtime failure and a missing key all read UNAVAILABLE`() {
    val rejected = stateWith(
      listOf(firmsState(kind = ProviderFailureKind.AUTHENTICATION_FAILED, message = "HTTP 403"))
    )
    val failed = stateWith(
      listOf(firmsState(kind = ProviderFailureKind.FAILED, message = "offline"))
    )
    val unconfigured = stateWith(listOf(firmsState(kind = ProviderFailureKind.UNCONFIGURED)))
    assertEquals("NASA FIRMS \u2022 UNAVAILABLE", rejected.firmsIndicatorLabel)
    assertEquals("NASA FIRMS \u2022 UNAVAILABLE", failed.firmsIndicatorLabel)
    assertEquals("NASA FIRMS \u2022 UNAVAILABLE", unconfigured.firmsIndicatorLabel)
    assertEquals(DataStatus.ERROR, rejected.firmsIndicatorStatus)
    assertEquals(DataStatus.NOT_CONFIGURED, unconfigured.firmsIndicatorStatus)
  }

  @Test
  fun `before any FIRMS result exists the chip says SYNCING or UNAVAILABLE, never LIVE`() {
    assertEquals("NASA FIRMS \u2022 SYNCING\u2026", stateWith(syncing = true).firmsIndicatorLabel)
    assertEquals(DataStatus.LOADING, stateWith(syncing = true).firmsIndicatorStatus)
    assertEquals("NASA FIRMS \u2022 UNAVAILABLE", stateWith().firmsIndicatorLabel)
    assertEquals(DataStatus.UNAVAILABLE, stateWith().firmsIndicatorStatus)
  }

  @Test
  fun `an empty but successful FIRMS fetch is still LIVE - zero observations is content, not failure`() {
    val ui = stateWith(listOf(firmsState(live = true, count = 0)))
    assertEquals("NASA FIRMS \u2022 LIVE", ui.firmsIndicatorLabel)
    assertEquals(DataStatus.SUCCESS, ui.firmsIndicatorStatus)
  }

  // ------------------------------------------- observation vs hazard zone

  private fun firmsObservation() = DisasterEvent(
    id = "firms-obs-1",
    source = DisasterSource.NASA_FIRMS,
    sourceEventId = "firms-obs-1",
    disasterType = DisasterType.WILDFIRE,
    title = "Active Fire Detection",
    description = "Satellite fire/hotspot detection from NASA FIRMS.",
    geometry = EventGeometry.Point(21.0, 79.0),
    latitude = 21.0,
    longitude = 79.0,
    severity = HazardSeverity.MODERATE,
    confidence = EventConfidence.NOMINAL,
    observedAtMillis = now - 3_600_000L,
    updatedAtMillis = now - 3_600_000L,
    origin = EventOrigin.OBSERVED,
    details = EventDetails.Fire("N", "VIIRS", 6.0, "D")
  )

  private fun quake() = DisasterEvent(
    id = "usgs-1",
    source = DisasterSource.USGS,
    sourceEventId = "usgs-1",
    disasterType = DisasterType.EARTHQUAKE,
    title = "M 4.2 Earthquake",
    description = "USGS observed earthquake.",
    geometry = EventGeometry.Point(12.0, 78.0),
    latitude = 12.0,
    longitude = 78.0,
    severity = HazardSeverity.MODERATE,
    observedAtMillis = now - 3_600_000L,
    updatedAtMillis = now - 3_600_000L,
    origin = EventOrigin.OBSERVED
  )

  private fun userFireReport() = DisasterEvent(
    id = "user-1",
    source = DisasterSource.USER_REPORT,
    sourceEventId = "user-1",
    disasterType = DisasterType.WILDFIRE,
    title = "Fire",
    description = "Citizen-reported fire.",
    geometry = EventGeometry.Point(11.5, 77.5),
    latitude = 11.5,
    longitude = 77.5,
    severity = HazardSeverity.HIGH,
    observedAtMillis = now - 600_000L,
    updatedAtMillis = now - 600_000L,
    origin = EventOrigin.REPORTED,
    details = EventDetails.UserIncident("Fire", "smoke on the ridge")
  )

  @Test
  fun `FIRMS observations never become hazard zones - no fake fire circles`() {
    val zones = toHazardZones(liveZoneEvents(listOf(firmsObservation()), now))
    assertTrue("a FIRMS detection must not produce a hazard zone", zones.isEmpty())
    // And the normalizer itself would have produced a 750 m circle if the
    // event had leaked through — proving the exclusion is the only guard.
    assertEquals(1, toHazardZones(listOf(firmsObservation())).size)
  }

  @Test
  fun `citizen fire reports and other providers keep their zone semantics`() {
    val zones = toHazardZones(liveZoneEvents(listOf(firmsObservation(), quake(), userFireReport()), now))
    assertEquals(2, zones.size)
    assertTrue(zones.any { it.type == com.example.data.model.HazardType.EARTHQUAKE })
    assertTrue(zones.any { it.type == com.example.data.model.HazardType.FIRE })
    // The surviving fire zone is the REPORTED one, not the satellite detection.
    assertTrue(
      zones.single { it.type == com.example.data.model.HazardType.FIRE }
        .provenance.source.contains("User", ignoreCase = true)
    )
  }

  @Test
  fun `expired or undated events are dropped from zone conversion`() {
    val expired = firmsObservation().copy(
      id = "old-quake", sourceEventId = "old-quake", source = DisasterSource.USGS,
      observedAtMillis = 0L, updatedAtMillis = 0L
    )
    assertTrue(liveZoneEvents(listOf(expired), now).isEmpty())
  }

  // ------------------------------------------------------- layer defaults

  @Test
  fun `the Active Fires layer is enabled by default so real hotspots are drawn`() {
    val ui = VippattiUiState()
    assertTrue(
      "ACTIVE_FIRES must be on by default (FIRMS visibility fix)",
      DisasterLayer.ACTIVE_FIRES in ui.enabledLayers
    )
    assertTrue(DisasterLayer.ACTIVE_FIRES.defaultOn)
    // Zoom LOD still applies at render time.
    assertFalse(DisasterLayer.ACTIVE_FIRES.isVisibleAt(4.0, enabled = true))
    assertTrue(DisasterLayer.ACTIVE_FIRES.isVisibleAt(9.5, enabled = true))
  }

  // ------------------------------------------------ cold-start cache gap

  private fun usgsState() = ProviderState(
    source = DisasterSource.USGS,
    eventCount = 3,
    fetchedAtMillis = now - 3_600_000L,
    isFromCache = true,
    isLive = false,
    statusMessage = null
  )

  @Test
  fun `a cached feed that never fetched FIRMS reports the provider as missing`() {
    val repository = DisasterDataRepository(
      providers = listOf(
        FirmsFireProviderForTest(),
        UsgsProviderForTest()
      ),
      cache = MemoryDisasterCache(),
      clock = { now },
      cacheDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
    )
    // Only USGS reported (its shard was cached); FIRMS has never fetched.
    assertTrue(
      "a provider without any shard must count as missing",
      repository.hasMissingProvider(listOf(usgsState()))
    )
    // Once every provider has a state, nothing is missing.
    assertFalse(
      repository.hasMissingProvider(
        listOf(usgsState(), firmsState(live = true))
      )
    )
  }

  /** Minimal stand-ins so the test does not build real HTTP clients. */
  private class FirmsFireProviderForTest : DisasterDataProvider {
    override val providerId = DisasterSource.NASA_FIRMS
    override suspend fun fetchIndiaEvents() = ProviderResult.Failure("test", ProviderFailureKind.FAILED)
  }

  private class UsgsProviderForTest : DisasterDataProvider {
    override val providerId = DisasterSource.USGS
    override suspend fun fetchIndiaEvents() = ProviderResult.Failure("test", ProviderFailureKind.FAILED)
  }
}
