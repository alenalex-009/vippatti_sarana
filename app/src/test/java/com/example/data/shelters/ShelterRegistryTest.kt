package com.example.data.shelters

import com.example.data.india.MaybeNumber
import com.example.data.model.GeoMath
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Safe-zone / capacity / LIVE-vs-DEMO contracts (section 30).
 *
 * The recurring assertion theme: an UNKNOWN must never render as a number.
 * "0 / 0 occupied" is a factual claim that the shelter is full; "Capacity
 * unavailable" is the truth when the registry is silent.
 */
class ShelterRegistryTest {

  private val visakhapatnam = GeoPoint(17.6868, 83.2185)

  private fun record(
    id: String = "s1",
    verification: ShelterVerification = ShelterVerification.VERIFIED,
    capacity: MaybeNumber = MaybeNumber.UNKNOWN,
    occupied: MaybeNumber = MaybeNumber.UNKNOWN,
    point: GeoPoint = visakhapatnam
  ) = ShelterRecord(
    id = id,
    name = "Test centre",
    latitude = point.lat,
    longitude = point.lon,
    facilityType = ShelterFacilityType.DEDICATED_SHELTER,
    authority = "District Administration",
    verification = verification,
    capacityTotal = capacity,
    capacityOccupied = occupied,
    source = "test registry"
  )

  // ---- capacity nullability (rules 12, 13, 16) --------------------------

  @Test
  fun `unknown capacity is reported as unavailable not zero`() {
    val r = record(capacity = MaybeNumber.UNKNOWN)
    assertEquals("Capacity unavailable", r.capacityLabel())
    assertFalse(r.capacityTotal.isKnown)
    assertFalse(r.capacityAvailable.isKnown)
  }

  @Test
  fun `unknown occupancy never reads as an empty shelter`() {
    val r = record(capacity = MaybeNumber.of(500.0), occupied = MaybeNumber.UNKNOWN)
    assertTrue(r.capacityLabel().contains("occupancy unknown"))
    assertFalse(r.capacityAvailable.isKnown)
  }

  @Test
  fun `available capacity is derived only when both inputs are known`() {
    val r = record(capacity = MaybeNumber.of(500.0), occupied = MaybeNumber.of(320.0))
    assertEquals(180.0, r.capacityAvailable.value!!, 0.001)
    assertTrue(r.capacityLabel().contains("320 / 500"))
  }

  @Test
  fun `over-occupancy never produces a negative availability`() {
    val r = record(capacity = MaybeNumber.of(100.0), occupied = MaybeNumber.of(140.0))
    assertEquals(0.0, r.capacityAvailable.value!!, 0.001)
  }

  // ---- bottleneck semantics ---------------------------------------------

  @Test
  fun `effective capacity is the minimum when every constraint is known`() {
    val r = record()
    val result = r.effectiveCapacity(
      mapOf(
        "shelter space" to MaybeNumber.of(500.0),
        "water" to MaybeNumber.of(300.0),
        "sanitation" to MaybeNumber.of(450.0),
        "power" to MaybeNumber.of(400.0)
      )
    )
    assertEquals(300.0, result.effective.value!!, 0.001)
    assertEquals("water", result.limiting)
    assertFalse(result.isIncomplete)
  }

  @Test
  fun `one unknown constraint makes the whole figure unknown`() {
    val r = record()
    val result = r.effectiveCapacity(
      mapOf(
        "shelter space" to MaybeNumber.of(500.0),
        "water" to MaybeNumber.UNKNOWN,
        "sanitation" to MaybeNumber.of(450.0)
      )
    )
    assertFalse("must not report the minimum of the known subset", result.effective.isKnown)
    assertEquals(listOf("water"), result.unknownConstraints)
    assertTrue(result.isIncomplete)
    assertTrue(result.label().contains("1 constraint unavailable"))
  }

  @Test
  fun `three unknown constraints are counted precisely`() {
    val result = record().effectiveCapacity(
      mapOf(
        "shelter space" to MaybeNumber.of(500.0),
        "water" to MaybeNumber.UNKNOWN,
        "sanitation" to MaybeNumber.UNKNOWN,
        "healthcare" to MaybeNumber.UNKNOWN
      )
    )
    assertEquals(3, result.unknownConstraints.size)
    assertTrue(result.label().contains("3 constraints unavailable"))
  }

