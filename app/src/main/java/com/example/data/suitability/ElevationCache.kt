package com.example.data.suitability

import com.example.data.routing.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Batched, honest elevation lookup for shelter ranking.
 *
 * Why this exists (user rule): safe places must be chosen by ALTITUDE and
 * PROXIMITY — "a safe place 2 km away" is only truly safe if it is not in a
 * flood depression. Open-Meteo's keyless elevation API accepts a comma list
 * of coordinates, so one HTTP round-trip can price an entire shelter set.
 *
 * Honesty contract (project-wide): every shelter either gets a real fetched
 * elevation, or it gets `null` and the evaluator treats altitude as UNKNOWN —
 * never zero, never guessed. A failed batch poisons nothing: individual
 * missing points simply stay unknown and are retried in a later batch.
 */
class ElevationCache(
  /** Injectable transport (same seam the terrain probe uses). */
  private val fetch: suspend (String) -> TerrainFetchResult =
    TerrainProbeService::httpFetch,
  /** Points per HTTP batch. Open-Meteo allows large lists; 40 keeps latency low. */
  private val batchSize: Int = 40,
) {

  /** Quantised key (~11 m resolution) — map drift reuses cached values. */
  private fun key(p: GeoPoint): Long =
    (p.lat * 10_000).toLong() * 100_000L + (p.lon * 10_000).toLong()

  private val results = HashMap<Long, Double>()

  /**
   * Fetches elevations for [points] that are not cached yet and returns the
   * full known map. Points whose batch failed are ABSENT from the result —
   * callers must handle "no altitude data" as its own state.
   */
  suspend fun elevations(points: List<GeoPoint>): Map<GeoPoint, Double> {
    val pending = LinkedHashSet<GeoPoint>()
    val answered = HashMap<GeoPoint, Double>()
    for (p in points) {
      val cached = results[key(p)]
      if (cached != null) answered[p] = cached else pending.add(p)
    }
    if (pending.isEmpty()) return answered

    // Batched sequential fetch (the shelter set fits in 1-2 batches;
    // bounded parallelism was not worth the coroutines machinery).
    withContext(Dispatchers.IO) {
      for (batch in pending.chunked(batchSize)) {
        val got: Map<GeoPoint, Double> = fetchBatch(batch)
        answered.putAll(got)
        for ((point, value) in got) results[key(point)] = value
      }
    }

    return answered
  }

  /** One Open-Meteo request for a coordinate batch; failures return empty. */
  private suspend fun fetchBatch(batch: List<GeoPoint>): Map<GeoPoint, Double> {
    val url = buildUrl(batch)
    return when (val r = fetch(url)) {
      is TerrainFetchResult.Ok -> parse(r.body, batch)
      is TerrainFetchResult.Failure -> emptyMap()
    }
  }

  companion object {
    /** Exposed for unit tests: exact request shape verified against the live API. */
    fun buildUrl(points: List<GeoPoint>): String =
      "https://api.open-meteo.com/v1/elevation" +
        "?latitude=" + points.joinToString(",") { it.lat.toString() } +
        "&longitude=" + points.joinToString(",") { it.lon.toString() }

    /**
     * Live-verified payload shape: {"elevation":[22.0,...]} — same length &
     * order as the request. Anything else yields no entries (honest unknown).
     */
    fun parse(body: String?, points: List<GeoPoint>): Map<GeoPoint, Double> {
      if (body.isNullOrBlank()) return emptyMap()
      val arr = try {
        JSONObject(body).optJSONArray("elevation") ?: return emptyMap()
      } catch (e: Exception) {
        return emptyMap()
      }
      if (arr.length() != points.size) return emptyMap()
      return buildMap {
        for (i in points.indices) {
          val v = if (arr.isNull(i)) continue else arr.optDouble(i, Double.NaN)
          if (!v.isNaN()) put(points[i], v)
        }
      }
    }
  }
}
