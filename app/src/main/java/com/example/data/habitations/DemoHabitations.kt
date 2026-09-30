package com.example.data.habitations

import com.example.data.disaster.PilotRegionData
import com.example.data.model.DataClassification
import com.example.data.model.GeoMath
import com.example.data.routing.GeoPoint
import com.example.data.suitability.SuitabilityBand
import com.example.data.suitability.SuitabilityStatus
import com.example.data.suitability.TerrainVerdict

/**
 * LABELLED SIMULATED habitation set used by the prioritization dashboard so
 * the ranking output can be demonstrated end to end on any device. These are
 * DEMONSTRATION records derived from the existing simulated pilot zone
 * network (same coordinates it ships with): they are never presented as real
 * census data, and real operator-entered registry records take their place as
 * soon as any exist.
 */
object DemoHabitations {

  val SIMULATED_SOURCE = "Demonstration set derived from the simulated pilot zone network (SIMULATED)"

  /**
   * Demo terrain verdict for the OUTSIDE-hazard settlements. SIMULATED
   * through and through: the verdict source string says so, and it exists
   * so the ranking engine's terrain branch (SHORT/MEDIUM/LOW) has realistic
   * geometry to demonstrate — not to fake an assessment.
   */
  private fun demoVerdict(band: SuitabilityBand): TerrainVerdict = TerrainVerdict(
    status = SuitabilityStatus.ASSESSED,
    band = band,
    score = when (band) {
      SuitabilityBand.RED_ZONE -> 25
      SuitabilityBand.HIGH_RISK -> 45
      SuitabilityBand.CAUTION -> 60
      SuitabilityBand.SAFE -> 85
    },
    slopePercent = null,
    rainfallMm24h = null,
    rainfallUsed = false,
    hardRuleHit = null,
    reasons = listOf("Demonstration terrain band (SIMULATED) — run a live terrain scan for a real assessment."),
    source = SIMULATED_SOURCE,
    disclaimer = "Demonstration value — not a live terrain assessment."
  )

  fun build(): List<Habitation> {
    // One settlement per simulated danger zone. The first ones sit just
    // INSIDE the hazard radius (the engine ranks them IMMEDIATE); the rest
    // sit well OUTSIDE it with varied labelled demo terrain bands, so the
    // full planning horizon - SHORT-TERM / MEDIUM-TERM / LOW - is visible
    // to the operator without touching any ranking logic.
    val settlements = PilotRegionData.hazardZones.mapIndexed { index, zone ->
      val insideHazard = index < 6
      val outsidePattern = index % 4
      val verdict = when {
        insideHazard -> null
        outsidePattern == 0 -> demoVerdict(SuitabilityBand.RED_ZONE)
        outsidePattern == 1 -> demoVerdict(SuitabilityBand.HIGH_RISK)
        outsidePattern == 2 -> demoVerdict(SuitabilityBand.SAFE)
        else -> null // unassessed -> MEDIUM-TERM horizon
      }
      Habitation(
        id = "demo-hab-$index",
        name = "${zone.name} settlement",
        point = if (insideHazard) {
          GeoMath.offsetPoint(zone.center, 45.0, zone.radiusMeters * 0.4)
        } else {
          GeoMath.offsetPoint(zone.center, 225.0, zone.radiusMeters * 3.0)
        },
        population = PopulationInput(
          value = 120 + index * 90, // deterministic demo sizes
          classification = DataClassification.SIMULATED,
          source = SIMULATED_SOURCE
        ),
        vulnerableShare = 0.25f,
        terrainVerdict = verdict,
        // Keep history below the escalation threshold for the outside set so
        // the tier shown is the terrain branch's own verdict.
        historicalEventCount = if (insideHazard && index % 3 == 0) 6 else 1
      )
    }
    return settlements
  }

  /**
   * The full dashboard input set: every real registry record, plus the
   * labelled demo network when [includeDemo]. A row-level source label
   * always tells the operator which set a record came from.
   */
  fun forDashboard(
    fieldRecords: List<Habitation>,
    includeDemo: Boolean
  ): List<Habitation> {
    if (!includeDemo) return fieldRecords
    val demo = build().filter { candidate -> fieldRecords.none { it.id == candidate.id } }
    return fieldRecords + demo
  }
}
