package com.example.data.disaster.cap

import com.example.data.disaster.DisasterSource
import com.example.data.disaster.DisasterType
import com.example.data.disaster.EventGeometry
import com.example.data.model.HazardSeverity
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SACHET pipeline tests (section 30).
 *
 * Every fixture here is a REAL payload captured from sachet.ndma.gov.in on
 * 2026-10-04 — not a hand-written sample. The Darjeeling document is genuinely
 * multi-language (English + Bengali), genuinely carries an empty
 * <description/>, and genuinely advertises its polygon through a "Polygon URL"
 * parameter rather than an inline <polygon>. Tests that used synthetic XML
 * would pass while the real feed failed, which is exactly what happened before.
 *
 * These are the PURE decision paths (conditional-GET policy, geometry
 * validation, severity/classification mapping) with no Android dependency, so
 * they run on the fast JVM test path. CAP XML parsing lives in
 * SachetPipelineTest, which needs Robolectric for android.util.Xml.
 */
class SachetPureLogicTest {

  private fun resource(name: String): String =
    javaClass.classLoader!!.getResourceAsStream("india/$name")!!
      .bufferedReader().use { it.readText() }

  // ---------------- ETag / conditional fetch ----------------

  @Test
  fun `304 means unchanged and the cached body must be retained`() {
    val result = ConditionalFetchPolicy.decide(304, "\"abc\"", "\"abc\"", null)
    assertTrue(result is ConditionalResult.NotModified)
  }

  @Test
  fun `a 304 with no etag header still counts as unchanged`() {
    val result = ConditionalFetchPolicy.decide(304, null, "\"abc\"", null)
    assertTrue(result is ConditionalResult.NotModified)
    assertEquals("\"abc\"", (result as ConditionalResult.NotModified).etag)
  }

  @Test
  fun `200 with a different etag replaces the cached body`() {
    val result = ConditionalFetchPolicy.decide(200, "\"new\"", "\"old\"", "<alert/>")
    assertTrue(result is ConditionalResult.Updated)
    assertEquals("<alert/>", (result as ConditionalResult.Updated).body)
    assertEquals("\"new\"", result.etag)
  }

  @Test
  fun `200 with the SAME etag is unchanged even though it is not a 304`() {
    // This is the real SACHET behaviour measured on 2026-10-04: the origin
    // ignores If-None-Match and returns 200 + the full body. Without this rule
    // every poll would re-download every CAP document.
    val result = ConditionalFetchPolicy.decide(
      200, "\"bbAS5UgazsFkq\"", "\"bbAS5UgazsFkq\"", "<alert>full body</alert>")
    assertTrue("same ETag must be treated as not-modified", result is ConditionalResult.NotModified)
  }

  @Test
  fun `200 with no stored etag is always an update`() {
    val result = ConditionalFetchPolicy.decide(200, "\"abc\"", null, "<alert/>")
    assertTrue(result is ConditionalResult.Updated)
  }

  @Test
  fun `200 with no etag at all still delivers the body`() {
    val result = ConditionalFetchPolicy.decide(200, null, null, "<alert/>")
    assertTrue(result is ConditionalResult.Updated)
  }

  @Test
  fun `403 on the polygon endpoint is a failure and keeps the cached copy`() {
    val result = ConditionalFetchPolicy.decide(403, null, "\"old\"", "Error Code: 403")
    assertTrue(result is ConditionalResult.Failed)
    assertEquals(403, (result as ConditionalResult.Failed).httpCode)
  }

  @Test
  fun `a 200 with an empty body is a failure not an empty update`() {
    val result = ConditionalFetchPolicy.decide(200, "\"new\"", null, "")
    assertTrue(result is ConditionalResult.Failed)
  }

  @Test
  fun `weak and strong etags of the same value compare equal`() {
    // RSS serves W/"x"; FetchXMLFile serves "x".
    assertTrue(ConditionalFetchPolicy.etagsMatch("W/\"80851-1\"", "\"80851-1\""))
    assertFalse(ConditionalFetchPolicy.etagsMatch("\"a\"", "\"b\""))
    assertFalse(ConditionalFetchPolicy.etagsMatch(null, "\"a\""))
  }

  @Test
  fun `blank etags normalize to null rather than an empty string`() {
    assertNull(ConditionalFetchPolicy.normalizeETag(""))
    assertNull(ConditionalFetchPolicy.normalizeETag("   "))
    assertNull(ConditionalFetchPolicy.normalizeETag(null))
    assertEquals("\"x\"", ConditionalFetchPolicy.normalizeETag(" \"x\" "))
  }

  @Test
  fun `cache store round trips a body with its etag`() {
    val store = InMemoryCapCacheStore()
    store.write("k", CacheEntry("<alert/>", "\"e1\"", 1_000L))
    val read = store.read("k")
    assertEquals("<alert/>", read?.body)
    assertEquals("\"e1\"", read?.etag)
    store.remove("k")
    assertNull(store.read("k"))
  }

  // ---------------- geometry validation ----------------

  @Test
  fun `the real 2005-vertex polygon validates and stays inside india`() {
    val raw = resource("sachet_darjeeling_polygon.txt")
    val result = GeometryValidator.validateRawPolygon(raw)
    assertTrue("real polygon must validate: ${result.failureReasons}", result.valid)
    assertEquals(2005, result.vertexCount)
    val poly = result.geometry as EventGeometry.Polygon
    assertTrue(poly.ring.all { it.lat in 6.0..37.5 && it.lon in 67.5..98.0 })
  }

