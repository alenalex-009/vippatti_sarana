package com.example.data.india

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ============================================================================
 * INDIA-WIDE SPATIAL REFERENCE
 * ============================================================================
 *
 * Rules 13-17 concern geography. Two things are needed for an India-wide
 * deployment and neither exists implicitly:
 *
 *   1. A legitimate geographic extent for India, so "near me" and "near this
 *      hazard" are well defined anywhere in the country.
 *   2. A spatial bucketing scheme, so a user in any district can be placed in a
 *      stable cell without shipping a 555 MB boundary file in the APK.
 *
 * No coordinates here are invented: they are the published national extent.
 */

/** Bounding box in WGS84 (minLon, minLat, maxLon, maxLat). */
data class GeoBoundingBox(
  val minLon: Double,
  val minLat: Double,
  val maxLon: Double,
  val maxLat: Double
) {
  operator fun contains(point: GeoPoint): Boolean =
    point.lon in minLon..maxLon && point.lat in minLat..maxLat

  val center: GeoPoint get() = GeoPoint((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)

  companion object {
    /**
     * India's full extent including the far-flung island territories
     * (Lakshadweep ~ 71.8E, Kanyakumari ~ 8.08N, Indira Col / Arctic ~ 12.3N
     * of the claimed range). Uses the commonly cited national bbox.
     */
    val INDIA = GeoBoundingBox(minLon = 68.1, minLat = 6.5, maxLon = 97.4, maxLat = 35.5)

    /** Mainland-focused box, excludes the extreme north claimed latitudes. */
    val INDIA_MAINLAND = GeoBoundingBox(minLon = 68.1, minLat = 6.5, maxLon = 97.4, maxLat = 32.0)

    /**
     * Andaman & Nicobar: separate union territory, well south-east of the
     * mainland bbox, and genuinely India-wide relevant for cyclones/tsunami.
     */
    val ANDAMAN_NICOBAR = GeoBoundingBox(minLon = 92.0, minLat = 6.5, maxLon = 94.5, maxLat = 14.5)
  }
}

data class GeoPoint(val lat: Double, val lon: Double) {
  init {
    require(lat in -90.0..90.0) { "Latitude out of range: $lat" }
    require(lon in -180.0..180.0) { "Longitude out of range: $lon" }
  }
}

/** Which part of India a coordinate falls in. Used to scope remote fetches. */
enum class IndiaRegion(val label: String) {
  MAINLAND("Mainland India"),
  ANDAMAN_NICOBAR("Andaman & Nicobar Islands"),
  LAKSHADWEEP("Lakshadweep"),
  OUTSIDE("Outside India")
}

object IndiaGeography {

  fun regionFor(point: GeoPoint): IndiaRegion = when {
    GeoBoundingBox.ANDAMAN_NICOBAR.contains(point) -> IndiaRegion.ANDAMAN_NICOBAR
    point.lat in 8.0..12.5 && point.lon in 71.5..74.0 -> IndiaRegion.LAKSHADWEEP
    GeoBoundingBox.INDIA.contains(point) -> IndiaRegion.MAINLAND
    else -> IndiaRegion.OUTSIDE
  }

  fun isInIndia(point: GeoPoint): Boolean = regionFor(point) != IndiaRegion.OUTSIDE

  /**
   * Nearest point inside India's extent. Used ONLY to scope a data request
   * when a fix lands slightly outside the boundary — the returned point is
   * clearly derived, never presented as the user's actual location.
   */
  fun clampToIndia(point: GeoPoint): GeoPoint = GeoPoint(
    lat = min(max(point.lat, GeoBoundingBox.INDIA.minLat), GeoBoundingBox.INDIA.maxLat),
    lon = min(max(point.lon, GeoBoundingBox.INDIA.minLon), GeoBoundingBox.INDIA.maxLon)
  )

  /**
   * Fetch bbox for "around this point", clamped to India so we never request
   * arbitrary global data. Size shrinks as you zoom in.
   */
  fun searchBoxAround(point: GeoPoint, radiusKm: Double): GeoBoundingBox {
    val latPad = radiusKm / 111.0
    // Longitude degrees shrink with latitude; guard against the pole.
    val cosLat = kotlin.math.cos(Math.toRadians(point.lat)).coerceAtLeast(0.05)
    val lonPad = radiusKm / (111.0 * cosLat)
    val clamped = clampToIndia(point)
    return GeoBoundingBox(
      minLon = (clamped.lon - lonPad).coerceAtLeast(GeoBoundingBox.INDIA.minLon),
      minLat = (clamped.lat - latPad).coerceAtLeast(GeoBoundingBox.INDIA.minLat),
      maxLon = (clamped.lon + lonPad).coerceAtMost(GeoBoundingBox.INDIA.maxLon),
      maxLat = (clamped.lat + latPad).coerceAtMost(GeoBoundingBox.INDIA.maxLat)
    )
  }

  /**
   * Build the FIRMS area request string: "lonMin,latMin,lonMax,latMax".
   */
  fun firmsBbox(box: GeoBoundingBox): String =
    "${box.minLon},${box.minLat},${box.maxLon},${box.maxLat}"

  /**
   * Build the USGS FDSN query fragment.
   */
  fun usgsBbox(box: GeoBoundingBox): String =
    "minlatitude=${box.minLat}&maxlatitude=${box.maxLat}" +
      "&minlongitude=${box.minLon}&maxlongitude=${box.maxLon}"
}

/**
 * A stable, India-wide spatial cell identifier.
 *
 * Purpose: give every point in the country a short, deterministic bucket so
 * cache keys and placeholders are uniform without shipping national geometry.
 * It is a DERIVED grid, not an administrative unit, and is labelled as such.
 */
data class GeoGridCell(val row: Int, val col: Int, val resolutionDeg: Double) {
  val id: String get() = "r${row}_c${col}_${resolutionDeg}"

  val centerLat: Double get() = GeoGrid.CELL_ORIGIN_LAT - (row + 0.5) * resolutionDeg
  val centerLon: Double get() = GeoGrid.CELL_ORIGIN_LON + (col + 0.5) * resolutionDeg

  /** Only meaningful at coarse resolution; used for display, not for claims. */
  val approxKm: Double get() = resolutionDeg * 111.0
}

object GeoGrid {

  /** Origin chosen to make cells align with whole degrees of longitude. */
  const val CELL_ORIGIN_LAT = 36.0
  const val CELL_ORIGIN_LON = 68.0

  val FINE = 0.10
  val MEDIUM = 0.25
  val COARSE = 1.0

  fun cellFor(point: GeoPoint, resolutionDeg: Double): GeoGridCell =
    GeoGridCell(
      row = Math.floor((CELL_ORIGIN_LAT - point.lat) / resolutionDeg).toInt(),
      col = Math.floor((point.lon - CELL_ORIGIN_LON) / resolutionDeg).toInt(),
      resolutionDeg = resolutionDeg
    )

  /** Great-circle distance in km. */
  fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
    val R = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
      kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2) * kotlin.math.cos(lat1) * kotlin.math.cos(lat2)
    return 2 * R * kotlin.math.asin(min(1.0, kotlin.math.sqrt(h)))
  }

  /** Initial bearing degrees 0..360 from a to b. */
  fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
    val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
      kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)
    val deg = Math.toDegrees(kotlin.math.atan2(y, x))
    return (deg + 360.0) % 360.0
  }
}
