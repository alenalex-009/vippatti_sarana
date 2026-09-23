package com.example.data.suitability

import com.example.data.routing.GeoPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contracts for the live terrain probe: stencil fetch + rainfall fetch +
 * coast grid -> engine verdict. The HTTP layer is an injected fetcher so the
 * whole path is exercised offline against REAL payload shapes.
 */
class TerrainProbeServiceTest {

  private fun okElevation(v: String) = TerrainFetchResult.Ok(v)

  @Test
  fun `a successful probe combines elevation rainfall and coast into an assessed verdict`() = runTest {
    val service = TerrainProbeService(
      fetch = { url ->
        when {
          url.contains("/v1/elevation") -> okElevation("""{"elevation":[500.0, 506.0, 499.0, 500.5, 500.0]}""")
          url.contains("precipitation_24h") || url.contains("precipitation_sum") ->
            okElevation("""{"daily":{"precipitation_sum":[0.0, 180.0]}}""")
          else -> TerrainFetchResult.Failure("unexpected url: $url")
        }
      }
    )
    val result = service.probe(GeoPoint(10.0, 77.0), coastGrid = null)
    assertTrue("expected Success, got $result", result is TerrainProbeResult.Success)
    val verdict = (result as TerrainProbeResult.Success).verdict
    assertEquals(180.0, verdict.rainfallMm24h!!, 0.001)
    assertTrue(verdict.rainfallUsed)
    // 20 % slope under extreme rain -> escalated red zone (engine contract).
    assertTrue(verdict.slopePercent!! > 15.0)
  }

  @Test
  fun `a failed elevation fetch yields an unavailable probe, never a guessed verdict`() = runTest {
    val service = TerrainProbeService(fetch = { TerrainFetchResult.Failure("no route to host") })
    val result = service.probe(GeoPoint(10.0, 77.0), coastGrid = null)
    assertTrue(result is TerrainProbeResult.ElevationUnavailable)
  }

  @Test
  fun `a failed rainfall fetch still assesses with rain excluded, not zeroed`() = runTest {
    val service = TerrainProbeService(
      fetch = { url ->
        if (url.contains("/v1/elevation")) okElevation("""{"elevation":[500.0, 500.0, 500.0, 500.0, 500.0]}""")
        else TerrainFetchResult.Failure("http 500")
      }
    )
    val result = service.probe(GeoPoint(10.0, 77.0), coastGrid = null)
    assertTrue(result is TerrainProbeResult.Success)
    val v = (result as TerrainProbeResult.Success).verdict
    assertNull(v.rainfallMm24h)
    assertTrue(v.reasons.any { it.contains("Rainfall unknown", ignoreCase = true) })
  }

  @Test
  fun `the elevation request carries all five stencil points in one URL`() = runTest {
    var seen = ""
    val service = TerrainProbeService(
      fetch = { url ->
        if (url.contains("/v1/elevation")) {
          seen = url
          okElevation("""{"elevation":[1.0, 1.0, 1.0, 1.0, 1.0]}""")
        } else okElevation("""{"daily":{"precipitation_sum":[0.0, 0.0]}}""")
      }
    )
    service.probe(GeoPoint(10.0, 77.0), coastGrid = null)
    val lats = seen.substringAfter("latitude=").substringBefore("&longitude").split(",")
    assertEquals("one request must carry the whole stencil", 5, lats.size)
  }

  @Test
  fun `coast grid answers feed the nearCoast input when available`() = runTest {
    // 2x2 grid: bottom-left cell coastal (0 km), others far.
    val bytes = java.nio.ByteBuffer.allocate(20 + 4)
      .order(java.nio.ByteOrder.LITTLE_ENDIAN)
      .putFloat(76f).putFloat(9f).putFloat(78f).putFloat(11f)
      .putShort(2).putShort(2)
      .put(byteArrayOf(0, 120, 120, 120))
    val grid = CoastDistanceGrid.load(bytes.array().inputStream())!!
    val service = TerrainProbeService(
      fetch = { url ->
        if (url.contains("/v1/elevation")) okElevation("""{"elevation":[2.0, 2.0, 2.0, 2.0, 2.0]}""")
        else okElevation("""{"daily":{"precipitation_sum":[0.0, 1.0]}}""")
      }
    )
    // Chennai-ish point inside the coastal cell of this toy grid
    val resNear = service.probe(GeoPoint(9.0, 76.0), coastGrid = grid)
    val vNear = (resNear as TerrainProbeResult.Success).verdict
    assertTrue(vNear.reasons.any { it.contains("coast", ignoreCase = true) && !it.contains("unresolved", ignoreCase = true) })
    // A point in the far cell mentions coast-distance too (either way it is real input)
    val resFar = service.probe(GeoPoint(11.0, 78.0), coastGrid = grid)
    val vFar = (resFar as TerrainProbeResult.Success).verdict
    assertTrue(vFar.reasons.any { it.contains("coast", ignoreCase = true) })
  }
}
