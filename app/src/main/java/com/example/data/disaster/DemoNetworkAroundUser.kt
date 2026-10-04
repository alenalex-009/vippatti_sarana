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

  // Walking-scale scenario: hazard just outside the user, primary shelter
  // reachable in ~10 minutes on foot. Distances are STARTS, not promises —
  // each shelter is walked outward until it clears the hazard circle for its
  // TYPE (see radiusMetersFor), so real distances differ per place + disaster
  // and always come from the actual coordinates (never hardcoded text).
  const val DEMO_HAZARD_DISTANCE_KM = 1.2
  const val DEMO_SHELTER_DISTANCE_KM = 0.8
  /** Candidate fan size; validation may trim the set (2-5 rule). */
  const val DEMO_SHELTER_COUNT = 5
  const val DEMO_SHELTER_CAPACITY = 240
  /** Type-filter secondary hazard ring: distance of the demo hazard from you. */
  const val DEMO_SECONDARY_DISTANCE_KM = 2.2
  /** Shelters paired to each regional demo hazard. */
  const val DEMO_PAIRED_SHELTERS_PER_HAZARD = 2
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
   * GEOGRAPHIC DEMO RULE (spec part 3A/6): the simulated disaster depends on
   * the land, deterministically — same area always produces the same story:
   *  - coast grid says <=5 km to the sea  -> FLOOD or CYCLONE (seed split),
   *  - otherwise a hill/steep hash        -> LANDSLIDE,
   *  - otherwise the remaining registry   -> EARTHQUAKE / FIRE / HEAVY RAIN.
   * [coastKm] is the real nearest-coast distance from the bundled Natural
   * Earth grid (null = grid cannot answer, falls back to the pure hash).
   * Still 100% SIMULATED: this decides which DEMO story to show, never a
   * claim about live conditions.
   */
  fun hazardTypeFor(point: GeoPoint, coastKm: Int? = null): HazardType {
    val seed = (((point.lat * 10_000).toLong() * 31L +
      (point.lon * 10_000).toLong()) % 7L + 7L) % 7L
    if (coastKm != null && coastKm <= 5) {
      // Coastal / low-lying: storm surge + inundation stories.
      return if (seed % 2L == 0L) HazardType.CYCLONE else HazardType.FLOOD
    }
    // Hilliness proxy from coordinates (deterministic, no network): ranges in
    // the Himalayan/ghat belts demonstrate slope failure.
    val hill = ((point.lat * 100).toLong() * 17L + (point.lon * 100).toLong()) % 11L
    return when {
      coastKm == null && hill < 3L -> HazardType.LANDSLIDE
      hill < 5L -> HazardType.LANDSLIDE
      else -> when (seed.toInt() % 4) {
        0 -> HazardType.FLOOD
        1 -> HazardType.EARTHQUAKE
        2 -> HazardType.FIRE
        else -> HazardType.HEAVY_RAINFALL
      }
    }
  }

  /**
   * Hazard footprint by disaster TYPE (spec part 6): a cyclone's impact band
   * is wider than a urban fire's perimeter; walking-out distances and the
   * map overlay both use these honest simulated radii, labelled DEMO.
   */
  fun radiusMetersFor(type: HazardType): Double = when (type) {
    HazardType.CYCLONE -> 2_600.0
    HazardType.FLOOD, HazardType.HEAVY_RAINFALL -> 1_800.0
    HazardType.EARTHQUAKE -> 1_600.0
    HazardType.LANDSLIDE -> 1_500.0
    HazardType.FIRE -> 1_400.0
    else -> 1_400.0
  }

  /** The demo hazard whose circle COVERS the focus point, type-aware footprint. */
  fun hazardNear(focus: GeoPoint, coastKm: Int? = null): HazardZone {
    val center = offset(focus, DEMO_HAZARD_DISTANCE_KM, bearingFor(focus))
    val type = hazardTypeFor(focus, coastKm)
    return HazardZone(
      id = "demo-hz-${quant(focus)}",
      name = "DEMO ${type.label} Zone (demo scenario)",
      type = type,
      severity = HazardSeverity.HIGH,
      center = center,
      radiusMeters = radiusMetersFor(type),
      riskLevel = "DEMO — demonstration only",
      trend = HazardTrend.STABLE,
      sourceStatus = "DEMO DATA — generated around your location, NOT real",
      lastUpdatedMillis = 0L,
      provenance = demoProvenance
    )
  }

  /**
   * The demo safe shelter opposite the hazard, disaster-aware. NULL when no
   * landward candidate survives the India/sea filters (e.g. open-water
   * coordinates) - previously `.first()` crashed there.
   */
  fun shelterNear(focus: GeoPoint, coastKm: Int? = null): SafeZone? =
    sheltersAround(focus, hazardBearingOnly = true, coastKm = coastKm).firstOrNull()

  /**
   * The demo shelter NETWORK around the focus, DISASTER-AWARE (spec parts
   * 5-7). Candidates are no longer plain "focus + offset": each bearing is
   * rejected unless it moves AWAY from the hazard in the direction the
   * CURRENT disaster requires:
   *
   *  CYCLONE / coastal FLOOD: landward — the candidate must sit FURTHER from
   *    the sea than the focus does (real coast grid), which structurally
   *    prevents "shelter in the Bay of Bengal" ocean routes;
   *  LANDSLIDE: downhill-and-away is not computable offline at candidate
   *    time, so candidates stay outside the hazard band with a generous
   *    buffer; elevation ranking happens in the evaluator (real SRTM);
   *  FIRE / EARTHQUAKE / FLOOD: outside the type-specific perimeter + 300 m
   *    safety buffer (walk-out, distance comes out of the coordinates).
   *
   * Every kept candidate is inside India, outside the hazard circle, and
   * named/typed honestly as SIMULATED. Distances shown in the UI are
   * measured from THESE coordinates (no hardcoded 799 m anywhere).
   */
  fun sheltersAround(
    focus: GeoPoint,
    hazardBearingOnly: Boolean = false,
    coastKm: Int? = null
  ): List<SafeZone> {
    val hazardBearing = bearingFor(focus)
    val hazardCenter = offset(focus, DEMO_HAZARD_DISTANCE_KM, hazardBearing)
    val type = hazardTypeFor(focus, coastKm)
    val radius = radiusMetersFor(type)
    // Landward rule: cyclone (and coastal flood) shelters must move AWAY
    // from the sea. Candidate coast distance must not shrink vs the focus.
    val demandLandward = type == HazardType.CYCLONE ||
      (type == HazardType.FLOOD && coastKm != null && coastKm <= 5)
    val out = ArrayList<SafeZone>(DEMO_SHELTER_COUNT)
    var idx = 0
    var fan = 0
    while (out.size < DEMO_SHELTER_COUNT && fan < 8) {
      val bearing = if (fan == 0) (hazardBearing + 180.0) % 360.0
        else (hazardBearing + 180.0 + fan * 45.0) % 360.0
      fan++
      var distance = DEMO_SHELTER_DISTANCE_KM + out.size * 0.7
      // Walk OUTWARD until this bearing clears the hazard circle + buffer.
      while (com.example.data.model.GeoMath.distanceMeters(
          hazardCenter, offset(focus, distance, bearing)
        ) <= radius + 300.0 && distance < 40.0
      ) {
        distance += 0.3
      }
      val p = offset(focus, distance, bearing)
      if (!IndiaGeo.contains(p)) continue
      if (demandLandward && coastKm != null) {
        val candCoast = coastKmOf(p)
        if (candCoast != null && candCoast < coastKm) continue // sea-ward: reject
      }
      out += SafeZone(
        id = "demo-sz-${quant(focus)}-$idx",
        name = DEMO_SHELTER_NAMES[(idx + namesOffset(focus)) % DEMO_SHELTER_NAMES.size] +
          " (demo)",
        lat = p.lat,
        lon = p.lon,
        locationNote = "Demo record — shelter about %.1f km from your location".format(
          com.example.data.model.GeoMath.distanceMeters(focus, p) / 1000.0
        ),
        capacityTotal = DEMO_SHELTER_CAPACITY + idx * 60,
        capacityCurrent = DEMO_SHELTER_OCCUPIED + idx * 45,
        waterAvailable = true,
        foodAvailable = true,
        electricityAvailable = true,
        sanitationAvailable = idx != 2,
        medicalSupport = true,
        accessibility = if (idx % 2 == 0) "DEMO — highway access" else "DEMO — district road access",
        womenChildrenSuitability = true,
        operatingStatus = when {
          idx == DEMO_SHELTER_COUNT - 1 && !hazardBearingOnly &&
            namesOffset(focus) % 2 == 0 -> "CLOSED"
          else -> "OPEN"
        },
        verificationStatus = "SIMULATED — not a verified shelter",
        elevationNote = "DEMO placeholder",
        provenance = demoProvenance
      )
      idx++
    }
    return out
  }

  /**
   * The pair for a focus point; null when the point is outside India OR no
   * demo shelter can be placed on land - a scenario without somewhere to go
   * is not a scenario; the VM then falls back to its honest empty state.
   */
  fun around(focus: GeoPoint, coastKm: Int? = null): Pair<HazardZone, SafeZone>? {
    if (!IndiaGeo.contains(focus)) return null
    val shelter = shelterNear(focus, coastKm) ?: return null
    return hazardNear(focus, coastKm) to shelter
  }

  /**
   * SECONDARY, TYPE-EXPLICIT demo hazard at a fixed bearing/distance from the
   * focus (USER FIX: with demo ON and a type filter tapped, the map must show
   * a hazard of THAT TYPE near the user - so "hazard strikes where I am, find
   * the way out" is demoable for every disaster, not just the one the place
   * hash produced). Deterministic: same focus + same type = same records.
   */
  /**
   * TRUE when a DEMO scenario of [type] is geographically plausible at this
   * point, using the real inputs the app has (Natural Earth coast distance;
   * deterministic hill belts). Returns false for obviously impossible demo
   * pairings (inland cyclone), so the scenario engine can substitute rather than mislead (spec phase 10). Still 100% demonstration logic.
   */
  fun plausibleAt(
    point: GeoPoint,
    type: com.example.data.model.HazardType,
    coastKm: Int? = null
  ): Boolean = when (type) {
    com.example.data.model.HazardType.CYCLONE -> coastKm != null && coastKm <= 60
    com.example.data.model.HazardType.LANDSLIDE ->
      // hill belts only: Himalayan fringe + Western Ghats + NE hill range
      ((point.lat * 100).toLong() * 17L + (point.lon * 100).toLong()) % 11L < 5 ||
        (point.lat > 23.0 && point.lon > 88.0) || point.lat > 30.0
    com.example.data.model.HazardType.FLOOD ->
      // flood needs water context: coastal/low-lying OR the flood-prone belts
      (coastKm != null && coastKm <= 30) ||
        (point.lat in 21.0..28.0 && point.lon in 86.0..93.0) ||  // Bengal-Bihar belt
        (point.lat in 25.0..29.5 && point.lon in 75.0..81.0) ||  // Punjab-Himalayan rivers
        ((point.lat * 10_000).toLong() % 7L) < 4L               // deterministic spread elsewhere
    else -> true  // earthquake/fire/heavy-rain demonstrate broadly
  }

  /** Plausibility-checked type choice: asked type when plausible, else the
   * natural scenario for this place (still deterministic). */
  fun demoTypeFor(point: GeoPoint, asked: com.example.data.model.HazardType?, coastKm: Int?):
    com.example.data.model.HazardType {
    if (asked == null) return hazardTypeFor(point, coastKm)
    return if (plausibleAt(point, asked, coastKm)) asked else hazardTypeFor(point, coastKm)
  }

  fun secondaryHazard(focus: GeoPoint, type: com.example.data.model.HazardType): HazardZone {
    val bearing = (bearingFor(focus) * 3 + type.ordinal * 47.0) % 360.0
    val center = offset(focus, DEMO_SECONDARY_DISTANCE_KM, bearing)
    return HazardZone(
      id = "demo-hz2-${quant(focus)}-${type.name.lowercase()}",
      name = "DEMO ${type.label} Zone (demo scenario)",
      type = type,
      severity = com.example.data.model.HazardSeverity.HIGH,
      center = center,
      radiusMeters = radiusMetersFor(type),
      riskLevel = "DEMO — demonstration only",
      trend = com.example.data.model.HazardTrend.STABLE,
      sourceStatus = "DEMO DATA — generated around your location for the ${type.label} filter, NOT real",
      lastUpdatedMillis = 0L,
      provenance = demoProvenance
    )
  }

  /**
   * Shelter cluster serving a GIVEN hazard (used by the type-filter scenario
   * and the regional overview pairing). Fans opposite/around the hazard,
   * walks each bearing until the candidate clears hazard.radius + 300 m from
   * the hazard CENTER, keeps India + landward rules, and stops at
   * [maxDistanceKm] from the hazard. Near/far spread is physical: candidates
   * start at 0.8 km steps out to the cap, so both a close fallback and a
   * farther bigger site exist (user: "near AND a bit far").
   */
  fun sheltersAroundHazard(
    anchor: GeoPoint,
    hazardRadiusMeters: Double,
    hazardBearingFromAnchor: Double,
    count: Int = 3,
    maxDistanceKm: Double = 9.0,
    coastKmAnchor: Int? = null,
    demandLandward: Boolean = false,
    idSalt: String = "",
    /**
     * Circle the placements must clear. Defaults to the anchor itself
     * (historic ring-around-hazard behavior for regional pairings). The
     * FOCUSED demo passes anchor = USER location and hazardCenter = the
     * scenario circle: placements then walk outward from the USER away from
     * the hazard - demo mode follows the same user-relative architecture as
     * live mode (EVACUATION CORRECTION spec 9).
     */
    hazardCenter: GeoPoint? = null
  ): List<SafeZone> {
    val out = ArrayList<SafeZone>(count)
    var idx = 0
    var ring = 0
    while (out.size < count && ring < 10) {
      val bearing = (hazardBearingFromAnchor + 180.0 + ring * 60.0) % 360.0
      ring++
      var distance = DEMO_SHELTER_DISTANCE_KM + out.size * 1.4
      val clearCenter = hazardCenter ?: anchor
      while (com.example.data.model.GeoMath.distanceMeters(
          clearCenter, offset(anchor, distance, bearing)
        ) <= hazardRadiusMeters + 300.0 && distance < maxDistanceKm
      ) {
        distance += 0.3
      }
      if (distance > maxDistanceKm) continue
      val p = offset(anchor, distance, bearing)
      if (!IndiaGeo.contains(p)) continue
      if (demandLandward && coastKmAnchor != null) {
        val candCoast = coastKmOf(p)
        if (candCoast != null && candCoast < coastKmAnchor) continue
      }
      out += SafeZone(
        id = "demo-sz${idSalt}-${quant(anchor)}-$idx",
        name = DEMO_SHELTER_NAMES[(idx + namesOffset(anchor)) % DEMO_SHELTER_NAMES.size] +
          " (demo)",
        lat = p.lat, lon = p.lon,
        locationNote = "Demo record — shelter about %.1f km from this hazard".format(
          com.example.data.model.GeoMath.distanceMeters(anchor, p) / 1000.0
        ),
        capacityTotal = DEMO_SHELTER_CAPACITY + idx * 90,
        capacityCurrent = DEMO_SHELTER_OCCUPIED + idx * 40,
        waterAvailable = true, foodAvailable = true,
        electricityAvailable = true, sanitationAvailable = idx != 1,
        medicalSupport = true,
        accessibility = if (idx % 2 == 0) "DEMO — highway access" else "DEMO — district road access",
        womenChildrenSuitability = true,
        operatingStatus = "OPEN",
        verificationStatus = "SIMULATED — not a verified shelter",
        elevationNote = "DEMO placeholder",
        provenance = demoProvenance
      )
      idx++
    }
    return out
  }

  /**
   * The regional hazard's PAIRED shelters (USER FIX: no unpaired green pins).
   * Each of the 14 simulated India hazards gets 2 shelters nearby-but-outside
   * its own circle - so the overview never shows "safe zone in the middle of
   * nowhere" and selecting any hazard routes to a shelter belonging to it.
   */
  fun pairedSheltersFor(hazard: HazardZone): List<SafeZone> =
    sheltersAroundHazard(
      anchor = hazard.center,
      hazardRadiusMeters = hazard.radiusMeters,
      hazardBearingFromAnchor = bearingFor(hazard.center),
      count = DEMO_PAIRED_SHELTERS_PER_HAZARD,
      maxDistanceKm = 7.0,
      idSalt = "-p"
    )

  private val DEMO_SHELTER_NAMES = listOf(
    "DEMO Community Hall", "DEMO School Shelter",
    "DEMO Relief Camp", "DEMO Stadium Shelter",
    "DEMO Warehouse Shelter"
  )

  /**
   * Optional coast-grid hookup for the landward rule: the VM injects the
   * bundled Natural Earth distance field. Kept as a settable provider so the
   * pure data layer stays free of Android/asset concerns (plain-JVM tests
   * leave it null -> landward checks no-op, geometry checks still run).
   */
  var coastKmOf: (GeoPoint) -> Int? = { _ -> null }

  // ---------------------------------------------------------------- internals

  /** Stable bearing (degrees) derived from the focus coordinates. */
  private fun bearingFor(point: GeoPoint): Double {
    // ~2.2 km quantised (see quant): walking a few metres must not spin the
    // scenario around the user and orphan the drawn route.
    val seed = (((point.lat * 50).toLong() xor (point.lon * 50).toLong()) * 71L % 3600L + 3600L) % 3600L
    return seed.toDouble() / 10.0
  }

  /** Offset a point by [distanceKm] along [bearingDeg] (flat-Earth, fine at these scales). */
  private fun offset(from: GeoPoint, distanceKm: Double, bearingDeg: Double): GeoPoint {
    val latDelta = distanceKm / 111.32 * cos(Math.toRadians(bearingDeg))
    val kmPerLonDeg = 111.32 * cos(Math.toRadians(from.lat)).coerceAtLeast(0.0001)
    val lonDelta = distanceKm / kmPerLonDeg * sin(Math.toRadians(bearingDeg))
    return GeoPoint(from.lat + latDelta, from.lon + lonDelta)
  }

  /**
   * Focus quantised to ~2.2 km buckets (0.02 deg) for IDS + bearing.
   * CRITICAL: this seeds demo-hz-/demo-sz- ids. A finer grid let 1 Hz GPS
   * jitter keep minting new ids, so every recompute saw the selected shelter
   * + its live corridor as 'zone disappeared from scope' and nulled them
   * (reported: tapping GO broke the route mid-guidance).
   */
  /** Place-seeded rotation so each location gets different shelter names. */
  private fun namesOffset(point: GeoPoint): Int =
    (((point.lat * 50).toLong() * 13 + (point.lon * 50).toLong()) % 97L).toInt()

  private fun quant(point: GeoPoint): String =
    "${(point.lat * 50).toLong()}_${(point.lon * 50).toLong()}"
}

