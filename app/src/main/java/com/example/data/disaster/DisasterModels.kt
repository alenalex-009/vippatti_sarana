package com.example.data.disaster

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.routing.GeoPoint
import kotlin.math.max
import kotlin.math.pow

/**
 * ============================================================================
 * NORMALIZED INDIA-WIDE DISASTER EVENT MODEL.
 * ============================================================================
 *
 * Every externally sourced disaster event keeps full PROVENANCE: source,
 * source event id, observed/updated times, status, confidence and an
 * observed/reported/derived/estimated classification. Nothing here is ever
 * fabricated — events exist only when a real provider returned them.
 */

/** Kind of disaster, provider-agnostic. */
enum class DisasterType(val label: String) {
  EARTHQUAKE("Earthquake"),
  WILDFIRE("Active Fire"),
  CYCLONE("Cyclone"),
  FLOOD("Flood"),
  HEAVY_RAINFALL("Heavy Rainfall"),
  LANDSLIDE("Landslide"),
  WEATHER_ALERT("Weather Alert"),
  OTHER("Other Hazard")
}

/** Authoritative (or user-report) origin of an event. */
enum class DisasterSource(val label: String) {
  USGS("USGS Earthquake Hazards Program"),
    NASA_FIRMS("NASA FIRMS"),
    IMD_CAP("India Meteorological Department (official CAP alert)"),
    NDMA_CAP("NDMA / SACHET national alerts (official CAP feed)"),
    /**
     * Central Water Commission river gauges. Registered as UNAVAILABLE, not
     * omitted: an empty river layer would read as "no flood risk", whereas an
     * explicit unavailable source reads as "we do not know".
     */
    CWC_GAUGE("Central Water Commission river gauge (unavailable)"),
    NDEM("NDEM / NRSC official layers (authorisation required)"),
    INCOIS("INCOIS tsunami warnings (portal only)"),
    USER_REPORT("User Report")
  }

/** Geographic representation kinds supported by the pipeline. */
enum class GeometryType { POINT, MULTIPOINT, LINE, POLYGON, RASTER_LAYER, UNLOCATED }

/** How the event data was produced (displayed as data type / provenance). */
enum class EventOrigin(val label: String) {
  OBSERVED("Observed"),
  REPORTED("Reported"),
  DERIVED("Derived"),
  SIMULATED("Simulated")
}

/** Lifecycle of an event. */
enum class EventStatus(val label: String) {
  ACTIVE("Active observation"),
  EXPIRED("Expired"),
  UNKNOWN("Status unknown")
}

/**
 * Confidence the PROVIDER itself attaches to an event — never fabricated by
 * this app. NASA FIRMS reports low/nominal/high detection confidence; USGS
 * and IMD CAP alerts do not always carry a confidence field (NOT_PROVIDED).
 */
enum class EventConfidence(val label: String) {
  HIGH("High"),
  NOMINAL("Nominal"),
  LOW("Low"),
  NOT_PROVIDED("Not provided by source")
}

/**
 * Geographic representation of one event. Point observations (earthquakes,
 * fire detections, user reports), lines (tracks), polygons (CAP alert areas)
 * and raster/GIS layers are all first-class.
 */
sealed class EventGeometry {
  abstract val type: GeometryType

  data class Point(val lat: Double, val lon: Double) : EventGeometry() {
    override val type get() = GeometryType.POINT
  }

  data class MultiPoint(val points: List<GeoPoint>) : EventGeometry() {
    override val type get() = GeometryType.MULTIPOINT
  }

  data class Line(val points: List<GeoPoint>) : EventGeometry() {
    override val type get() = GeometryType.LINE
  }

  data class Polygon(val ring: List<GeoPoint>) : EventGeometry() {
    override val type get() = GeometryType.POLYGON
  }

  /** Reserved for providers exposing raster/GIS layers rather than records. */
  data class RasterLayer(val layerId: String, val title: String) : EventGeometry() {
    override val type get() = GeometryType.RASTER_LAYER
  }

