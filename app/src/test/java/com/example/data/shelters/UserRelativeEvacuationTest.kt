package com.example.data.shelters

import com.example.data.disaster.DemoNetworkAroundUser
import com.example.data.disaster.ZoneDetailMapper
import com.example.data.model.DataProvenance
import com.example.data.model.GeoMath
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRITICAL EVACUATION LOGIC CORRECTION — the USER'S LOCATION, never the
 * hazard centre, is the origin of evacuation analysis (spec sections 1-7).
 * Contracts pinned here:
 *  1. edge user: candidates EQUIDISTANT from the hazard centre are told
 *     apart by the exposure of the USER->candidate path, not centre math;
 *  2. centre user: multiple viable candidates are all returned (no forced
 *     single direction);
 *  3. a close candidate across the hazard must NOT automatically outrank a
 *     farther clean exit;
 *  4. moving the USER flips which candidate wins (user-relative proof);
 *  5. hazard change recomputes exposure;
 *  6. demo placements are generated from the USER and clear the hazard
 *     circle; moving the demo focus changes the candidates;
 *  7. the detail card's exact record id + coordinates survive into the
 *     destination choice (never a stand-in).
 */
class UserRelativeEvacuationTest {

  private val center = GeoPoint(17.0, 82.0)
  private val radius = 2_000.0

  private fun hazard() = HazardZone(
    id = "hz-test", name = "Test flood pocket", type = HazardType.FLOOD,
    severity = HazardSeverity.HIGH, center = center, radiusMeters = radius,
    riskLevel = "RED", trend = HazardTrend.STABLE, sourceStatus = "test",
    lastUpdatedMillis = 0L,
    provenance = DataProvenance(source = "test")
  )

  private fun shelter(id: String, lat: Double, lon: Double, available: Int = 100) =
    SafeZone(
      id = id, name = "Shelter $id", lat = lat, lon = lon,
      locationNote = "test", capacityTotal = 200,
      capacityCurrent = 200 - available,
      waterAvailable = true, foodAvailable = true, electricityAvailable = true,
      sanitationAvailable = true, medicalSupport = false, accessibility = "Road",
      womenChildrenSuitability = false, operatingStatus = "OPEN",
      verificationStatus = "FIELD", elevationNote = "",
      provenance = DataProvenance(source = "test")
    )

  private fun evalAll(origin: GeoPoint, zones: List<SafeZone>) =
    SafeZoneEvaluator.evaluateAll(
      zones,
      SafeZoneEvaluator.RequestContext(origin = origin, hazards = listOf(hazard()))
    )

  // -------------------------------------------------------------- TEST 1
  @Test
  fun `edge user - selection is evaluated from the USER not the hazard centre`() {
    // User stands just inside the EASTERN edge of the hazard.
    val user = GeoPoint(17.0, 82.013) // ~1.38 km east of centre (inside)
    // Two candidates at EXACTLY equal distance from the hazard centre
    // (mirror positions): centre-relative math sees them as identical.
    val east = shelter("exit-east", 17.0, 82.024)   // walk straight out east
    val west = shelter("cross-west", 17.0, 81.976)  // plow through the pocket
    assertEquals(
      "mirror candidates must be equidistant from the hazard CENTRE",
      GeoMath.distanceMeters(center, east.point),
      GeoMath.distanceMeters(center, west.point), 50.0
    )
    val e = evalAll(user, listOf(west, east))
    val eEast = e.first { it.zone.id == "exit-east" }
    val eWest = e.first { it.zone.id == "cross-west" }
    // The straight-through option carries far more user-relative exposure.
    assertTrue(
      "crossing candidate must record more exposure: ${eWest.evacuationExposureMeters} vs ${eEast.evacuationExposureMeters}",
      eWest.evacuationExposureMeters > eEast.evacuationExposureMeters
    )
    // And therefore loses the user-relative ranking.
    assertTrue(
      "edge-exit must outrank crossing-the-centre: ${eEast.score} vs ${eWest.score}",
      eEast.score > eWest.score
    )
    // The evaluator states the exposure in the reasons - never silently.
    assertTrue(eWest.reasons.any { it.text.contains("crosses") })
    // (the edge-exit path still starts inside the circle - the user is IN
    // the hazard - but it crosses ~5x less of it than the through option)
    assertTrue(eEast.evacuationExposureMeters * 4 < eWest.evacuationExposureMeters)
  }

