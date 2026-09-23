package com.example.data.shelters

import com.example.data.model.GeoMath
import com.example.data.risk.RiskLevel

/**
 * ============================================================================
 * EMERGENCY SHELTER GUIDANCE — "when a disaster happens, show me where to go"
 * ============================================================================
 *
 * Pure decision function over state the ViewModel already holds:
 *   risk level (from live hazards) + evaluated shelters + current destination.
 *
 * Rules:
 * - RED/ORANGE risk demands a concrete suggestion the moment ANY feasible
 *   shelter exists — the NEAREST feasible one (distance, not composite score:
 *   under threat, metres matter more than amenities; the full ranking stays
 *   available in the panels).
 * - Ineligible shelters (full / closed / inside a hazard) are never suggested;
 *   their real rejection reasons are surfaced instead of silence.
 * - YELLOW/GREEN must not nag.
 * - No invented destinations: with nothing eligible the guidance says exactly
 *   that and points at 112.
 */
sealed class EmergencyGuidance {
  /** Calm risk, or an existing flow already handles it. */
  data object None : EmergencyGuidance()

  /** A route/destination is already active — the guidance defers to it. */
  data object AlreadyRouting : EmergencyGuidance()

  /** Danger, but no shelter records exist in scope at all. */
  data class NoShelterKnown(
    val headline: String,
    val detail: String
  ) : EmergencyGuidance()

  /** Danger, shelters exist but every one is ineligible. */
  data class NoShelterEligible(
    val headline: String,
    /** "<name>: <real rejection reason>" per candidate, nearest first. */
    val rejections: List<String>
  ) : EmergencyGuidance()

  /** Danger with a nearest feasible shelter — the actionable card. */
  data class SuggestShelter(
    val evaluation: SafeZoneEvaluation,
    val distanceMeters: Double,
    val headline: String,
    val detail: String
  ) : EmergencyGuidance()

  companion object {

    fun forSituation(
      riskLevel: RiskLevel,
      evaluations: List<SafeZoneEvaluation>,
      hasActiveDestination: Boolean
    ): EmergencyGuidance {
      if (riskLevel != RiskLevel.RED && riskLevel != RiskLevel.ORANGE) return None
      if (hasActiveDestination) return AlreadyRouting

      val feasible = evaluations.filter { it.isFeasible }
      if (evaluations.isEmpty()) {
        return NoShelterKnown(
          headline = "DANGER NEAR YOU — no registered shelter in range",
          detail = "No shelter record exists for this area in this build. Move to " +
            "higher ground away from watercourses and riverbanks and call 112."
        )
      }
      if (feasible.isEmpty()) {
        return NoShelterEligible(
          headline = "DANGER NEAR YOU — no eligible shelter nearby",
          rejections = evaluations.sortedBy { it.distanceMeters }.map {
            "${it.zone.name}: ${it.rejectionReason?.label ?: "ineligible"}"
          }
        )
      }
      val nearest = feasible.minByOrNull { it.distanceMeters }!!
      val skippedIneligible = evaluations.size - feasible.size
      return SuggestShelter(
        evaluation = nearest,
        distanceMeters = nearest.distanceMeters,
        headline = "Head to the nearest safe zone: ${nearest.zone.name}",
        detail = buildString {
          append("About ${GeoMath.formatKm(nearest.distanceMeters)} away")
          nearest.capacityReport.availableCapacity.let { if (it > 0) append(" • $it spaces open") }
          if (skippedIneligible > 0) {
            append(" • $skippedIneligible closer option(s) ruled out as ineligible")
          }
        }
      )
    }
  }
}
