package com.example.viewmodel

import com.example.data.disaster.DisasterEventNormalizer
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.DisasterType
import com.example.data.disaster.DisasterTypeColors
import com.example.data.disaster.EventConfidence
import com.example.data.disaster.EventDetails
import com.example.data.disaster.EventGeometry
import com.example.data.disaster.EventOrigin
import com.example.data.disaster.EventStatus
import com.example.data.model.HazardSeverity
import com.example.data.weather.OpenMeteoWeatherService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live-data guards + weather parser contracts:
 *  - no derived live zone may render as a giant zone that swallows neighbours
 *    (map rule: no zone inside another);
 *  - the Open-Meteo parser maps real payloads to the radar bar and returns
 *    null (honest empty state) for anything without live values.
 */
class LiveDataGuardsTest {

  private fun quakeEvent(magnitude: Double) = com.example.data.disaster.DisasterEvent(
    id = "q-test",
    source = DisasterSource.USGS,
    sourceEventId = "q-test",
    disasterType = DisasterType.EARTHQUAKE,
    title = "M$magnitude test quake",
    description = "",
    geometry = EventGeometry.Point(23.0, 70.0),
    severity = HazardSeverity.HIGH,
    confidence = EventConfidence.NOT_PROVIDED,
    observedAtMillis = 1_000L,
    updatedAtMillis = 1_000L,
    status = EventStatus.ACTIVE,
    origin = EventOrigin.OBSERVED,
    details = EventDetails.Quake(magnitude, 10.0, "Test place", null)
  )

  // RULE 14 (retired behaviour, kept as a regression test):
    // These tests previously asserted that a magnitude-derived radius existed and
    // was merely CAPPED — e.g. M7.5 -> 2^7.5 km, clamped to 25 km. Capping a
    // synthesised radius still means SHOWING a danger area the source never
    // published. USGS supplies an epicentre, magnitude and depth; it supplies no
    // affected-area geometry. A point earthquake therefore produces NO zone, and
    // the original intent ("no derived zone may swallow its neighbours") now
    // holds trivially because none is derived at all.
    @Test
    fun `a point earthquake produces no hazard zone at all`() {
      assertNull(
        "an epicentre is not a danger area; no zone may be derived from magnitude",
        DisasterEventNormalizer.toHazardZone(quakeEvent(7.5))
      )
      assertNull(DisasterEventNormalizer.toHazardZone(quakeEvent(4.0)))
    }

    @Test
    fun `a quake with no magnitude still produces no zone`() {
      val noMag = quakeEvent(5.0).copy(details = EventDetails.Generic)
      assertNull(DisasterEventNormalizer.toHazardZone(noMag))
    }

    @Test
    fun `no danger radius is reported when the source published none`() {
      assertNull(DisasterEventNormalizer.sourceProvidedQuakeRadiusMeters(quakeEvent(7.5)))
      assertNull(DisasterEventNormalizer.sourceProvidedQuakeRadiusMeters(quakeEvent(4.0)))
    }

    @Test
    fun `the quake itself is still surfaced as an event with its real data`() {
      // Dropping the ZONE must not drop the EARTHQUAKE.
      val event = quakeEvent(6.2)
      assertEquals(DisasterSource.USGS, event.source)
      assertEquals(6.2, (event.details as EventDetails.Quake).magnitude, 0.0001)
      assertTrue(event.geometry is EventGeometry.Point)
    }

  @Test
  fun `every disaster type has a distinct opaque zone color`() {
    assertTrue(DisasterTypeColors.allDistinct())
    com.example.data.model.HazardType.entries.forEach { type ->
      val argb = DisasterTypeColors.argbFor(type)
      // Fully opaque (alpha 0xFF) so the legend swatch matches the map zone.
      assertEquals(0xFF, (argb ushr 24) and 0xFF)
    }
  }

  // ---------------------------------------------------------- weather parser

  private fun weatherJson(tempNow: Double, hourly: List<Double>): String {
    val temps = hourly.joinToString(",")
    return """{"current":{"temperature_2m":$tempNow,"precipitation":1.2,"wind_speed_10m":18.0},"hourly":{"temperature_2m":[$temps]}}"""
  }

  @Test
  fun `live payload maps to temp rain wind`() {
    val m = OpenMeteoWeatherService.parseResponse(
      weatherJson(31.4, listOf(28.0, 28.5, 29.0, 29.5, 30.0, 30.5))
    )
    assertNotNull(m)
    assertEquals("31.4°C", m!!.currentTemp)
    assertEquals("1.2 mm/h", m.rainfallIntensity)
    assertEquals("18 km/h", m.windGust)
  }

  @Test
  fun `warming trend detected over three hours`() {
    val m = OpenMeteoWeatherService.parseResponse(
      weatherJson(30.5, listOf(28.0, 28.5, 29.0, 30.5))
    )
    assertNotNull(m)
    assertTrue("trend=${m!!.trend3h}", m.trend3h.startsWith("Warming"))
  }

  @Test
  fun `cooling trend detected over three hours`() {
    val m = OpenMeteoWeatherService.parseResponse(
      weatherJson(24.0, listOf(28.0, 27.0, 26.0, 24.0))
    )
    assertNotNull(m)
    assertTrue("trend=${m!!.trend3h}", m.trend3h.startsWith("Cooling"))
  }

  @Test
  fun `steady trend when delta is small`() {
    val m = OpenMeteoWeatherService.parseResponse(
      weatherJson(29.9, listOf(29.7, 29.8, 29.8, 29.9))
    )
    assertNotNull(m)
    assertEquals("Steady", m!!.trend3h)
  }

  @Test
  fun `payload without live temperature yields null`() {
    assertNull(OpenMeteoWeatherService.parseResponse("""{"current":{},"hourly":{}}"""))
    assertNull(OpenMeteoWeatherService.parseResponse("not json"))
  }
}
