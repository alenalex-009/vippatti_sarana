package com.example.data.suitability

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.routing.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * ============================================================================
 * TERRAIN HABITABILITY / DYNAMIC RED-ZONE ENGINE (SIH 26191 core ask)
 * ============================================================================
 *
 * "Dynamically identify and update multi-hazard Red Zones (areas unsuitable
 * for permanent habitation)" — this engine answers the terrain half of that
 * with REAL open data only:
 *
 *   - SRTM 30 m elevation (via the keyless Open-Meteo Elevation API, a 5-point
 *     stencil around the site) -> local slope,
 *   - Open-Meteo forecast precipitation (mm/24 h) -> the live trigger,
 *   - distance to coast from the OSM land polygon -> coastal exposure.
 *
 * Honesty rules, identical to the rest of the codebase:
 *   - No elevation, no verdict: `INSUFFICIENT_DATA`, never a guessed score.
 *   - A missing rainfall/coast input is EXCLUDED from the weighting and said
 *     so in the reasons — it is never assumed to be zero or far.
 *   - Every threshold is CONFIGURED and displayed with a disclaimer; this is
 *     a decision-support heuristic, NOT an official GSI/PMGSY red-zone order.
 */

/** The verdict band rendered across the UI. */
enum class SuitabilityBand(val label: String) {
  SAFE("Suitable"),
  CAUTION("Caution — review before habitation"),
  HIGH_RISK("High Risk — relocation candidate"),
  RED_ZONE("RED ZONE — unsuitable for permanent habitation")
}

enum class SuitabilityStatus { ASSESSED, INSUFFICIENT_DATA }

/** The five-point SRTM stencil: centre + 30 m in each cardinal direction. */
data class ElevationGrid(
  val center: Double,
  val north: Double,
  val south: Double,
  val east: Double,
  val west: Double,
  /** Ground spacing between neighbours in meters (≈ the API step used). */
  val stepMeters: Double
)

/**
 * Pure scoring core — no I/O, fully unit-testable.
 *
 * Model (all thresholds CONFIGURED, documented, and surfaced in [TerrainVerdict]):
 *   slope              0-100 pts (35 % and above = hard RED ZONE)
 *   slope x rain       landslide trigger multiplier from live 24 h rainfall
 *   coastal elevation  low-lying land near the sea is flooded out of SAFE
 * The score is the weighted mean of the factors that actually HAVE a value;
 * a factor without data is dropped, its weight renormalised, and the removal
 * stated in [TerrainVerdict.reasons].
 */
object TerrainSuitabilityEngine {

  /** Hard line: slope at or above this permanently disqualifies habitation. */
  const val HARD_SLOPE_PERCENT = 35.0
  /** Start of the high-risk slope band. */
  const val HIGH_RISK_SLOPE_PERCENT = 25.0
  /** Start of the caution slope band. */
  const val CAUTION_SLOPE_PERCENT = 10.0
  /** Extreme 24 h rainfall (mm) that can escalate a moderate hillside. */
  const val EXTREME_RAIN_MM24 = 150.0
  const val HEAVY_RAIN_MM24 = 60.0
  /** Elevation (m above sea level) considered flood-exposed on the coast. */
  const val COASTAL_LOW_ELEVATION_M = 5.0

  private const val WEIGHT_SLOPE = 0.60
  private const val WEIGHT_COASTAL = 0.25
  private const val WEIGHT_RAIN_BASELINE = 0.15

  /**
   * Steepest cardinal slope (percent) across the stencil. Each neighbour pair
   * gives a rise/run with [ElevationGrid.stepMeters] as the run.
   */
  fun maxSlopePercent(grid: ElevationGrid): Double {
    val runs = grid.stepMeters
    return maxOf(
      abs(grid.north - grid.center) / runs,
      abs(grid.south - grid.center) / runs,
      abs(grid.east - grid.center) / runs,
      abs(grid.west - grid.center) / runs
    ) * 100.0
  }

  /**
   * The five stencil coordinates (centre first, then N, S, E, W) used to build
   * [ElevationGrid]s from the elevation API. Kept here so the request order,
   * the parser and the tests share ONE definition.
   */
  fun samplePoints(center: GeoPoint, stepMeters: Double): List<GeoPoint> {
    val dLat = stepMeters / 110_574.0
    val dLon = stepMeters / (110_574.0 * cos(Math.toRadians(center.lat)))
    return listOf(
      center,
      GeoPoint(center.lat + dLat, center.lon),          // north
      GeoPoint(center.lat - dLat, center.lon),          // south
      GeoPoint(center.lat, center.lon + dLon),          // east
      GeoPoint(center.lat, center.lon - dLon)           // west
    )
  }

