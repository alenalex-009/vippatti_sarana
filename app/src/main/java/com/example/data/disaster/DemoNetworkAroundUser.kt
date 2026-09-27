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
 *  - WALKING SCALE (user rule: "if a safe place is 2 km away I can't
 *    travel that far"): the hazard center sits ~1.2 km away with a ~1.6 km
 *    radius, so the FOCUS ITSELF lies inside the zone (1.2 < 1.6) - risk
 *    assessment honestly escalates and the guidance card fires;
 *  - the primary shelter sits ~0.8 km away on the OPPOSITE bearing (~2 km
 *    from the hazard center, outside its radius) - reachable on foot in
 *    ~10 minutes, eligible in every evaluator rule;
 *  - focus outside India produces NOTHING (the India-only guard keeps the
 *    final word);
 *  - both records carry classification = SIMULATED + ids namespaced "demo-"
 *    so they can never collide with registry or live records;
 *  - names stay place-generic: the coordinates are demo, so the names must
 *    not pretend to know local geography.
 */
object DemoNetworkAroundUser {

  // WALKING-SCALE scenario (user rule: "a safe place 2 km away is already
  // too far") - the hazard sits just outside the user, the primary shelter is
  // 0.8 km away on the opposite bearing, and the fanned options are 1.5-3.5 km.
  const val DEMO_HAZARD_DISTANCE_KM = 1.2
  const val DEMO_SHELTER_DISTANCE_KM = 0.8
  /** Demo shelters generated AROUND the focus so the app always has multiple
   * nearest safe zones to present (user request: not one lone shelter). */
  const val DEMO_SHELTER_COUNT = 5
  const val DEMO_HAZARD_RADIUS_M = 1_600.0
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
   * (the primary opposite shelter is the first entry, ~0.8 km WALKING scale).
   * Distances vary slightly per index so the ranking has real spread. Every
   * shelter sits outside the hazard circle: bearings that face the hazard are
   * walked outward in small steps until they clear it, so the whole set stays
   * as close to the user as the circle allows.
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
      // Walk OUTWARD in small steps until this shelter clears the hazard
      // circle by a safety margin - works at any bearing angle and keeps
      // every demo option as close to walking scale as possible.
      while (com.example.data.model.GeoMath.distanceMeters(
          hazardCenter, offset(focus, distance, bearing)
        ) <= DEMO_HAZARD_RADIUS_M + 300.0 && distance < 40.0
      ) {
        distance += 0.3
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
