package com.example.data.shelters

import com.example.data.risk.HazardAnalysisService
import com.example.data.model.GeoMath
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint

/**
 * Why a candidate shelter was REJECTED (hard ineligibility).
 */
enum class RejectionReason(val label: String) {
  INSIDE_HAZARD_AREA("Inside an active hazard area"),
  NO_REMAINING_CAPACITY("Full — no remaining capacity"),
  UNREACHABLE("Unreachable"),
  NOT_OPERATING("Not operating")
}

/** User-facing ranking reason shown in the "Why this safe zone?" panel. */
data class SelectionReason(val text: String)

/**
 * A fully evaluated safe-zone candidate.
 */
data class SafeZoneEvaluation(
  val zone: SafeZone,
  val distanceMeters: Double,
  val isFeasible: Boolean,
  val rejectionReason: RejectionReason?,
  /** Higher is better. */
  val score: Int,
  val reasons: List<SelectionReason>,
  val hazardExposureCount: Int,
  val capacityReport: ShelterCapacityService.CapacityReport
) {
  val rankExplanation: String get() = reasons.joinToString(" • ") { it.text }
}

/**
 * Structured safe-zone selection engine.
 *
 * Replaces "nearest shelter only" thinking with a two-phase decision:
 *
 *   PHASE 1 — REJECT ineligibles: inside hazard area / full / not operating /
 *   unreachable (unreachable is reserved for future blocked-road data).
 *
 *   PHASE 2 — RANK feasible survivors on safety, remaining capacity, distance,
 *   travel time, accessibility, resources, medical support and vulnerable
 *   suitability.
 */
object SafeZoneEvaluator {

  /** Candidate-origin context for one decision run. */
  data class RequestContext(
    val origin: GeoPoint,
    val hazards: List<com.example.data.model.HazardZone>,
    val walkingSpeedMps: Double = 1.35,
    val hasVulnerableMembers: Boolean = false,
    val needsMedicalSupport: Boolean = false,
    /**
     * Fetched elevation of the ORIGIN (Open-Meteo SRTM, ~m above sea level).
     * Null = altitude unknown - the evaluator never guesses it.
     */
    val originElevationMeters: Double? = null,
    /**
     * Fetched elevation per shelter id. Missing entries stay UNKNOWN and
     * score neutrally (no fabricated hill, no penalty).
     */
    val zoneElevations: Map<String, Double> = emptyMap()
  )

  fun evaluateAll(zones: List<SafeZone>, ctx: RequestContext): List<SafeZoneEvaluation> =
    zones.map { evaluate(it, ctx) }

