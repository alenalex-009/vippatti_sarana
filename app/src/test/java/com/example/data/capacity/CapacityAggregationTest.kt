package com.example.data.capacity

import com.example.data.capacity.CapacityAggregation.AreaDemand
import com.example.data.capacity.CapacityAggregation.Colony
import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * APPROVED PHASE 8 CONTRACTS (docs/carrying-capacity-research.md, user
 * approval 2026-10-02):
 *  1. GoI cyclone-shelter regime (3 sq ft + terrace) applies ONLY when asked
 *     for; camp-style regime is the default; the regime is stamped + cited.
 *  2. Confidence label from measurement coverage - never hides missing lines.
 *  3. Colony catchment split: proportional on sourced demand, equal-split
 *     fallback when demand is unsourced, sites without a figure excluded
 *     (never zero), a colony with no population gets NO adequacy verdict.
 *  4. City/ward rollup counts each DISTINCT site once - reconciles exactly.
 */
class CapacityAggregationTest {

  private fun site(
    id: String,
    lat: Double,
    lon: Double,
    total: Int = 500,
    current: Int = 0,
    land: Double? = 1200.0,
    water: Double? = 2400.0,
    toilets: Int? = 12
  ) = SafeZone(
    id = id, name = "Hall $id", lat = lat, lon = lon,
    locationNote = "test", capacityTotal = total, capacityCurrent = current,
    waterAvailable = true, foodAvailable = true, electricityAvailable = true,
    sanitationAvailable = true, medicalSupport = false, accessibility = "Road",
    womenChildrenSuitability = false, operatingStatus = "OPEN",
    verificationStatus = "FIELD", elevationNote = "",
    landAreaSquareMeters = land, waterLitresPerDay = water, toiletCount = toilets,
    provenance = DataProvenance(source = "test", classification = DataClassification.SIMULATED)
  )

  private val demand = RelocationDemand.fromUserProfile(householdSize = 4, source = "test")

  // ---------------------------------------------------------------- 1. regime

  @Test
  fun `cyclone regime multiplies usable levels at 3 sq ft per person and cites it`() {
    val s = site("regime-1", 20.0, 86.0)
    val camp = CarryingCapacityEngine.assess(s, demand, 0L)
    val cyc = CarryingCapacityEngine.assess(
      s, demand, 0L, CarryingCapacityEngine.Regime.CYCLONE_SHELTER
    )
    val campLand = camp.resources.first { it.resource == CapacityResource.LAND_AREA }
    val cycLand = cyc.resources.first { it.resource == CapacityResource.LAND_AREA }
    // 1200 m2 / 4.5 = 266 camp-style; 1200*2 / 0.2787 = 8611 cyclone-style.
    assertEquals(266, campLand.peopleSupported)
    assertEquals(8611, cycLand.peopleSupported)
    assertTrue("regime must be named in the basis",
      cycLand.basis.contains("GoI cyclone-shelter guidance"))
    assertTrue(campLand.basis.contains("Sphere 2018"))
    assertEquals(CarryingCapacityEngine.Regime.CYCLONE_SHELTER, cyc.regime)
  }

  @Test
  fun `cyclone regime never becomes the silent default`() {
    val s = site("regime-2", 20.0, 86.0)
    val a = CarryingCapacityEngine.assess(s, demand, 0L)
    assertEquals(CarryingCapacityEngine.Regime.CAMP_STYLE, a.regime)
  }

  // ------------------------------------------------------------- 2. confidence

  @Test
  fun `unassessed lines drop confidence and stay listed`() {
    val full = site("conf-1", 20.0, 86.0)
    val partial = site("conf-2", 20.0, 86.0, land = null, water = null, toilets = null)
    val aFull = CarryingCapacityEngine.assess(full, demand, 0L)
    val aPart = CarryingCapacityEngine.assess(partial, demand, 0L)
    // Simulated records are LOW by rule (research doc H.1: never high trust
    // on demo data); the DIFFERENCE contract is the coverage signal:
    assertEquals(CapacityConfidence.LOW, aFull.confidence) // simulated provenance
    assertEquals(CapacityConfidence.LOW, aPart.confidence)  // most lines missing
    assertTrue("missing lines must be listed, not zeroed",
      aPart.unavailableResources.size > aFull.unavailableResources.size)
  }

  @Test
  fun `measured verified full record outranks a partial one on confidence`() {
    val measured = site("conf-3", 20.0, 86.0).let {
      it.copy(provenance = DataProvenance(
        source = "registry", classification = DataClassification.OBSERVED, isVerified = true))
    }
    val a = CarryingCapacityEngine.assess(measured, demand, 0L)
    // All four quantifiable cap lines (spaces, land, water, sanitation)
    // carry values on a verified record -> HIGH is reachable.
    assertEquals(CapacityConfidence.HIGH, a.confidence)
    val noLand = measured.copy(landAreaSquareMeters = null)
    val b = CarryingCapacityEngine.assess(noLand, demand, 0L)
    assertEquals(CapacityConfidence.MEDIUM, b.confidence)
  }

  // ------------------------------------------------------- 3. colony splitting

