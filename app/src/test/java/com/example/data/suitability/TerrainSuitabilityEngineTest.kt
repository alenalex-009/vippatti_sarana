package com.example.data.suitability

import com.example.data.model.GeoMath
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contracts for the terrain habitability / red-zone engine (SIH 26191:
 * "dynamically identify and update multi-hazard Red Zones"). Every rule here
 * is a CONFIGURED, documented threshold — never an official standard — and no
 * verdict may be produced from missing data.
 */
class TerrainSuitabilityEngineTest {

  private fun grid(c: Double, n: Double = c, s: Double = c, e: Double = c, w: Double = c) =
    ElevationGrid(center = c, north = n, south = s, east = e, west = w, stepMeters = 30.0)

  // ---- slope math ----------------------------------------------------------

  @Test
  fun `flat terrain has near-zero slope`() {
    val slope = TerrainSuitabilityEngine.maxSlopePercent(grid(600.0))
    assertEquals(0.0, slope, 0.001)
  }

  @Test
  fun `slope is rise over the 30 m step`() {
    // 1.5 m rise across 30 m = 5 %
    val slope = TerrainSuitabilityEngine.maxSlopePercent(grid(600.0, n = 601.5))
    assertEquals(5.0, slope, 0.001)
  }

  @Test
  fun `steepest neighbour wins`() {
    val slope = TerrainSuitabilityEngine.maxSlopePercent(grid(600.0, n = 600.3, w = 602.4))
    assertEquals(8.0, slope, 0.001)
  }

  // ---- verdict bands -------------------------------------------------------

  @Test
  fun `gentle inland slope scores SAFE`() {
    val v = TerrainSuitabilityEngine.evaluate(
      elevation = grid(650.0, n = 650.2, s = 649.9, e = 650.1, w = 650.0),
      rainfallMm24h = null,
      nearCoast = false
    )
    assertEquals(SuitabilityStatus.ASSESSED, v.status)
    assertTrue("score was ${v.score}: ${v.reasons}", v.score >= 70)
    assertEquals(SuitabilityBand.SAFE, v.band)
  }

  @Test
  fun `cliff terrain is a RED ZONE regardless of everything else`() {
    // 20 m over 30 m = ~67 % slope — permanently unsuitable for habitation.
    val v = TerrainSuitabilityEngine.evaluate(
      elevation = grid(900.0, n = 920.0, s = 898.0, e = 901.0, w = 899.0),
      rainfallMm24h = null,
      nearCoast = false
    )
    assertEquals(SuitabilityBand.RED_ZONE, v.band)
    assertTrue(v.hardRuleHit != null)
    assertTrue(v.isUnsuitableForPermanentHabitation)
  }

  @Test
  fun `steep slope alone is not a hard-rule red zone but is high risk`() {
    // 9 m / 30 m = 30 % — inside the 25..35 high-risk band, below the 35 hard line.
    val v = TerrainSuitabilityEngine.evaluate(
      elevation = grid(700.0, n = 709.0, s = 699.5, e = 700.5, w = 700.0),
      rainfallMm24h = null,
      nearCoast = false
    )
    assertTrue(v.hardRuleHit == null)
    assertTrue(
      "expected HIGH_RISK or worse, got ${v.band} (score ${v.score})",
      v.band == SuitabilityBand.HIGH_RISK || v.band == SuitabilityBand.RED_ZONE
    )
  }

  @Test
  fun `low coastal elevation is flooded out of SAFE`() {
    val v = TerrainSuitabilityEngine.evaluate(
      elevation = grid(2.0),          // 2 m amsl, flat
      rainfallMm24h = 12.0,
      nearCoast = true
    )
    assertTrue("coastal 2 m flats must not be SAFE, got ${v.band}", v.band != SuitabilityBand.SAFE)
  }

  // ---- live rainfall trigger (the "dynamic" part) --------------------------

  @Test
  fun `extreme rain on moderate slope can escalate a CAUTION hillside to red zone`() {
    // 6 m / 30 m = 20 % slope: clearly not SAFE on its own.
    val dry = TerrainSuitabilityEngine.evaluate(
      elevation = grid(800.0, n = 806.0, s = 799.0, e = 800.5, w = 800.0),
      rainfallMm24h = 0.0,
      nearCoast = false
    )
    val soaked = TerrainSuitabilityEngine.evaluate(
      elevation = grid(800.0, n = 806.0, s = 799.0, e = 800.5, w = 800.0),
      rainfallMm24h = 180.0,          // extreme 24 h rain
      nearCoast = false
    )
    assertTrue("rain must strictly lower the score", soaked.score < dry.score)
    assertEquals(SuitabilityBand.RED_ZONE, soaked.band)
  }