  /**
   * @param rainfallMm24h live 24 h precipitation forecast, null = unknown
   * @param nearCoast     true/false from real coast distance, null = unknown
   */
  fun evaluate(
    elevation: ElevationGrid?,
    rainfallMm24h: Double?,
    nearCoast: Boolean?
  ): TerrainVerdict {
    if (elevation == null) {
      return TerrainVerdict(
        status = SuitabilityStatus.INSUFFICIENT_DATA,
        band = null,
        score = -1,
        slopePercent = null,
        rainfallMm24h = rainfallMm24h,
        rainfallUsed = false,
        hardRuleHit = null,
        reasons = listOf(
          "No elevation data — habitability cannot be assessed from thin air."
        ),
        source = "SRTM 30 m (Open-Meteo Elevation API) + Open-Meteo forecast",
        disclaimer = DISCALIMER
      )
    }

    val slope = maxSlopePercent(elevation)
    val reasons = mutableListOf<String>()

    // --- hard rule first: geology-grade exclusion, no weighting argument ----
    if (slope >= HARD_SLOPE_PERCENT) {
      return TerrainVerdict(
        status = SuitabilityStatus.ASSESSED,
        band = SuitabilityBand.RED_ZONE,
        score = 0,
        slopePercent = slope,
        rainfallMm24h = rainfallMm24h,
        rainfallUsed = rainfallMm24h != null,
        hardRuleHit = "slope %.0f%% ≥ %.0f%%".format(slope, HARD_SLOPE_PERCENT),
        reasons = reasons.apply {
          add("Terrain falls ${"%.1f".format(rise(elevation))} m over ${elevation.stepMeters.toInt()} m — slope %.0f%%.".format(slope))
          add("Slopes at or above %.0f%% are unsuitable for permanent habitation.".format(HARD_SLOPE_PERCENT))
        }.toList(),
        source = "SRTM 30 m (Open-Meteo Elevation API)",
        disclaimer = DISCALIMER
      )
    }

    // --- factor scores (0..100, higher = safer) ------------------------------
    var weighted = 0.0
    var weightSum = 0.0

    val slopeScore = when {
      slope < CAUTION_SLOPE_PERCENT -> 100.0
      slope < HIGH_RISK_SLOPE_PERCENT ->
        100.0 - (slope - CAUTION_SLOPE_PERCENT) / (HIGH_RISK_SLOPE_PERCENT - CAUTION_SLOPE_PERCENT) * 55.0
      else -> 45.0 - (slope - HIGH_RISK_SLOPE_PERCENT) / (HARD_SLOPE_PERCENT - HIGH_RISK_SLOPE_PERCENT) * 45.0
    }
    weighted += slopeScore * WEIGHT_SLOPE
    weightSum += WEIGHT_SLOPE
    reasons.add("Slope %.1f%% (SRTM 30 m stencil).".format(slope))

    // Rainfall: baseline weight as a standalone hazard driver, plus a
    // landslide TRIGGER that multiplies the slope deficit while saturated.
    var rainScore: Double? = null
    var triggerFactor = 1.0
    if (rainfallMm24h != null) {
      rainScore = when {
        rainfallMm24h >= EXTREME_RAIN_MM24 -> 0.0
        rainfallMm24h >= HEAVY_RAIN_MM24 -> 50.0
        rainfallMm24h >= 15.0 -> 80.0
        else -> 100.0
      }
      weighted += rainScore * WEIGHT_RAIN_BASELINE
      weightSum += WEIGHT_RAIN_BASELINE
      if (rainfallMm24h >= EXTREME_RAIN_MM24) triggerFactor = 2.0
      else if (rainfallMm24h >= HEAVY_RAIN_MM24) triggerFactor = 1.35
      reasons.add(
        if (rainfallMm24h >= 1.0)
          "Live 24 h rainfall %.0f mm — saturated slopes amplify landslide risk.".format(rainfallMm24h)
        else "Live 24 h rainfall near zero."
      )
    } else {
      reasons.add("Rainfall unknown — the landslide trigger is excluded, not assumed dry.")
    }

    // Coastal exposure: only when BOTH elevation and distance are known.
    if (nearCoast != null) {
      val low = elevation.center
      val coastalScore = when {
        !nearCoast -> 100.0
        low >= 10.0 -> 80.0
        low >= COASTAL_LOW_ELEVATION_M -> 40.0
        else -> 0.0
      }
      weighted += coastalScore * WEIGHT_COASTAL
      weightSum += WEIGHT_COASTAL
      reasons.add(
        if (nearCoast)
          "Site is within 5 km of the coast at %.0f m elevation — storm-surge exposure applies.".format(low)
        else "Site is more than 5 km from the coast."
      )
    } else {
      reasons.add("Coast distance unresolved — coastal exposure excluded from the score.")
    }

    var score = weighted / weightSum

    // The dynamic escalation: on an already-risky slope, extreme rain can push
    // a CAUTION hillside over the line. It can never create a risk out of a
    // flat (slope < CAUTION) site — that would fabricate hazards.
    val escalating = rainfallMm24h != null && rainfallMm24h >= HEAVY_RAIN_MM24 && slope >= CAUTION_SLOPE_PERCENT
    if (escalating) {
      val deficit = 100.0 - score
      score = (100.0 - deficit * triggerFactor).coerceIn(0.0, 100.0)
      reasons.add("Rainfall trigger x%.2f applied to a %.0f%% hillside.".format(triggerFactor, slope))
    }

    val band0 = bandFor(score, slope, rainfallMm24h)
    var band = band0
    // Slope floor: a hillside at or past the high-risk slope line is a
    // relocation candidate whatever the weighted average says.
    if (slope >= HIGH_RISK_SLOPE_PERCENT &&
      (band == SuitabilityBand.SAFE || band == SuitabilityBand.CAUTION)
    ) {
      band = SuitabilityBand.HIGH_RISK
      score = minOf(score, 40.0)
      reasons.add("Slope at or beyond the %.0f%% high-risk line.".format(HIGH_RISK_SLOPE_PERCENT))
    }
    // Coastal elevation cap: sub-5 m land within sight of the sea can never
    // claim SAFE — storm surge and tidal inundation dominate the score.
    if (nearCoast == true && elevation.center < COASTAL_LOW_ELEVATION_M &&
      band != SuitabilityBand.RED_ZONE
    ) {
      band = SuitabilityBand.HIGH_RISK
      score = minOf(score, 30.0)
      reasons.add(
        "Coastal site at %.0f m elevation — storm-surge exposure caps habitability.".format(elevation.center)
      )
    }

    val hard = if (band == SuitabilityBand.RED_ZONE && slope >= CAUTION_SLOPE_PERCENT &&
      (rainfallMm24h ?: 0.0) >= EXTREME_RAIN_MM24
    ) "moderate slope under extreme rainfall" else null

    return TerrainVerdict(
      status = SuitabilityStatus.ASSESSED,
      band = band,
      score = score.roundToInt().coerceIn(0, 100),
      slopePercent = slope,
      rainfallMm24h = rainfallMm24h,
      rainfallUsed = rainfallMm24h != null,
      hardRuleHit = hard,
      reasons = reasons.toList(),
      source = if (rainfallMm24h != null)
        "SRTM 30 m (Open-Meteo Elevation API) + Open-Meteo forecast"
      else "SRTM 30 m (Open-Meteo Elevation API)",
      disclaimer = DISCALIMER
    )
  }

