package com.example.data.shelters

import com.example.data.model.HazardType

/**
 * DISASTER-SPECIFIC SUITABILITY (spec §7).
 *
 * The evaluator already removes candidates INSIDE an active hazard area and
 * ranks by real terrain elevation. This object adds, per hazard TYPE present
 * in the current picture:
 *
 *  - score deltas computed ONLY from data that actually exists
 *    (coast distance from the land grid, fetched elevations);
 *  - honest planning-advisory lines + explicit "NOT ASSESSED" statements for
 *    constraints the app has no data for (structural safety, slope/runout,
 *    fuel load, storm-surge exposure per site).
 *
 * Rule #7 of the takeover spec: never turn "nearest green marker" into a
 * safety guarantee, and never let "higher ground" become an automatic safe
 * verdict - every advisory below is labelled as planning guidance, not a
 * site-verified fact.
 */
internal object DisasterSuitability {

  /** Cyclone: inland distance (km) at which a site starts earning trust. */
  private const val CYCLONE_INLAND_KM = 10
  /** Cyclone: inside this coast distance the site is penalised (grid fact). */
  private const val CYCLONE_COASTAL_KM = 3

  data class Outcome(
    /** Added to the composite ranking score (may be negative). */
    val scoreDelta: Int,
    /** Reasons to surface in the "Why this safe zone?" panel. */
    val advisories: List<String>
  )

  /**
   * @param hazardTypes distinct types of the hazards in the current picture
   * @param coastKm distance of THIS site to the coast from the land grid
   *                 (null = unknown/lookup failed - never assumed)
   * @param climbMeters site elevation minus origin elevation (null = unknown)
   */
  fun assess(
    hazardTypes: Set<HazardType>,
    coastKm: Int?,
    climbMeters: Double?
  ): Outcome {
    var delta = 0
    val notes = mutableListOf<String>()

    if (hazardTypes.contains(HazardType.FLOOD) ||
      hazardTypes.contains(HazardType.HEAVY_RAINFALL)) {
      when {
        climbMeters == null -> notes +=
          "Flood suitability: site elevation NOT ASSESSED - no terrain figure for this record."
        climbMeters >= 2.0 -> {
          delta += 8
          notes += "Flood: terrain measured ~%.0f m ABOVE your location (planning guidance, not a flood-proof guarantee).".format(climbMeters)
        }
        climbMeters <= -2.0 -> {
          delta -= 20
          notes += "Flood caution: terrain measured ~%.0f m BELOW your location - water can collect here. Avoid unless assessed.".format(-climbMeters)
        }
        else -> notes += "Flood: terrain at about the same height as you - drainage behaviour not assessed."
      }
    }

    if (hazardTypes.contains(HazardType.CYCLONE)) {
      when {
        coastKm == null -> notes +=
          "Cyclone suitability: coast distance NOT ASSESSED for this site."
        coastKm <= CYCLONE_COASTAL_KM -> {
          delta -= 18
          notes += "Cyclone caution: only ~${coastKm} km from the coast - storm-surge exposure has NOT been assessed per site."
        }
        coastKm >= CYCLONE_INLAND_KM -> {
          delta += 10
          notes += "Cyclone: ~${coastKm} km inland (land-grid measurement)."
        }
        else -> notes += "Cyclone: ~${coastKm} km from the coast (mid-distance; surge zones not mapped per site)."
      }
      notes += "Structural sturdiness is NOT VERIFIED from available data - official cyclone shelters should be preferred where designated."
    }

    if (hazardTypes.contains(HazardType.EARTHQUAKE)) {
      // No structural dataset exists for these records. Honesty over comfort:
      // the app must not imply any listed shelter is seismically safe.
      notes += "Earthquake: BUILDING SAFETY NOT ASSESSED (no structural data connected). Favour open assembly ground; stay away from walls and tall structures."
    }

    if (hazardTypes.contains(HazardType.LANDSLIDE)) {
      when {
        climbMeters == null -> notes +=
          "Landslide suitability: SLOPE/RUNOUT NOT ASSESSED - no slope data connected."
        climbMeters >= 2.0 -> {
          // Higher ground is NOT automatically safe (spec §7: a steep
          // landslide-prone slope above you can be the hazard itself).
          notes += "Landslide caution: the site is higher ground, but SLOPE STABILITY around it is NOT ASSESSED - higher does not mean safer."
          delta += 4
        }
        else -> notes += "Landslide caution: low ground below slopes can be in a runout path; slope NOT ASSESSED for this record."
      }
    }

    if (hazardTypes.contains(HazardType.FIRE)) {
      notes += "Fire: surrounding FUEL/VEGETATION NOT ASSESSED - prefer open, cleared ground; wind/smoke corridors are not modelled."
    }

    return Outcome(delta, notes)
  }
}
