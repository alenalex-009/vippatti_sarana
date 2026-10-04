package com.example.data.model

import com.example.data.routing.GeoPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin geodesic helpers shared by hazard analysis, safe-zone
 * evaluation and routing. No Android dependencies so it stays unit-testable.
 */
object GeoMath {

  private const val METERS_PER_DEGREE_LAT = 110_574.0

  fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val sinLat = sin(dLat / 2)
    val sinLon = sin(dLon / 2)
    val h = sinLat * sinLat +
      cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sinLon * sinLon
    return 6_371_000.0 * 2 * atan2(sqrt(h), sqrt(1 - h))
  }

  fun isWithinRadius(point: GeoPoint, center: GeoPoint, radiusMeters: Double): Boolean =
    distanceMeters(point, center) <= radiusMeters

  /** True when segment a-b ever comes within radiusMeters of circle center. */
  fun segmentTouchesCircle(
    center: GeoPoint,
    radiusMeters: Double,
    a: GeoPoint,
    b: GeoPoint
  ): Boolean = closestApproachMeters(center, a, b) <= radiusMeters

  /**
   * Fraction (0..1) of the STRAIGHT path origin->dest that lies INSIDE the
   * hazard circle. 0 when the path never touches it. Sampled (fine at these
   * scales); deterministic and pure - the evaluator uses it to measure how
   * much hazard a user must cross to reach a candidate. This estimates the
   * straight path; real road geometry gets the same check from the OSRM
   * corridor at routing time (HazardRoutingPolicy).
   */
  fun insideCircleRatio(
    origin: GeoPoint,
    dest: GeoPoint,
    center: GeoPoint,
    radiusMeters: Double
  ): Double {
    if (!segmentTouchesCircle(center, radiusMeters, origin, dest)) return 0.0
    val steps = 40
    var inside = 0
    for (i in 1 until steps) {
      val t = i.toDouble() / steps
      val p = GeoPoint(origin.lat + (dest.lat - origin.lat) * t,
        origin.lon + (dest.lon - origin.lon) * t)
      if (distanceMeters(p, center) <= radiusMeters) inside++
    }
    return inside.toDouble() / (steps - 1)
  }

  /**
   * Point [distanceMeters] away from [origin] along [bearingDeg] (true north,
   * clockwise). Standard great-circle destination formula — the inverse of
   * [distanceMeters]/[bearingDegrees], so callers can push a waypoint clear of
   * a hazard in a *chosen* direction instead of nudging raw lat/lon by an
   * angle's numeric value.
   */
  fun offsetPoint(origin: GeoPoint, bearingDeg: Double, distanceMeters: Double): GeoPoint {
    val angular = distanceMeters / 6_371_000.0
    val bearing = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(origin.lat)
    val lon1 = Math.toRadians(origin.lon)
    val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
    val lon2 = lon1 + atan2(
      sin(bearing) * sin(angular) * cos(lat1),
      cos(angular) - sin(lat1) * sin(lat2)
    )
    // Normalise longitude into [-180, 180) so far-flung offsets stay valid.
    val lonDeg = ((Math.toDegrees(lon2) + 540.0) % 360.0) - 180.0
    return GeoPoint(Math.toDegrees(lat2), lonDeg)
  }

  /** Great-circle bearing in degrees [0..360) from [from] to [to]. */
  fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
    val lat1 = Math.toRadians(from.lat)
    val lat2 = Math.toRadians(to.lat)
    val dLon = Math.toRadians(to.lon - from.lon)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val deg = Math.toDegrees(atan2(y, x))
    return (deg + 360.0) % 360.0
  }

  /**
   * Closest approach (meters) between [target] and the straight segment a->b,
   * computed in a local equirectangular frame. Used for hazard-avoidance checks
   * along a candidate evacuation route.
   */
  fun closestApproachMeters(target: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
    val latRad = Math.toRadians(a.lat)
    val xScale = METERS_PER_DEGREE_LAT * cos(latRad)
    val yScale = METERS_PER_DEGREE_LAT
    val bx = (b.lon - a.lon) * xScale
    val by = (b.lat - a.lat) * yScale
    val px = (target.lon - a.lon) * xScale
    val py = (target.lat - a.lat) * yScale
    val len2 = bx * bx + by * by
    val t = if (len2 == 0.0) 0.0 else (((px * bx) + (py * by)) / len2).coerceIn(0.0, 1.0)
    val cx = t * bx
    val cy = t * by
    return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
  }

  /** Compact distance label, e.g. "840 m" or "12.3 km" (locale-stable). */
  fun formatKm(meters: Double): String =
    if (meters >= 1000) String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
    else String.format(java.util.Locale.US, "%.0f m", meters)
}
