package com.example.data.routing

import android.util.Log
import com.example.data.model.GeoMath
import com.example.data.model.HazardZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class GeoPoint(
  val lat: Double,
  val lon: Double
)

data class RouteStep(
  val instruction: String,
  val distanceMeters: Double,
  val durationSeconds: Double
)

/**
 * Disaster-aware evacuation route result.
 *
 * Beyond distance/duration, every route carries a SAFETY evaluation:
 * hazard warnings, route safety status and a route-safety score. This is the
 * single routing result type used across the whole application — there is no
 * second parallel routing implementation.
 */
data class RouteResult(
  val distanceMeters: Double,
  val durationSeconds: Double,
  val pathPoints: List<GeoPoint>,
  val steps: List<RouteStep>,
  val isLiveOsrm: Boolean,
  val summary: String,
  val travelMode: String,
  /** Human warnings for each hazard the route passes near/through. */
  val hazardWarnings: List<RouteHazardWarning> = emptyList(),
  /** SAFE / CAUTION / DANGER for the selected route. */
  val routeSafetyStatus: RouteSafetyStatus = RouteSafetyStatus.SAFE,
  /** 0..100 — higher is safer. */
  val routeSafetyScore: Int = 100,
  /** Destination label (e.g. shelter name). */
  val destinationName: String = "Safe Zone"
) {
  /**
   * Stable content-derived identity (derived once from distance, path length
   * and path content). List UI must compare routes by this id — never by
   * object identity (===), because state copies create equal-but-distinct
   * instances.
   */
  val routeId: String

  init {
    routeId = "route-${distanceMeters.roundToInt()}-${pathPoints.size}-${pathPoints.hashCode()}"
  }

  val isReroutable: Boolean get() = hazardWarnings.any { it.isBlocking }
}

enum class RouteSafetyStatus(val label: String) {
  SAFE("Safe Corridor"),
  CAUTION("Caution — Hazard Nearby"),
  DANGER("Danger — Route Enters Hazard Zone")
}

/**
 * One hazard warning attached to a route.
 */
data class RouteHazardWarning(
  val hazardName: String,
  val hazardTypeLabel: String,
  val message: String,
  /** True when the route actually enters the hazard zone. */
  val isBlocking: Boolean
)

/**
 * Penalty policy for disaster-aware routing.
 *
 * FUTURE INTEGRATION: when live road/hazard feeds (KSDMA closures, CWC gauge
 * flooding, GSI slope alerts) are connected, they plug into
 * [computePenaltyFor] — the architecture is already structured around
 * Safety + Risk + Distance + ETA + Accessibility, so the safest route is NOT
 * automatically the shortest one. Prepared policies cover flood avoidance,
 * landslide avoidance, fire-zone avoidance, blocked-road avoidance,
 * bridge-risk avoidance, restricted-area avoidance, road accessibility,
 * emergency corridors and alternative routes.
 */
object HazardRoutingPolicy {

  /** How much a hazard penalizes a candidate path (meters-equivalent). */
  fun computePenaltyFor(
    path: List<GeoPoint>,
    hazards: List<HazardZone>
  ): HazardPenalty {
    if (path.size < 2) return HazardPenalty.NONE
    var penaltyMeters = 0.0
    val warnings = mutableListOf<RouteHazardWarning>()
    var worstStatus: RouteSafetyStatus? = null

    for (hazard in hazards) {
      var minApproach = Double.MAX_VALUE
      var enters = false
      for (i in 0 until path.size - 1) {
        val approach = GeoMath.closestApproachMeters(hazard.center, path[i], path[i + 1])
        if (approach < minApproach) minApproach = approach
        if (approach <= hazard.radiusMeters) {
          enters = true
          break
        }
      }
      if (minApproach > hazard.radiusMeters * 4) continue // far away — ignore

      // CAUTION band: outside the zone but still within the same "nearby"
      // margin the detour builder uses. Without this the gate collapsed to
      // `enters` alone (which already implies `minApproach <= radiusMeters`),
      // so the CAUTION branch below — and the amber "Hazard Nearby" state the
      // UI renders for it — was unreachable dead code.
      val nearMiss = minApproach <= hazard.radiusMeters * CAUTION_BAND_FACTOR
      if (enters || nearMiss) {
        val severityFactor = hazard.severity.weight.toDouble()
        penaltyMeters += BASE_PENALTY_METERS * severityFactor * (if (enters) 2.0 else 1.0)
        val status = if (enters) RouteSafetyStatus.DANGER else RouteSafetyStatus.CAUTION
        if (worstStatus == null || status.ordinal > worstStatus!!.ordinal) worstStatus = status
        warnings += RouteHazardWarning(
          hazardName = hazard.name,
          hazardTypeLabel = hazard.type.label,
          message = if (enters) {
            "Route passes THROUGH the ${hazard.name} (${hazard.type.label}, ${hazard.severity.label}). " +
              "Find an alternative corridor or wait for official guidance."
          } else {
            "Route runs close to the ${hazard.name} (${hazard.type.label}). Allow extra time and stay alert."
          },
          isBlocking = enters && hazard.severity.weight >= 3
        )
      }
    }

    val safetyScore = (100 - penaltyMeters / PENALTY_SCALE).roundToInt().coerceIn(0, 100)
    return HazardPenalty(
      penaltyMeters = penaltyMeters,
      warnings = warnings,
      safetyScore = safetyScore,
      status = worstStatus ?: RouteSafetyStatus.SAFE
    )
  }

