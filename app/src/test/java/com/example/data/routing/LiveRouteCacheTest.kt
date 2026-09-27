package com.example.data.routing

import com.example.data.disaster.PilotRegionData
import com.example.data.routing.GeoPoint
import com.example.data.routing.LiveRouteCache
import com.example.data.routing.RouteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Live road-route cache contracts: repeat views of the same shelter paint
 * exact cached roads instantly; anything that changes the answer (position,
 * mode, shelter, hazard picture) is a miss and revalidates over the network.
 */
class LiveRouteCacheTest {

  private fun liveRoute(points: List<GeoPoint> = listOf(GeoPoint(20.0, 78.0), GeoPoint(20.1, 78.1))) =
    RouteResult(
      distanceMeters = 12_000.0,
      durationSeconds = 9_000.0,
      pathPoints = points,
      steps = emptyList(),
      isLiveOsrm = true,
      summary = "Test road route",
      travelMode = TravelMode.FOOT
    )

  private val origin = GeoPoint(20.5937, 78.9629)
  private val hazards = PilotRegionData.hazardZones.take(3)

  @Test
  fun `same inputs produce a stable key`() {
    val a = LiveRouteCache.key("sz-1", TravelMode.FOOT, origin, hazards)
    val b = LiveRouteCache.key("sz-1", TravelMode.FOOT, origin, hazards)
    assertEquals(a, b)
  }

  @Test
  fun `small gps jitter reuses but a real move misses`() {
    val base = LiveRouteCache.key("sz-1", TravelMode.FOOT, origin, hazards)
    val jitter = LiveRouteCache.key(
      "sz-1", TravelMode.FOOT, GeoPoint(origin.lat + 0.001, origin.lon + 0.001), hazards
    )
    assertEquals(base, jitter)
    val moved = LiveRouteCache.key(
      "sz-1", TravelMode.FOOT, GeoPoint(origin.lat + 0.05, origin.lon), hazards
    )
    assertNotEquals(base, moved)
  }

  @Test
  fun `mode shelter or hazard change misses`() {
    val base = LiveRouteCache.key("sz-1", TravelMode.FOOT, origin, hazards)
    assertNotEquals(base, LiveRouteCache.key("sz-1", TravelMode.DRIVING, origin, hazards))
    assertNotEquals(base, LiveRouteCache.key("sz-2", TravelMode.FOOT, origin, hazards))
    assertNotEquals(base, LiveRouteCache.key("sz-1", TravelMode.FOOT, origin, hazards.drop(1)))
  }

  @Test
  fun `put get round trip returns exact road geometry`() {
    val cache = LiveRouteCache()
    val key = LiveRouteCache.key("sz-1", TravelMode.FOOT, origin, hazards)
    assertNull(cache.get(key))
    val route = liveRoute()
    cache.put(key, route)
    assertEquals(route, cache.get(key))
    assertNotNull(cache.get(key))
  }

  @Test
  fun `offline corridors and empty paths are never cached`() {
    val cache = LiveRouteCache()
    val key = LiveRouteCache.key("sz-1", TravelMode.FOOT, origin, hazards)
    cache.put(key, liveRoute().copy(isLiveOsrm = false))
    assertNull(cache.get(key))
    cache.put(key, liveRoute(emptyList()))
    assertNull(cache.get(key))
  }

  @Test
  fun `oldest entries evicted beyond capacity`() {
    val cache = LiveRouteCache(maxEntries = 2)
    val k1 = LiveRouteCache.key("sz-1", TravelMode.FOOT, origin, hazards)
    val k2 = LiveRouteCache.key("sz-2", TravelMode.FOOT, origin, hazards)
    val k3 = LiveRouteCache.key("sz-3", TravelMode.FOOT, origin, hazards)
    cache.put(k1, liveRoute())
    cache.put(k2, liveRoute())
    cache.put(k3, liveRoute())
    assertEquals(2, cache.size())
    assertNull(cache.get(k1))
    assertNotNull(cache.get(k3))
  }

  // ---------------------------------------------------------- B7: LRU + TTL

  @Test
  fun `eviction is least-recently-used - a read protects the entry`() {
    var clock = 0L
    val cache = LiveRouteCache(maxEntries = 2, ttlMillis = 0L, now = { clock })
    val k1 = LiveRouteCache.key("sz-1", TravelMode.FOOT, origin, hazards)
    val k2 = LiveRouteCache.key("sz-2", TravelMode.FOOT, origin, hazards)
    val k3 = LiveRouteCache.key("sz-3", TravelMode.FOOT, origin, hazards)
    cache.put(k1, liveRoute())
    cache.put(k2, liveRoute())
    clock += 1
    cache.get(k1)                    // k1 becomes most recently used
    clock += 1
    cache.put(k3, liveRoute())       // capacity pressure must drop k2 (LRU)
    assertNotNull("recently-read entry must survive eviction", cache.get(k1))
    assertNull("least-recently-used entry must be the one evicted", cache.get(k2))
    assertNotNull(cache.get(k3))
  }

  @Test
  fun `entries expire after the TTL and force revalidation`() {
    var clock = 1000L
    val cache = LiveRouteCache(
      ttlMillis = LiveRouteCache.DEFAULT_TTL_MILLIS,
      now = { clock }
    )
    val key = LiveRouteCache.key("sz-1", TravelMode.FOOT, origin, hazards)
    cache.put(key, liveRoute())
    clock += LiveRouteCache.DEFAULT_TTL_MILLIS / 2
    assertNotNull("half-life read must still hit", cache.get(key))
    clock += LiveRouteCache.DEFAULT_TTL_MILLIS          // now well past TTL
    assertNull("expired route must NOT be served as fresh", cache.get(key))
    assertEquals("expired entry is dropped, not kept", 0, cache.size())
  }

  @Test
  fun `a refreshed put restarts the TTL`() {
    var clock = 0L
    val cache = LiveRouteCache(ttlMillis = 100L, now = { clock })
    val key = LiveRouteCache.key("sz-1", TravelMode.FOOT, origin, hazards)
    cache.put(key, liveRoute())
    clock += 90
    cache.put(key, liveRoute())      // revalidated: timer restarts
    clock += 90                      // 90 < 100 since the refresh
    assertNotNull(cache.get(key))
  }
}
