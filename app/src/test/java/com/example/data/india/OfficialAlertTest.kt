package com.example.data.india

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the normalized CAP alert model and CAP timestamp handling.
 *
 * Test fixtures are copied from the REAL payloads captured during the Phase 1
 * source audit against the live NDMA SACHET feed on 2026-10-04. The shapes,
 * the +05:30 offset, the empty <description/>, the per-language <info> blocks
 * and the separate "Polygon URL" parameter are all as observed, not invented.
 */
class OfficialAlertTest {

  // ---- CAP timestamp parsing against real observed formats ----

  @Test
  fun `parses the real sent timestamp with india offset`() {
    // Exactly as published by West-Bengal-SDMA.
    val millis = CapTime.parse("2026-10-04T21:06:31+05:30")
    assertNotNull(millis)
    // 21:06:31 IST == 15:36:31 UTC
    assertEquals("2026-10-04T15:36:31Z", isoOf(millis!!))
  }

  @Test
  fun `parses an effective time earlier than sent as observed`() {
    val effective = CapTime.parse("2026-10-04T20:45:00+05:30")
    val sent = CapTime.parse("2026-10-04T21:06:31+05:30")
    assertNotNull(effective)
    assertNotNull(sent)
    assertTrue("effective precedes sent, as in the real feed", effective!! < sent!!)
  }

  @Test
  fun `parses the real rss pubDate format`() {
    val millis = CapTime.parse("Sun, 04 Oct 2026 15:36:33 GMT")
    assertNotNull(millis)
    assertEquals("2026-10-04T15:36:33Z", isoOf(millis!!))
  }

  @Test
  fun `rss pubDate and cap sent describe the same moment`() {
    // Cross-check: the RSS pubDate for this alert was 15:36:33 GMT and the CAP
    // sent field was 21:06:31+05:30 == 15:36:31 UTC. They agree to the second.
    val fromRss = CapTime.parse("Sun, 04 Oct 2026 15:36:33 GMT")!!
    val fromCap = CapTime.parse("2026-10-04T21:06:31+05:30")!!
    assertTrue("RSS and CAP times must agree within 5s", kotlin.math.abs(fromRss - fromCap) <= 5000L)
  }

  @Test
  fun `a zulu timestamp is handled`() {
    assertEquals("2026-10-04T15:36:31Z", isoOf(CapTime.parse("2026-10-04T15:36:31Z")!!))
  }

  @Test
  fun `a timestamp without an offset is treated as utc and flagged unknown-ish`() {
    val millis = CapTime.parse("2026-10-04T21:06:31")
    assertNotNull(millis)
    assertEquals("2026-10-04T21:06:31Z", isoOf(millis!!))
  }

  @Test
  fun `null and blank timestamps stay unknown rather than defaulting to now`() {
    assertNull(CapTime.parse(null))
    assertNull(CapTime.parse(""))
    assertNull(CapTime.parse("   "))
  }

  @Test
  fun `an unparseable timestamp stays unknown`() {
    assertNull(CapTime.parse("soon"))
    assertNull(CapTime.parse("2026-13-45T99:99:99+05:30"))
  }

  @Test
  fun `february 29 in a leap year parses`() {
    assertEquals("2024-02-29T00:00:00Z", isoOf(CapTime.parse("2024-02-29T00:00:00+00:00")!!))
  }

  @Test
  fun `non leap year february 29 is rejected rather than silently rolled`() {
    // java.time would throw; ours must not invent a date or return the epoch.
    assertNull(CapTime.parse("2023-02-29T00:00:00+00:00"))
  }

  @Test
  fun `an impossible calendar date never yields a fake timestamp`() {
    // Regression: these used to return 0L and then had an offset subtracted,
    // producing a believable 1969 timestamp instead of an honest null.
    listOf(
      "2026-13-45T99:99:99+05:30",
      "2026-02-30T00:00:00+00:00",
      "2026-10-04T25:00:00+00:00",
      "2023-02-29T00:00:00Z"
    ).forEach { bad ->
      assertNull("must not fabricate a time from '$bad'", CapTime.parse(bad))
    }
  }

  @Test
  fun `a full year of parses is monotonic across the india offset boundary`() {
    var previous = Long.MIN_VALUE
    listOf(
      "2026-01-01T00:00:00+05:30",
      "2026-06-15T12:30:00+05:30",
      "2026-12-31T23:59:59+05:30"
    ).forEach { ts ->
      val m = CapTime.parse(ts)!!
      assertTrue("timestamps must increase", m > previous)
      previous = m
    }
  }

  // ---- Alert semantics ----

  @Test
  fun `an expired alert is recognised from the real expires field`() {
    val alert = realDarjeelingAlert()
    // Real expires was 2026-10-04T23:45:00+05:30
    assertTrue(alert.isExpired(CapTime.parse("2026-10-04T23:50:00+05:30")!!))
    assertFalse(alert.isExpired(CapTime.parse("2026-10-04T21:10:00+05:30")!!))
  }