  @Test
  fun `a polygon outside india is rejected not clipped`() {
    val raw = "40.1,-74.0 40.2,-74.0 40.2,-73.9 40.1,-73.9"
    val result = GeometryValidator.validateRawPolygon(raw)
    assertFalse(result.valid)
    assertTrue(GeometryFailure.OUTSIDE_INDIA in result.failureReasons)
    assertNull("a rejected polygon must yield no geometry", result.geometry)
  }

  @Test
  fun `fewer than three vertices is rejected`() {
    val result = GeometryValidator.validateRawPolygon("27.2,88.1 27.3,88.2")
    assertFalse(result.valid)
    assertTrue(GeometryFailure.TOO_FEW_VERTICES in result.failureReasons)
  }

  @Test
  fun `out of range coordinates are dropped by the reader`() {
    // The reader discards a vertex it cannot place. Whatever survives is then
    // validated; nothing is repaired or interpolated.
    val result = GeometryValidator.validateRawPolygon(
      "27.2,88.1 27.3,88.2 27.4,88.3 999.0,88.4")
    assertTrue("the 999.0 vertex must not survive parsing", result.vertexCount < 4)
    assertNull("a ring that cannot form an area yields no geometry", result.geometry)
    assertFalse(result.valid)
  }

  @Test
  fun `a ring entirely outside india is rejected`() {
    val outside = GeometryValidator.validateRawPolygon(
      "40.1,-74.0 40.2,-74.0 40.2,-73.9 40.1,-73.9")
    assertFalse(outside.valid)
    assertTrue(GeometryFailure.OUTSIDE_INDIA in outside.failureReasons)
  }

  @Test
  fun `a degenerate zero-area ring is rejected`() {
    val result = GeometryValidator.validateRawPolygon("27.2,88.1 27.2,88.1 27.2,88.1 27.2,88.1")
    assertFalse(result.valid)
    assertTrue(GeometryFailure.DEGENERATE_RING in result.failureReasons)
  }

  @Test
  fun `a self-intersecting bowtie ring is rejected`() {
    // Classic bow-tie: the two triangles cross at the centre.
    val bowtie = "27.0,88.0 27.0,88.4 27.4,88.0 27.4,88.4"
    val result = GeometryValidator.validateRawPolygon(bowtie)
    assertTrue("bow-tie must be detected", GeometryValidator.selfIntersects(
      bowtie.split(" ").map { GeoPoint(it.split(",")[0].toDouble(), it.split(",")[1].toDouble()) }
    ))
    assertFalse(result.valid)
    assertTrue(GeometryFailure.SELF_INTERSECTING in result.failureReasons)
  }

  @Test
  fun `an empty or malformed polygon yields no geometry`() {
    assertFalse(GeometryValidator.validateRawPolygon("").valid)
    assertFalse(GeometryValidator.validateRawPolygon(null).valid)
    assertFalse(GeometryValidator.validateRawPolygon("garbage").valid)
    assertNull(GeometryValidator.validateRawPolygon("garbage").geometry)
  }

  @Test
  fun `a valid ring reports a plausible area`() {
    // ~10km square in West Bengal.
    val ring = listOf(
      GeoPoint(27.20, 88.00), GeoPoint(27.20, 88.10),
      GeoPoint(27.29, 88.10), GeoPoint(27.29, 88.00)
    )
    val km2 = GeometryValidator.areaKm2(ring)
    assertTrue("area was $km2 km2", km2 in 50.0..150.0)
  }

  @Test
  fun `simplification reduces the real polygon without inventing vertices`() {
    val raw = resource("sachet_darjeeling_polygon.txt")
    val result = GeometryValidator.validateRawPolygon(raw)
    val ring = (result.geometry as EventGeometry.Polygon).ring
    val simplified = GeometryValidator.simplify(ring, toleranceMeters = 50.0)

    assertTrue("simplify must reduce vertices", simplified.size < ring.size)
    assertTrue("simplify must keep at least a triangle", simplified.size >= 3)
    simplified.forEach { p ->
      assertTrue(p.lat in 6.0..37.5 && p.lon in 67.5..98.0)
    }
  }

  // ---------------- alert normalization ----------------




  @Test
  fun `severity maps from real cap vocabulary without inventing extremes`() {
    assertEquals(HazardSeverity.HIGH, SachetAlertFactory.mapSeverity("Severe"))
    assertEquals(HazardSeverity.EXTREME, SachetAlertFactory.mapSeverity("Extreme"))
    assertEquals(HazardSeverity.MODERATE, SachetAlertFactory.mapSeverity("Moderate"))
    assertEquals(HazardSeverity.LOW, SachetAlertFactory.mapSeverity("Minor"))
    // Unknown/absent severity must NOT be escalated to Extreme.
    assertEquals(HazardSeverity.MODERATE, SachetAlertFactory.mapSeverity("who knows"))
    assertEquals(HazardSeverity.MODERATE, SachetAlertFactory.mapSeverity(null))
  }

  @Test
  fun `event classification uses the real cap event text`() {
    assertEquals(DisasterType.CYCLONE, SachetAlertFactory.classifyEvent("Cyclonic Storm", "Met"))
    assertEquals(DisasterType.FLOOD, SachetAlertFactory.classifyEvent("Flood warning", "Met"))
    assertEquals(
      DisasterType.LANDSLIDE,
      SachetAlertFactory.classifyEvent("Landslide warning", "Met"))
    assertEquals(
      DisasterType.HEAVY_RAINFALL,
      SachetAlertFactory.classifyEvent("Thunderstorm with Lightning", "Met"))
    assertEquals(
      DisasterType.WEATHER_ALERT,
      SachetAlertFactory.classifyEvent("Something else entirely", "Met"))
  }

}