package com.example.data.disaster

import com.example.data.model.GeoMath
import com.example.data.model.HazardType
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardZone
import com.example.data.model.HazardTrend
import com.example.data.model.DataProvenance
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 10 + 13 CONTRACTS:
 *  - demo scenario plausibility: an inland point never gets a cyclone demo,
 *    coastal points can; deterministic, no RNG;
 *  - demo hazard cards carry coherent internally-defined scenario values
 *    derived from the zone's own geometry (Phase 13), while real records
 *    still surface missing fields as null -> "Data unavailable".
 */
class DemoPlausibilityAndScenarioTest {

  // Jaisalmer (Thar desert, ~150 km from any sea)
  private val inlandDesert = GeoPoint(26.9157, 70.9083)
  // Chennai coast
  private val coastPoint = GeoPoint(13.0827, 80.2707)

  @Test
  fun `cyclone demo is implausible far inland and plausible on the coast`() {
    assertFalse("desert cyclone story must be refused",
      DemoNetworkAroundUser.plausibleAt(inlandDesert, HazardType.CYCLONE, coastKm = 320))
    assertTrue("coastal cyclone demo is the canonical scenario",
      DemoNetworkAroundUser.plausibleAt(coastPoint, HazardType.CYCLONE, coastKm = 0))
  }

  @Test
  fun `type substitution is deterministic - same place same answer`() {
    val asked = HazardType.CYCLONE
    val a = DemoNetworkAroundUser.demoTypeFor(inlandDesert, asked, coastKm = 320)
    val b = DemoNetworkAroundUser.demoTypeFor(inlandDesert, asked, coastKm = 320)
    assertEquals(a, b)
    assertFalse("inland never resolves to CYCLONE", a == HazardType.CYCLONE)
    val c = DemoNetworkAroundUser.demoTypeFor(coastPoint, asked, coastKm = 0)
    assertEquals("coast keeps the asked type", HazardType.CYCLONE, c)
  }

  @Test
  fun `earthquake and fire demonstrate broadly and landslide is deterministic per place`() {
    // Phase 10: quake + fire are geographically permissive for a demo.
    assertTrue(DemoNetworkAroundUser.plausibleAt(inlandDesert, HazardType.EARTHQUAKE, 320))
    assertTrue(DemoNetworkAroundUser.plausibleAt(inlandDesert, HazardType.FIRE, 320))
    // Landslide uses a fixed hill-belt rule: same place must ALWAYS give the
    // same answer (no RNG), and the two test points are stable calls.
    val l1 = DemoNetworkAroundUser.plausibleAt(coastPoint, HazardType.LANDSLIDE, 0)
    val l2 = DemoNetworkAroundUser.plausibleAt(coastPoint, HazardType.LANDSLIDE, 0)
    assertEquals("landslide plausibility is a stable per-place rule", l1, l2)
    // A Himalayan belt point IS in the hill rule (lat>30): plausible there.
    assertTrue("hill-belt landslide must be allowed",
      DemoNetworkAroundUser.plausibleAt(GeoPoint(31.1, 77.17), HazardType.LANDSLIDE, 900))
  }

  @Test
  fun `crossing-run geometry counts re-entry into a hazard`() {
    val hazard = HazardZone(
      id = "hz-x", name = "circle", type = HazardType.FLOOD,
      severity = HazardSeverity.HIGH, center = GeoPoint(10.0, 77.0),
      radiusMeters = 2_000.0, riskLevel = "X", trend = HazardTrend.STABLE,
      sourceStatus = "test", lastUpdatedMillis = 0L,
      provenance = DataProvenance(source = "test")
    )
    // corridor A: clean pass north of the circle
    val clean = listOf(GeoPoint(10.05, 76.95), GeoPoint(10.06, 77.0), GeoPoint(10.05, 77.05))
    // corridor B: dips into the circle, out, then back in -> two runs
    val reentry = listOf(
      GeoPoint(10.03, 76.97),   // outside
      GeoPoint(10.0, 77.0),     // inside
      GeoPoint(10.04, 77.02),   // outside
      GeoPoint(10.002, 77.001), // inside again
      GeoPoint(10.03, 77.05)    // outside
    )
    val runsA = com.example.data.routing.HazardRoutingPolicy.hazardCrossingRuns(clean, listOf(hazard))
    val runsB = com.example.data.routing.HazardRoutingPolicy.hazardCrossingRuns(reentry, listOf(hazard))
    assertEquals("clean corridor never enters", 0, runsA)
    assertTrue("re-entry corridor counts each separate run: $runsB", runsB >= 2)
  }

  @Test
  fun `demo hazard card gains scenario values while live records do not`() {
    val demo = HazardZone(
      id = "demo-hz-test", name = "DEMO Flood Zone (demo scenario)",
      type = HazardType.FLOOD, severity = HazardSeverity.HIGH,
      center = GeoPoint(17.6935, 83.2921), radiusMeters = 1_800.0,
      riskLevel = "DEMO — demonstration only", trend = HazardTrend.WORSENING,
      sourceStatus = "DEMO DATA — generated around your location, NOT real",
      lastUpdatedMillis = 0L, provenance = DataProvenance(source = "test")
    )
    val detail = ZoneDetailMapper.map(demo, null, emptyList())
    val scenarioSection = detail.sections.firstOrNull { it.heading.contains("SCENARIO VALUES") }
    assertTrue("demo card must carry the scenario section", scenarioSection != null)
    val fields = scenarioSection!!.fields.associate { it.label to it.value }
    assertTrue("onset is a demo value, not null",
      (fields["Scenario onset (demo)"] ?: "").contains("h"))
    // Population example derives from the zone's OWN radius (no fake number):
    // area = pi r^2; 4000/km2 planning density.
    val areaKm2 = Math.PI * 1.8 * 1.8
    val expected = (areaKm2 * 4_000).toInt()
    assertTrue("example population follows the geometry: ${fields["Affected population (demo planning example)"]}",
      (fields["Affected population (demo planning example)"] ?: "")
        .contains(java.text.NumberFormat.getInstance(java.util.Locale.US).format(expected)))
    // Flood guidance is disaster-specific:
    assertTrue((fields["Response guidance (demo)"] ?: "").contains("higher ground"))

    // A LIVE-style zone (no event) keeps honest nulls -> UI "Data unavailable".
    val live = demo.copy(id = "live-xyz", name = "Real flood alert")
    val liveDetail = ZoneDetailMapper.map(live, null, emptyList())
    assertTrue("live records get NO invented scenario values",
      liveDetail.sections.none { it.heading.contains("SCENARIO VALUES") })
  }
}