  fun evaluate(zone: SafeZone, ctx: RequestContext): SafeZoneEvaluation {
    val distance = GeoMath.distanceMeters(ctx.origin, zone.point)
    val exposures = HazardAnalysisService.affectingHazards(zone.point, ctx.hazards)
    val capacity = ShelterCapacityService.report(zone)
    val reasons = mutableListOf<SelectionReason>()

    // -------- PHASE 1: hard rejections --------------------------------------
    var rejection: RejectionReason? = null
    if (exposures.isNotEmpty()) {
      rejection = RejectionReason.INSIDE_HAZARD_AREA
    } else if (!capacity.acceptsNewOccupants) {
      rejection = RejectionReason.NO_REMAINING_CAPACITY
    } else if (zone.operatingStatus.trim().uppercase() != "OPEN") {
      rejection = RejectionReason.NOT_OPERATING
    }
    // Unreachable: reserved for future blocked-road/bridge data — no
    // fabricated road closures at this stage.
    if (rejection == null && distance > UNREACHABLE_DISTANCE_LIMIT_METERS) {
      rejection = RejectionReason.UNREACHABLE
    }

    if (rejection != null) {
      return SafeZoneEvaluation(
        zone = zone,
        distanceMeters = distance,
        isFeasible = false,
        rejectionReason = rejection,
        score = 0,
        reasons = listOf(SelectionReason(rejection.label)),
        hazardExposureCount = exposures.size,
        capacityReport = capacity
      )
    }

    // -------- PHASE 2: composite ranking score ------------------------------
    val safetyScore = (100 - exposures.size * 25).coerceAtLeast(0)
    val capacityScore = if (zone.capacityTotal > 0) {
      ((capacity.availableCapacity.toFloat() / zone.capacityTotal) * 100f).toInt().coerceIn(0, 100)
    } else 0
    // Walking-impact curve (user rule: relocate to NEARBY places):
    // <= 1 km = full score (comfortably on foot), linear to ZERO at 10 km.
    // The old flat /40 km scale made 2 km and 8 km almost indistinguishable.
    val distanceScore = (100.0 - ((distance - 1_000.0).coerceAtLeast(0.0) /
      (WALKABLE_DISTANCE_CEILING_METERS - 1_000.0)) * 100.0)
      .coerceIn(0.0, 100.0).toInt()
    // Altitude score: being HIGHER than the hazard origin means water runs
    // away from you, not toward the shelter. Only computed from REAL fetched
    // elevations; unknown altitude scores neutral NEUTRAL and is labelled.
    val zoneElev = ctx.zoneElevations[zone.id]
    val altitudeKnown = zoneElev != null && ctx.originElevationMeters != null
    val climb = if (altitudeKnown) zoneElev!! - ctx.originElevationMeters!! else 0.0
    val altitudeScore = when {
      !altitudeKnown -> ALTITUDE_NEUTRAL_SCORE
      climb >= 2.0 -> 100
      climb >= 0.0 -> 70
      climb >= -5.0 -> 35
      else -> 0
    }
    val resourceScore = listOf(
      zone.waterAvailable,
      zone.foodAvailable,
      zone.electricityAvailable,
      zone.sanitationAvailable
    ).count { it } * 25
    val medicalScore = if (zone.medicalSupport) 100 else 0
    val accessibilityScore = when {
      zone.accessibility.contains("wheelchair", ignoreCase = true) ||
        zone.accessibility.contains("ambulance", ignoreCase = true) -> 100
      zone.accessibility.contains("Highway", ignoreCase = true) ||
        zone.accessibility.contains("District HQ", ignoreCase = true) -> 80
      else -> 40
    }
    val vulnerableScore = when {
      zone.womenChildrenSuitability && zone.medicalSupport -> 100
      zone.womenChildrenSuitability -> 65
      else -> 30
    }
    var score = (
      safetyScore * W_SAFETY +
        capacityScore * W_CAPACITY +
        distanceScore * W_DISTANCE +
        altitudeScore * W_ALTITUDE +
        accessibilityScore * W_ACCESS +
        resourceScore * W_RESOURCES +
        medicalScore * W_MEDICAL +
        vulnerableScore * W_VULNERABLE
      ).toInt()
    if (ctx.needsMedicalSupport && zone.medicalSupport) score += BONUS_MEDICAL_NEEDED
    if (ctx.hasVulnerableMembers && zone.womenChildrenSuitability) score += BONUS_VULNERABLE
    score = score.coerceIn(0, 100 + BONUS_MEDICAL_NEEDED + BONUS_VULNERABLE)

    // -------- User-facing "Why this safe zone?" reasons ----------------------
    if (exposures.isEmpty()) {
      reasons += SelectionReason("Lower hazard exposure — outside all active hazard areas")
    }
    if (capacity.availableCapacity > 0) {
      reasons += SelectionReason(
        "${capacity.availableCapacity} people capacity remaining (${capacity.statusLabel})"
      )
    }
    reasons += SelectionReason(
      "About ${GeoMath.formatKm(distance)} away (~${estimateTravelMinutes(distance, ctx.walkingSpeedMps)} min walk)"
    )
    when {
      altitudeKnown && climb >= 2.0 -> reasons += SelectionReason(
        "Terrain %.0f m HIGHER than your location - water drains away from it".format(climb)
      )
      altitudeKnown && climb <= -5.0 -> reasons += SelectionReason(
        "CAUTION: terrain %.0f m LOWER than your location - could collect flood water".format(-climb)
      )
      altitudeKnown -> reasons += SelectionReason(
        "Terrain at about the same height as your location"
      )
      else -> reasons += SelectionReason(
        "Altitude of this shelter is not yet known (terrain lookup pending or unavailable)"
      )
    }
    if (zone.medicalSupport) reasons += SelectionReason("Medical support available on site")
    if (ctx.hasVulnerableMembers && zone.womenChildrenSuitability) {
      reasons += SelectionReason("Suitable for women, children and elderly evacuees")
    }
    if (zone.waterAvailable && zone.foodAvailable) {
      reasons += SelectionReason("Water and food reserves stocked")
    }
    if (zone.accessibility.contains("wheelchair", ignoreCase = true)) {
      reasons += SelectionReason("Wheelchair accessible")
    }

    return SafeZoneEvaluation(
      zone = zone,
      distanceMeters = distance,
      isFeasible = true,
      rejectionReason = null,
      score = score,
      reasons = reasons,
      hazardExposureCount = exposures.size,
      capacityReport = capacity
    )
  }

  /** Ranked candidate list (feasible only, best first). */
  fun ranked(zones: List<SafeZone>, ctx: RequestContext): List<SafeZoneEvaluation> =
    evaluateAll(zones, ctx).filter { it.isFeasible }.sortedByDescending { it.score }

  fun estimateTravelMinutes(distanceMeters: Double, speedMps: Double): Int =
    (distanceMeters / speedMps / 60.0).toInt().coerceAtLeast(1)

  // Composite weights: safety dominates, then capacity, distance, access,
  // resources, medical, vulnerable suitability.
  private const val W_SAFETY = 0.28
  private const val W_CAPACITY = 0.16
  // NEARBY-FIRST + ALTITUDE (user rules): distance is now the second
  // strongest factor after safety, and real SRTM altitude counts.
  private const val W_DISTANCE = 0.24
  private const val W_ALTITUDE = 0.06
  private const val W_ACCESS = 0.08
  private const val W_RESOURCES = 0.06
  private const val W_MEDICAL = 0.06
  private const val W_VULNERABLE = 0.06
  private const val WALKABLE_DISTANCE_CEILING_METERS = 10_000.0
  private const val ALTITUDE_NEUTRAL_SCORE = 50
  private const val BONUS_MEDICAL_NEEDED = 10
  private const val BONUS_VULNERABLE = 10
  private const val UNREACHABLE_DISTANCE_LIMIT_METERS = 40_000.0
}
