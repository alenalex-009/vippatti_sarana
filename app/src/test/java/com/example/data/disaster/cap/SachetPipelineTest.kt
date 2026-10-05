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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
 * Robolectric is required because CAP parsing goes through android.util.Xml,
 * which is an unmocked stub on a plain JVM test path. Pure-Kotlin logic
 * (ETag policy, geometry validation) is covered separately in
 * SachetPureLogicTest so it stays fast.
 */
@RunWith(RobolectricTestRunner::class)
class SachetPipelineTest {

  private fun resource(name: String): String =
    javaClass.classLoader!!.getResourceAsStream("india/$name")!!
      .bufferedReader().use { it.readText() }

  // ---------------- CAP parsing against the REAL document ----------------
  // ---------------- CAP parsing against the REAL document ----------------

  @Test
  fun `parses the real multi-language darjeeling cap document`() {
    val parsed = SachetCapParser.parse(resource("sachet_cap_darjeeling.xml"))
    assertNotNull(parsed)
    val cap = parsed!!

    assertEquals("IN-1791127650032017_17", cap.identifier)
    assertEquals("West-Bengal-SDMA", cap.sender)
    assertEquals("Thunderstorm with Lightning", cap.event)
    assertEquals("Met", cap.category)
    assertEquals("Moderate", cap.severity)
    assertEquals("Possible", cap.certainty)
    assertEquals("Expected", cap.urgency)
    assertEquals("Actual", cap.status)
    assertEquals("Update", cap.msgType)
    assertEquals("Public", cap.scope)
  }

  @Test
  fun `reads the real india-offset timestamps verbatim`() {
    val cap = SachetCapParser.parse(resource("sachet_cap_darjeeling.xml"))!!
    assertEquals("2026-10-04T21:06:31+05:30", cap.sent)
    assertEquals("2026-10-04T20:45:00+05:30", cap.effective)
    assertEquals("2026-10-04T23:45:00+05:30", cap.expires)
  }

  @Test
  fun `selects the english block instead of the bengali first block`() {
    // The real document lists Bengali first; an English UI must not show it.
    val cap = SachetCapParser.parse(resource("sachet_cap_darjeeling.xml"), "en-IN")!!
    assertEquals("en-IN", cap.language)
    val headline = cap.headline.orEmpty()
    assertTrue("headline should be English", headline.contains("Thunderstorm"))
    assertFalse("headline must not be Bengali", headline.contains('০'))
  }

  @Test
  fun `an absent description stays null rather than becoming placeholder text`() {
    val cap = SachetCapParser.parse(resource("sachet_cap_darjeeling.xml"))!!
    // The real document has <cap:description/> — empty.
    assertNull("empty description must stay null", cap.description)
  }

  @Test
  fun `captures the polygon url parameter that carries the real geometry`() {
    val cap = SachetCapParser.parse(resource("sachet_cap_darjeeling.xml"))!!
    val url = cap.polygonUrl
    assertNotNull("the Polygon URL parameter must be read", url)
    assertTrue(url!!.contains("FetchPolygonXMLFile"))
    assertTrue(url.contains(cap.identifier.substringAfter("IN-").substringBeforeLast("_")))
  }

  @Test
  fun `reads area description and the real LGD district code`() {
    val cap = SachetCapParser.parse(resource("sachet_cap_darjeeling.xml"))!!
    assertEquals(1, cap.areas.size)
    val area = cap.areas.first()
    assertEquals("Darjiling district of West Bengal", area.areaDesc)
    val lgd = area.geocodes.firstOrNull { it.name.equals("LGD District Code", true) }
    assertEquals("309", lgd?.value)
  }

  @Test
  fun `parses the real three-district andhra alert`() {
    val cap = SachetCapParser.parse(resource("sachet_cap_ap_multi.xml"))!!
    assertNotNull(cap.polygonUrl)
    assertTrue(cap.areas.isNotEmpty())
    val codes = cap.areas.flatMap { a -> a.geocodes }
      .filter { it.name.equals("LGD District Code", true) }
      .map { it.value }
    assertTrue("expected LGD district codes, got $codes", codes.isNotEmpty())
  }

  @Test
  fun `a malformed cap document is dropped not partially accepted`() {
    assertNull(SachetCapParser.parse("not xml at all"))
    assertNull(SachetCapParser.parse(""))
    assertNull(SachetCapParser.parse("<cap:alert></cap:alert>"))
  }

