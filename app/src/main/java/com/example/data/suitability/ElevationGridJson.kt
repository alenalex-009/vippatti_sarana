package com.example.data.suitability

import com.example.data.routing.GeoPoint
import org.json.JSONObject

/**
 * Parses the Open-Meteo Elevation API response for the 5-point stencil into an
 * [ElevationGrid]. Live schema (verified against the service 2026-09-22):
 *   GET https://api.open-meteo.com/v1/elevation?latitude=a,b,c&longitude=d,e,f
 *   -> {"elevation":[640.0, 640.0, 627.0]}
 * The request point ORDER defines the result order: centre, north, south,
 * east, west. Any missing/null elevation, an error payload, or a short result
 * list yields null — the engine then honestly reports INSUFFICIENT_DATA
 * instead of scoring invented numbers.
 */
object ElevationGridJson {

  fun parse(body: String?, stepMeters: Double = 30.0): ElevationGrid? {
    if (body.isNullOrBlank()) return null
    return try {
      val root = JSONObject(body)
      if (root.optBoolean("error", false)) return null
      val values = root.optJSONArray("elevation") ?: return null
      if (values.length() < 5) return null
      val list = (0 until 5).map { i ->
        if (values.isNull(i)) return null
        val v = values.optDouble(i, Double.NaN)
        if (v.isNaN()) return null else v
      }
      ElevationGrid(
        center = list[0],
        north = list[1],
        south = list[2],
        east = list[3],
        west = list[4],
        stepMeters = stepMeters
      )
    } catch (_: Exception) {
      null
    }
  }

  /** Single-request URL with all five stencil coordinates (centre first). */
  fun buildElevationUrl(points: List<GeoPoint>): String =
    "https://api.open-meteo.com/v1/elevation" +
      "?latitude=" + points.joinToString(",") { it.lat.toString() } +
      "&longitude=" + points.joinToString(",") { it.lon.toString() }
}