  @Test
  fun `no constraints at all is unavailable rather than infinite`() {
    val result = record().effectiveCapacity(emptyMap())
    assertFalse(result.effective.isKnown)
  }

  // ---- LIVE vs DEMO separation (section 13) ------------------------------

  @Test
  fun `only verified and official-derived records count as real`() {
    assertTrue(ShelterVerification.VERIFIED.countsAsReal)
    assertTrue(ShelterVerification.OFFICIAL_DERIVED.countsAsReal)
    assertFalse(ShelterVerification.SIMULATED.countsAsReal)
    assertFalse(ShelterVerification.COMMUNITY_REPORTED.countsAsReal)
    assertFalse(ShelterVerification.ESTIMATED.countsAsReal)
    assertFalse(ShelterVerification.UNKNOWN.countsAsReal)
  }

  @Test
  fun `simulated facilities never appear in the real facility list`() {
    ShelterRegistry.registerOfficial(listOf(record("real-1", ShelterVerification.VERIFIED)))
    ShelterRegistry.registerDemo(listOf(record("demo-1", ShelterVerification.SIMULATED)))

    val real = ShelterRegistry.realFacilities()
    assertEquals(1, real.size)
    assertEquals("real-1", real.first().id)
    assertTrue(ShelterRegistry.hasVerifiedFacility)
  }

  @Test
  fun `with no official registry the honest message is shown`() {
    ShelterRegistry.registerOfficial(emptyList())
    assertFalse(ShelterRegistry.hasVerifiedFacility)
    assertEquals(
      "No verified evacuation centre found for this area.",
      ShelterRegistry.unavailableMessage()
    )
  }

  @Test
  fun `every simulated record is labelled as simulated`() {
    val sim = record(verification = ShelterVerification.SIMULATED)
    assertEquals("SIMULATED", sim.verification.label)
    assertTrue(sim.verification.label.contains("SIMULATED"))
    assertFalse("a simulated record must never count as a real facility", sim.isRealFacility)
  }

  @Test
  fun `demo shelter rows carry an explicit demo label`() {
    val demo = com.example.data.demo.DemoShelter(
      id = "d1", name = "Demo", point = visakhapatnam,
      capacityTotal = 100, capacityOccupied = null, designatedFor = emptySet())
    assertEquals("SIMULATED / DEMO", demo.label)
    assertTrue(com.example.data.demo.DemoShelter(
      id = "d2", name = "Demo2", point = visakhapatnam,
      capacityTotal = null, capacityOccupied = null, designatedFor = emptySet()
    ).capacityLabel.contains("Capacity unavailable"))
  }

  // ---- user-relative evacuation, preserved (section 12) ------------------

  private fun zone(
    center: GeoPoint,
    radiusMeters: Double,
    type: HazardType = HazardType.FLOOD
  ) = HazardZone(
    id = "hz", name = "z", type = type, severity = HazardSeverity.HIGH,
    center = center, radiusMeters = radiusMeters, riskLevel = "High",
    trend = HazardTrend.STABLE, sourceStatus = "test",
    lastUpdatedMillis = 0L,
    provenance = DataProvenance(
      source = "test", status = DataProvenance.STATUS_LIVE, confidence = 0.8,
      isVerified = true, classification = DataClassification.OBSERVED
    )
  )

  private fun legacySafeZone(id: String, point: GeoPoint) = SafeZone(
    id = id, name = id, lat = point.lat, lon = point.lon, locationNote = "",
    capacityTotal = 300, capacityCurrent = 50,
    waterAvailable = true, foodAvailable = true, electricityAvailable = true,
    sanitationAvailable = true, medicalSupport = false,
    accessibility = "Road access", womenChildrenSuitability = true,
    operatingStatus = "OPEN", verificationStatus = "Verified", elevationNote = "",
    provenance = DataProvenance(
      source = "test", status = DataProvenance.STATUS_LIVE, confidence = 0.7,
      isVerified = true, classification = DataClassification.OBSERVED
    )
  )