  @Test
  fun `rain trigger never upgrades a SAFE flat inland site into a red zone alone`() {
    val v = TerrainSuitabilityEngine.evaluate(
      elevation = grid(300.0),
      rainfallMm24h = 200.0,
      nearCoast = false
    )
    // Rain alone on 0 % slope 300 m inland cannot invent a landslide red zone.
    assertTrue(v.hardRuleHit == null)
  }

  // ---- honesty: missing data is never a fake verdict ------------------------

  @Test
  fun `missing elevation yields INSUFFICIENT_DATA not a fabricated score`() {
    val v = TerrainSuitabilityEngine.evaluate(elevation = null, rainfallMm24h = 50.0, nearCoast = null)
    assertEquals(SuitabilityStatus.INSUFFICIENT_DATA, v.status)
    assertEquals(-1, v.score)
    assertTrue(v.reasons.any { it.contains("elevation", ignoreCase = true) })
  }

  @Test
  fun `unknown coast proximity is excluded from the weighting not assumed`() {
    val known = TerrainSuitabilityEngine.evaluate(grid(4.0), rainfallMm24h = 0.0, nearCoast = true)
    val unknown = TerrainSuitabilityEngine.evaluate(grid(4.0), rainfallMm24h = 0.0, nearCoast = null)
    // Both are assessed; the unknown one must not pretend coastal flooding exists.
    assertEquals(SuitabilityStatus.ASSESSED, unknown.status)
    assertTrue(unknown.score > known.score)
    assertTrue(unknown.reasons.any { it.contains("coast", ignoreCase = true) })
  }

  // ---- provenance -----------------------------------------------------------

  @Test
  fun `every assessed verdict carries derived source provenance and threshold disclaimer`() {
    val v = TerrainSuitabilityEngine.evaluate(grid(650.0), null, false)
    assertEquals(SuitabilityStatus.ASSESSED, v.status)
    assertTrue(v.source.contains("SRTM", ignoreCase = true))
    assertTrue(v.source.contains("Open-Meteo", ignoreCase = true) || v.rainfallUsed == null)
    assertTrue(v.disclaimer.contains("not an official", ignoreCase = true))
  }

  // ---- grid builder geometry -------------------------------------------------

  @Test
  fun `sample points sit about 30 m from the centre in the four cardinal steps`() {
    val pts = TerrainSuitabilityEngine.samplePoints(GeoPoint(10.0, 77.0), stepMeters = 30.0)
    assertEquals(5, pts.size)
    pts.drop(1).forEach { p ->
      val d = GeoMath.distanceMeters(pts.first(), p)
      assertTrue("offset was $d m", d in 25.0..35.0)
    }
  }

  // ---- grid parsing from the wire format -------------------------------------

  // Schema pinned from the LIVE service (verified 2026-09-22):
  //   https://api.open-meteo.com/v1/elevation?latitude=..&longitude=..
  //   -> {"elevation":[640.0, 640.0, 627.0]}

  @Test
  fun `elevation grid is parsed from the five value order centre N S E W`() {
    val json = """{"elevation":[100.0, 103.0, 97.0, 101.0, 99.0]}"""
    val g = ElevationGridJson.parse(json)!!
    assertEquals(100.0, g.center, 0.001)
    assertEquals(103.0, g.north, 0.001)
    assertEquals(97.0, g.south, 0.001)
    assertEquals(101.0, g.east, 0.001)
    assertEquals(99.0, g.west, 0.001)
  }

  @Test
  fun `a malformed or error payload parses to null instead of guessing`() {
    assertFalse(ElevationGridJson.parse("""{"reason":"Not Found","error":true}""") != null)
    assertFalse(ElevationGridJson.parse("not json at all") != null)
    assertFalse(ElevationGridJson.parse("""{"elevation":[]}""") != null)
  }

  @Test
  fun `null elevations inside the payload yield null not zero`() {
    val json = """{"elevation":[null, 103.0, 97.0, 101.0, 99.0]}"""
    assertEquals(null, ElevationGridJson.parse(json))
  }

  @Test
  fun `the request url lists the five stencil coordinates in order`() {
    val pts = TerrainSuitabilityEngine.samplePoints(GeoPoint(10.0, 77.0), stepMeters = 30.0)
    val url = ElevationGridJson.buildElevationUrl(pts)
    assertTrue(url.startsWith("https://api.open-meteo.com/v1/elevation?"))
    val lats = url.substringAfter("latitude=").substringBefore("&longitude") .split(",")
    val lons = url.substringAfter("longitude=").split(",")
    assertEquals(5, lats.size)
    assertEquals(lats[0], "10.0")
    assertTrue(lats[1].toDouble() > lats[0].toDouble())   // north of centre
    assertTrue(lats[2].toDouble() < lats[0].toDouble())   // south of centre
    assertTrue(lons[3].toDouble() > lons[0].toDouble())   // east of centre
    assertTrue(lons[4].toDouble() < lons[0].toDouble())   // west of centre
  }
}
