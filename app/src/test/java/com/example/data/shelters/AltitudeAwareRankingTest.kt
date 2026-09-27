package com.example.data.shelters

import com.example.data.model.DataProvenance
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ALTITUDE-AWARE RANKING (user rule: "choose safe locations based on
 * altitude and nearby places"). Contracts:
 *  - at EQUAL distance, a shelter on genuinely higher terrain (real fetched
 *    SRTM numbers) outranks one in a depression;
 *  - unknown altitude scores NEUTRAL, never a fabricated penalty, and the
 *    reason line says so;
 *  - the walking-impact distance curve makes 2 km clearly rank above 9 km.
 */
class AltitudeAwareRankingTest {

  private val origin = GeoPoint(17.0, 82.0)

  private fun zone(id: String, lat: Double, lon: Double) = SafeZone(
    id = id, name = "Shelter $id", lat = lat, lon = lon,
    locationNote = "test", capacityTotal = 200, capacityCurrent = 0,
    waterAvailable = true, foodAvailable = true, electricityAvailable = true,
    sanitationAvailable = true, medicalSupport = false, accessibility = "Road",
    womenChildrenSuitability = false, operatingStatus = "OPEN",
    verificationStatus = "FIELD", elevationNote = "",
    provenance = DataProvenance(source = "test")
  )

  private fun ctx(
    elevations: Map<String, Double>,
    originElev: Double?
  ) = SafeZoneEvaluator.RequestContext(
    origin = origin,
    hazards = emptyList<HazardZone>(),
    originElevationMeters = originElev,
    zoneElevations = elevations
  )

  @Test
  fun `at equal distance, higher terrain outranks a depression when altitude is known`() {
    // Mirror-image positions: straight-line distance from the origin is
    // EXACTLY equal, so the ONLY scoring difference is altitude.
    val east = zone("east", 17.001, 82.018)
    val west = zone("west", 17.001, 81.982)
    val evaluations = SafeZoneEvaluator.evaluateAll(
      listOf(west, east),
      // east = +100 m hill above us; west = 26 m BELOW us (a depression
      // that would collect flood water - the CAUTION branch).
      ctx(mapOf("east" to 106.0, "west" to -20.0), originElev = 6.0)
    )
    val e = evaluations.first { it.zone.id == "east" }
    val w = evaluations.first { it.zone.id == "west" }
    assertEquals("distances must be identical for this test", e.distanceMeters, w.distanceMeters, 0.01)
    assertTrue(
      "+100 m hill must beat an equal-distance flood pit: ${e.score} vs ${w.score}",
      e.score > w.score
    )
    assertTrue(e.reasons.any { it.text.contains("HIGHER") })
    assertTrue(w.reasons.any { it.text.contains("LOWER") })
  }

  @Test
  fun `unknown altitude stays neutral and admits it - never a fabricated verdict`() {
    val z = zone("no-alt", 17.001, 82.0)
    val unknown = SafeZoneEvaluator.evaluate(z, ctx(emptyMap(), originElev = null))
    val pit = SafeZoneEvaluator.evaluate(
      z, ctx(mapOf("no-alt" to -50.0), originElev = 6.0)
    )
    val hill = SafeZoneEvaluator.evaluate(
      z, ctx(mapOf("no-alt" to 200.0), originElev = 6.0)
    )
    // NEUTRAL sits strictly between a measured depression and a measured hill.
    assertTrue("unknown must beat a real depression", unknown.score > pit.score)
    assertTrue("unknown must lose to a real hill", unknown.score < hill.score)
    assertTrue(
      "the reason line must state altitude is not known",
      unknown.reasons.any { it.text.contains("not yet known") }
    )
    assertTrue(pit.reasons.none { it.text.contains("not yet known") })
  }

  @Test
  fun `walking impact - a 2 km shelter ranks above a 9 km shelter`() {
    val near = zone("near", 17.018, 82.0) // ~2.0 km due north
    val far = zone("far", 17.081, 82.0)   // ~9.0 km due north
    val ranked = SafeZoneEvaluator.ranked(listOf(far, near), ctx(emptyMap(), null))
    assertEquals("near", ranked.first().zone.id)
    val distances = SafeZoneEvaluator.evaluateAll(
      listOf(near, far), ctx(emptyMap(), null)
    ).associate { it.zone.id to it.distanceMeters }
    assertTrue("near really ~2 km", distances["near"]!! in 1_500.0..2_600.0)
    assertTrue("far really ~9 km", distances["far"]!! in 8_000.0..9_900.0)
  }
}
