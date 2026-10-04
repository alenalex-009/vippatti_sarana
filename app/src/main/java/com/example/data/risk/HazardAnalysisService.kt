package com.example.data.risk

import com.example.data.model.GeoMath
import com.example.data.model.HazardZone
import com.example.data.routing.GeoPoint

/**
 * Result of analyzing hazards around a single point (usually the user).
 */
data class HazardExposure(
  val hazard: HazardZone,
  /** Distance in meters from the point to the hazard center (negative inside). */
  val distanceToCenterMeters: Double,
  val isInsideZone: Boolean
)

/**
 * Pure hazard analysis over the India-network hazard dataset.
 *
 * FUTURE INTEGRATION: when live feeds (IMD rainfall, KSDMA alerts, NRSC flood
 * extents, CWC river gauges, GSI landslide warnings) are connected, only the
 * input list changes — every consumer (risk engine, evaluator, routing
 * penalties, map overlays) already speaks HazardZone.
 */
object HazardAnalysisService {

  /**
   * All hazards affecting [point]. A hazard affects the point when and ONLY
   * when the point lies inside the hazard's published radius:
   *   distance(center) <= radiusMeters
   *
   * DOCUMENTED DECISION (audit item B6, reconciled 2026-09-25): this service
   * deliberately applies NO extra safety margin. Earlier wording here claimed
   * "or within the safety margin of its edge" - the implementation never had
   * one, and inventing one now would silently change risk levels without a
   * hazard-science basis, violating the no-fabrication rule. The margin idea
   * lives where it was actually built: ROUTE safety, which uses the provider
   * geometry only to raise a CAUTION band near a zone edge (see
   * OsrmRoutingService CAUTION_BAND_FACTOR). A user standing just OUTSIDE a
   * zone stays non-exposed here and their route toward/along it still shows
   * CAUTION - the two systems stay complementary, not duplicated.
   * If a real caution band is ever wanted for personal risk, it must come
   * from a documented IMD/NDMA buffer rule, not from a constant of ours.
   */
  fun affectingHazards(point: GeoPoint, hazards: List<HazardZone>): List<HazardExposure> =
    hazards.map { hazard ->
      val d = GeoMath.distanceMeters(point, hazard.center)
      HazardExposure(
        hazard = hazard,
        distanceToCenterMeters = d,
        isInsideZone = d <= hazard.radiusMeters
      )
    }.filter { it.isInsideZone }

  /**
   * Where the USER stands relative to one hazard circle (spec: USER POSITION
   * RELATIVE TO HAZARD). Only classification the circle geometry actually
   * supports: inside / near the boundary (within 500 m outside) / outside.
   * There is deliberately NO "deep central" class - a circle gives no basis
   * for it; claiming one would be an invented classification.
   */
  enum class PositionVsHazard(val label: String) {
    INSIDE("You are INSIDE this hazard area"),
    NEAR_EDGE("You are near the edge of this hazard area"),
    OUTSIDE("You are outside this hazard area")
  }

  fun classifyPosition(
    point: GeoPoint,
    hazard: HazardZone,
    edgeMarginMeters: Double = 500.0
  ): Pair<PositionVsHazard, Double> {
    val d = GeoMath.distanceMeters(point, hazard.center)
    val inside = d <= hazard.radiusMeters
    val position = when {
      inside -> PositionVsHazard.INSIDE
      d <= hazard.radiusMeters + edgeMarginMeters -> PositionVsHazard.NEAR_EDGE
      else -> PositionVsHazard.OUTSIDE
    }
    // Signed distance to the EDGE: negative = how deep inside, positive =
    // how far outside. Straight from the circle geometry, nothing invented.
    return position to (d - hazard.radiusMeters)
  }

  /**
   * Nearest hazard of any type — used for GREEN risk explanations
   * ("no active hazard within X km").
   */
  fun nearestHazard(point: GeoPoint, hazards: List<HazardZone>): HazardExposure? {
    if (hazards.isEmpty()) return null
    return hazards
      .map { h ->
        val d = GeoMath.distanceMeters(point, h.center)
        HazardExposure(h, d, d <= h.radiusMeters)
      }
      .minByOrNull { it.distanceToCenterMeters }
  }

  }
