package com.example.data.shelters

import com.example.data.model.DataProvenance
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC §7 CONTRACTS: disaster-specific safe-zone logic, honest data gates.
 *  - higher ground is never an automatic safe verdict (landslide advisory);
 *  - missing terrain/coast data stays NOT ASSESSED (never zero, never guessed);
 *  - earthquake: structural safety is stated as unassessed;
 *  - coastal proximity penalises cyclone suitability ONLY when the land grid
 *    actually measured it;
 *  - advisories flow into the evaluator's "Why this safe zone?" reasons.
 */
class DisasterSuitabilityTest {

  @Test
  fun `no hazard types yields no advisories and no score change`() {
    val r = DisasterSuitability.assess(emptySet(), coastKm = null, climbMeters = null)
    assertTrue(r.advisories.isEmpty())
    assertTrue(r.scoreDelta == 0)
  }

  @Test
  fun `flood with unknown elevation says NOT ASSESSED, never assumes safety`() {
    val r = DisasterSuitability.assess(setOf(HazardType.FLOOD), coastKm = null, climbMeters = null)
    assertTrue(r.advisories.any { it.contains("NOT ASSESSED") })
    assertTrue("missing data must not earn a bonus", r.scoreDelta == 0)
  }

  @Test
  fun `landslide higher ground is advice not a guarantee`() {
    val r = DisasterSuitability.assess(setOf(HazardType.LANDSLIDE), coastKm = null, climbMeters = 40.0)
    val text = r.advisories.joinToString(" ")
    assertTrue("must explicitly refuse 'higher = safe': $text", text.contains("higher does not mean safer"))
  }

  @Test
  fun `earthquake never claims structural safety`() {
    val r = DisasterSuitability.assess(setOf(HazardType.EARTHQUAKE), coastKm = null, climbMeters = 5.0)
    assertTrue(r.advisories.any { it.contains("NOT ASSESSED") && it.contains("BUILDING SAFETY") })
  }

  @Test
  fun `cyclone coastal proximity measured by the grid is penalised`() {
    val r = DisasterSuitability.assess(setOf(HazardType.CYCLONE), coastKm = 2, climbMeters = null)
    assertTrue(r.scoreDelta < 0)
    assertTrue(r.advisories.any { it.contains("storm-surge exposure has NOT been assessed") })
  }

  @Test
  fun `cyclone coast distance unknown stays unassessed and unpenalised`() {
    val r = DisasterSuitability.assess(setOf(HazardType.CYCLONE), coastKm = null, climbMeters = null)
    assertTrue(r.scoreDelta == 0)
    assertTrue(r.advisories.any { it.contains("NOT ASSESSED") })
  }

  // ---- evaluator integration ------------------------------------------------

  private fun zone(id: String, lat: Double, lon: Double) = SafeZone(
    id = id, name = "Shelter $id", lat = lat, lon = lon,
    locationNote = "test", capacityTotal = 200, capacityCurrent = 0,
    waterAvailable = true, foodAvailable = true, electricityAvailable = true,
    sanitationAvailable = true, medicalSupport = false, accessibility = "Road",
    womenChildrenSuitability = false, operatingStatus = "OPEN",
    verificationStatus = "FIELD", elevationNote = "",
    provenance = DataProvenance(source = "test")
  )

  private fun flood(id: String, lat: Double, lon: Double) = HazardZone(
    id = id, name = "test flood", type = HazardType.FLOOD,
    severity = HazardSeverity.HIGH, center = GeoPoint(lat, lon),
    radiusMeters = 500.0, riskLevel = "ORANGE", trend = HazardTrend.STABLE,
    sourceStatus = "test", lastUpdatedMillis = 0L,
    provenance = DataProvenance(source = "test")
  )

  @Test
  fun `evaluator surfaces the flood advisory in Why-this-safe-zone reasons`() {
    val origin = GeoPoint(17.0, 82.0)
    val evaluations = SafeZoneEvaluator.evaluateAll(
      listOf(zone("s1", 17.01, 82.01)),
      SafeZoneEvaluator.RequestContext(
        origin = origin,
        hazards = listOf(flood("h1", 17.0, 82.0)),
        originElevationMeters = null,
        zoneElevations = emptyMap(),
        coastKmOf = { null }
      )
    )
    val reasons = evaluations.first().reasons.map { it.text }
    assertTrue(
      "flood NOT-ASSESSED advisory must reach the reasons list: $reasons",
      reasons.any { it.startsWith("Flood suitability:") }
    )
  }

  @Test
  fun `coastal site ranks below inland site when a cyclone is active and grid is present`() {
    // Mirror positions, equal distance, equal everything else: only the
    // measured coast distance differs (3 km vs 30 km from the land grid).
    val ctx = SafeZoneEvaluator.RequestContext(
      origin = GeoPoint(20.0, 86.0),
      hazards = listOf(
        HazardZone(
          id = "cy", name = "cyclone", type = HazardType.CYCLONE,
          severity = HazardSeverity.HIGH, center = GeoPoint(20.2, 86.2),
          radiusMeters = 800.0, riskLevel = "ORANGE", trend = HazardTrend.WORSENING,
          sourceStatus = "test", lastUpdatedMillis = 0L,
          provenance = DataProvenance(source = "test")
        )
      ),
      coastKmOf = { pt -> if (pt.lon < 86.0) 3 else 30 }
    )
    val coastal = zone("coastal", 20.001, 85.982)
    val inland = zone("inland", 20.001, 86.018)
    val eCoastal = SafeZoneEvaluator.evaluate(coastal, ctx)
    val eInland = SafeZoneEvaluator.evaluate(inland, ctx)
    assertTrue(
      "measured 3 km coast site must score below the 30 km inland site: " +
        "${eCoastal.score} vs ${eInland.score}",
      eCoastal.score < eInland.score
    )
    assertTrue(
      "inland site should state the measured advantage",
      eInland.reasons.any { it.text.contains("inland") }
    )
  }
}
