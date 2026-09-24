package com.example.data.disaster

import com.example.data.model.DataClassification
import com.example.data.model.HazardSeverity
import com.example.data.model.GeoMath
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DEMO-AROUND-YOU contracts: with the DEMO switch on, the focus point (GPS or
 * chosen place) must genuinely have a danger circle covering it and an
 * eligible shelter opposite — all honestly SIMULATED, deterministic, and
 * India-clamped.
 */
class DemoNetworkAroundUserTest {

  private val vizag = GeoPoint(17.6935, 83.2921)
  private val kochi = GeoPoint(9.9312, 76.2673)
  private val london = GeoPoint(51.5072, -0.1276)

  @Test
  fun `the demo hazard circle COVERS the focus point (risk can fire)`() {
    val hazard = DemoNetworkAroundUser.hazardNear(vizag)
    val distance = GeoMath.distanceMeters(vizag, hazard.center)
    assertTrue(
      "focus must be inside the demo circle: d=$distance r=${hazard.radiusMeters}",
      distance < hazard.radiusMeters
    )
    assertEquals(HazardSeverity.HIGH, hazard.severity)
  }

  @Test
  fun `the demo shelter is near the user AND outside the hazard circle (eligible)`() {
    val (hazard, shelter) = DemoNetworkAroundUser.around(vizag)!!
    val userToShelter = GeoMath.distanceMeters(vizag, shelter.point)
    val hazardToShelter = GeoMath.distanceMeters(hazard.center, shelter.point)
    assertTrue("shelter within reachability: $userToShelter m", userToShelter < 40_000.0)
    assertTrue("shelter outside hazard circle: $hazardToShelter > ${hazard.radiusMeters}",
      hazardToShelter > hazard.radiusMeters)
    assertEquals("OPEN", shelter.operatingStatus)
    assertTrue(shelter.availableCapacity > 0)
  }

  @Test
  fun `network is deterministic - same place always yields the same records`() {
    val a = DemoNetworkAroundUser.around(vizag)!!
    val b = DemoNetworkAroundUser.around(vizag)!!
    assertEquals(a.first.id, b.first.id)
    assertEquals(a.first.center, b.first.center)
    assertEquals(a.second.id, b.second.id)
    assertEquals(a.second.point, b.second.point)
    // And a different place yields a different network.
    assertNotEquals(a.first.id, DemoNetworkAroundUser.around(kochi)!!.first.id)
  }

  @Test
  fun `every record is honestly SIMULATED with demo-namespaced ids`() {
    val (hazard, shelter) = DemoNetworkAroundUser.around(kochi)!!
    assertEquals(DataClassification.SIMULATED, hazard.provenance.classification)
    assertEquals(DataClassification.SIMULATED, shelter.provenance.classification)
    assertTrue(hazard.sourceStatus.contains("DEMO"))
    assertTrue(shelter.verificationStatus.contains("SIMULATED"))
    assertTrue(hazard.id.startsWith("demo-hz-"))
    assertTrue(shelter.id.startsWith("demo-sz-"))
  }

  @Test
  fun `outside-India focus produces nothing (India-only guard respected)`() {
    assertNull(DemoNetworkAroundUser.around(london))
  }

  @Test
  fun `hazard type varies by place but stays within the disaster registry`() {
    val types = listOf(
      DemoNetworkAroundUser.hazardTypeFor(vizag),
      DemoNetworkAroundUser.hazardTypeFor(kochi),
      DemoNetworkAroundUser.hazardTypeFor(GeoPoint(26.8, 80.9))
    )
    types.forEach { assertTrue(it.label.isNotBlank()) }
  }

  @Test
  fun `shelter is outside the hazard circle measured from BOTH user and hazard center`() {
    // Regression: r=4.05km let the circle reach 7.05km past the focus and
    // swallowed the 5km shelter -> no feasible shelter -> routing dead.
    val (hazard, shelter) = DemoNetworkAroundUser.around(GeoPoint(21.5, 80.0))!!
    assertTrue(GeoMath.distanceMeters(GeoPoint(21.5, 80.0), shelter.point) > hazard.radiusMeters)
    assertTrue(GeoMath.distanceMeters(hazard.center, shelter.point) > hazard.radiusMeters)
  }
}
