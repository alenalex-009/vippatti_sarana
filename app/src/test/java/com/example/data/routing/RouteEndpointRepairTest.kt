package com.example.data.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * User bugs #1 + "make sure alternate routes also end up to the relocated
 * places correctly": OSRM snaps route ends to the nearest ROAD, so every
 * corridor - active OR alternative - must be repaired to start at the USER
 * and end at the SHELTER door. This is the pure geometry function used by
 * every parse, so one test covers all routes.
 */
class RouteEndpointRepairTest {

  private fun p(lat: Double, lon: Double) = GeoPoint(lat, lon)

  @Test
  fun `off-road destination is appended so the line ends at the shelter`() {
    val origin = p(17.0, 82.0)
    val shelter = p(17.009, 82.0)          // ~1 km north, off-road
    val snapped = listOf(p(17.0, 82.0005), p(17.008, 82.0)) // road ends ~110 m short
    val repaired = OsrmRoutingService.repairEndpoints(snapped, origin, shelter)
    assertEquals(shelter, repaired.last())
    assertTrue(
      "repaired line must end AT the shelter door",
      com.example.data.model.GeoMath.distanceMeters(repaired.last(), shelter) < 1.0
    )
  }

  @Test
  fun `off-road origin is prepended so the line starts at the user`() {
    val user = p(17.0, 82.0)
    val shelter = p(17.009, 82.0)
    val snapped = listOf(p(17.001, 82.001), p(17.0088, 82.0))
    val repaired = OsrmRoutingService.repairEndpoints(snapped, user, shelter)
    assertEquals(user, repaired.first())
    assertEquals(shelter, repaired.last())
    // interior road geometry is preserved untouched, in order
    assertEquals(snapped, repaired.subList(1, repaired.size - 1))
  }

  @Test
  fun `nearby endpoints within 20 m are NOT duplicated`() {
    val user = p(17.0, 82.0)
    val shelter = p(17.009, 82.0)
    val snapped = listOf(p(17.00005, 82.0), p(17.00899, 82.0)) // both on-road
    val repaired = OsrmRoutingService.repairEndpoints(snapped, user, shelter)
    assertEquals(snapped, repaired)
  }

  @Test
  fun `empty geometry falls back to the straight user-to-shelter line`() {
    val user = p(17.0, 82.0)
    val shelter = p(17.009, 82.0)
    assertEquals(listOf(user, shelter), OsrmRoutingService.repairEndpoints(emptyList(), user, shelter))
  }
}
