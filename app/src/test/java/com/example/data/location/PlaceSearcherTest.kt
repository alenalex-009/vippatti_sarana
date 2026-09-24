package com.example.data.location

import com.example.data.routing.GeoPoint
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLACE SEARCH contracts (offline — HTTP is a fake lambda, never a socket):
 *  - India-only results are offered (the app is India-scoped);
 *  - every failure mode is a stated Failure, never an empty success;
 *  - malformed/unreachable payloads degrade honestly.
 */
class PlaceSearcherTest {

  private fun searcher(json: String?) =
    NominatimPlaceSearcher(fetch = { json })

  @Test
  fun `indian city result is returned with name, kind and coordinates`() = runBlocking {
    val json = """
      [{"name":"Visakhapatnam","type":"city","addresstype":"city",
        "lat":"17.6935","lon":"83.2921",
        "display_name":"Visakhapatnam, Visakhapatnam, Andhra Pradesh, India"}]
    """.trimIndent()
    val result = searcher(json).search("vizag")
    assertTrue(result is PlaceSearchResult.Found)
    val found = result as PlaceSearchResult.Found
    assertEquals(1, found.candidates.size)
    val candidate = found.candidates.first()
    assertEquals("Visakhapatnam", candidate.name)
    assertEquals("city", candidate.kind)
    assertEquals(17.6935, candidate.point.lat, 1e-6)
    assertEquals(83.2921, candidate.point.lon, 1e-6)
    assertTrue(candidate.displayName.endsWith("India"))
  }

  @Test
  fun `results outside India are filtered out and the user is told nothing matched`() = runBlocking {
    val json = """
      [{"name":"Visaginas","type":"city","lat":"55.6","lon":"25.8",
        "display_name":"Visaginas, Lithuania"}]
    """.trimIndent()
    val result = searcher(json).search("visa")
    assertTrue(result is PlaceSearchResult.Failure)
    assertTrue((result as PlaceSearchResult.Failure).reason.contains("No place in India"))
  }

  @Test
  fun `unreachable transport is a stated failure not an empty success`() = runBlocking {
    val result = searcher(null).search("kerala")
    assertTrue(result is PlaceSearchResult.Failure)
    assertTrue((result as PlaceSearchResult.Failure).reason.contains("unreachable"))
  }

  @Test
  fun `garbage response is an honest parse failure`() = runBlocking {
    val result = searcher("<html>rate limited</html>").search("goa")
    assertTrue(result is PlaceSearchResult.Failure)
  }

  @Test
  fun `queries shorter than two letters never hit the network`() = runBlocking {
    var fetched = false
    val s = NominatimPlaceSearcher(fetch = { fetched = true; null })
    assertTrue(s.search("a") is PlaceSearchResult.Failure)
    assertTrue(s.search("") is PlaceSearchResult.Failure)
    assertTrue(!fetched)
  }

  @Test
  fun `url is india-biased json and encodes the query`() {
    val url = NominatimPlaceSearcher.buildUrl("Andhra Pradesh")
    assertTrue(url.startsWith("https://nominatim.openstreetmap.org/search?"))
    assertTrue(url.contains("countrycodes=in"))
    assertTrue(url.contains("format=jsonv2"))
    assertTrue(url.contains("Andhra+Pradesh") || url.contains("Andhra%20Pradesh"))
  }

  @Test
  fun `result list is capped so the picker stays scannable`() = runBlocking {
    val many = (1..12).joinToString(
      prefix = "[", postfix = "]"
    ) { """{"name":"Place$it","type":"city","lat":"10.0","lon":"77.0","display_name":"Place$it, Tamil Nadu, India"}""" }
    val result = searcher(many).search("place") as PlaceSearchResult.Found
    assertEquals(NominatimPlaceSearcher.MAX_RESULTS, result.candidates.size)
  }
}
