package com.example.data.suitability

import com.example.data.routing.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Outcome of one HTTP GET as the probe sees it. */
sealed class TerrainFetchResult {
  data class Ok(val body: String) : TerrainFetchResult()
  data class Failure(val detail: String) : TerrainFetchResult()
}

/** Result of one full probe — never a fabricated verdict on partial data. */
sealed class TerrainProbeResult {
  data class Success(
    val verdict: TerrainVerdict,
    /** True when coast proximity came from the offline grid (not unknown). */
    val coastKnown: Boolean
  ) : TerrainProbeResult()

  /** No elevation stencil could be fetched — habitability cannot be assessed. */
  data class ElevationUnavailable(val detail: String) : TerrainProbeResult()
}

/**
 * Live terrain probe: SRTM stencil elevation (Open-Meteo Elevation API) +
 * 24 h rainfall (Open-Meteo forecast) + the offline coast grid ->
 * [TerrainSuitabilityEngine.evaluate]. Keyless endpoints, same standing as the
 * weather service. The HTTP layer is injectable so every branch is unit-tested
 * offline against the live-verified payload shapes.
 */
class TerrainProbeService(
  /** Injectable transport: url -> result. Production uses OkHttp on IO. */
  private val fetch: suspend (String) -> TerrainFetchResult = TerrainProbeService::httpFetch
) {

  suspend fun probe(point: GeoPoint, coastGrid: CoastDistanceGrid?): TerrainProbeResult {
    val stencil = TerrainSuitabilityEngine.samplePoints(point, stepMeters = 30.0)
    val elevResult = fetch(ElevationGridJson.buildElevationUrl(stencil))
    val grid = (elevResult as? TerrainFetchResult.Ok)?.body.let { ElevationGridJson.parse(it, stepMeters = 30.0) }
    if (grid == null) {
      val detail = when (elevResult) {
        is TerrainFetchResult.Failure -> elevResult.detail
        is TerrainFetchResult.Ok -> "payload carried no usable elevation"
        null -> "no response"
      }
      return TerrainProbeResult.ElevationUnavailable(detail)
    }
    val rainResult = fetch(buildRainUrl(point))
    val rain = (rainResult as? TerrainFetchResult.Ok)?.body?.let { parseRainfallMm24h(it) }
    val nearCoast = coastGrid?.isNearCoast(point)
    val verdict = TerrainSuitabilityEngine.evaluate(grid, rain, nearCoast)
    return TerrainProbeResult.Success(verdict, coastKnown = nearCoast != null)
  }

  companion object {
    private val httpClient: OkHttpClient by lazy {
      OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    }

    /** Real request URL for the live 24 h rainfall input. */
    fun buildRainUrl(point: GeoPoint): String =
      "https://api.open-meteo.com/v1/forecast?latitude=${point.lat}&longitude=${point.lon}" +
        "&daily=precipitation_sum&forecast_days=2&past_days=0&timezone=auto"

    /**
     * Max daily precipitation over the forecast window (mm/24 h). Null when
     * the payload has no usable series — rain stays EXCLUDED, never zeroed.
     */
    fun parseRainfallMm24h(body: String): Double? = try {
      val daily = JSONObject(body).optJSONObject("daily")
      val series = daily?.optJSONArray("precipitation_sum")
      if (series == null || series.length() == 0) null
      else (0 until series.length())
        .map { series.optDouble(it, Double.NaN) }
        .filter { !it.isNaN() }
        .maxOrNull()
    } catch (_: Exception) {
      null
    }

    /** Production transport: blocking OkHttp moved onto Dispatchers.IO. */
    suspend fun httpFetch(url: String): TerrainFetchResult = withContext(Dispatchers.IO) {
      try {
        val request = Request.Builder()
          .url(url)
          .header("User-Agent", "VippattiSarana-DisasterRelief/1.0 (Android; terrain suitability)")
          .build()
        httpClient.newCall(request).execute().use { response ->
          val body = response.body?.string()
          if (!response.isSuccessful) {
            TerrainFetchResult.Failure("HTTP ${response.code}")
          } else if (body.isNullOrBlank()) {
            TerrainFetchResult.Failure("empty response body")
          } else {
            TerrainFetchResult.Ok(body)
          }
        }
      } catch (error: Exception) {
        TerrainFetchResult.Failure(error::class.java.simpleName)
      }
    }
  }
}
