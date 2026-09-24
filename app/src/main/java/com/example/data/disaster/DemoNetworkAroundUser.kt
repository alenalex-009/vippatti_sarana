package com.example.data.disaster

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * ============================================================================
 * DEMO NETWORK AROUND A FOCUS POINT (SIMULATED, honestly labelled)
 * ============================================================================
 *
 * WHY THIS EXISTS: the India-wide demo network (PilotRegionData) is 14 spots
 * mostly far from any given user, so with the DEMO switch ON, the map near
 * the user shows nothing — the exact "where is my danger zone / safe zone?"
 * complaint. This generator materialises ONE small, deterministic, clearly
 * SIMULATED hazard + shelter pair AROUND the current focus point (device GPS
 * or the chosen place from the picker), so demo mode always demonstrates the
 * full journey: danger near you -> nearest safe zone -> route there.
 *
 * Rules (each unit-tested):
 *  - deterministic: same focus => same network (coordinate-seeded, no RNG);
 *  - the hazard center sits ~3 km away with radius ~4 km, so the FOCUS ITSELF
 *    lies inside the zone (3 < 4.05) — risk assessment honestly escalates
 *    and the guidance card fires;
 *  - the shelter sits ~5 km away on the OPPOSITE bearing (~8 km from the
 *    hazard center, outside its radius) — eligible in every evaluator rule
 *    and within the 40 km reachability limit;
 *  - focus outside India produces NOTHING (the India-only guard keeps the
 *    final word);
 *  - both records carry classification = SIMULATED + ids namespaced "demo-"
 *    so they can never collide with registry or live records;
 *  - names stay place-generic: the coordinates are demo, so the names must
 *    not pretend to know local geography.
 */
object DemoNetworkAroundUser {

  const val DEMO_HAZARD_DISTANCE_KM = 3.0
  const val DEMO_SHELTER_DISTANCE_KM = 5.0
  const val DEMO_HAZARD_RADIUS_M = 4_050.0
  const val DEMO_SHELTER_CAPACITY = 240
  const val DEMO_SHELTER_OCCUPIED = 30

  val demoProvenance: DataProvenance
    get() = DataProvenance(
      source = "Vippatti Sarana DEMO network generated around the current focus point (SIMULATED, not real)",
      status = "SIMULATED DEMO",
      confidence = 0.3,
      isVerified = false,
      classification = DataClassification.SIMULATED
    )

  /**
   * Deterministic hazard mix from the focus coordinates (a tiny hash of the
   * coordinate decimals): different places demonstrate different disasters,
   * while the SAME place always shows the SAME demo hazard.
   */
  fun hazardTypeFor(point: GeoPoint): HazardType {
    val seed = (((point.lat * 10_000).toLong() * 31L +
      (point.lon * 10_000).toLong()) % 7L + 7L) % 7L
    return when (seed.toInt()) {
      0 -> HazardType.FLOOD
      1 -> HazardType.HEAVY_RAINFALL
      2 -> HazardType.LANDSLIDE
      3 -> HazardType.CYCLONE
      4 -> HazardType.FIRE
      5 -> HazardType.EARTHQUAKE
      else -> HazardType.WEATHER_ALERT
    }
  }

  /** The demo hazard whose circle COVERS the focus point (d = 3 km < r = 4.05 km). */
  fun hazardNear(focus: GeoPoint): HazardZone {
    val center = offset(focus, DEMO_HAZARD_DISTANCE_KM, bearingFor(focus))
    val type = hazardTypeFor(focus)
    return HazardZone(
      id = "demo-hz-${quant(focus)}",
      name = "DEMO ${type.label} Zone (simulated)",
      type = type,
      severity = HazardSeverity.HIGH,
      center = center,
      radiusMeters = DEMO_HAZARD_RADIUS_M,
      riskLevel = "SIMULATED — demonstration only",
      trend = HazardTrend.STABLE,
      sourceStatus = "DEMO DATA — generated around your location, NOT real",
      lastUpdatedMillis = 0L,
      provenance = demoProvenance
    )
  }

  /**
   * The demo safe shelter, opposite the hazard bearing ~5 km away: outside
   * every hazard circle (8 km from its center > 4.05 km radius), OPEN, with
   * free capacity — so the evaluator ranks it and the GO card can route.
   */
  fun shelterNear(focus: GeoPoint): SafeZone {
    val p = offset(focus, DEMO_SHELTER_DISTANCE_KM, (bearingFor(focus) + 180.0) % 360.0)
    return SafeZone(
      id = "demo-sz-${quant(focus)}",
      name = "DEMO Relief Shelter (simulated)",
      lat = p.lat,
      lon = p.lon,
      locationNote = "SIMULATED record — demo shelter placed opposite the demo hazard, " +
        "about ${DEMO_SHELTER_DISTANCE_KM.toInt()} km from your location",
      capacityTotal = DEMO_SHELTER_CAPACITY,
      capacityCurrent = DEMO_SHELTER_OCCUPIED,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "DEMO — road access assumed",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "SIMULATED — not a verified shelter",
      elevationNote = "DEMO placeholder",
      provenance = demoProvenance
    )
  }

  /** The pair for a focus point; null when the point is outside India. */
  fun around(focus: GeoPoint): Pair<HazardZone, SafeZone>? {
    if (!IndiaGeo.contains(focus)) return null
    return hazardNear(focus) to shelterNear(focus)
  }

  // ---------------------------------------------------------------- internals

  /** Stable bearing (degrees) derived from the focus coordinates. */
  private fun bearingFor(point: GeoPoint): Double {
    val seed = (((point.lat * 10_000).toLong() xor (point.lon * 10_000).toLong()) % 3600L + 3600L) % 3600L
    return seed.toDouble() / 10.0
  }

  /** Offset a point by [distanceKm] along [bearingDeg] (flat-Earth, fine at these scales). */
  private fun offset(from: GeoPoint, distanceKm: Double, bearingDeg: Double): GeoPoint {
    val latDelta = distanceKm / 111.32 * cos(Math.toRadians(bearingDeg))
    val kmPerLonDeg = 111.32 * cos(Math.toRadians(from.lat)).coerceAtLeast(0.0001)
    val lonDelta = distanceKm / kmPerLonDeg * sin(Math.toRadians(bearingDeg))
    return GeoPoint(from.lat + latDelta, from.lon + lonDelta)
  }

  /** Coordinate bucket inside ids so a moved focus gets fresh ids. */
  private fun quant(point: GeoPoint): String =
    "${(point.lat * 100).toLong()}_${(point.lon * 100).toLong()}"
}