  private fun bandFor(score: Double, slope: Double, rain: Double?): SuitabilityBand = when {
    // Escalation band: a hillside (>= CAUTION slope) soaked by extreme rain.
    slope >= CAUTION_SLOPE_PERCENT && (rain ?: 0.0) >= EXTREME_RAIN_MM24 -> SuitabilityBand.RED_ZONE
    score >= 70.0 -> SuitabilityBand.SAFE
    score >= 45.0 -> SuitabilityBand.CAUTION
    score >= 25.0 -> SuitabilityBand.HIGH_RISK
    else -> SuitabilityBand.RED_ZONE
  }

  private fun rise(grid: ElevationGrid): Double =
    maxOf(
      abs(grid.north - grid.center), abs(grid.south - grid.center),
      abs(grid.east - grid.center), abs(grid.west - grid.center)
    )

  const val DISCALIMER =
    "Decision-support heuristic on open data; NOT an official government red-zone notification."
}

/** Output of one habitability assessment. */
data class TerrainVerdict(
  val status: SuitabilityStatus,
  val band: SuitabilityBand?,
  /** 0..100 habitability score; -1 when INSUFFICIENT_DATA. */
  val score: Int,
  val slopePercent: Double?,
  val rainfallMm24h: Double?,
  val rainfallUsed: Boolean,
  val hardRuleHit: String?,
  val reasons: List<String>,
  val source: String,
  val disclaimer: String
) {
  val isUnsuitableForPermanentHabitation: Boolean
    get() = status == SuitabilityStatus.ASSESSED && band == SuitabilityBand.RED_ZONE

  fun provenance(recordedAt: Long): DataProvenance = DataProvenance(
    source = source,
    status = DataProvenance.STATUS_LIVE,
    confidence = if (status == SuitabilityStatus.ASSESSED) 0.7 else 0.0,
    isVerified = false,
    classification = DataClassification.DERIVED,
    recordedAtMillis = recordedAt,
    lastUpdatedMillis = recordedAt
  )
}