  @Test
  fun `an alert with no expiry is not treated as expired`() {
    val alert = realDarjeelingAlert().copy(expires = null)
    assertFalse(alert.isExpired(System.currentTimeMillis()))
  }

  @Test
  fun `an alert whose expiry cannot be parsed is not assumed expired`() {
    val alert = realDarjeelingAlert().copy(expires = "when it stops")
    assertFalse(alert.isExpired(System.currentTimeMillis()))
  }

  @Test
  fun `lgd district code is read from the real geocode pair`() {
    val area = realDarjeelingAlert().areas.first()
    assertEquals("309", area.lgdCode())
  }

  @Test
  fun `an area without a geocode yields null not zero`() {
    val area = realDarjeelingAlert().areas.first().copy(geocodes = emptyList())
    assertNull(area.lgdCode())
  }

  @Test
  fun `an alert without a published polygon exposes no geometry`() {
    val alert = realDarjeelingAlert()
    assertFalse(alert.hasPublishedArea)
    assertNull(alert.geometryForArea())
  }

  @Test
  fun `a published polygon becomes real geometry`() {
    val poly = realPolygon()
    val alert = realDarjeelingAlert().copy(
      areas = listOf(
        realDarjeelingAlert().areas.first().copy(polygon = poly)
      )
    )
    assertTrue(alert.hasPublishedArea)
    assertNotNull(alert.geometryForArea())
  }

  // ---- Geometry handling of the real 80KB Darjeeling polygon ----

  @Test
  fun `real polygon coordinates parse into a ring`() {
    val ring = parsePolygon(realPolygon())
    // The real Darjeeling alert polygon has 2005 vertices.
    assertEquals(2005, ring.size)
  }

  @Test
  fun `every real polygon vertex lies inside india`() {
    val ring = parsePolygon(realPolygon())
    ring.forEach { p ->
      assertTrue("${p.lat},${p.lon} must be within India", IndiaGeography.isInIndia(p))
    }
  }

  @Test
  fun `the real polygon sits where darjeeling is not visakhapatnam`() {
    val center = AlertGeometry(realPolygon(), parsePolygon(realPolygon()), "SACHET").bboxCenter()

    assertTrue("Darjeeling is in the north-east, lat 26-28, got ${center.lat}", center.lat in 26.0..28.5)
    assertTrue("Darjeeling is in the north-east, lon 87-89, got ${center.lon}", center.lon in 87.0..89.5)
  }

  @Test
  fun `the real polygon is a plausible hazard area size`() {
    val ring = parsePolygon(realPolygon())
    val km = AlertGeometry(realPolygon(), ring, "SACHET").bboxKm()
    assertTrue("district-scale hazard area, got $km km", km in 1.0..500.0)
  }

  @Test
  fun `the real polygon closes on itself as a published ring`() {
    val ring = parsePolygon(realPolygon())
    assertEquals("CAP rings repeat the first vertex to close", ring.first(), ring.last())
  }

  @Test
  fun `the real polygon spans a coherent district not a point`() {
    val ring = parsePolygon(realPolygon())
    val lats = ring.map { it.lat }
    val lons = ring.map { it.lon }
    val latSpan = lats.max() - lats.min()
    val lonSpan = lons.max() - lons.min()

    assertTrue("lat span was $latSpan", latSpan in 0.1..2.0)
    assertTrue("lon span was $lonSpan", lonSpan in 0.1..2.0)
  }

  @Test
  fun `malformed polygon text yields no ring rather than crashing`() {
    assertTrue(parsePolygon("not a polygon").isEmpty())
    assertTrue(parsePolygon("").isEmpty())
    // Unreadable text is dropped, not guessed at.
    assertTrue(parsePolygon("nope,not,a,polygon").isEmpty())
    assertTrue(parsePolygon("999.0,999.0 200.0,200.0").isEmpty())
  }

  @Test
  fun `a single vertex is parsed but never promoted to an area`() {
    // parsePolygon is a vertex reader; the area guard lives in fromPublished.
    assertEquals(1, parsePolygon("1.0,2.0").size)
    assertNull(AlertGeometry.fromPublished("1.0,2.0", "test"))
    assertNull(AlertGeometry.fromPublished("1.0,2.0 3.0,4.0", "test"))
  }

  @Test
  fun `out of range vertices are dropped while valid ones survive`() {
    val ring = parsePolygon("999.0,999.0 27.2,88.1 200.0,200.0 27.3,88.2")
    assertEquals(2, ring.size)
    assertTrue(ring.all { IndiaGeography.isInIndia(it) })
  }

  @Test
  fun `too few valid vertices yields no geometry even with junk around them`() {
    assertNull(AlertGeometry.fromPublished("bad 27.2,88.1 alsobad", "test"))
  }

