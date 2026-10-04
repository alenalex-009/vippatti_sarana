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

  // ---------------- PHASE 1 (correction pass): hierarchy + stress -----------

  @Test
  fun `addressdetails rings map to the Indian admin levels actually returned`() = runBlocking {
    // Real Nominatim shape (validated live 2026-10-03): suburb + city +
    // state_district + state. The app maps district from state_district.
    val json = """
      [{"name":"MVP Colony","type":"suburb","addresstype":"suburb",
        "lat":"17.7250","lon":"83.3100",
        "display_name":"MVP Colony, Visakhapatnam, Andhra Pradesh, India",
        "address":{"suburb":"MVP Colony","city":"Visakhapatnam",
          "county":"Visakhapatnam Urban","state_district":"Visakhapatnam",
          "state":"Andhra Pradesh","country":"India","country_code":"in"}}]
    """.trimIndent()
    val found = searcher(json).search("MVP Colony") as PlaceSearchResult.Found
    val c0 = found.candidates.single()
    assertEquals("Andhra Pradesh", c0.state)
    assertEquals("Visakhapatnam", c0.district)          // from state_district
    assertEquals("Visakhapatnam Urban", c0.subDistrict) // from county
    assertEquals("Visakhapatnam", c0.villageTown)       // from city
    assertEquals("MVP Colony", c0.locality)             // from suburb
    assertTrue("hierarchy line shows real levels", c0.adminLine().contains("Andhra Pradesh"))
  }

  @Test
  fun `missing admin levels stay null - never inherited from another result`() = runBlocking {
    val json = """
      [{"name":"Some Village","type":"village","lat":"17.0","lon":"82.0",
        "display_name":"Some Village, India",
        "address":{"village":"Some Village","state":"Andhra Pradesh","country":"India"}}]
    """.trimIndent()
    val c0 = (searcher(json).search("village") as PlaceSearchResult.Found).candidates.single()
    assertEquals("Andhra Pradesh", c0.state)
    assertEquals(null, c0.district)
    assertEquals(null, c0.subDistrict)
    assertEquals(null, c0.ward)
    // adminLine only stitches together what EXISTS:
    assertTrue(!c0.adminLine().contains("Not available"))
  }

  @Test
  fun `same-display_name duplicates parse safely - the LazyColumn crash class`() = runBlocking {
    // The 2026-10-03 crash: two results with IDENTICAL display_name ->
    // duplicate LazyColumn key. Parsing must keep both, and the picker key
    // (name|lat|lon) stays unique because coordinates differ.
    val json = """
      [{"name":"Rajiv Gandhi Nagar","type":"suburb","lat":"17.71","lon":"83.30",
        "display_name":"Rajiv Gandhi Nagar, Andhra Pradesh, India"},
       {"name":"Rajiv Gandhi Nagar","type":"suburb","lat":"18.99","lon":"81.11",
        "display_name":"Rajiv Gandhi Nagar, Andhra Pradesh, India"}]
    """.trimIndent()
    val found = searcher(json).search("rajiv nagar") as PlaceSearchResult.Found
    assertEquals(2, found.candidates.size)
    val keys = found.candidates.map { "${it.name}|${it.point.lat}|${it.point.lon}" }.toSet()
    assertEquals("composite keys must be unique", 2, keys.size)
  }

  @Test
  fun `stress inputs degrade honestly - empty, symbol-only, very long, unreadable`() = runBlocking {
    // 1-char query: refused with a stated reason, no network, no crash.
    assertTrue(searcher("[]").search("a") is PlaceSearchResult.Failure)
    // unusual characters: must not throw out of the VM (searcher sanitises by
    // URL-encoding; a valid-but-empty array is an honest no-match failure).
    assertTrue(searcher("[]").search("''\"><&%/#") is PlaceSearchResult.Failure)
    // very long query: still safe.
    assertTrue(searcher("[]").search("x".repeat(500)) is PlaceSearchResult.Failure)
    // unreadable payload: honest failure, never a crash or empty success.
    val bad = searcher("not json at all {{{").search("vizag")
    assertTrue(bad is PlaceSearchResult.Failure)
    // transport exception: stated failure.
    val boom = NominatimPlaceSearcher(fetch = { throw java.io.IOException("socket died") })
    val r = boom.search("vizag")
    assertTrue(r is PlaceSearchResult.Failure)
    assertTrue((r as PlaceSearchResult.Failure).reason.contains("unreachable") ||
      (r).reason.contains("failed"))
  }

}
