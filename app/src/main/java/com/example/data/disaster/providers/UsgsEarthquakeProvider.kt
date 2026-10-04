package com.example.data.disaster.providers

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterEvent
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.DisasterType
import com.example.data.disaster.EventDetails
import com.example.data.disaster.EventGeometry
import com.example.data.disaster.EventOrigin
import com.example.data.disaster.IndiaGeo
import com.example.data.disaster.ProviderResult
import com.example.data.model.HazardSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ============================================================================
 * PRIORITY 1 PROVIDER — USGS EARTHQUAKES (official machine-readable feed).
 * ============================================================================
 *
 * Official endpoint (FDSN event service, documented at
 * https://earthquake.usgs.gov/fdsnws/event/1/):
 *
 *   https://earthquake.usgs.gov/fdsnws/event/1/query
 *     ?format=geojson
 *     &starttime=<ISO date>&endtime=<ISO date>
 *     &minlatitude=6&maxlatitude=37.5&minlongitude=67.5&maxlongitude=98
 *     &minmagnitude=4&orderby=time
 *
 * No API key, no scraping — a documented GeoJSON catalog service. Live-
 * validated from this environment (real events returned for India bbox).
 */
class UsgsEarthquakeProvider(
  private val httpClient: OkHttpClient = defaultHttpClient()
) : DisasterDataProvider {

  override val providerId: DisasterSource = DisasterSource.USGS

  override suspend fun fetchIndiaEvents(): ProviderResult = withContext(Dispatchers.IO) {
    val url = buildUrl()
    try {
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", "VippattiSarana-DisasterRelief/1.0 (Android; USGS EQ Feed)")
        .build()
      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()
        if (!response.isSuccessful || body.isNullOrBlank()) {
          return@withContext ProviderResult.Failure(
            "USGS earthquake service unavailable (HTTP ${response.code})."
          )
        }
        val events = UsgsGeoJsonParser.parse(body)
        ProviderResult.Success(events, System.currentTimeMillis())
      }
    } catch (e: IOException) {
      ProviderResult.Failure("No connection to the earthquake service — cached data stays available.")
    } catch (e: Exception) {
      ProviderResult.Failure("Earthquake feed error (${e.javaClass.simpleName}).")
    }
  }

  companion object {
    const val BASE_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query"
    // PHASE 15 freshness: a magnitude-4+ event older than 3 days is context,
    // not a current hazard for the local map. (Cache policy already drops
    // beyond 7 days; the query window is now tighter on purpose.)
    const val LOOKBACK_DAYS = 3
    const val MIN_MAGNITUDE = 4.0

    fun defaultHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /** URL is internal + testable; bounds come from [IndiaGeo]. */
    fun buildUrl(nowMillis: Long = System.currentTimeMillis()): String {
      val end = java.time.Instant.ofEpochMilli(nowMillis)
      val start = end.minusSeconds(LOOKBACK_DAYS * 24L * 3600L)
      return "$BASE_URL?format=geojson" +
        "&starttime=${start}&endtime=${end}" +
        "&minlatitude=${IndiaGeo.MIN_LAT}&maxlatitude=${IndiaGeo.MAX_LAT}" +
        "&minlongitude=${IndiaGeo.MIN_LON}&maxlongitude=${IndiaGeo.MAX_LON}" +
        "&minmagnitude=$MIN_MAGNITUDE&orderby=time"
    }
  }
}

/**
 * Pure parser for the USGS GeoJSON feature schema (documented, live-validated).
 * Keeps magnitude, depth, coordinates, place, timestamp, event id and url.
 */
object UsgsGeoJsonParser {

  fun parse(body: String): List<DisasterEvent> {
    val root = JSONObject(body)
    val features = root.optJSONArray("features") ?: return emptyList()
    val out = mutableListOf<DisasterEvent>()
    for (i in 0 until features.length()) {
      parseFeature(features.optJSONObject(i) ?: continue)?.let { out.add(it) }
    }
    return out
  }

  private fun parseFeature(feature: JSONObject): DisasterEvent? {
    val id = feature.optString("id")
    if (id.isBlank()) return null
    val props = feature.optJSONObject("properties") ?: return null
    val geometry = feature.optJSONObject("geometry") ?: return null
    val coords = geometry.optJSONArray("coordinates") ?: return null
    if (coords.length() < 2) return null
    val lon = coords.optDouble(0, Double.NaN)
    val lat = coords.optDouble(1, Double.NaN)
    if (lat.isNaN() || lon.isNaN() || !IndiaGeo.contains(lat, lon)) return null

    val magnitude = props.optDouble("mag", Double.NaN).takeIf { !it.isNaN() } ?: return null
    val timeMillis = props.optLong("time", 0L)
    if (timeMillis <= 0L) return null
    val place = props.optString("place").ifBlank { "Location not provided by source" }
    val depthKm = coords.optDouble(2, 0.0)
    val url = props.optString("url").takeIf { it.isNotBlank() }

    return DisasterEvent(
      id = "usgs-$id",
      source = DisasterSource.USGS,
      sourceEventId = id,
      disasterType = DisasterType.EARTHQUAKE,
      title = "M ${formatMagnitude(magnitude)} Earthquake — $place",
      description = "Magnitude $magnitude earthquake detected by USGS at $place " +
        "(depth ${formatDepth(depthKm)} km).",
      geometry = EventGeometry.Point(lat, lon),
      latitude = lat,
      longitude = lon,
      severity = quakeSeverity(magnitude),
      observedAtMillis = timeMillis,
      updatedAtMillis = timeMillis,
      origin = EventOrigin.OBSERVED,
      url = url,
      details = EventDetails.Quake(
        magnitude = magnitude,
        depthKm = depthKm,
        place = place,
        url = url
      )
    )
  }

  fun quakeSeverity(magnitude: Double): HazardSeverity = when {
    magnitude >= 6.5 -> HazardSeverity.EXTREME
    magnitude >= 5.5 -> HazardSeverity.HIGH
    magnitude >= 4.5 -> HazardSeverity.MODERATE
    else -> HazardSeverity.LOW
  }

  fun formatMagnitude(mag: Double): String = String.format(java.util.Locale.US, "%.1f", mag)
  fun formatDepth(km: Double): String = String.format(java.util.Locale.US, "%.1f", km)
}
