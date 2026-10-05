package com.example.data.disaster.cap

import com.example.data.disaster.EventGeometry
import com.example.data.india.GeoBoundingBox
import com.example.data.routing.GeoPoint

/**
 * ============================================================================
 * AUTHORITY GEOMETRY VALIDATION (section 3)
 * ============================================================================
 *
 * Geometry published by an authority is trusted to be REAL, not to be
 * well-formed. Before any polygon reaches the map it is checked for:
 *
 *   - minimum vertex count
 *   - latitude in [-90, 90], longitude in [-180, 180]
 *   - finite (non-NaN / non-infinite) coordinates
 *   - containment within India's published national extent
 *   - self-intersection
 *   - degenerate / zero-area rings
 *   - implausible size
 *
 * NOTHING is repaired. A suspicious ring is REJECTED and reported with the
 * reason, so the UI can say "official alert — geometry unavailable" instead of
 * drawing a shape that looks authoritative but is not what the source sent.
 *
 * A ring that is partly outside India is rejected rather than clipped: clipping
 * would silently alter the authority's geometry, which is a fabrication of a
 * different kind.
 */

data class GeometryValidation(
  val valid: Boolean,
  val vertexCount: Int,
  val failureReasons: List<GeometryFailure>,
  /** Set when valid. Null when rejected — never a substitute shape. */
  val geometry: EventGeometry? = null
)

enum class GeometryFailure(val label: String) {
  EMPTY("no coordinates"),
  TOO_FEW_VERTICES("fewer than 3 vertices"),
  NON_FINITE("non-finite coordinate"),
  LATITUDE_OUT_OF_RANGE("latitude outside -90..90"),
  LONGITUDE_OUT_OF_RANGE("longitude outside -180..180"),
  OUTSIDE_INDIA("outside India's national extent"),
  DEGENERATE_RING("degenerate or zero-area ring"),
  SELF_INTERSECTING("self-intersecting ring"),
  IMPLAUSIBLE_SIZE("implausible extent"),
  NOT_IN_POLYGON_FORMAT("not in the published lat,lon format")
}

object GeometryValidator {

  const val MIN_VERTICES = 3
  const val MAX_VERTICES = 50_000

  /**
   * India's published national extent. A ring entirely outside it is not a
   * geometry error in general, but for an INDIA-wide app it means the parse or
   * the payload is wrong, so it is rejected rather than rendered off-map.
   */
  val INDIA_BBOX = GeoBoundingBox(minLon = 67.5, minLat = 6.0, maxLon = 98.0, maxLat = 37.5)

  /** Larger than this in km on a side is not a hazard area; it is a bad parse. */
  const val MAX_PLAUSIBLE_EXTENT_KM = 2000.0

  /**
   * Validates a raw published polygon string (whitespace-separated "lat,lon").
   * Returns a REJECTED result with reasons rather than throwing.
   */
  fun validateRawPolygon(raw: String?): GeometryValidation {
    if (raw.isNullOrBlank()) {
      return GeometryValidation(false, 0, listOf(GeometryFailure.EMPTY))
    }
    val ring = mutableListOf<GeoPoint>()
    val failures = mutableListOf<GeometryFailure>()
    var malformed = false

    raw.trim().split(Regex("\\s+")).forEach { token ->
      val parts = token.split(",")
      if (parts.size != 2) {
        malformed = true
        return@forEach
      }
      val lat = parts[0].trim().toDoubleOrNull()
      val lon = parts[1].trim().toDoubleOrNull()
      if (lat == null || lon == null) {
        malformed = true
        return@forEach
      }
      if (!lat.isFinite() || !lon.isFinite()) {
        failures += GeometryFailure.NON_FINITE
        return@forEach
      }
      if (lat < -90.0 || lat > 90.0) {
        failures += GeometryFailure.LATITUDE_OUT_OF_RANGE
        return@forEach
      }
      if (lon < -180.0 || lon > 180.0) {
        failures += GeometryFailure.LONGITUDE_OUT_OF_RANGE
        return@forEach
      }
      ring += GeoPoint(lat, lon)
    }

    if (malformed && ring.isEmpty()) {
      return GeometryValidation(false, 0, listOf(GeometryFailure.NOT_IN_POLYGON_FORMAT))
    }
    return validateRing(ring, extraFailures = if (malformed) listOf(GeometryFailure.NOT_IN_POLYGON_FORMAT) else emptyList())
  }