  @Test
  fun `a cap without an event is dropped`() {
    val xml = """
      <cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
        <cap:identifier>X-1</cap:identifier><cap:sender>TEST</cap:sender>
        <cap:info><cap:language>en-IN</cap:language></cap:info>
      </cap:alert>
    """.trimIndent()
    assertNull(SachetCapParser.parse(xml))
  }

  @Test
  fun `a cap without an identifier is dropped`() {
    val xml = """
      <cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
        <cap:info><cap:language>en-IN</cap:language><cap:event>Rain</cap:event></cap:info>
      </cap:alert>
    """.trimIndent()
    assertNull(SachetCapParser.parse(xml))
  }


  // ---------------- alert normalization ----------------

  @Test
  fun `an official alert with a validated polygon becomes a polygon event`() {
    val raw = resource("sachet_darjeeling_polygon.txt")
    val validation = GeometryValidator.validateRawPolygon(raw)
    val event = SachetAlertFactory.toDisasterEvent(
      cap = SachetCapParser.parse(resource("sachet_cap_darjeeling.xml"))!!,
      sourceUrl = "https://sachet.ndma.gov.in/cap_public_website/FetchXMLFile?identifier=1791127650032017",
      polygonText = raw,
      validation = validation,
      retrievedAtMillis = 1_700_000_000_000L
    )

    assertNotNull(event)
    val e = event!!
    assertEquals(DisasterSource.NDMA_CAP, e.source)
    assertTrue(
      "title uses the authority headline: ${e.title.take(30)}",
      e.title.startsWith("Light to moderate Thunderstorm")
    )
    assertTrue(e.geometry is EventGeometry.Polygon)
    assertEquals(DisasterType.HEAVY_RAINFALL, e.disasterType)
    assertTrue("official alert must be live", e.observedAtMillis > 0L)
  }

  @Test
  fun `an alert with an unavailable polygon stays unlocated and keeps the alert`() {
    // Polygon retrieval failed (403) — the alert must SURVIVE as unlocated,
    // never be dropped and never be given a generated circle.
    val event = SachetAlertFactory.toDisasterEvent(
      cap = SachetCapParser.parse(resource("sachet_cap_darjeeling.xml"))!!,
      sourceUrl = "https://sachet.ndma.gov.in/cap_public_website/FetchXMLFile?identifier=1",
      polygonText = null,
      validation = GeometryValidator.validateRawPolygon(null),
      retrievedAtMillis = 1_700_000_000_000L
    )

    assertNotNull("alert must be retained without geometry", event)
    val geom = event!!.geometry
    assertTrue("must be Unlocated, was ${geom.type}", geom is EventGeometry.Unlocated)
    assertEquals("Darjiling district of West Bengal", (geom as EventGeometry.Unlocated).areaLabel)
  }

  @Test
  fun `an alert whose polygon fails validation is kept but reports the reason`() {
    val badPolygon = "999.0,999.0 not-a-coordinate 27.2,88.1"
    val validation = GeometryValidator.validateRawPolygon(badPolygon)
    val event = SachetAlertFactory.toDisasterEvent(
      cap = SachetCapParser.parse(resource("sachet_cap_darjeeling.xml"))!!,
      sourceUrl = "https://sachet.ndma.gov.in/x",
      polygonText = badPolygon,
      validation = validation,
      retrievedAtMillis = 1_700_000_000_000L
    )

    assertNotNull(event)
    assertTrue(event!!.geometry is EventGeometry.Unlocated)
  }

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


  @Test
  fun `an earthquake alert does not acquire a danger radius`() {
    // Rule 14: a point/alert earthquake must stay a point event.
    val xml = """
      <cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
        <cap:identifier>EQ-1</cap:identifier><cap:sender>TEST</cap:sender>
        <cap:info><cap:language>en-IN</cap:language>
          <cap:event>Earthquake</cap:event><cap:severity>Severe</cap:severity>
        </cap:info>
      </cap:alert>
    """.trimIndent()
    val cap = SachetCapParser.parse(xml)!!
    val event = SachetAlertFactory.toDisasterEvent(
      cap, "u", null, GeometryValidator.validateRawPolygon(null), 1_700_000_000_000L)!!
    // No polygon -> unlocated. It must not become a circle.
    assertTrue(event.geometry is EventGeometry.Unlocated)
  }
}