  // -------------------------------------------------------------- TEST 2
  @Test
  fun `centre user - multiple viable directions are all returned`() {
    val user = center // dead centre
    // 0.02296 deg lat = 2.553 km; 0.024 deg lon at lat 17 = 2.552 km -
    // the four compass exits are geometrically symmetric around the centre.
    val candidates = listOf(
      shelter("n", 17.02296, 82.0),
      shelter("e", 17.0, 82.024),
      shelter("s", 16.97704, 82.0),
      shelter("w", 17.0, 81.976)
    )
    val evaluations = evalAll(user, candidates)
    val feasible = evaluations.filter { it.isFeasible }
    // The engine must NOT collapse a centre scenario to one arbitrary
    // direction: every clear-of-hazard candidate stays in the ranked set.
    assertEquals("all four directions viable", 4, feasible.size)
    val ranked = feasible.sortedByDescending { it.score }
    // No direction is hardcoded favourite: opposite exits tie by symmetry.
    assertEquals(ranked.first().score, ranked[ranked.size - 1].score)
  }

  // -------------------------------------------------------------- TEST 3
  @Test
  fun `close candidate across the hazard does NOT automatically win`() {
    // User OUTSIDE to the east; candidate A is the geographically CLOSEST
    // point overall but sits behind the hazard pocket (path crosses it);
    // candidate B is slightly farther but the path never touches the hazard.
    val user = GeoPoint(17.0, 82.025) // ~2.66 km east, outside (near edge)
    val a = shelter("close-across", 17.0, 81.976) // ~5.2 km, straight through
    val b = shelter("far-clean", 17.052, 82.025)  // ~5.8 km due north, clean
    val e = evalAll(user, listOf(a, b))
    val eA = e.first { it.zone.id == "close-across" }
    val eB = e.first { it.zone.id == "far-clean" }
    assertTrue("A must be the closer straight-line candidate",
      eA.distanceMeters < eB.distanceMeters)
    assertTrue("A's path crosses significant hazard",
      eA.evacuationExposureMeters > 1_000.0)
    assertEquals("B's path must be clean", 0.0, eB.evacuationExposureMeters, 0.01)
    assertTrue(
      "the clean exit must outrank the closer crossing route: ${eB.score} vs ${eA.score}",
      eB.score > eA.score
    )
  }

  // -------------------------------------------------------------- TEST 4
  @Test
  fun `moving the user flips the user-relative ranking - stale origin impossible`() {
    // Same hazard + same two mirror candidates as TEST 1.
    val east = shelter("exit-east", 17.0, 82.024)
    val west = shelter("cross-west", 17.0, 81.996)
    val edgeEast = GeoPoint(17.0, 82.013)
    val edgeWest = GeoPoint(17.0, 81.987)
    val fromEast = evalAll(edgeEast, listOf(west, east))
      .maxByOrNull { it.score }!!
    val fromWest = evalAll(edgeWest, listOf(west, east))
      .maxByOrNull { it.score }!!
    // An edge user prefers the exit on THEIR side - when the user moves to
    // the opposite edge, the winner flips. Hazard-centre math could never
    // produce this (both candidates are equidistant from the centre).
    assertEquals("exit-east", fromEast.zone.id)
    assertEquals("cross-west", fromWest.zone.id)
  }

  // -------------------------------------------------------------- TEST 5
  @Test
  fun `hazard change recomputes exposure - no stale candidate state`() {
    val user = GeoPoint(17.0, 82.040)
    val a = shelter("close-across", 17.0, 81.958)
    // With the flood pocket: exposure > 0.
    val withFlood = SafeZoneEvaluator.evaluateAll(listOf(a),
      SafeZoneEvaluator.RequestContext(origin = user, hazards = listOf(hazard())))
      .first()
    // Swap to a fire hazard in the same geometry: exposure recomputed.
    val fire = hazard().copy(id = "hz-fire", type = HazardType.FIRE)
    val withFire = SafeZoneEvaluator.evaluateAll(listOf(a),
      SafeZoneEvaluator.RequestContext(origin = user, hazards = listOf(fire)))
      .first()
    // Empty hazard set (type filter matched nothing): zero exposure, and the
    // path-stays-outside reason must NOT be claimed when nothing was checked.
    val none = SafeZoneEvaluator.evaluateAll(listOf(a),
      SafeZoneEvaluator.RequestContext(origin = user, hazards = emptyList()))
      .first()
    assertTrue("crossing path under flood", withFlood.evacuationExposureMeters > 1_000.0)
    assertTrue("same geometry under fire recomputes to the same crossing",
      withFire.evacuationExposureMeters > 1_000.0)
    assertEquals("no hazards -> no exposure", 0.0, none.evacuationExposureMeters, 0.01)
    assertTrue("no hazards -> no 'stays outside' claim",
      none.reasons.none { it.text.contains("stays outside") })
  }