  /** Validates an already-parsed ring. */
  fun validateRing(
    ring: List<GeoPoint>,
    extraFailures: List<GeometryFailure> = emptyList()
  ): GeometryValidation {
    val failures = extraFailures.toMutableList()

    if (ring.isEmpty()) return GeometryValidation(false, 0, failures + GeometryFailure.EMPTY)
    if (ring.size < MIN_VERTICES) {
      return GeometryValidation(false, ring.size, failures + GeometryFailure.TOO_FEW_VERTICES)
    }
    if (ring.size > MAX_VERTICES) {
      return GeometryValidation(false, ring.size, failures + GeometryFailure.IMPLAUSIBLE_SIZE)
    }

    if (ring.any { !it.lat.isFinite() || !it.lon.isFinite() }) {
      failures += GeometryFailure.NON_FINITE
    }

    val outside = ring.any {
      it.lat < INDIA_BBOX.minLat || it.lat > INDIA_BBOX.maxLat ||
        it.lon < INDIA_BBOX.minLon || it.lon > INDIA_BBOX.maxLon
    }
    if (outside) failures += GeometryFailure.OUTSIDE_INDIA

    // A published ring normally repeats its first vertex last. That is fine.
    // Zero-area (all vertices equal) is not.
    val distinct = ring.distinct()
    if (distinct.size < MIN_VERTICES) failures += GeometryFailure.DEGENERATE_RING

    if (ring.size >= MIN_VERTICES && areaKm2(ring) <= 0.0) {
      failures += GeometryFailure.DEGENERATE_RING
    }

    if (extentKm(ring) > MAX_PLAUSIBLE_EXTENT_KM) {
      failures += GeometryFailure.IMPLAUSIBLE_SIZE
    }

    if (selfIntersects(ring)) failures += GeometryFailure.SELF_INTERSECTING

    failures.distinct()
    if (failures.isNotEmpty()) {
      return GeometryValidation(false, ring.size, failures)
    }

    return GeometryValidation(
      valid = true,
      vertexCount = ring.size,
      failureReasons = emptyList(),
      geometry = EventGeometry.Polygon(ring)
    )
  }

  /** Shoelace area in square km on a local equirectangular projection. */
  fun areaKm2(ring: List<GeoPoint>): Double {
    if (ring.size < 3) return 0.0
    val latRef = ring.map { it.lat }.average()
    val kmPerDegLat = 110.574
    val kmPerDegLon = 111.320 * Math.cos(Math.toRadians(latRef))
    var sum = 0.0
    for (i in ring.indices) {
      val a = ring[i]
      val b = ring[(i + 1) % ring.size]
      sum += (a.lon * kmPerDegLon) * (b.lat * kmPerDegLat) -
        (b.lon * kmPerDegLon) * (a.lat * kmPerDegLat)
    }
    return kotlin.math.abs(sum / 2.0)
  }

  /** Largest bounding-box side in km. */
  fun extentKm(ring: List<GeoPoint>): Double {
    if (ring.isEmpty()) return 0.0
    val latRef = ring.map { it.lat }.average()
    val kmPerDegLat = 110.574
    val kmPerDegLon = 111.320 * Math.cos(Math.toRadians(latRef))
    val latKm = (ring.maxOf { it.lat } - ring.minOf { it.lat }) * kmPerDegLat
    val lonKm = (ring.maxOf { it.lon } - ring.minOf { it.lon }) * kmPerDegLon
    return maxOf(latKm, lonKm)
  }