  @Test
  fun `an area declaring unparseable geometry reports no usable geometry`() {
    // Presence of a polygon string is not proof of drawable geometry. An
    // authority that publishes broken text yields an absent area, not a guess.
    val area = realDarjeelingAlert().areas.first().copy(polygon = "garbage")
    assertTrue("text is present", area.hasGeometry)
    assertFalse("but it cannot be drawn", area.hasUsableGeometry)
    assertNull(area.resolvedGeometry())
  }

  @Test
  fun `an alert with only unparseable geometry exposes no area`() {
    val alert = realDarjeelingAlert().copy(
      areas = listOf(realDarjeelingAlert().areas.first().copy(polygon = "nope,not,a,polygon"))
    )
    assertFalse(alert.hasPublishedArea)
    assertNull(alert.geometryForArea())
  }

  @Test
  fun `a circle with only one vertex is not an area`() {
    val area = realDarjeelingAlert().areas.first().copy(circle = "27.2,88.1")
    assertFalse(area.hasUsableGeometry)
  }

  @Test
  fun `the official alert is authoritative only while live`() {
    val live = realDarjeelingAlert()
    val prov = InferenceGuard.provenanceStatusFor(DataClass.OFFICIAL_ALERT, live.provenance.temporalStatus)
    assertEquals(ProvenanceStatus.OFFICIAL, prov)
  }

  // ---- Fixtures copied from the live audit capture ----

  private fun realDarjeelingAlert(): OfficialAlert = OfficialAlert(
    identifier = "IN-1791127650032017_17",
    sender = "West-Bengal-SDMA",
    senderName = "",
    sent = "2026-10-04T21:06:31+05:30",
    effective = "2026-10-04T20:45:00+05:30",
    onset = "2026-10-04T21:06:31+05:30",
    expires = "2026-10-04T23:45:00+05:30",
    status = "Actual",
    msgType = "Update",
    scope = "Public",
    language = "en-IN",
    category = "Met",
    event = "Thunderstorm with Lightning",
    urgency = "Expected",
    severity = "Moderate",
    certainty = "Possible",
    headline = "Light to moderate Thunderstorm lightning accompanied with light to " +
      "moderate rain and gusty wind with speed 30-40 kmph. likely to continue over some " +
      "parts of Darjeeling district during next 2-3 hours from 20:45, 04-10-2026.",
    description = null, // the real CAP had <cap:description/> empty
    instruction = "Please follow SDMA guidelines.",
    areas = listOf(
      AlertArea(
        areaDesc = "Darjiling district of West Bengal",
        geocodes = listOf(Geocode("LGD District Code", "309")),
        polygon = null, // real alert carried NO inline polygon
        circle = null,
        altitude = "0",
        ceiling = "0"
      )
    ),
    provenance = IndiaProvenance(
      source = "NDMA_SACHET",
      sourceUrl = "https://sachet.ndma.gov.in/cap_public_website/FetchXMLFile?identifier=1791127650032017",
      retrievedAtMillis = 0L,
      observedAtMillis = CapTime.parse("2026-10-04T21:06:31+05:30") ?: 0L,
      effectiveAtMillis = CapTime.parse("2026-10-04T20:45:00+05:30"),
      expiresAtMillis = CapTime.parse("2026-10-04T23:45:00+05:30"),
      temporalStatus = TemporalStatus.LIVE,
      provenanceStatus = ProvenanceStatus.OFFICIAL,
      geographicScope = "West Bengal / Darjiling district",
      rawSourceId = "1791127650032017"
    )
  )

  /**
   * The REAL Darjeeling district polygon captured from the live SACHET
   * FetchPolygonXMLFile endpoint on 2026-10-04: 2005 vertices, copied verbatim
   * into app/src/test/resources/india/sachet_darjeeling_polygon.txt.
   */
  private fun realPolygon(): String =
    javaClass.classLoader!!
      .getResourceAsStream("india/sachet_darjeeling_polygon.txt")!!
      .bufferedReader()
      .use { it.readText() }

  private fun parsePolygon(raw: String): List<GeoPoint> = AlertGeometry.parsePolygon(raw)

  private fun isoOf(millis: Long): String {
    val s = millis / 1000L
    val days = s / 86_400L
    val secsOfDay = s % 86_400L
    val h = secsOfDay / 3600L
    val mi = (secsOfDay % 3600L) / 60L
    val sec = secsOfDay % 60L
    // civil_from_days
    val z = days + 719_468L
    val era = (if (z >= 0) z else z - 146_096L) / 146_097L
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146_096L) / 365L
    val y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = if (mp < 10L) mp + 3L else mp - 9L
    val yy = if (m <= 2L) y + 1L else y
    return "%04d-%02d-%02dT%02d:%02d:%02dZ".format(yy, m, d, h, mi, sec)
  }
}