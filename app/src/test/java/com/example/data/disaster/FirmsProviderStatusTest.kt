package com.example.data.disaster

import com.example.data.disaster.providers.FirmsFireProvider
import com.example.data.model.DataStatus
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 3 - FIRMS provider status honesty.
 *
 * The four states are distinct and machine-checkable:
 *   UNCONFIGURED (no MAP_KEY) / AUTHENTICATION_FAILED (key rejected) /
 *   FAILED (network, server, parse) / AVAILABLE (a valid response).
 *
 * An unconfigured optional source must never read as an error OR as live, and
 * no failure may be presented with invented fire data.
 */
class FirmsProviderStatusTest {

  private val now = 1_800_000_000_000L

  // ------------------------------------------------------- provider behaviour

  @Test
  fun `a blank MAP_KEY is UNCONFIGURED and makes no network call`() = runBlocking {
    val result = FirmsFireProvider(mapKeyProvider = { "" }).fetchIndiaEvents()
    assertTrue(result is ProviderResult.Failure)
    val failure = result as ProviderResult.Failure
    assertEquals(ProviderFailureKind.UNCONFIGURED, failure.kind)
    assertTrue(failure.reason.contains("FIRMS_MAP_KEY"))
  }

  @Test
  fun `the committed placeholder key is UNCONFIGURED, not a rejected credential`() = runBlocking {
    val result = FirmsFireProvider(
      mapKeyProvider = { FirmsFireProvider.FIRMS_PLACEHOLDER_KEY }
    ).fetchIndiaEvents()
    assertEquals(
      ProviderFailureKind.UNCONFIGURED,
      (result as ProviderResult.Failure).kind
    )
  }

  @Test
  fun `an invalid or expired MAP_KEY (HTTP 400) is AUTHENTICATION_FAILED, not a network error`() = runBlocking {
    // FIRMS answers a malformed/expired/revoked key with 400 "Invalid MAP_KEY."
    // (verified against the live API). Simulated here without a network:
    // the provider must classify it as a credential failure so the UI tells
    // the user to regenerate the key instead of blaming connectivity.
    val fake400Client = OkHttpClient.Builder()
      .addInterceptor(Interceptor { chain ->
        Response.Builder()
          .request(chain.request())
          .protocol(Protocol.HTTP_1_1)
          .code(400)
          .message("Bad Request")
          .body("Invalid MAP_KEY.\n".toResponseBody(null))
          .build()
      })
      .build()
    val result = FirmsFireProvider(
      mapKeyProvider = { "stale-or-revoked-key" },
      httpClient = fake400Client
    ).fetchIndiaEvents()
    assertTrue(result is ProviderResult.Failure)
    val failure = result as ProviderResult.Failure
    assertEquals(ProviderFailureKind.AUTHENTICATION_FAILED, failure.kind)
    assertTrue(failure.reason.contains("HTTP 400"))
    assertTrue(failure.reason.contains("expired", ignoreCase = true))
  }

  @Test
  fun `a failure never carries fabricated fire events`() = runBlocking {
    val result = FirmsFireProvider(mapKeyProvider = { "" }).fetchIndiaEvents()
    // Only Success may carry events; Failure has none by construction.
    assertFalse(result is ProviderResult.Success)
  }

  // ------------------------------------------------- provider status mapping

  private fun state(
    kind: ProviderFailureKind? = null,
    fromCache: Boolean = false,
    live: Boolean = false,
    message: String? = null
  ) = ProviderState(
    source = DisasterSource.NASA_FIRMS,
    eventCount = if (fromCache) 4 else 0,
    fetchedAtMillis = now,
    isFromCache = fromCache,
    isLive = live,
    statusMessage = message,
    failureKind = kind
  )

  @Test
  fun `unconfigured maps to NOT_CONFIGURED, never ERROR and never LIVE`() {
    val status = state(kind = ProviderFailureKind.UNCONFIGURED).dataStatus()
    assertEquals(DataStatus.NOT_CONFIGURED, status)
    assertTrue(status != DataStatus.ERROR)
    assertTrue(status != DataStatus.SUCCESS)
    assertEquals(
      "no live source configured in this build",
      state(kind = ProviderFailureKind.UNCONFIGURED).cacheNote
    )
  }

  @Test
  fun `a rejected credential maps to ERROR and keeps the provider reason`() {
    val rejected = state(
      kind = ProviderFailureKind.AUTHENTICATION_FAILED,
      message = "NASA FIRMS rejected the MAP_KEY (HTTP 403) — verify FIRMS_MAP_KEY."
    )
    assertEquals(DataStatus.ERROR, rejected.dataStatus())
    assertTrue(rejected.recordStamp().errorMessage!!.contains("HTTP 403"))
  }

  @Test
  fun `a runtime failure maps to ERROR`() {
    assertEquals(
      DataStatus.ERROR,
      state(kind = ProviderFailureKind.FAILED, message = "Fire detection feed error (IOException).")
        .dataStatus()
    )
  }

  @Test
  fun `a valid response is the only case that reads LIVE`() {
    val available = state(live = true)
    assertEquals(DataStatus.SUCCESS, available.dataStatus())
    assertNull(available.failureKind)
    assertEquals(null, available.cacheNote)
  }

  @Test
  fun `cached events never read LIVE, whatever the failure kind`() {
    ProviderFailureKind.entries.forEach { kind ->
      val cached = state(kind = kind, fromCache = true, live = true, message = "reason")
      assertEquals("$kind with cache", DataStatus.STALE, cached.dataStatus())
    }
    // An unconfigured source that still has a cached shard says so explicitly.
    assertEquals(
      "cached events, live source not configured",
      state(kind = ProviderFailureKind.UNCONFIGURED, fromCache = true).cacheNote
    )
  }

  @Test
  fun `the map status label separates failed, unavailable and unconfigured`() {
    val uiState = com.example.viewmodel.VippattiUiState(
      providerStates = listOf(
        state(live = true), // AVAILABLE
        state(kind = ProviderFailureKind.FAILED, message = "offline"),
        state(kind = ProviderFailureKind.UNCONFIGURED)
      ),
      disasterLastSyncMillis = now
    )
    val label = uiState.disasterDataStatusLabel
    assertTrue(label, label.contains("LIVE"))
    assertTrue(label, label.contains("1 FAILED"))
    assertTrue(label, label.contains("1 NOT CONFIGURED"))
    // Only the provider that actually answered may carry a LIVE status.
    assertEquals(
      1,
      uiState.providerStatuses.count { (_, status) -> status == DataStatus.SUCCESS }
    )
    assertEquals(
      listOf(DataStatus.NOT_CONFIGURED),
      uiState.providerStatuses
        .map { (stateRef, status) -> status }
        .filter { it == DataStatus.NOT_CONFIGURED }
    )
  }
}
