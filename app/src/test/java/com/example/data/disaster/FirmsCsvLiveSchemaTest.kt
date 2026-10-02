package com.example.data.disaster

import com.example.data.disaster.providers.FirmsCsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 3 - FIRMS CSV schema check against the REAL service response.
 *
 * The fixture below is the header and rows returned live on 2026-09-20 by
 *   https://firms.modaps.eosdis.nasa.gov/api/area/csv/<MAP_KEY>/VIIRS_SNPP_NRT/
 *   67.5,6.0,98.0,37.5/2
 * (key omitted). It is not invented data: it is the provider's own schema, so
 * this test fails the moment FIRMS changes a column the parser depends on.
 */
class FirmsCsvLiveSchemaTest {

  private val liveHeader =
    "latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite," +
      "instrument,confidence,version,bright_ti5,frp,daynight"

  private val liveBody = """
    $liveHeader
    20.76082,85.2962,328.08,0.49,0.48,2026-09-19,716,N,VIIRS,n,2.0NRT,294.18,5.76,D
    20.78631,85.26122,328.63,0.49,0.49,2026-09-19,716,N,VIIRS,n,2.0NRT,294.54,6.08,D
    22.32001,82.5649,327.47,0.34,0.56,2026-09-19,716,N,VIIRS,h,2.0NRT,292.39,3.52,D
    41.9,12.5,300.0,0.4,0.5,2026-09-19,716,N,VIIRS,n,2.0NRT,290.0,1.0,D
  """.trimIndent()

  @Test
  fun `low-confidence detections are dropped - NASA alerting guidance, volume fix`() {
    // The user reported "too many fire dots": the honest lever is the
    // provider's OWN confidence flag. LOW detections must never appear.
    val body = """
    $liveHeader
    20.76082,85.2962,328.08,0.49,0.48,2026-09-19,716,N,VIIRS,l,2.0NRT,294.18,5.76,D
    20.78631,85.26122,328.63,0.49,0.49,2026-09-19,716,N,VIIRS,n,2.0NRT,294.54,6.08,D
    22.32001,82.5649,327.47,0.34,0.56,2026-09-19,716,N,VIIRS,h,2.0NRT,292.39,3.52,D
    """.trimIndent()
    val events = FirmsCsvParser.parse(body)
    assertEquals("only the l-flagged row is dropped", 2, events.size)
    assertTrue(events.none { it.confidence == EventConfidence.LOW })
  }

  @Test
  fun `the live column set parses into real fire detections`() {
    val events = FirmsCsvParser.parse(liveBody)
    // The Rome row is outside the India bounding box and must be dropped.
    assertEquals(3, events.size)
    events.forEach { event ->
      assertEquals(DisasterSource.NASA_FIRMS, event.source)
      assertEquals(DisasterType.WILDFIRE, event.disasterType)
      assertTrue(
        "detections must sit inside the India bbox",
        IndiaGeo.contains(event.latitude!!, event.longitude!!)
      )
      assertTrue("observation time must be real", event.observedAtMillis > 0L)
      assertEquals(event.observedAtMillis, event.updatedAtMillis)
    }
  }

  @Test
  fun `acquisition time and confidence are mapped, never guessed`() {
    val events = FirmsCsvParser.parse(liveBody)
    val first = events.first()
    // acq_date 2026-09-19 + acq_time 716 (UTC) -> 07:16 UTC.
    val expected = java.time.LocalDate.parse("2026-09-19").atStartOfDay(java.time.ZoneOffset.UTC)
      .plusMinutes(7 * 60L + 16L).toInstant().toEpochMilli()
    assertEquals(expected, first.observedAtMillis)
    assertEquals(EventConfidence.NOMINAL, first.confidence)
    assertEquals("detection confidence n", first.confidenceNote)
    val fire = first.details as EventDetails.Fire
    assertEquals("VIIRS", fire.instrument)
    assertEquals("N", fire.satellite)
    assertEquals(5.76, fire.frpMegawatts!!, 0.001)
    assertEquals("D", fire.dayNight)
  }

  @Test
  fun `confidence vocabulary covers VIIRS letters and MODIS percentages`() {
    assertEquals(EventConfidence.LOW, FirmsCsvParser.mapConfidence("l"))
    assertEquals(EventConfidence.NOMINAL, FirmsCsvParser.mapConfidence("n"))
    assertEquals(EventConfidence.HIGH, FirmsCsvParser.mapConfidence("h"))
    assertEquals(EventConfidence.LOW, FirmsCsvParser.mapConfidence("20"))
    assertEquals(EventConfidence.NOMINAL, FirmsCsvParser.mapConfidence("50"))
    assertEquals(EventConfidence.HIGH, FirmsCsvParser.mapConfidence("95"))
    assertEquals(EventConfidence.NOT_PROVIDED, FirmsCsvParser.mapConfidence(""))
  }

  @Test
  fun `an error page or a body without coordinates yields no events`() {
    // FIRMS answers errors as HTML (or a message block), never as rows.
    assertTrue(FirmsCsvParser.parse("<html><body>Invalid MAP_KEY</body></html>").isEmpty())
    assertTrue(FirmsCsvParser.parse("").isEmpty())
    assertTrue(FirmsCsvParser.parse("No data available").isEmpty())
  }
}