  data class HazardPenalty(
    val penaltyMeters: Double,
    val warnings: List<RouteHazardWarning>,
    val safetyScore: Int,
    val status: RouteSafetyStatus
  ) {
    companion object { val NONE = HazardPenalty(0.0, emptyList(), 100, RouteSafetyStatus.SAFE) }
  }

  private const val BASE_PENALTY_METERS = 1_500.0
  private const val PENALTY_SCALE = 40.0
  /** Near-miss band (× hazard radius) that raises CAUTION without entering the zone. */
  private const val CAUTION_BAND_FACTOR = 1.5
}

/**
 * Open Source Routing Machine (OSRM) road-routing service — the single
 * road-routing engine of the application.
 *
 * Pipeline: CURRENT LOCATION -> HAZARD CHECK -> SAFE-ZONE OPTIONS ->
 * SAFE ROUTE -> DESTINATION. Walking and driving profiles are supported.
 * Offline, a geodesic hazard-avoiding fallback corridor keeps the app useful.
 */
object OsrmRoutingService {

  private const val TAG = "OsrmRoutingService"

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      // Generous read window: cross-country road routes are large payloads
      // that need server compute time. The map already shows the instant
      // preview corridor, so waiting longer here only ever upgrades the
      // user to real roads — it never leaves them staring at a spinner.
      .readTimeout(30, TimeUnit.SECONDS)
      .build()
  }

  /**
   * OSRM route URL for [endpoint], with coordinates in the OSRM-mandated
   * longitude,latitude order (NOT latitude,longitude). Defaults to full road
   * geometry (`overview=full&geometries=geojson`) so the returned shape
   * follows every bend of the road network; [overview] can be lowered to
   * "simplified" as a resilience fallback on very long trips.
   */
  fun buildRouteUrl(
    endpoint: String,
    origin: GeoPoint,
    destination: GeoPoint,
    wantAlternatives: Int,
    overview: String = "full"
  ): String = "$endpoint/" +
    "${origin.lon},${origin.lat};${destination.lon},${destination.lat}" +
    "?overview=$overview&geometries=geojson&steps=true&alternatives=$wantAlternatives"

  /**
   * Decodes an OSRM GeoJSON coordinates array (`[longitude, latitude]` per
   * entry) into map points. EVERY returned point is kept in order — never
   * collapsed to origin + destination.
   */
  fun decodeGeoJsonCoordinates(coordsArray: org.json.JSONArray): List<GeoPoint> =
    buildList {
      for (i in 0 until coordsArray.length()) {
        val pt = coordsArray.optJSONArray(i) ?: continue
        add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
      }
    }

  /**
   * Fetches up to [wantAlternatives] LIVE road routes from OSRM
   * (`alternatives=N` returns genuinely different road corridors, not
   * offsets of one line). Empty list when the network fails — callers fall
   * back to the offline corridor. Each route is hazard-evaluated.
   *
   * Blocking network IO: call from Dispatchers.IO (or use the suspend
   * [fetchLiveRoutesAsync] wrapper, which switches context itself).
   */
  fun fetchLiveRoutes(
    origin: GeoPoint,
    destination: GeoPoint,
    mode: String,
    hazards: List<HazardZone>,
    destinationName: String,
    wantAlternatives: Int,
    overview: String = "full"
  ): List<RouteResult> {
    // Profile-matched endpoints: the router.project-osrm.org reference server hosts
    // only the car profile — a /foot/ request there silently returns car
    // geometry. The FOSSGIS community server runs dedicated foot + car
    // instances, so each travel mode queries its own profile.
    val endpoint = if (mode == "driving") "https://routing.openstreetmap.de/routed-car/route/v1/driving" else "https://routing.openstreetmap.de/routed-foot/route/v1/foot"

    // First attempt honours the requested overview. ACTIVE corridors ask for
    // full road geometry (exact route lines); the ALTERNATIVES button asks for
    // "simplified" — genuinely fewer bytes from the shared FOSSGIS server,
    // which is what made it feel dead/laggy (reported bug). Simplified lines
    // STILL follow the road network.
    val primaryUrl = buildRouteUrl(endpoint, origin, destination, wantAlternatives, overview = overview)
    val primaryRoutes = tryRequestRoutes(primaryUrl, mode, hazards, destinationName, wantAlternatives)
    if (primaryRoutes != null && primaryRoutes.isNotEmpty()) return primaryRoutes

    // Retry once with the OTHER overview: very long trips can exceed the
    // full-geometry payload limits on shared servers, and a simplified
    // endpoint hiccup is worth retrying exactly.
    val retryUrl = buildRouteUrl(
      endpoint, origin, destination, wantAlternatives,
      overview = if (overview == "simplified") "full" else "simplified"
    )
    return tryRequestRoutes(retryUrl, mode, hazards, destinationName, wantAlternatives) ?: emptyList()
  }

  /** One OSRM request attempt; null when the attempt fully failed. */
  private fun tryRequestRoutes(
    url: String,
    mode: String,
    hazards: List<HazardZone>,
    destinationName: String,
    wantAlternatives: Int
  ): List<RouteResult>? = try {
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", "VippattiSarana-DisasterRelief/1.0 (Android; OSM OSRM Routing)")
      .build()
    httpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) return null
      val responseBody = response.body?.string()
      if (responseBody.isNullOrBlank()) return null
      val json = JSONObject(responseBody)
      if (json.optString("code") != "Ok") return null
      val routes = json.optJSONArray("routes") ?: return null
      buildList {
        for (i in 0 until routes.length().coerceAtMost(wantAlternatives)) {
          parseOsrmRoute(routes.getJSONObject(i), mode, hazards, destinationName)?.let(::add)
        }
      }
    }
  } catch (e: Exception) {
    Log.w(TAG, "OSRM routing attempt failed (${url.substringAfter("route/v1/").substringBefore("?")}): ${e.message}")
    null
  }

  /**
   * Suspend wrapper over [fetchLiveRoutes] for Main-thread callers (the
   * instant-corridor-then-upgrade pattern): switches to IO itself.
   */
  suspend fun fetchLiveRoutesAsync(
    origin: GeoPoint,
    destination: GeoPoint,
    mode: String,
    hazards: List<HazardZone>,
    destinationName: String,
    wantAlternatives: Int
  ): List<RouteResult> = withContext(Dispatchers.IO) {
    fetchLiveRoutes(origin, destination, mode, hazards, destinationName, wantAlternatives)
  }

  /**
   * Calculates a road route using the public OSRM HTTP API and evaluates the
   * returned geometry against the current hazard picture. Falls back to an
   * offline hazard-skirting corridor when the network is unavailable.
   */
  suspend fun calculateRoute(
    origin: GeoPoint,
    destination: GeoPoint,
    mode: String = "foot", // "foot" or "driving"
    hazards: List<HazardZone> = emptyList(),
    destinationName: String = "Safe Zone"
  ): RouteResult = withContext(Dispatchers.IO) {
    fetchLiveRoutes(origin, destination, mode, hazards, destinationName, wantAlternatives = 1)
      .firstOrNull()
      ?: calculateOfflineTacticalRoute(origin, destination, mode, hazards, destinationName)
  }

  private fun parseOsrmRoute(
    routeObj: JSONObject,
    mode: String,
    hazards: List<HazardZone>,
    destinationName: String
  ): RouteResult? {
    val distance = routeObj.optDouble("distance", 0.0)
    val duration = routeObj.optDouble("duration", 0.0)

    val geometry = routeObj.optJSONObject("geometry") ?: return null
    val coordinates = geometry.optJSONArray("coordinates") ?: return null
    val points = decodeGeoJsonCoordinates(coordinates)

    val stepsList = mutableListOf<RouteStep>()
    val legs = routeObj.optJSONArray("legs")
    var summary = "OSRM Safe Evacuation Route"
    if (legs != null && legs.length() > 0) {
      val leg0 = legs.getJSONObject(0)
      summary = leg0.optString("summary", summary)
      val stepsArray = leg0.optJSONArray("steps")
      if (stepsArray != null) {
        for (s in 0 until stepsArray.length()) {
          val stepObj = stepsArray.getJSONObject(s)
          val maneuver = stepObj.optJSONObject("maneuver")
          val instruction = when (maneuver?.optString("type")) {
            "depart" -> "Depart along marked evacuation path"
            "turn" -> "Turn ${maneuver.optString("modifier")} onto ${stepObj.optString("name", "safe road")}"
            "arrive" -> "Arrive safely at $destinationName"
            else -> stepObj.optString("name").ifBlank { "Continue on designated route" }
          }
          stepsList.add(RouteStep(instruction, stepObj.optDouble("distance", 0.0), stepObj.optDouble("duration", 0.0)))
        }
      }
    }
    if (stepsList.isEmpty()) {
      stepsList.add(RouteStep("Follow evacuation corridor to $destinationName", distance, duration))
    }

    val penalty = HazardRoutingPolicy.computePenaltyFor(points, hazards)
    return RouteResult(
      distanceMeters = distance,
      durationSeconds = duration,
      pathPoints = points,
      steps = stepsList,
      isLiveOsrm = true,
      summary = summary.ifBlank { "OSRM Validated Passage" },
      travelMode = mode,
      hazardWarnings = penalty.warnings,
      routeSafetyStatus = penalty.status,
      routeSafetyScore = penalty.safetyScore,
      destinationName = destinationName
    )
  }

  /**
   * Offline disaster fallback: builds a hazard-skirting corridor of waypoints
   * (offset perpendicular from any nearby hazard) and computes a geodesic
   * distance with walking/driving ETA plus the same hazard-safety evaluation.
   */
  fun calculateOfflineTacticalRoute(
    origin: GeoPoint,
    destination: GeoPoint,
    mode: String,
    hazards: List<HazardZone> = emptyList(),
    destinationName: String = "Safe Zone"
  ): RouteResult {
    val waypoints = mutableListOf(origin)

    // Intermediate tactical waypoints that skirt around nearby hazard areas.
    // Each waypoint is a real great-circle offset from the corridor anchor on
    // the side the hazard is NOT on, out at a clearance just beyond the hazard
    // radius. The previous version used the bearing's numeric value as a raw
    // lat/lon delta, which pushed the waypoint north for some bearings no
    // matter where the hazard actually lay.
    for (hazard in hazards) {
      val approachStart = GeoMath.closestApproachMeters(hazard.center, origin, destination)
      if (approachStart <= hazard.radiusMeters * 1.5) {
        val corridorBearing = GeoMath.bearingDegrees(origin, destination)
        // Signed turn from the corridor heading to the hazard: negative means
        // the hazard lies to the left, so the detour breaks right.
        val hazardBearing = GeoMath.bearingDegrees(origin, hazard.center)
        val hazardIsLeft = ((hazardBearing - corridorBearing + 540.0) % 360.0) - 180.0 < 0.0
        val detourBearing = (corridorBearing + if (hazardIsLeft) 90.0 else -90.0) % 360.0

        // Anchor on the corridor at the hazard's along-track position, then
        // step sideways until the path clears the hazard edge with margin.
        val legMeters = GeoMath.distanceMeters(origin, destination)
        val anchor = if (legMeters > 0.0) {
          val along = (legMeters * 0.5)
            .coerceAtMost((approachStart + hazard.radiusMeters * 0.5).coerceAtLeast(0.0))
            .coerceIn(0.0, legMeters)
          GeoMath.offsetPoint(origin, corridorBearing, along)
        } else {
          origin
        }
        val clearanceMeters = maxOf(
          hazard.radiusMeters * 1.25,
          approachStart + hazard.radiusMeters * 0.25
        )
        waypoints.add(GeoMath.offsetPoint(anchor, detourBearing, clearanceMeters))
      }
    }
    waypoints.add(destination)

    var totalMeters = 0.0
    for (i in 0 until waypoints.size - 1) {
      totalMeters += GeoMath.distanceMeters(waypoints[i], waypoints[i + 1])
    }

    // Walking pace ~4.8 km/h; driving ~30 km/h during evacuation conditions.
    val speedMps = if (mode == "driving") 8.33 else 1.35
    val durationSeconds = totalMeters / speedMps

    // Guidance is derived from the corridor actually built, not from invented
    // road names: this fallback has no road data behind it, so it describes the
    // manoeuvre and names the real hazards being avoided (names come from the
    // live hazard list). A fabricated "elevated bypass road" would be rendered
    // verbatim as turn-by-turn guidance in the nav HUD.
    val avoided = hazards
      .filter { GeoMath.closestApproachMeters(it.center, origin, destination) <= it.radiusMeters * 1.5 }
      .sortedBy { GeoMath.closestApproachMeters(it.center, origin, destination) }
    val steps = buildList {
      add(RouteStep("Leave the hazard area by the clearest corridor", totalMeters * 0.35, durationSeconds * 0.35))
      for (hazard in avoided) {
        add(
          RouteStep(
            "Detour around the ${hazard.name} (${hazard.type.label}) — keep outside the marked zone",
            totalMeters * 0.35 / avoided.size,
            durationSeconds * 0.35 / avoided.size
          )
        )
      }
      add(RouteStep("Arrive at $destinationName", totalMeters * 0.30, durationSeconds * 0.30))
    }

    val penalty = HazardRoutingPolicy.computePenaltyFor(waypoints, hazards)
    return RouteResult(
      distanceMeters = totalMeters,
      durationSeconds = durationSeconds,
      pathPoints = waypoints,
      steps = steps,
      isLiveOsrm = false,
      summary = "Offline Hazard-Skirting Corridor",
      travelMode = mode,
      hazardWarnings = penalty.warnings,
      routeSafetyStatus = penalty.status,
      routeSafetyScore = penalty.safetyScore,
      destinationName = destinationName
    )
  }

  /**
   * Alternative routes: computes up to [maxAlternatives] distinct candidate
   * corridors to the same destination (today via slightly different offline
   * detour geometries; live OSRM alternatives feed in naturally later).
   */
  suspend fun calculateAlternativeRoutes(
    origin: GeoPoint,
    destination: GeoPoint,
    mode: String,
    hazards: List<HazardZone> = emptyList(),
    destinationName: String = "Safe Zone",
    maxAlternatives: Int = 2
  ): List<RouteResult> = withContext(Dispatchers.IO) {
    // ONE fast request for genuinely different OSRM road corridors.
    // (The old version fired a second full-route request and then SYNTHESIZED
    // straight-line "detour variants" the UI filtered out as non-live — two
    // slow network calls + fake data, every single tap. That was the
    // "Alternatives lags and does nothing" report.)
    //
    // overview=simplified: alternatives render as ghost lines + a distance/
    // safety comparison; simplified road geometry is the honest fast answer
    // and still follows real roads. Offline, an empty list means "no
    // verified alternatives" — said out loud, never papered over.
    val live = fetchLiveRoutes(
      origin, destination, mode, hazards, destinationName,
      wantAlternatives = (maxAlternatives + 1).coerceIn(2, 3),
      overview = "simplified"
    )
    live.distinctBy { it.routeId }.sortedByDescending { it.routeSafetyScore }
  }

  /**
   * Standard Haversine distance.
   */
  fun formatDistance(meters: Double): String = GeoMath.formatKm(meters)

  fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60.0).roundToInt()
    return if (totalMinutes >= 60) {
      "${totalMinutes / 60}h ${totalMinutes % 60}m"
    } else {
      "${totalMinutes.coerceAtLeast(1)} mins"
    }
  }
}
