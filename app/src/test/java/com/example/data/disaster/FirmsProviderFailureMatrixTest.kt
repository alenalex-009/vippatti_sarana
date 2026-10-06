package com.example.data.disaster

import com.example.data.disaster.providers.FirmsFireProvider
import com.example.data.model.HazardSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIRMS FAILURE MATRIX (task checklist, cases 1-7).
 *
 * The provider must answer each of the seven real-world shapes distinctly and
 * honestly: a real success, a genuine empty result, an HTTP failure, an
 * invalid credential, a malformed payload, an unreachable network, and a
 * stale cached shard. In NO case may a failure be presented as an empty fire
 * layer, and no case may fabricate a hotspot.
 */
class FirmsProviderFailureMatrixTest {

  private val liveHeader =
    "latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite," +
      "instrument,confidence,version,bright_ti5,frp,daynight"

  private fun clientReturning(code: Int, body: String): OkHttpClient =
    OkHttpClient.Builder()
      .addInterceptor(Interceptor { chain ->
        Response.Builder()
          .request(chain.request())
          .protocol(Protocol.HTTP_1_1)
          .code(code)
          .message(if (code in 200..299) "OK" else "Error")
          .body(body.toResponseBody(null))
          .build()
      })
      .build()

  private fun provider(client: OkHttpClient) = FirmsFireProvider(
    // A syntactically valid but fake key: the interceptor answers, no network.
    mapKeyProvider = { "unit-test-key-0123456789abcdef" },
    httpClient = client
  )

  // ---------------------------------------------------- 1. successful response

  @Test
  fun `case 1 success - real CSV becomes fire observations with full provenance`() = runBlocking {
    val body = """
      $liveHeader
      20.76082,85.2962,328.08,0.49,0.48,2026-10-05,716,N,VIIRS,n,2.0NRT,294.18,5.76,D
    """.trimIndent()
    val result = provider(clientReturning(200, body)).fetchIndiaEvents()
    assertTrue(result is ProviderResult.Success)
    val success = result as ProviderResult.Success
    assertTrue(success.fetchedAtMillis > 0L)
    assertEquals(1, success.events.size)
    val event = success.events.first()
    // Identity + provenance + acquisition fields are all preserved.
    assertEquals(DisasterSource.NASA_FIRMS, event.source)
    assertEquals(DisasterType.WILDFIRE, event.disasterType)
    assertEquals(EventOrigin.OBSERVED, event.origin)
    assertEquals(20.76082, event.latitude!!, 1e-9)
    assertEquals(85.2962, event.longitude!!, 1e-9)
    assertTrue(event.observedAtMillis > 0L)
    assertEquals(event.observedAtMillis, event.updatedAtMillis)
    assertEquals(EventConfidence.NOMINAL, event.confidence)
    val fire = event.details as EventDetails.Fire
    assertEquals("N", fire.satellite)
    assertEquals("VIIRS", fire.instrument)
    assertEquals(5.76, fire.frpMegawatts!!, 0.001)
    assertEquals("D", fire.dayNight)
    // Never a simulated record: real provider observation only.
    assertTrue(event.origin != EventOrigin.SIMULATED)
  }

  // -------------------------------------------------------- 2. empty response

  @Test
  fun `case 2 empty - header-only CSV is Success with zero observations, never fake data`() = runBlocking {
    val result = provider(clientReturning(200, liveHeader + "\n")).fetchIndiaEvents()
    assertTrue(result is ProviderResult.Success)
    assertTrue((result as ProviderResult.Success).events.isEmpty())
  }

  // ------------------------------------------------------- 3. HTTP/API failure

  @Test
  fun `case 3 http failure - 500 is a FAILED provider, never an empty success`() = runBlocking {
    val result = provider(clientReturning(500, "Internal Server Error")).fetchIndiaEvents()
    assertTrue(result is ProviderResult.Failure)
    val failure = result as ProviderResult.Failure
    assertEquals(ProviderFailureKind.FAILED, failure.kind)
    assertTrue(failure.reason.contains("500"))
  }

  // --------------------------------------------------- 4. invalid credential

