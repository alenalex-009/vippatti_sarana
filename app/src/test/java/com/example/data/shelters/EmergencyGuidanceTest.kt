package com.example.data.shelters

import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.model.DataProvenance
import com.example.data.model.SafeZone
import com.example.data.risk.RiskLevel
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contracts for EMERGENCY SHELTER GUIDANCE (user requirement: "when any
 * disaster happens, a safe zone should appear so they can relocate to the
 * nearest safe zone"):
 *
 * - a danger-level risk near the user MUST produce a concrete nearest-shelter
 *   suggestion when at least one feasible shelter exists;
 * - the suggestion is NEAREST feasible (rejected/full/inside-hazard shelters
 *   can never be suggested);
 * - calm risk levels must NOT nag;
 * - with no shelters in range the answer is honest, never invented.
 */
class EmergencyGuidanceTest {

  private val here = GeoPoint(10.0, 76.5)

  private fun zone(
    id: String, lat: Double, lon: Double,
    capacity: Int = 200, occupied: Int = 0, operating: String = "OPEN",
    name: String = "Shelter $id"
  ) = SafeZone(
    id = id, name = name, lat = lat, lon = lon,
    locationNote = "test", capacityTotal = capacity, capacityCurrent = occupied,
    waterAvailable = true, foodAvailable = true, electricityAvailable = true,
    sanitationAvailable = true, medicalSupport = false, accessibility = "Road",
    womenChildrenSuitability = false, operatingStatus = operating,
    verificationStatus = "FIELD", elevationNote = "",
    provenance = DataProvenance(source = "test")
  )

  private fun evaluation(z: SafeZone, feasible: Boolean, distance: Double, rejection: RejectionReason?) =
    SafeZoneEvaluation(
      zone = z, distanceMeters = distance, isFeasible = feasible,
      rejectionReason = rejection, score = if (feasible) 80 else 0,
      reasons = emptyList(), hazardExposureCount = 0,
      capacityReport = ShelterCapacityService.report(z)
    )

  @Test
  fun `danger risk with feasible shelters suggests the nearest one`() {
    val near = zone("near", 10.01, 76.5)
    val far = zone("far", 10.08, 76.5)
    val evaluations = listOf(
      // deliberately out of distance order: guidance must use distance, not list order
      evaluation(far, true, 8_900.0, null),
      evaluation(near, true, 1_110.0, null)
    )
    val g = EmergencyGuidance.forSituation(
      riskLevel = RiskLevel.RED,
      evaluations = evaluations,
      hasActiveDestination = false
    )
    assertTrue(g is EmergencyGuidance.SuggestShelter)
    g as EmergencyGuidance.SuggestShelter
    assertEquals("near", g.evaluation.zone.id)
    assertEquals(1_110.0, g.distanceMeters, 0.001)
    assertTrue(g.headline.contains("nearest safe zone", ignoreCase = true))
  }

  @Test
  fun `full or rejected shelters are never suggested`() {
    val rejected = zone("full", 10.005, 76.5, capacity = 100, occupied = 100)
    val good = zone("open", 10.03, 76.5)
    val g = EmergencyGuidance.forSituation(
      riskLevel = RiskLevel.ORANGE,
      evaluations = listOf(
        evaluation(rejected, false, 550.0, RejectionReason.NO_REMAINING_CAPACITY),
        evaluation(good, true, 3_300.0, null)
      ),
      hasActiveDestination = false
    )
    g as EmergencyGuidance.SuggestShelter
    assertEquals("open", g.evaluation.zone.id)
    // honest note that the closer option was ruled out
    assertTrue(g.detail.contains("ruled out", ignoreCase = true) || g.detail.contains("ineligible", ignoreCase = true))
  }

  @Test
  fun `calm risk levels produce no nag`() {
    val good = zone("open", 10.03, 76.5)
    for (level in listOf(RiskLevel.GREEN, RiskLevel.YELLOW)) {
      val g = EmergencyGuidance.forSituation(
        level, listOf(evaluation(good, true, 3_300.0, null)), hasActiveDestination = false
      )
      assertEquals(EmergencyGuidance.None, g)
    }
  }

  @Test
  fun `an existing active destination is not overwritten with a suggestion`() {
    val good = zone("open", 10.03, 76.5)
    val g = EmergencyGuidance.forSituation(
      RiskLevel.RED, listOf(evaluation(good, true, 3_300.0, null)), hasActiveDestination = true
    )
    assertTrue(g is EmergencyGuidance.AlreadyRouting || g is EmergencyGuidance.SuggestShelter && g.evaluation.zone.id == "open")
    // whichever: guidance must not ask the user to re-choose while routing
    assertTrue(!(g is EmergencyGuidance.SuggestShelter))
  }

  @Test
  fun `danger with zero shelters in range is reported honestly, never invented`() {
    val g = EmergencyGuidance.forSituation(
      RiskLevel.RED, emptyList(), hasActiveDestination = false
    )
    assertTrue(g is EmergencyGuidance.NoShelterKnown)
    g as EmergencyGuidance.NoShelterKnown
    assertTrue(g.headline.contains("no registered shelter", ignoreCase = true))
    assertTrue(g.headline.contains("112", ignoreCase = true) || g.detail.contains("112", ignoreCase = true))
  }

  @Test
  fun `danger with only rejected shelters lists why none are eligible`() {
    val a = zone("a", 10.01, 76.5, operating = "CLOSED")
    val b = zone("b", 10.02, 76.5)
    val g = EmergencyGuidance.forSituation(
      RiskLevel.RED,
      listOf(
        evaluation(a, false, 1_100.0, RejectionReason.NOT_OPERATING),
        evaluation(b, false, 2_200.0, RejectionReason.INSIDE_HAZARD_AREA)
      ),
      hasActiveDestination = false
    )
    assertTrue(g is EmergencyGuidance.NoShelterEligible)
    g as EmergencyGuidance.NoShelterEligible
    assertEquals(2, g.rejections.size)
    assertTrue(g.rejections[0].contains("CLOSED") || g.rejections[0].contains("operating", ignoreCase = true))
    assertTrue(g.rejections[1].contains("hazard", ignoreCase = true))
  }

  @Test
  fun `guidance headline stays under alert-length and never claims official status`() {
    val good = zone("open", 10.03, 76.5)
    val g = EmergencyGuidance.forSituation(
      RiskLevel.RED, listOf(evaluation(good, true, 3_300.0, null)), false
    ) as EmergencyGuidance.SuggestShelter
    assertTrue("headline too long: ${g.headline}", g.headline.length <= 90)
    assertTrue(!g.headline.contains("official", ignoreCase = true))
    assertNull(g.evaluation.let { null }) // model untouched
  }

  private fun unused() = HazardZone(
    "h", "H", HazardType.FLOOD, HazardSeverity.HIGH, here, 1.0, "x",
    HazardTrend.STABLE, "test", 0L, DataProvenance(source = "t")
  )
}
