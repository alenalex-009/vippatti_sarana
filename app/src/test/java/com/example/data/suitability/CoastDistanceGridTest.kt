package com.example.data.suitability

import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Contracts for the offline coast-distance grid (built by
 * tools/coast_distance_prepare.py from Natural Earth public-domain land
 * polygons). The grid turns "is this site near the sea?" from a guess into a
 * REAL derived input for the terrain suitability engine.
 */
class CoastDistanceGridTest {

  /** Header: lonMin latMin lonMax latMax (Float LE) + cols,rows (UShort LE) + row-major bytes. */
  private fun buildGrid(
    lonMin: Float, latMin: Float, lonMax: Float, latMax: Float,
    cols: Int, rows: Int, bytes: ByteArray
  ): ByteArray {
    val buf = ByteBuffer.allocate(20 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
    buf.putFloat(lonMin).putFloat(latMin).putFloat(lonMax).putFloat(latMax)
    buf.putShort(cols.toShort()).putShort(rows.toShort())
    buf.put(bytes)
    return buf.array()
  }

  @Test
  fun `a cell reads back its distance in km`() {
    // 2x2 grid over lon 78..80, lat 8..10. bytes row-major from latMin row.
    val g = buildGrid(78f, 8f, 80f, 10f, 2, 2, byteArrayOf(0, 50, 120, 200.toByte()))
    val grid = CoastDistanceGrid.load(ByteArrayInputStream(g))!!
    // bottom-left cell (lat 8, lon 78) = 0 km
    assertEquals(0, grid.distanceKm(GeoPoint(8.0, 78.0)))
    // bottom-right (lat 8, lon 80) = 50
    assertEquals(50, grid.distanceKm(GeoPoint(8.0, 80.0)))
    // top row: 120 / 200
    assertEquals(120, grid.distanceKm(GeoPoint(10.0, 78.0)))
    assertEquals(200, grid.distanceKm(GeoPoint(10.0, 80.0)))
  }

  @Test
  fun `nearest-neighbour lookup for a point inside the bbox`() {
    val g = buildGrid(78f, 8f, 80f, 10f, 2, 2, byteArrayOf(0, 50, 120, 200.toByte()))
    val grid = CoastDistanceGrid.load(ByteArrayInputStream(g))!!
    // lat 9.4 is nearer the second row (10) than the first (8)
    assertEquals(120, grid.distanceKm(GeoPoint(9.4, 78.05)))
  }

  @Test
  fun `points outside the grid coverage return null - never extrapolated`() {
    val g = buildGrid(78f, 8f, 80f, 10f, 2, 2, byteArrayOf(0, 50, 120, 200.toByte()))
    val grid = CoastDistanceGrid.load(ByteArrayInputStream(g))!!
    assertNull(grid.distanceKm(GeoPoint(7.99, 79.0)))
    assertNull(grid.distanceKm(GeoPoint(9.0, 80.01)))
  }

  @Test
  fun `a truncated or malformed stream fails to load instead of half-loading`() {
    val g = buildGrid(78f, 8f, 80f, 10f, 2, 2, byteArrayOf(0, 50, 120, 200.toByte()))
    assertNull(CoastDistanceGrid.load(ByteArrayInputStream(g.copyOf(g.size - 5))))
    assertNull(CoastDistanceGrid.load(ByteArrayInputStream(ByteArray(8))))
  }

  @Test
  fun `nearCoast answer is honest null when the point is not covered`() {
    val g = buildGrid(78f, 8f, 80f, 10f, 2, 2, byteArrayOf(0, 50, 120, 200.toByte()))
    val grid = CoastDistanceGrid.load(ByteArrayInputStream(g))!!
    assertEquals(true, grid.isNearCoast(GeoPoint(8.0, 78.0), maxKm = 5))
    assertEquals(false, grid.isNearCoast(GeoPoint(10.0, 80.0), maxKm = 5))
    assertNull(grid.isNearCoast(GeoPoint(12.0, 85.0), maxKm = 5))
  }

  @Test
  fun `bundled India asset covers the India bbox and matches known geography`() {
    // Read the ACTUAL shipped asset from the repo (not from assets/ at runtime)
    val f = java.io.File(
      System.getProperty("user.dir"),
      "src/main/assets/geo/coast_distance_india.bin"
    )
    assertTrue("asset missing: ${f.absolutePath}", f.isFile)
    val grid = CoastDistanceGrid.load(f.inputStream())!!
    // Chennai sits on the east coast; Madurai ~100+ km inland; Delhi far inland.
    assertTrue("Chennai should read as coastal", (grid.distanceKm(GeoPoint(13.06, 80.27)) ?: 99) <= 12)
    assertTrue("Madurai should read inland", (grid.distanceKm(GeoPoint(9.92, 78.12)) ?: 0) > 40)
    assertTrue("Delhi should read far from any coast", (grid.distanceKm(GeoPoint(28.6, 77.2)) ?: 0) >= 200)
    // Kochi west coast, and Andaman waters (Bay of Bengal at 15,85) are sea.
    assertTrue("Kochi should read as coastal", (grid.distanceKm(GeoPoint(9.93, 76.27)) ?: 99) <= 12)
    assertEquals(0, grid.distanceKm(GeoPoint(15.0, 85.0)))
  }
}
