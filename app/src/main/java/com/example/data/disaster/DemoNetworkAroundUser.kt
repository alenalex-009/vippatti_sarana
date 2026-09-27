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
  /** Demo shelters generated AROUND the focus so the app always has multiple
   * nearest safe zones to present (user request: not one lone shelter). */
  const val DEMO_SHELTER_COUNT = 4
  const val DEMO_HAZARD_RADIUS_M = 4_000.0
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
   * the hazard circle (5 km from focus AND 8 km from hazard center, both >
   * 4 km radius), OPEN, with free capacity — so the evaluator ranks it and
   * the GO card can route.
   */
  fun shelterNear(focus: GeoPoint): SafeZone = sheltersAround(focus, hazardBearingOnly = true).first()

  /**
   * The demo shelter NETWORK around the focus: [DEMO_SHELTER_COUNT] shelters on
   * evenly spaced bearings so at least one is ALWAYS opposite the demo hazard
   * (the old single opposite shelter is still the first entry). Distances vary
   * slightly per index so the ranking has real spread. Every one sits outside
   * the hazard circle: bearing spread + 5 km keeps them beyond the 4 km radius
   * from the hazard center as long as they face away from it; those that would
   * fall inside are nudged to 6 km.
   */
  fun sheltersAround(focus: GeoPoint, hazardBearingOnly: Boolean = false): List<SafeZone> {
    val hazardBearing = bearingFor(focus)
    val out = ArrayList<SafeZone>(DEMO_SHELTER_COUNT)
    for (i in 0 until DEMO_SHELTER_COUNT) {
      // Shelter 0 sits exactly opposite the hazard; the others fan around the
      // compass so the carousel shows a genuine set of NEARBY options.
      val bearing = if (i == 0) (hazardBearing + 180.0) % 360.0
        else (hazardBearing + 180.0 + i * 90.0) % 360.0
      var distance = DEMO_SHELTER_DISTANCE_KM + i * 0.7
      val p0 = offset(focus, distance, bearing)
      // Keep every demo shelter OUTSIDE the demo hazard circle (center->shelter
      // must exceed the radius, else the evaluator rejects it as trapped).
      val hazardCenter = offset(focus, DEMO_HAZARD_DISTANCE_KM, hazardBearing)
      if (com.example.data.model.GeoMath.distanceMeters(hazardCenter, p0) <= DEMO_HAZARD_RADIUS_M + 300.0) {
        distance += 2.0
      }
      val p = offset(focus, distance, bearing)
      if (!IndiaGeo.contains(p)) continue
      out += SafeZone(
        id = "demo-sz-${quant(focus)}-$i",
        name = DEMO_SHELTER_NAMES[i % DEMO_SHELTER_NAMES.size] + " (simulated)",
        lat = p.lat,
        lon = p.lon,
        locationNote = "SIMULATED record — demo shelter about %.1f km from your location".format(
          com.example.data.model.GeoMath.distanceMeters(focus, p) / 1000.0
        ),
        capacityTotal = DEMO_SHELTER_CAPACITY + i * 60,
        capacityCurrent = DEMO_SHELTER_OCCUPIED + i * 45,
        waterAvailable = true,
        foodAvailable = true,
        electricityAvailable = true,
        sanitationAvailable = i != 2,
        medicalSupport = true,
        accessibility = if (i % 2 == 0) "DEMO — highway access" else "DEMO — district road access",
        womenChildrenSuitability = true,
        operatingStatus = if (i == DEMO_SHELTER_COUNT - 1 && !hazardBearingOnly) "CLOSED" else "OPEN",
        verificationStatus = "SIMULATED — not a verified shelter",
        elevationNote = "DEMO placeholder",
        provenance = demoProvenance
      )
    }
    return out
  }

  /** The pair for a focus point; null when the point is outside India. */
  fun around(focus: GeoPoint): Pair<HazardZone, SafeZone>? {
    if (!IndiaGeo.contains(focus)) return null
    return hazardNear(focus) to shelterNear(focus)
  }

  private val DEMO_SHELTER_NAMES = listOf(
    "DEMO Community Hall", "DEMO School Shelter",
    "DEMO Relief Camp", "DEMO Stadium Shelter"
  )

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
