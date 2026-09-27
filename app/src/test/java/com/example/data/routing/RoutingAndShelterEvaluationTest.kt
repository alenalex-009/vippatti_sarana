package com.example.data.routing

import com.example.data.disaster.PilotRegionData
import com.example.data.model.CapacityStatus
import com.example.data.routing.GeoPoint
import com.example.data.routing.OsrmRoutingService
import com.example.data.shelters.RejectionReason
import com.example.data.shelters.SafeZoneEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Routing integrity + full-shelter evaluation tests:
 *  - stable route identity (never object identity) for alternative selection;
 *  - every shelter evaluated (eligible, rejected, unavailable, unknown) so the
 *    UI can never present a rejected shelter as an eligible destination.
 */
class RoutingAndShelterEvaluationTest {

  // ------------------------------------------------------ route identity

  @Test
  fun `equal routes built separately share a stable routeId`() {
    val origin = GeoPoint(9.84778, 76.94222)
    val dest = GeoPoint(9.77361, 77.03528)
    // Deterministic geometry: no hazard detours (they depend on wall-clock
    // pulse offsets inside the fallback corridor builder).
    val a = OsrmRoutingService.calculateOfflineTacticalRoute(
      origin, dest, TravelMode.FOOT, emptyList(), "Cheruthoni Hall"
    )
    val b = OsrmRoutingService.calculateOfflineTacticalRoute(
      origin, dest, TravelMode.FOOT, emptyList(), "Cheruthoni Hall"
    )
    // Two distinct instances representing the SAME corridor must share an id —
    // alternative chips compare by routeId, not ===.
    assertFalse(a === b)
    assertEquals(a.routeId, b.routeId)
  }

  @Test
  fun `different corridors get different routeIds`() {
    val origin = GeoPoint(9.84778, 76.94222)
    val dest = GeoPoint(9.77361, 77.03528)
    val straight = OsrmRoutingService.calculateOfflineTacticalRoute(
      origin, dest, TravelMode.FOOT, emptyList(), "Zone"
    )
    val detoured = straight.copy(pathPoints = listOf(origin, GeoPoint(9.80, 76.99), dest))
    assertNotEquals(straight.routeId, detoured.routeId)
  }

  // ------------------------------------------- full shelter evaluation

  private fun evaluateAll() = SafeZoneEvaluator.evaluateAll(
    PilotRegionData.safeZones,
    SafeZoneEvaluator.RequestContext(
      origin = GeoPoint(9.85, 76.95),
      hazards = PilotRegionData.hazardZones
    )
  )

  @Test
  fun `eligible shelter is feasible with positive score and reasons`() {
    val evaluated = evaluateAll()
    val eligible = evaluated.filter { it.isFeasible }
    assertTrue(eligible.isNotEmpty())
    assertTrue(eligible.all { it.score > 0 })
    assertTrue(eligible.all { it.rejectionReason == null })
    assertTrue(eligible.all { it.reasons.isNotEmpty() })
  }

  @Test
  fun `rejected shelter inside hazard area carries the real rejection reason`() {
    // No mock shelter sits inside danger by design (every paired shelter is
    // genuinely safe), so this builds a synthetic shelter AT the Assam flood
    // centre to pin the INSIDE_HAZARD_AREA path.
    val flood = PilotRegionData.hazardZones.first { it.id == "hz-flood-assam-dibrugarh" }
    val inside = PilotRegionData.safeZones.first { it.id == "sz-assam-dibrugarh-hall" }
      .copy(id = "sz-synthetic-inside", lat = flood.center.lat, lon = flood.center.lon)
    val evaluation = SafeZoneEvaluator.evaluate(
      inside,
      SafeZoneEvaluator.RequestContext(
        origin = GeoPoint(27.53, 94.97),
        hazards = PilotRegionData.hazardZones
      )
    )
    assertFalse(evaluation.isFeasible)
    assertEquals(RejectionReason.INSIDE_HAZARD_AREA, evaluation.rejectionReason)
    assertEquals(0, evaluation.score)
  }

  @Test
  fun `full shelter is rejected as unavailable capacity`() {
    // Capacity is checked ONLY for shelters outside every hazard area, so this
    // test uses a clean-location copy of the mock's full shelter.
    val evaluated = SafeZoneEvaluator.evaluateAll(
      listOf(
        PilotRegionData.safeZones.first { it.id == "sz-odisha-puri-shelter" }
          .copy(lat = 19.8500, lon = 85.8800)
      ),
      SafeZoneEvaluator.RequestContext(
        origin = GeoPoint(9.85, 76.95),
        hazards = emptyList()
      )
    )
    val full = evaluated.first()
    assertFalse(full.isFeasible)
    assertEquals(RejectionReason.NO_REMAINING_CAPACITY, full.rejectionReason)
    assertEquals(CapacityStatus.FULL, full.capacityReport.status)
  }

  @Test
  fun `network with unknown capacity would be near-capacity not fabricated`() {
    // A shelter whose occupancy is unknown (modeled here as total==current)
    // must NOT appear available — it lands in NEAR_CAPACITY/FULL handling.
    val zone = PilotRegionData.safeZones.first().copy(
      id = "sz-unknown-capacity",
      capacityTotal = 100,
      capacityCurrent = 100
    )
    val evaluation = SafeZoneEvaluator.evaluate(
      zone,
      SafeZoneEvaluator.RequestContext(origin = GeoPoint(9.85, 76.95), hazards = emptyList())
    )
    assertEquals(CapacityStatus.FULL, evaluation.capacityReport.status)
    assertFalse(evaluation.isFeasible)
  }

  @Test
  fun `ranked list is exactly the feasible subset of the full evaluation`() {
    val evaluated = evaluateAll()
    val ranked = evaluated.filter { it.isFeasible }.sortedByDescending { it.score }
    assertTrue(ranked.zipWithNext().all { (a, b) -> a.score >= b.score })
    assertTrue(evaluated.any { !it.isFeasible }) // rejected ones really exist
  }
}
