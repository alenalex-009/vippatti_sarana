package com.example.data.location

import com.example.data.routing.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * ============================================================================
 * PLACE SEARCH (forward geocoding) — "choose a state / city to look at"
 * ============================================================================
 *
 * Lets a user WITHOUT usable GPS (or an authority reviewing another district)
 * point the whole app at a chosen place. Transport is Nominatim/OpenStreetMap
 * search (keyless, real). The HTTP layer is injectable so every rule below is
 * unit-tested offline:
 *  - only results inside India are offered (the app is India-scoped, and an
 *    out-of-India centre would silently return empty hazard data);
 *  - a failed/empty response is a stated Failure, never an empty success;
 *  - the search TEXT is echoed into the candidate name so a result is always
 *    traceable to what the user typed.
 */
data class PlaceCandidate(
  val name: String,
  val displayName: String,
  val point: GeoPoint,
  /** "city" / "state" / "county" / "administrative" — OSM's own class. */
  val kind: String
)

sealed class PlaceSearchResult {
  data class Found(val candidates: List<PlaceCandidate>) : PlaceSearchResult()
  data class Failure(val reason: String) : PlaceSearchResult()
}

interface PlaceSearcher {
  suspend fun search(query: String): PlaceSearchResult
}

class NominatimPlaceSearcher(
  /** Injected transport for tests; production uses the OkHttp one below. */
  private val fetch: suspend (String) -> String? = ::httpFetch
) : PlaceSearcher {

  override suspend fun search(query: String): PlaceSearchResult {
    val q = query.trim()
    if (q.length < 2) return PlaceSearchResult.Failure("Type at least 2 letters of a place name.")
    val body = try {
      fetch(buildUrl(q))
    } catch (error: Exception) {
      return PlaceSearchResult.Failure("Place search failed (${error.javaClass.simpleName}).")
    } ?: return PlaceSearchResult.Failure(
      "Place search is unreachable right now — try again when connected."
    )
    return try {
      val array = JSONArray(body)
      val candidates = mutableListOf<PlaceCandidate>()
      for (i in 0 until array.length()) {
        val o = array.optJSONObject(i) ?: continue
        val lat = o.optString("lat").toDoubleOrNull() ?: continue
        val lon = o.optString("lon").toDoubleOrNull() ?: continue
        val display = o.optString("display_name")
        // India-scoped: silently skipping foreign hits would confuse; keep
        // only Indian results and say so when everything was filtered out.
        if (!display.endsWith("India")) continue
        candidates += PlaceCandidate(
          name = o.optString("name").ifBlank { q },
          displayName = display,
          point = GeoPoint(lat, lon),
          kind = o.optString("type").ifBlank { o.optString("addresstype") }
        )
        if (candidates.size == MAX_RESULTS) break
      }
      if (candidates.isEmpty()) {
        PlaceSearchResult.Failure("No place in India matched \"$q\".")
      } else PlaceSearchResult.Found(candidates)
    } catch (_: Exception) {
      PlaceSearchResult.Failure("Place search returned an unreadable response.")
    }
  }

  companion object {
    const val MAX_RESULTS = 6

    /** Single endpoint, India-biased, English labels, Nominatim-compliant UA set in fetch. */
    fun buildUrl(query: String): String =
      "https://nominatim.openstreetmap.org/search?q=" +
        URLEncoder.encode(query, "UTF-8") +
        "&format=jsonv2&limit=8&countrycodes=in&accept-language=en"

    suspend fun httpFetch(url: String): String? = withContext(Dispatchers.IO) {
      try {
        val request = Request.Builder()
          .url(url)
          .header("User-Agent", "VippattiSarana-DisasterRelief/1.0 (Android; place search)")
          .build()
        client.newCall(request).execute().use { r ->
          if (r.isSuccessful) r.body?.string() else null
        }
      } catch (_: Exception) {
        null
      }
    }

    private val client: OkHttpClient by lazy {
      OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()
    }
  }
}