  /**
   * A real record the source published WITHOUT usable geometry (e.g. an IMD CAP
   * alert with no `<polygon>`). It carries the provider's own area text and no
   * coordinates: such a record is never pinned to a placeholder point, is never
   * drawn on the map and produces no local hazard zone - it stays visible as an
   * official alert with its stated area instead.
   */
  data class Unlocated(val areaLabel: String?) : EventGeometry() {
    override val type get() = GeometryType.UNLOCATED
  }
}

/**
 * Provider-specific detail payload (kept typed so the detail UI can render
 * honest, per-source information without guessing).
 */
sealed class EventDetails {
  data class Quake(
    val magnitude: Double,
    val depthKm: Double,
    val place: String,
    val url: String?
  ) : EventDetails()

  data class Fire(
    val satellite: String,
    val instrument: String,
    val frpMegawatts: Double?,
    val dayNight: String?
  ) : EventDetails()

  data class OfficialAlert(
    val event: String,
    val urgency: String,
    val certainty: String,
    val senderName: String,
    val instruction: String?,
    val webLink: String?
  ) : EventDetails()

  data class UserIncident(
    val categoryLabel: String,
    val reporterNote: String
  ) : EventDetails()

  object Generic : EventDetails()
}

/**
 * The normalized disaster event record.
 */
data class DisasterEvent(
  val id: String,
  val source: DisasterSource,
  val sourceEventId: String,
  val disasterType: DisasterType,
  val title: String,
  val description: String,
  val geometry: EventGeometry,
  /** Convenience latitude for point geometries (null otherwise). */
  val latitude: Double? = null,
  /** Convenience longitude for point geometries (null otherwise). */
  val longitude: Double? = null,
  val severity: HazardSeverity,
  val confidence: EventConfidence = EventConfidence.NOT_PROVIDED,
  val confidenceNote: String? = null,
  val observedAtMillis: Long,
  val updatedAtMillis: Long,
  /** null = no official expiry (kept while the fetch window includes it). */
  val expiresAtMillis: Long? = null,
  val status: EventStatus = EventStatus.ACTIVE,
  val origin: EventOrigin,
  val affectedAreaLabel: String? = null,
  val url: String? = null,
  val details: EventDetails = EventDetails.Generic
) {
  init {
    require(sourceEventId.isNotBlank()) { "sourceEventId must retain provider provenance" }
  }

  /** Deduplication key — the same provider event must never appear twice. */
  val dedupeKey: String get() = "${source.name}:$sourceEventId"

  val isActive: Boolean get() = status != EventStatus.EXPIRED
}

/**
 * One color per disaster type — the map's visual vocabulary. Every danger
 * zone on the radar renders in its disaster's color (flood blue, fire
 * orange, cyclone purple ...), so the type reads directly off the zone
 * itself. A legend row above the map keys each color to its disaster.
 * Opaque ARGB ints shared by the osmdroid engine and the Compose legend.
 */
object DisasterTypeColors {

  fun argbFor(type: HazardType): Int = when (type) {
    HazardType.FLOOD -> 0xFF2196F3.toInt() // river blue
    HazardType.HEAVY_RAINFALL -> 0xFF00BCD4.toInt() // rain cyan
    HazardType.LANDSLIDE -> 0xFF8D6E63.toInt() // earth brown
    HazardType.EARTHQUAKE -> 0xFFFF5722.toInt() // seismic deep-orange
    HazardType.CYCLONE -> 0xFFAB47BC.toInt() // storm purple
    HazardType.FIRE -> 0xFFFF9800.toInt() // fire orange
    HazardType.WEATHER_ALERT -> 0xFFFFCA28.toInt() // alert yellow
    HazardType.OTHER -> 0xFF78909C.toInt() // slate grey
  }

  /** Every type maps to a distinct color (legend must never repeat a swatch). */
  fun allDistinct(): Boolean =
    HazardType.entries.map { argbFor(it) }.toSet().size == HazardType.entries.size
}