  @Test
  fun `shared site splits capacity proportionally on sourced demand`() {
    // One hall between two colonies; equal walking distance is NOT required -
    // membership is walk-reach, the split uses demand only.
    val hall = site("hall-A", 17.0, 82.0)
    val a = Colony("col-a", "Colony A", GeoPoint(17.0, 82.0), AreaDemand(120, "FIELD"))
    val b = Colony("col-b", "Colony B", GeoPoint(17.005, 82.0), AreaDemand(90, "FIELD"))
    val usable: (SafeZone) -> Int? = { z ->
      CarryingCapacityEngine.assess(z, RelocationDemand.fromUserProfile(4, "x"), 0L)
        .effectiveCapacity
    }
    val results = CapacityAggregation.colonyCapacities(listOf(a, b), listOf(hall), usable)
    val ca = results.first { it.colony.id == "col-a" }
    val cb = results.first { it.colony.id == "col-b" }
    val siteCap = ca.allocations.single().siteCapacity!!
    // claims never exceed the site; together they partition it up to the
    // single person that integer division can lose.
    val claimed = (ca.capacity ?: 0) + (cb.capacity ?: 0)
    assertTrue("partition $claimed of $siteCap", claimed <= siteCap && claimed >= siteCap - 1)
    assertTrue("A (demand 120) claims more than B (90): ${ca.capacity} vs ${cb.capacity}",
      (ca.capacity ?: 0) > (cb.capacity ?: 0))
    assertTrue(ca.allocations.single().basis.contains("proportional") ||
      ca.allocations.single().basis.contains("Split"))
    assertTrue("split rule must be labelled a project assumption",
      ca.methodNote.contains("PROJECT ASSUMPTION"))
  }

  @Test
  fun `a colony without population data gets capacity but NO verdict`() {
    val hall = site("hall-B", 17.0, 82.0)
    val c = Colony("col-c", "Colony C", GeoPoint(17.0, 82.0), AreaDemand.UNAVAILABLE)
    val usable: (SafeZone) -> Int? = { _ -> 160 }
    val r = CapacityAggregation.colonyCapacities(listOf(c), listOf(hall), usable).single()
    assertEquals(160, r.capacity)
    assertNull("no demand figure -> no adequacy claim", r.surplus)
    assertNull(r.shortfall)
    assertTrue("must say population missing, not fake an OK: ${'$'}{r.verdict}",
      r.verdict.contains("NO POPULATION DATA"))
  }

  @Test
  fun `sites with no capacity figure are excluded and never counted as zero`() {
    val good = site("hall-C", 17.0, 82.0)
    val broken = good.copy(id = "hall-D", landAreaSquareMeters = null,
      waterLitresPerDay = null, toiletCount = null, capacityTotal = 0)
    val c = Colony("col-d", "D", GeoPoint(17.0, 82.0), AreaDemand(500, "FIELD"))
    val usable: (SafeZone) -> Int? = { z ->
      CarryingCapacityEngine.assess(z, RelocationDemand.fromUserProfile(4, "x"), 0L)
        .effectiveCapacity
    }
    val r = CapacityAggregation.colonyCapacities(listOf(c), listOf(good, broken), usable).single()
    assertNotNull(r.capacity)
    assertTrue("only the assessed site contributes", r.allocations.first {
      it.siteId == "hall-D" }.colonyClaim == null)
    assertTrue(r.verdict.contains("SHORTFALL") || r.verdict.contains("covers"))
  }

  @Test
  fun `no reachable site means NOT AVAILABLE, not capacity zero`() {
    val far = site("hall-E", 25.0, 80.0) // ~900 km away
    val c = Colony("col-e", "E", GeoPoint(17.0, 82.0), AreaDemand(100, "FIELD"))
    val r = CapacityAggregation.colonyCapacities(listOf(c), listOf(far), { _ -> 100 }).single()
    assertNull(r.capacity)
    assertTrue("NOT AVAILABLE not zero: ${'$'}{r.verdict}",
      r.verdict.startsWith("Capacity NOT AVAILABLE"))
  }

  // ----------------------------------------------------------- 4. dedup rollup

  @Test
  fun `city rollup counts each distinct site exactly once`() {
    val s1 = site("u1", 17.0, 82.0)
    val s2 = site("u2", 17.01, 82.01)
    val usable: (SafeZone) -> Int? = { z -> when (z.id) { "u1" -> 160; else -> 200 } }
    val dupes = listOf(s1, s2, s1, s1) // same sites referenced by many colonies
    val r = CapacityAggregation.uniqueCapacity(
      dupes, usable, AreaDemand(300, "CENSUS 2011 (HISTORICAL BASELINE)"), "City"
    )
    assertEquals("dedup by id", 360, r.totalCapacity)
    assertEquals(2, r.sitesCounted)
    assertTrue("covers-demand verdict", r.verdict.contains("covers demand"))
    assertEquals(60, r.surplus)
    val tight = CapacityAggregation.uniqueCapacity(
      dupes, usable, AreaDemand(500, "x"), "City"
    )
    assertTrue("shortfall verdict", tight.verdict.contains("SHORTFALL"))
    assertEquals(140, tight.shortfall)
  }

  @Test
  fun `unsourced population gives NO adequacy verdict at city level`() {
    val s1 = site("u3", 17.0, 82.0)
    val r = CapacityAggregation.uniqueCapacity(listOf(s1), { _ -> 160 },
      AreaDemand.UNAVAILABLE, "Ward")
    assertNull(r.shortfall)
    assertNull(r.surplus)
    assertTrue("unsourced verdict: ${'$'}{r.verdict}", r.verdict.contains("NOT SOURCED"))
  }
}
