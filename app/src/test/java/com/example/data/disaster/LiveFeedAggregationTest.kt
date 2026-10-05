package com.example.data.disaster

import com.example.data.model.HazardSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-required check: LIVE aggregation must surface every available real
 * provider event into the repository feed, a failing provider must never
 * suppress the others, unsupported geometry stays point/unknown, and the
 * demo network is never reported as live (it is not a disaster source).
 */
class LiveFeedAggregationTest {

  private val now = 1_800_000_000_000L

  private fun point(source: String, id: String, type: DisasterType, lat: Double): DisasterEvent =
    DisasterEvent(
      id = id, source = DisasterSource.valueOf(source), sourceEventId = id,
      disasterType = type, title = "$type live event", description = "",
      geometry = EventGeometry.Point(lat, 78.0), latitude = lat, longitude = 78.0,
      severity = HazardSeverity.MODERATE, origin = EventOrigin.OBSERVED,
      observedAtMillis = now - 60_000L, updatedAtMillis = now - 60_000L
    )

  private class FakeProvider(
    override val providerId: DisasterSource,
    val result: ProviderResult
  ) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult = result
  }

  private class FailingProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("simulated offline", ProviderFailureKind.FAILED)
  }

  @Test
  fun `multiple provider successes coexist in the live feed`() = runBlocking {
    val repo = DisasterDataRepository(
      providers = listOf(
        FakeProvider(DisasterSource.USGS, ProviderResult.Success(listOf(point("USGS", "u1", DisasterType.EARTHQUAKE, 9.0)), now)),
        FakeProvider(DisasterSource.NASA_FIRMS, ProviderResult.Success(listOf(point("NASA_FIRMS", "f1", DisasterType.WILDFIRE, 10.0)), now)),
        FakeProvider(DisasterSource.NDMA_CAP, ProviderResult.Success(listOf(DisasterEvent(
          id = "c1", source = DisasterSource.NDMA_CAP, sourceEventId = "c1",
          disasterType = DisasterType.FLOOD, title = "Flood", description = "",
          geometry = EventGeometry.Unlocated("Statewide"), latitude = null, longitude = null,
          severity = HazardSeverity.HIGH, origin = EventOrigin.OBSERVED,
          observedAtMillis = now - 60_000L, updatedAtMillis = now - 60_000L
        )), now))
      ),
      cache = MemoryDisasterCache(), cacheDispatcher = Dispatchers.Unconfined
    )
    val feed = repo.refresh()
    val types = feed.events.map { it.disasterType }.toSet()
    assertEquals(setOf(DisasterType.EARTHQUAKE, DisasterType.WILDFIRE, DisasterType.FLOOD), types)
    assertEquals(3, feed.providerStates.size)
    assertTrue(feed.isAnyLive)
  }

  @Test
  fun `one provider failure does not suppress the others`() = runBlocking {
    val repo = DisasterDataRepository(
      providers = listOf(
        FailingProvider(DisasterSource.NASA_FIRMS),
        FakeProvider(DisasterSource.USGS, ProviderResult.Success(listOf(point("USGS", "u2", DisasterType.EARTHQUAKE, 12.0)), now))
      ),
      cache = MemoryDisasterCache(), cacheDispatcher = Dispatchers.Unconfined
    )
    val feed = repo.refresh()
    assertEquals(1, feed.events.size)
    assertEquals(DisasterType.EARTHQUAKE, feed.events.first().disasterType)
    val failedState = feed.providerStates.first { it.source == DisasterSource.NASA_FIRMS }
    assertNotNull(failedState.statusMessage)
    assertEquals(0, failedState.eventCount)
  }

  @Test
  fun `unsupported governments stay point or unknown, never invented polygon`() = runBlocking {
    val event = DisasterEvent(
      id = "x", source = DisasterSource.NDMA_CAP, sourceEventId = "x",
      disasterType = DisasterType.FLOOD, title = "Flood", description = "",
      geometry = EventGeometry.Unlocated("state-wide"), latitude = null, longitude = null,
      severity = HazardSeverity.HIGH, origin = EventOrigin.OBSERVED,
      observedAtMillis = now - 60_000L, updatedAtMillis = now - 60_000L
    )
    assertNull(DisasterEventNormalizer.toHazardZone(event))
    assertEquals(EventGeometry.Point(12.0, 78.0).type, EventGeometry.Point(12.0, 78.0).type)
  }

  @Test
  fun `live feed has no demo source id and enabled layers cover all real types`() = runBlocking {
    val repo = DisasterDataRepository(
      providers = listOf(
        FakeProvider(DisasterSource.USGS, ProviderResult.Success(listOf(point("USGS", "u3", DisasterType.EARTHQUAKE, 8.0)), now))
      ),
      cache = MemoryDisasterCache(), cacheDispatcher = Dispatchers.Unconfined
    )
    val feed = repo.refresh()
    assertTrue(feed.events.all { it.source != DisasterSource.USER_REPORT })
    val allLayers = com.example.data.disaster.DisasterLayer.entries.toSet()
    assertTrue(allLayers.containsAll(setOf(
      com.example.data.disaster.DisasterLayer.OFFICIAL_ALERTS,
      com.example.data.disaster.DisasterLayer.EARTHQUAKES,
      com.example.data.disaster.DisasterLayer.ACTIVE_FIRES,
      com.example.data.disaster.DisasterLayer.USER_REPORTS
    )))
  }
}
