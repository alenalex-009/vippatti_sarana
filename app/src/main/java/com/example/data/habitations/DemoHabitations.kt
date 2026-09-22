package com.example.data.habitations

import com.example.data.disaster.PilotRegionData
import com.example.data.model.DataClassification
import com.example.data.model.GeoMath
import com.example.data.routing.GeoPoint

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

  fun build(): List<Habitation> {
    // One settlement per simulated danger zone, sitting just inside its
    // hazard radius so the tier logic has realistic geometry to rank.
    val settlements = PilotRegionData.hazardZones.mapIndexed { index, zone ->
      Habitation(
        id = "demo-hab-$index",
        name = "${zone.name} settlement",
        point = GeoMath.offsetPoint(zone.center, 45.0, zone.radiusMeters * 0.4),
        population = PopulationInput(
          value = 120 + index * 90, // deterministic demo sizes
          classification = DataClassification.SIMULATED,
          source = SIMULATED_SOURCE
        ),
        vulnerableShare = 0.25f,
        historicalEventCount = if (index % 3 == 0) 6 else 1
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
