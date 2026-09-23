package com.example.data.disaster

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contracts for the NEARBY-FIRST / level-of-detail map rules — the logic that
 * turns "wtf is this carpet of circles" into "my area, readable".
 */
class MapFocusTest {

  private val here = GeoPoint(10.0, 76.5)

  private fun zone(lat: Double, lon: Double) = HazardZone(
    id = "z$lat", name = "Z", type = HazardType.FLOOD, severity = HazardSeverity.HIGH,
    center = GeoPoint(lat, lon), radiusMeters = 2000.0, riskLevel = "High",
    trend = HazardTrend.STABLE, sourceStatus = "LIVE", lastUpdatedMillis = 1L,
    provenance = DataProvenance(source = "t")
  )

  private fun shelter(lat: Double, lon: Double) = SafeZone(
    id = "s$lat", name = "S", lat = lat, lon = lon, locationNote = "",
    capacityTotal = 10, capacityCurrent = 0, waterAvailable = true, foodAvailable = true,
    electricityAvailable = true, sanitationAvailable = true, medicalSupport = false,
    accessibility = "Road", womenChildrenSuitability = false, operatingStatus = "OPEN",
    verificationStatus = "FIELD", elevationNote = "",
    provenance = DataProvenance(source = "t")
  )

  @Test
  fun `distance threshold boundary is exact`() {
    val justInside = GeoMathOffset.north(here, 49_000.0)
    val justOutside = GeoMathOffset.north(here, 51_000.0)
    assertTrue(MapFocus.isNear(here, justInside))
    assertFalse(MapFocus.isNear(here, justOutside))
  }

  @Test
  fun `no focus point means nothing is folded - never filter without knowing where near means`() {
    val far = zone(28.0, 77.0)
    assertEquals(listOf(far), MapFocus.nearbyHazardZones(listOf(far), null))
  }

  @Test
  fun `distant zones fold at city scale but everything renders at country scale`() {
    val near = zone(10.02, 76.5)
    val far = zone(20.0, 78.0)
    val city = MapFocus.nearbyHazardZones(listOf(near, far), here)
    assertEquals(listOf(near), city)
    // country scale: the caller does not apply nearby filtering at all (the
    // engine checks isCityScale before filtering); rule stated as a test:
    assertFalse(MapFocus.isCityScale(5.0))
    assertTrue(MapFocus.isCityScale(9.0))
  }

  @Test
  fun `safe zones use the same focus rule`() {
    val near = shelter(10.01, 76.5)
    val far = shelter(15.0, 78.0)
    assertEquals(listOf(near), MapFocus.nearbySafeZones(listOf(near, far), here))
  }

  @Test
  fun `radius circles only draw once the zoom is honest for them`() {
    assertFalse(MapFocus.drawsRadiusCircle(5.0))
    assertFalse(MapFocus.drawsRadiusCircle(8.9))
    assertTrue(MapFocus.drawsRadiusCircle(9.0))
    assertTrue(MapFocus.drawsRadiusCircle(15.0))
  }

  @Test
  fun `unlocated and raster events never produce a marker`() {
    val raster = com.example.data.disaster.MapFocus.eventPoint(
      DisasterEvent(
        id = "r", source = DisasterSource.NASA_FIRMS, sourceEventId = "r",
        disasterType = DisasterType.FLOOD, title = "layer", description = "",
        geometry = EventGeometry.RasterLayer("l", "flood layer"),
        severity = HazardSeverity.HIGH, confidence = EventConfidence.NOT_PROVIDED,
        observedAtMillis = 0L, updatedAtMillis = 0L, status = EventStatus.ACTIVE,
        origin = EventOrigin.OBSERVED, details = EventDetails.OfficialAlert(
          "Flood", "Actual", "Likely", "sender", null, null
        )
      )
    )
    assertNull(raster)
    val unlocated = com.example.data.disaster.MapFocus.eventPoint(
      DisasterEvent(
        id = "u", source = DisasterSource.IMD_CAP, sourceEventId = "u",
        disasterType = DisasterType.HEAVY_RAINFALL, title = "alert", description = "",
        geometry = EventGeometry.Unlocated("Kerala"),
        severity = HazardSeverity.MODERATE, confidence = EventConfidence.NOT_PROVIDED,
        observedAtMillis = 0L, updatedAtMillis = 0L, status = EventStatus.ACTIVE,
        origin = EventOrigin.REPORTED, details = EventDetails.OfficialAlert(
          "Rain", "Actual", "Likely", "IMD", null, null
        )
      )
    )
    assertNull("unlocated alerts must never gain a fake position", unlocated)
  }

  private object GeoMathOffset {
    /** Straight-north offset in meters (sufficient for boundary tests). */
    fun north(p: GeoPoint, meters: Double): GeoPoint =
      com.example.data.model.GeoMath.offsetPoint(p, 0.0, meters)
  }
}