  @Test
  fun `edge of hazard prefers the outward exit over the nearer inward one`() {
    val hazardCenter = GeoMath.offsetPoint(visakhapatnam, 90.0, 3_000.0)
    val hazard = zone(hazardCenter, 3_000.0)
    // User sits on the west rim; "inward" (east) is nearer but crosses hazard.
    val user = GeoMath.offsetPoint(hazardCenter, 270.0, 2_900.0)

    val outward = legacySafeZone(
      "outward", GeoMath.offsetPoint(hazardCenter, 270.0, 5_000.0))
    val inward = legacySafeZone(
      "inward", GeoMath.offsetPoint(hazardCenter, 90.0, 3_500.0))

    val ranked = SafeZoneEvaluator.ranked(
      listOf(inward, outward),
      SafeZoneEvaluator.RequestContext(origin = user, hazards = listOf(hazard))
    )

    assertEquals(2, ranked.size)
    assertEquals(
      "the outward exit must win despite being farther",
      "outward", ranked.first().zone.id
    )
    assertTrue(ranked.first().evacuationExposureMeters < ranked.last().evacuationExposureMeters)
  }

  @Test
  fun `a shelter inside the hazard is rejected outright`() {
    val hazardCenter = GeoMath.offsetPoint(visakhapatnam, 0.0, 2_000.0)
    val hazard = zone(hazardCenter, 3_000.0)
    val inside = legacySafeZone("inside", hazardCenter)

    val result = SafeZoneEvaluator.evaluate(
      inside, SafeZoneEvaluator.RequestContext(origin = visakhapatnam, hazards = listOf(hazard)))

    assertFalse(result.isFeasible)
    assertEquals(RejectionReason.INSIDE_HAZARD_AREA, result.rejectionReason)
  }

  @Test
  fun `ranking is recomputed when the user moves`() {
    val hazard = zone(visakhapatnam, 3_000.0)
    val north = legacySafeZone("north", GeoMath.offsetPoint(visakhapatnam, 0.0, 4_000.0))
    val south = legacySafeZone("south", GeoMath.offsetPoint(visakhapatnam, 180.0, 4_000.0))

    val fromNorth = SafeZoneEvaluator.ranked(
      listOf(north, south),
      SafeZoneEvaluator.RequestContext(origin = GeoMath.offsetPoint(visakhapatnam, 0.0, 500.0), hazards = listOf(hazard)))
    val fromSouth = SafeZoneEvaluator.ranked(
      listOf(north, south),
      SafeZoneEvaluator.RequestContext(origin = GeoMath.offsetPoint(visakhapatnam, 180.0, 500.0), hazards = listOf(hazard)))

    assertEquals("north", fromNorth.first().zone.id)
    assertEquals("south", fromSouth.first().zone.id)
  }

  @Test
  fun `every recommendation carries a human-readable explanation`() {
    val hazard = zone(GeoMath.offsetPoint(visakhapatnam, 0.0, 8_000.0), 2_000.0)
    val shelter = legacySafeZone("shelter", GeoMath.offsetPoint(visakhapatnam, 45.0, 3_000.0))

    val result = SafeZoneEvaluator.evaluate(
      shelter, SafeZoneEvaluator.RequestContext(origin = visakhapatnam, hazards = listOf(hazard)))

    assertTrue(result.isFeasible)
    assertTrue(result.reasons.isNotEmpty())
    assertTrue(result.rankExplanation.isNotBlank())
    assertTrue(result.rankExplanation.contains("km") || result.rankExplanation.contains("m"))
  }

  @Test
  fun `a full shelter is rejected rather than ranked last`() {
    val hazard = zone(GeoMath.offsetPoint(visakhapatnam, 0.0, 9_000.0), 1_000.0)
    val full = legacySafeZone("full", GeoMath.offsetPoint(visakhapatnam, 45.0, 2_000.0))
      .copy(capacityTotal = 100, capacityCurrent = 100)

    val result = SafeZoneEvaluator.evaluate(
      full, SafeZoneEvaluator.RequestContext(origin = visakhapatnam, hazards = listOf(hazard)))

    assertFalse(result.isFeasible)
    assertEquals(RejectionReason.NO_REMAINING_CAPACITY, result.rejectionReason)
  }
}