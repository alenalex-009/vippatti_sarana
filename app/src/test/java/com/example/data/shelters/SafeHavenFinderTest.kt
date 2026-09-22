package com.example.data.shelters

import com.example.data.model.GeoMath
import com.example.data.routing.GeoPoint
import com.example.data.suitability.CoastDistanceGrid
import com.example.data.suitability.ElevationGrid
import com.example.data.suitability.TerrainProbeResult
import com.example.data.suitability.TerrainSuitabilityEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contracts for the terrain-derived SAFE HAVEN finder: when no registered
 * shelter exists (the honest reality for most of India in this build), a
 * disaster must still surface a place to go — the nearest terrain location
 * the habitability engine actually rates SAFE. Probing is injected, so the
 * whole ring-search rule set is offline-testable.
 */
class SafeHavenFinderTest {

  private val origin = GeoPoint(10.0, 76.5)

  /** Wide rings so the 5 km/20 km search actually reaches candidates. */
  private val rings = listOf(1_000.0, 2_000.0, 5_000.0, 20_000.0)

  private fun verdictFor(safe: Boolean) = TerrainSuitabilityEngine.evaluate(
    elevation = if (safe) ElevationGrid(300.0, 300.0, 300.0, 300.0, 300.0, 30.0)
    else ElevationGrid(300.0, 330.0, 300.0, 300.0, 300.0, 30.0),   // 100 % slope -> RED
    rainfallMm24h = null,
    nearCoast = null
  )

  private fun finder(probeSafe: (GeoPoint) -> Boolean) = SafeHavenFinder(probe = { p, _ ->
    TerrainProbeResult.Success(verdictFor(probeSafe(p)), coastKnown = false)
  })

  @Test
  fun `a safe haven is found when at least one candidate rates SAFE`() = runTest {
    // Everything east of the origin is safe, everything west is steep.
    val haven = finder { p -> p.lon > origin.lon }
      .find(origin, coastGrid = null, maxCandidates = 64, rings = rings)
    assertNotNull(haven)
    haven!!
    assertTrue("haven must lie east of origin", haven.point.lon > origin.lon)
    assertTrue(haven.verdict.band == com.example.data.suitability.SuitabilityBand.SAFE)
    assertTrue(haven.distanceMeters > 0)
  }

  @Test
  fun `no safe haven is invented when every candidate fails`() = runTest {
    val haven = finder { false }.find(origin, coastGrid = null, maxCandidates = 64, rings = rings)
    assertNull(haven)
  }

  @Test
  fun `probe failure on a candidate simply excludes it`() = runTest {
    var calls = 0
    val finder = SafeHavenFinder(probe = { _, _ ->
      calls++
      TerrainProbeResult.ElevationUnavailable("no data")
    })
    assertNull(finder.find(origin, coastGrid = null, maxCandidates = 8, rings = rings))
    assertTrue("every candidate was still attempted", calls >= 8)
  }

  @Test
  fun `the closest safe ring wins, not the first probed point`() = runTest {
    // Only >= 1.5 km candidates are safe; the 1 km ring must be skipped over.
    val haven = finder { p -> GeoMath.distanceMeters(origin, p) >= 1_500.0 }
      .find(origin, coastGrid = null, maxCandidates = 64, rings = rings)!!
    assertTrue("chosen haven was ${haven.distanceMeters} m", haven.distanceMeters >= 1_500.0)
    assertTrue("nearest safe ring should win", haven.distanceMeters <= 2_600.0)
  }

  @Test
  fun `haven carries the engine disclaimer and an honest headline`() = runTest {
    val haven = finder { true }.find(origin, coastGrid = null, maxCandidates = 8, rings = rings)!!
    assertTrue(haven.verdict.disclaimer.contains("not an official", ignoreCase = true))
    assertTrue(haven.headline.contains("open terrain", ignoreCase = true))
  }
}
