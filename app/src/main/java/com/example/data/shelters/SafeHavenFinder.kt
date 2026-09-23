package com.example.data.shelters

import com.example.data.model.GeoMath
import com.example.data.routing.GeoPoint
import com.example.data.suitability.CoastDistanceGrid
import com.example.data.suitability.SuitabilityBand
import com.example.data.suitability.TerrainProbeResult
import com.example.data.suitability.TerrainProbeService
import com.example.data.suitability.TerrainVerdict

/**
 * ============================================================================
 * TERRAIN-DERIVED SAFE HAVEN FINDER
 * ============================================================================
 *
 * Last-resort answer to "a disaster is happening and no registered shelter
 * exists here": probe the actual terrain outward in rings and return the
 * NEAREST point the habitability engine rates SAFE (gentle slope, no live
 * rainfall escalation, not a sub-5 m coastal flat).
 *
 * Honesty contract: the result is a DERIVED open-terrain suggestion, always
 * labelled as such with the engine disclaimer — never a "shelter", never a
 * verified facility, and no route to it is drawn unless OSRM answers. The
 * finder probes through an injectable service (production: the real live
 * TerrainProbeService) so the whole rule set is offline-testable.
 */
data class SafeHaven(
  val point: GeoPoint,
  val distanceMeters: Double,
  val verdict: TerrainVerdict
) {
  val headline: String
    get() = "Nearest open terrain rated safe (≈${GeoMath.formatKm(distanceMeters)} away)"
  val detail: String
    get() = verdict.reasons.joinToString(" • ")
}

class SafeHavenFinder(
  /** One candidate probe: point + coast grid -> verdict result. */
  private val probe: suspend (GeoPoint, CoastDistanceGrid?) -> TerrainProbeResult,
  /** Cardinal bearings probed per ring (degrees from true north). */
  private val bearings: List<Double> = listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)
) {

  /**
   * Searches rings (nearest first) up to [maxCandidates] probes. Returns the
   * closest candidate the engine rates SAFE, or null when nothing safe was
   * found — never a fabricated destination.
   */
  suspend fun find(
    origin: GeoPoint,
    coastGrid: CoastDistanceGrid?,
    maxCandidates: Int = 32,
    rings: List<Double> = listOf(1_000.0, 2_500.0, 5_000.0)
  ): SafeHaven? {
    var probes = 0
    for (radius in rings) {
      for (bearing in bearings) {
        if (probes >= maxCandidates) return null
        probes++
        val candidate = GeoMath.offsetPoint(origin, bearing, radius)
        val result = probe(candidate, coastGrid)
        if (result is TerrainProbeResult.Success && result.verdict.band == SuitabilityBand.SAFE) {
          return SafeHaven(
            point = candidate,
            distanceMeters = GeoMath.distanceMeters(origin, candidate),
            verdict = result.verdict
          )
        }
      }
    }
    return null
  }

  /** Production probe: the live terrain service against a real coast grid. */
  companion object {
    fun live(service: TerrainProbeService): SafeHavenFinder =
      SafeHavenFinder(probe = { point, grid -> service.probe(point, grid) })
  }
}