/**
 * India geographic constants and membership tests (pure Kotlin, testable).
 * Used for India-wide bounding-box provider queries and client-side filters.
 */
object IndiaGeo {
  /** Continental India + island territories bounding box. */
  const val MIN_LAT = 6.0
  const val MAX_LAT = 37.5
  const val MIN_LON = 67.5
  const val MAX_LON = 98.0

  /** National map default view — the whole of India, NOT any pilot district. */
  const val CENTER_LAT = 20.5937
  const val CENTER_LON = 78.9629

  fun contains(lat: Double, lon: Double): Boolean =
    lat in MIN_LAT..MAX_LAT && lon in MIN_LON..MAX_LON

  fun contains(point: GeoPoint): Boolean = contains(point.lat, point.lon)
}

// ============================================================================
// NORMALIZATION -> the existing HazardZone intelligence pipeline
// ============================================================================

/**
 * Converts a normalized [DisasterEvent] into the existing [HazardZone] shape
 * so the risk engine, safe-zone evaluator and OSRM hazard routing keep working
 * unchanged. Zones derived here always carry the LIVE/observed provenance of
 * their source, never mock-network labels.
 */
object DisasterEventNormalizer {

  /** Display/analysis zone radius derived per event type (documented, approximate). */
  fun toHazardZone(event: DisasterEvent): HazardZone? = when (event.geometry) {
    is EventGeometry.Point -> pointZone(event, event.geometry.lat, event.geometry.lon)
    is EventGeometry.MultiPoint ->
      event.geometry.points.firstOrNull()?.let { pointZone(event, it.lat, it.lon) }
    is EventGeometry.Line ->
      event.geometry.points.firstOrNull()?.let { pointZone(event, it.lat, it.lon) }
    is EventGeometry.Polygon -> polygonZone(event, event.geometry.ring)
    is EventGeometry.RasterLayer -> null
    // No geometry from the source -> no local zone. A national-scale alert must
    // not become a hazard blob at an invented coordinate.
    is EventGeometry.Unlocated -> null
  }

  private fun pointZone(event: DisasterEvent, lat: Double, lon: Double): HazardZone? {
      val radius = when (event.disasterType) {
        // RULE 14 (hard requirement): an earthquake epicentre is NOT a danger
        // radius. The previous code computed 2^M km from magnitude and rendered
        // it as a hazard circle, which manufactures a hazard boundary the
        // source never published — the same failure mode as faking a FIRMS
        // perimeter. USGS supplies no affected-area geometry, so a point quake
        // produces NO zone. It stays visible as an earthquake EVENT with its
        // magnitude/depth/place and no invented danger area.
        DisasterType.EARTHQUAKE -> return null

        DisasterType.WILDFIRE -> FIRE_ZONE_RADIUS_METERS
        else -> DEFAULT_POINT_ZONE_RADIUS_METERS
      }
      return baseZone(event, GeoPoint(lat, lon), radius)
    }

  private fun polygonZone(event: DisasterEvent, ring: List<GeoPoint>): HazardZone? {
    if (ring.size < 3) return null
    val centroid = GeoPoint(ring.sumOf { it.lat } / ring.size, ring.sumOf { it.lon } / ring.size)
    // Circumradius covers the polygon extent for radius-based hazard checks,
    // CAPPED so a district-size alert polygon never renders as a giant zone
    // that swallows neighbouring zones (map rule: no zone inside another).
    var maxDistSq = 0.0
    for (p in ring) {
      val dLat = (p.lat - centroid.lat) * 111_320.0
      val dLon = (p.lon - centroid.lon) * 111_320.0 *
        kotlin.math.cos(Math.toRadians(centroid.lat))
      maxDistSq = max(maxDistSq, dLat * dLat + dLon * dLon)
    }
    val radius = kotlin.math.sqrt(maxDistSq)
      .coerceIn(MIN_ZONE_RADIUS_METERS, MAX_ZONE_RADIUS_METERS)
    return baseZone(event, centroid, radius)
  }

