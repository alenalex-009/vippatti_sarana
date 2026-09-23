package com.example.data.disaster

import com.example.data.model.GeoMath
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint

/**
 * ============================================================================
 * MAP FOCUS RULES — the "Google Maps" behavior, made explicit + testable
 * ============================================================================
 *
 * Two rules the current map violates (everything at once, everywhere):
 *
 *  1. NEARBY FIRST: at city zoom, the map shows only what is close to YOU
 *     (within [nearbyRadiusMeters] of the focus point). Distant events exist
 *     but are folded into a count ("214 alerts across India — see all"), so
 *     the first view is calm and relevant, not a national carpet.
 *  2. LEVEL OF DETAIL: giant radius circles only appear once the zoom can
 *     actually show them honestly ([RADIUS_CIRCLE_MIN_ZOOM]); below that a
 *     hazard is a small dot. Dots never claim more precision than the zoom
 *     has. Safe places appear from [SAFE_DOT_MIN_ZOOM] up (they matter when
 *     you are about to walk to one, not at country scale).
 */
object MapFocus {

  /** Radius circles are only honest from this zoom up (≈ district scale). */
  const val RADIUS_CIRCLE_MIN_ZOOM = 9.0
  /** Safe-place markers appear from this zoom up (≈ city scale). */
  const val SAFE_DOT_MIN_ZOOM = 8.5
  /** How far "nearby" reaches by default: 50 km. */
  const val NEARBY_RADIUS_METERS = 50_000.0

  /** Whether the map should render at all with no usable focus point. */
  fun isNear(focus: GeoPoint?, point: GeoPoint, radiusMeters: Double = NEARBY_RADIUS_METERS): Boolean =
    focus == null || GeoMath.distanceMeters(focus, point) <= radiusMeters

  fun nearbyHazardZones(
    zones: List<HazardZone>,
    focus: GeoPoint?,
    radiusMeters: Double = NEARBY_RADIUS_METERS
  ): List<HazardZone> =
    if (focus == null) zones else zones.filter { isNear(focus, it.center, radiusMeters) }

  fun nearbySafeZones(
    zones: List<SafeZone>,
    focus: GeoPoint?,
    radiusMeters: Double = NEARBY_RADIUS_METERS
  ): List<SafeZone> =
    if (focus == null) zones else zones.filter { isNear(focus, it.point, radiusMeters) }

  /** True when a provider event carries usable coordinates for the map. */
  fun eventPoint(event: DisasterEvent): GeoPoint? = when (val g = event.geometry) {
    is EventGeometry.Point -> GeoPoint(g.lat, g.lon)
    is EventGeometry.MultiPoint -> g.points.firstOrNull()
    is EventGeometry.Line -> g.points.firstOrNull()
    is EventGeometry.Polygon -> g.ring.firstOrNull()
    is EventGeometry.RasterLayer -> null
    is EventGeometry.Unlocated -> null
  }

  fun nearbyEvents(
    events: List<DisasterEvent>,
    focus: GeoPoint?,
    radiusMeters: Double = NEARBY_RADIUS_METERS
  ): List<DisasterEvent> =
    if (focus == null) events else events.filter { event ->
      eventPoint(event)?.let { isNear(focus, it, radiusMeters) } ?: true
    }

  /**
   * The single honest question the camera answers: is this a NEARBY view?
   * Above [SAFE_DOT_MIN_ZOOM] we are looking at a city - nearby-first rules
   * apply; below it the whole country is intentionally visible.
   */
  fun isCityScale(zoom: Double): Boolean = zoom >= SAFE_DOT_MIN_ZOOM

  /**
   * What a hazard SHOULD look like at a zoom: a small marker, or its real
   * radius circle only once the zoom is honest for it.
   */
  fun drawsRadiusCircle(zoom: Double): Boolean = zoom >= RADIUS_CIRCLE_MIN_ZOOM
}