  /**
   * O(n²) self-intersection test, run only on rings that already passed the
   * cheap checks. A published hazard polygon has a few thousand vertices at
   * most, which is fine here; the ring is also simplified before display.
   *
   * Adjacent edges sharing a vertex are allowed (that is normal for a closed
   * ring). Only non-adjacent crossings count.
   */
  fun selfIntersects(ring: List<GeoPoint>): Boolean {
    val n = ring.size
    if (n < 4) return false
    // Skip the duplicated closing vertex for edge generation.
    val pts = if (ring.first() == ring.last()) ring.dropLast(1) else ring
    val m = pts.size
    if (m < 4) return false

    var checked = 0
    for (i in 0 until m) {
      val a1 = pts[i]
      val a2 = pts[(i + 1) % m]
      for (j in i + 1 until m) {
        // Skip adjacent edges (they legitimately share an endpoint).
        if (j == i) continue
        if (j == (i + 1) % m) continue
        if (i == 0 && j == m - 1) continue

        val b1 = pts[j]
        val b2 = pts[(j + 1) % m]
        if (segmentsProperlyCross(a1, a2, b1, b2)) return true

        checked++
        // Bound the cost on very large rings.
        if (checked > 400_000) return false
      }
    }
    return false
  }

  /** True only for a PROPER crossing (shared endpoints and collinear touches do not count). */
  private fun segmentsProperlyCross(p1: GeoPoint, p2: GeoPoint, p3: GeoPoint, p4: GeoPoint): Boolean {
    val d1 = cross(p3, p4, p1)
    val d2 = cross(p3, p4, p2)
    val d3 = cross(p1, p2, p3)
    val d4 = cross(p1, p2, p4)

    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
      ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    ) return true
    return false
  }

  private fun cross(a: GeoPoint, b: GeoPoint, c: GeoPoint): Double =
    (b.lon - a.lon) * (c.lat - a.lat) - (b.lat - a.lat) * (c.lon - a.lon)

  /**
   * Douglas–Peucker simplification. The real Darjeeling alert polygon has
   * ~2000 vertices; the map does not need that resolution, and section 32
   * forbids pushing huge geometry into Compose state.
   * Returns the simplified ring; never invents points.
   */
  fun simplify(points: List<GeoPoint>, toleranceMeters: Double = 25.0): List<GeoPoint> {
    if (points.size <= 2) return points
    val tol = toleranceMeters / 110_574.0 // degrees of latitude

    fun perpDistance(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
      val dx = b.lon - a.lon
      val dy = b.lat - a.lat
      if (dx == 0.0 && dy == 0.0) {
        return kotlin.math.hypot(p.lon - a.lon, p.lat - a.lat)
      }
      val t = ((p.lon - a.lon) * dx + (p.lat - a.lat) * dy) / (dx * dx + dy * dy)
      val projLon = a.lon + t * dx
      val projLat = a.lat + t * dy
      return kotlin.math.hypot(p.lon - projLon, p.lat - projLat)
    }

    fun douglasPeucker(list: List<GeoPoint>): List<GeoPoint> {
      if (list.size < 3) return list
      var maxDist = 0.0
      var index = 0
      for (i in 1 until list.size - 1) {
        val d = perpDistance(list[i], list.first(), list.last())
        if (d > maxDist) {
          maxDist = d
          index = i
        }
      }
      return if (maxDist > tol) {
        val left = douglasPeucker(list.subList(0, index + 1))
        val right = douglasPeucker(list.subList(index, list.size))
        left.dropLast(1) + right
      } else {
        listOf(list.first(), list.last())
      }
    }

    val closed = points.firstOrNull() == points.lastOrNull()
    val working = if (closed) points.dropLast(1) else points
    val simplified = douglasPeucker(working)
    return if (closed && simplified.size >= 2) simplified + simplified.first() else simplified
  }
}