  private fun baseZone(event: DisasterEvent, center: GeoPoint, radius: Double): HazardZone {
    val classification = when (event.source) {
      DisasterSource.USER_REPORT -> DataClassification.ESTIMATED
      else -> DataClassification.OBSERVED
    }
    return HazardZone(
      id = "live-${event.dedupeKey}",
      name = event.title,
      type = toHazardType(event.disasterType),
      severity = event.severity,
      center = center,
      radiusMeters = radius,
      riskLevel = event.severity.label,
      trend = HazardTrend.STABLE,
      sourceStatus = sourceStatusLabel(event),
      lastUpdatedMillis = event.updatedAtMillis,
      provenance = DataProvenance(
        source = event.source.label,
        status = DataProvenance.STATUS_LIVE,
        confidence = when (event.confidence) {
          EventConfidence.HIGH -> 0.9
          EventConfidence.NOMINAL -> 0.7
          EventConfidence.LOW -> 0.4
          EventConfidence.NOT_PROVIDED -> 0.6
        },
        isVerified = event.source != DisasterSource.USER_REPORT,
        classification = classification,
        recordedAtMillis = event.observedAtMillis,
        lastUpdatedMillis = event.updatedAtMillis
      )
    )
  }

  /**
   * Radius for a POINT earthquake, decided through [com.example.data.india.InferenceGuard].
   *
   * Returns null unless the SOURCE published a danger radius. USGS never does,
   * so this is always null for real feeds — the constant below exists only so
   * a future authoritative provider that publishes a radius can use it without
   * reintroducing magnitude-derived geometry.
   */
  fun sourceProvidedQuakeRadiusMeters(event: DisasterEvent): Double? =
    when (
      val decision = com.example.data.india.InferenceGuard.dangerRadiusFromEpicentre(
        sourceRadiusKm = com.example.data.india.MaybeNumber.UNKNOWN,
        magnitude = (event.details as? EventDetails.Quake)?.let {
          com.example.data.india.MaybeNumber.of(it.magnitude)
        } ?: com.example.data.india.MaybeNumber.UNKNOWN
      )
    ) {
      is com.example.data.india.InferenceResult.Allowed ->
        decision.value.orNull()?.times(1000.0)
      // Refused: the source published no radius. Do not synthesise one.
      is com.example.data.india.InferenceResult.Refused -> null
    }

  fun toHazardType(type: DisasterType): HazardType = when (type) {
    DisasterType.EARTHQUAKE -> HazardType.EARTHQUAKE
    DisasterType.WILDFIRE -> HazardType.FIRE
    DisasterType.FLOOD -> HazardType.FLOOD
    DisasterType.HEAVY_RAINFALL, DisasterType.WEATHER_ALERT -> HazardType.HEAVY_RAINFALL
    DisasterType.LANDSLIDE -> HazardType.LANDSLIDE
    DisasterType.CYCLONE, DisasterType.OTHER -> HazardType.OTHER
  }

  private fun sourceStatusLabel(event: DisasterEvent): String {
    val confidence = when (event.confidence) {
      EventConfidence.NOT_PROVIDED -> "Confidence not provided by source"
      else -> "Source confidence: ${event.confidence.label}" +
        (event.confidenceNote?.let { " ($it)" } ?: "")
    }
    return "${event.origin.label} ${event.disasterType.label.lowercase()} from ${event.source.label}. $confidence."
  }

  const val FIRE_ZONE_RADIUS_METERS = 750.0
  const val DEFAULT_POINT_ZONE_RADIUS_METERS = 2_500.0
  const val MIN_ZONE_RADIUS_METERS = 500.0
  /**
   * Ceiling for ANY derived live zone radius (quake felt-area, alert-polygon
   * circumradius): no live zone may render larger than this, so a large
   * district alert can never swallow neighbouring zones on the map.
   */
  const val MAX_ZONE_RADIUS_METERS = 25_000.0
}