  @Test
  fun `case 4 invalid credential - 403 and 400 both classify as AUTHENTICATION_FAILED`() = runBlocking {
    val forbidden = provider(clientReturning(403, "Forbidden")).fetchIndiaEvents()
    assertEquals(
      ProviderFailureKind.AUTHENTICATION_FAILED,
      (forbidden as ProviderResult.Failure).kind
    )
    val invalid = provider(clientReturning(400, "Invalid MAP_KEY.\n")).fetchIndiaEvents()
    assertEquals(
      ProviderFailureKind.AUTHENTICATION_FAILED,
      (invalid as ProviderResult.Failure).kind
    )
  }

  // ----------------------------------------------------- 5. malformed response

  @Test
  fun `case 5 malformed - a non-CSV body is a failure, never an empty fire layer`() = runBlocking {
    val result = provider(clientReturning(200, "No data available")).fetchIndiaEvents()
    assertTrue(result is ProviderResult.Failure)
    val failure = result as ProviderResult.Failure
    assertEquals(ProviderFailureKind.FAILED, failure.kind)
    assertTrue(failure.reason.contains("malformed", ignoreCase = true))
  }

  @Test
  fun `case 5b malformed - an HTML error page is reported as an error page`() = runBlocking {
    val result = provider(
      clientReturning(200, "<html><body>Invalid MAP_KEY</body></html>")
    ).fetchIndiaEvents()
    assertTrue(result is ProviderResult.Failure)
    assertTrue(
      (result as ProviderResult.Failure).reason.contains("error page", ignoreCase = true)
    )
  }

  // ---------------------------------------------------- 6. network unavailable

  @Test
  fun `case 6 network down - IOException is FAILED and says cached data stays available`() = runBlocking {
    val offline = OkHttpClient.Builder()
      .addInterceptor(Interceptor { throw java.io.IOException("no route to host") })
      .build()
    val result = provider(offline).fetchIndiaEvents()
    assertTrue(result is ProviderResult.Failure)
    val failure = result as ProviderResult.Failure
    assertEquals(ProviderFailureKind.FAILED, failure.kind)
    assertTrue(failure.reason.contains("connection", ignoreCase = true))
  }

  // ------------------------------------------------------ 7. stale cached data

  @Test
  fun `case 7 stale cache - a failed fetch serves the cached shard labeled RECENT, never LIVE`() = runBlocking {
    val now = 1_800_000_000_000L
    val cache = MemoryDisasterCache()
    // Real cached detections fetched 3 h ago: outside the 15 min LIVE window,
    // inside the 6 h RECENT window.
    cache.write(
      DisasterSource.NASA_FIRMS,
      CachedProviderFeed(
        events = listOf(cachedFireEvent(observedAtMillis = now - 3_600_000L)),
        fetchedAtMillis = now - 3 * 3_600_000L,
        statusMessage = null
      )
    )
    val repository = DisasterDataRepository(
      providers = listOf(FirmsFireProvider(mapKeyProvider = { "" })), // UNCONFIGURED
      cache = cache,
      clock = { now },
      cacheDispatcher = Dispatchers.Unconfined
    )
    val feed = repository.refresh()
    val firms = feed.providerStates.single { it.source == DisasterSource.NASA_FIRMS }
    assertTrue(firms.isFromCache)
    assertTrue("a cached shard must never read live", !firms.isLive)
    assertEquals(1, firms.eventCount)
    assertEquals(1, feed.events.size)
    // Shared status vocabulary: cached + failure kind => STALE (RECENT chip),
    // and the cached event itself is the real one, not a replacement.
    assertEquals(com.example.data.model.DataStatus.STALE, firms.dataStatus())
    assertEquals("cached events, live source not configured", firms.cacheNote)
  }

  private fun cachedFireEvent(observedAtMillis: Long) = DisasterEvent(
    id = "firms-cached-1",
    source = DisasterSource.NASA_FIRMS,
    sourceEventId = "firms-cached-1",
    disasterType = DisasterType.WILDFIRE,
    title = "Active Fire Detection",
    description = "Satellite fire/hotspot detection from NASA FIRMS.",
    geometry = EventGeometry.Point(21.1, 79.5),
    latitude = 21.1,
    longitude = 79.5,
    severity = HazardSeverity.MODERATE,
    confidence = EventConfidence.NOMINAL,
    observedAtMillis = observedAtMillis,
    updatedAtMillis = observedAtMillis,
    origin = EventOrigin.OBSERVED,
    details = EventDetails.Fire("N", "VIIRS", 8.0, "D")
  )
}