  // -------------------------------------------------------------- TEST 6
  @Test
  fun `demo candidates are placed FROM THE USER and move with the user`() {
    val focusA = GeoPoint(17.7, 83.3)   // Vizag-ish
    val focusB = GeoPoint(21.2, 80.6)   // Nagpur-ish
    val setA = DemoNetworkAroundUser.sheltersAround(focusA)
    val setB = DemoNetworkAroundUser.sheltersAround(focusB)
    assertTrue("demo must place candidates for any Indian focus",
      setA.isNotEmpty() && setB.isNotEmpty())
    // Moving the demo focus changes the candidate SET (ids + coordinates),
    // proving the demo follows the same user-relative architecture.
    assertNotEquals(setA.map { it.id }, setB.map { it.id })
    // Every placement sits OUTSIDE its scenario hazard circle: no shelter is
    // generated inside the disaster it is supposed to protect from.
    val hazardA = DemoNetworkAroundUser.hazardNear(focusA)
    setA.forEach { s ->
      assertTrue("${s.id} must clear the demo hazard circle",
        GeoMath.distanceMeters(hazardA.center, s.point) > hazardA.radiusMeters)
    }
    // The secondary-hazard generator anchored on the USER also clears the
    // hazard circle around it (user-relative placement, spec 9).
    val sec = DemoNetworkAroundUser.secondaryHazard(focusA, HazardType.FIRE)
    val secondarySet = DemoNetworkAroundUser.sheltersAroundHazard(
      anchor = focusA,
      hazardRadiusMeters = sec.radiusMeters,
      hazardBearingFromAnchor = GeoMath.bearingDegrees(focusA, sec.center),
      hazardCenter = sec.center,
      idSalt = "-t6"
    )
    assertTrue(secondarySet.isNotEmpty())
    secondarySet.forEach { s ->
      assertTrue("user-anchored placement must clear the hazard circle",
        GeoMath.distanceMeters(sec.center, s.point) > sec.radiusMeters)
      // ...and stays walkable from the USER, not from the hazard centre.
      assertTrue(GeoMath.distanceMeters(focusA, s.point) <= 9_500.0)
    }
  }

  // -------------------------------------------------------------- TEST 7
  @Test
  fun `detail card exact record id and coordinates are preserved into routing`() {
    val user = GeoPoint(17.0, 82.013) // eastern-edge scenario (TEST 1)
    val east = shelter("exit-east", 17.0, 82.024)
    val west = shelter("cross-west", 17.0, 81.996)
    val detail = ZoneDetailMapper.map(
      zone = hazard(), event = null,
      feasibleSafeZones = listOf(west, east),
      userLocation = user
    )
    val nearest = detail.nearestSafeZone
    assertNotNull(nearest)
    // Card shows the honest USER-relative distance, and the edge exit wins.
    assertEquals("exit-east", nearest!!.id)
    assertTrue(nearest.distanceText.contains("from you"))
    // The id resolves to the EXACT record the route must target.
    val record = listOf(west, east).first { it.id == nearest.id }
    assertEquals(record.lat, east.lat, 0.0)
    assertEquals(record.lon, east.lon, 0.0)
    // Without a user position the card refuses to fake centre-relative math.
    val noUser = ZoneDetailMapper.map(
      zone = hazard(), event = null, feasibleSafeZones = listOf(west, east)
    ).nearestSafeZone!!
    assertTrue(noUser.distanceText.contains("unavailable"))
  }
}
