package com.example.data.india

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Phase 1 India-wide data foundation.
 *
 * These lock in the CRITICAL RULES. Each test names the rule it enforces so a
 * future change that breaks an honesty guarantee fails loudly here.
 */
class IndiaDataFoundationTest {

  // ---- Rule 6/8: missing values must stay unknown, never become zero ----

  @Test
  fun `unknown number stays unknown rather than becoming zero`() {
    val unknown = MaybeNumber.UNKNOWN

    assertTrue(unknown.isUnknown)
    assertNull(unknown.orNull())
  }

  @Test(expected = IllegalStateException::class)
  fun `asking an unknown number for its value throws instead of substituting`() {
    MaybeNumber.UNKNOWN.valueOrThrow("capacity")
  }

  @Test
  fun `arithmetic on an unknown operand stays unknown`() {
    val result = MaybeNumber.of(10.0) + MaybeNumber.UNKNOWN
    assertTrue(result.isUnknown)
  }

  @Test
  fun `arithmetic between known operands is allowed`() {
    val result = MaybeNumber.of(10.0) + MaybeNumber.of(5.0)
    assertEquals(15.0, result.value!!, 0.0001)
  }

  // ---- Rule 13: hazard observation is NOT an evacuation zone ----

  @Test
  fun `earthquake observation cannot be promoted to an evacuation zone`() {
    val result = InferenceGuard.observationToEvacuationZone(EntityKind.EARTHQUAKE_OBSERVATION)

    assertFalse(result.isAllowed)
    assertTrue(result is InferenceResult.Refused)
    assertEquals("evacuationZoneGeometry", (result as InferenceResult.Refused).unknownField)
  }

  @Test
  fun `alert cannot be promoted to an evacuation zone`() {
    assertFalse(InferenceGuard.observationToEvacuationZone(EntityKind.ALERT).isAllowed)
  }

  @Test
  fun `a published hazard zone may be used as published`() {
    assertTrue(InferenceGuard.observationToEvacuationZone(EntityKind.HAZARD_ZONE).isAllowed)
  }

  // ---- Rule 14: epicentre is NOT a danger radius ----

  @Test
  fun `danger radius is refused when the source published none`() {
    val result = InferenceGuard.dangerRadiusFromEpicentre(
      sourceRadiusKm = MaybeNumber.UNKNOWN,
      magnitude = MaybeNumber.of(6.1)
    )

    assertFalse(result.isAllowed)
    val refused = result as InferenceResult.Refused
    assertEquals("dangerRadiusKm", refused.unknownField)
    assertTrue(refused.reason.contains("magnitude alone"))
  }

  @Test
  fun `danger radius is used when the source actually published one`() {
    val result = InferenceGuard.dangerRadiusFromEpicentre(
      sourceRadiusKm = MaybeNumber.of(40.0),
      magnitude = MaybeNumber.of(6.1)
    )

    assertTrue(result.isAllowed)
    assertEquals(40.0, (result as InferenceResult.Allowed).value.value!!, 0.0001)
  }

  // ---- Rule 15: satellite hotspot is NOT a fire perimeter ----

  @Test
  fun `hotspots alone do not constitute a fire perimeter`() {
    val result = InferenceGuard.hotspotToPerimeter(hotspotCount = 37, publishedPerimeterPresent = false)

    assertFalse(result.isAllowed)
    assertEquals("firePerimeter", (result as InferenceResult.Refused).unknownField)
  }

  @Test
  fun `a published perimeter is accepted`() {
    assertTrue(InferenceGuard.hotspotToPerimeter(5, publishedPerimeterPresent = true).isAllowed)
  }

  // ---- Rule 16/17: an OSRM route is NOT a safe route ----

  @Test
  fun `computed route without closure data is unverified, not safe`() {
    val result = InferenceGuard.routeToSafeRoute(
      hasAuthorityClearance = false,
      hasAuthorityWarning = false,
      hasClosureFeed = false
    )

    val allowed = result as InferenceResult.Allowed
    assertEquals(RouteSafetyStatus.UNVERIFIED_NO_DATA, allowed.value.safetyStatus)
    assertFalse(allowed.value.closureDataAvailable)
    assertFalse(allowed.value.safetyStatus.label.contains("clear", ignoreCase = true))
  }

  @Test
  fun `authority warning outranks a computed route`() {
    val result = InferenceGuard.routeToSafeRoute(
      hasAuthorityClearance = false,
      hasAuthorityWarning = true,
      hasClosureFeed = false
    )

    assertEquals(
      RouteSafetyStatus.AUTHORITY_ADVISED_AGAINST,
      (result as InferenceResult.Allowed).value.safetyStatus
    )
  }

  @Test
  fun `authority clearance is the only route state reported as cleared`() {
    val result = InferenceGuard.routeToSafeRoute(
      hasAuthorityClearance = true,
      hasAuthorityWarning = false,
      hasClosureFeed = true
    )

    assertEquals(
      RouteSafetyStatus.AUTHORITY_CLEARED,
      (result as InferenceResult.Allowed).value.safetyStatus
    )
  }

  // ---- Rule 18: never overclaim official / verified / live ----

  @Test
  fun `live official alert is reported as official`() {
    assertEquals(
      ProvenanceStatus.OFFICIAL,
      InferenceGuard.provenanceStatusFor(DataClass.OFFICIAL_ALERT, TemporalStatus.LIVE)
    )
  }

  @Test
  fun `cached official alert is not reported as live official`() {
    val status = InferenceGuard.provenanceStatusFor(DataClass.OFFICIAL_ALERT, TemporalStatus.CACHED)
    assertNotEqualsOfficial(status)
  }

  @Test
  fun `scientific observation is never labelled official`() {
    val status = InferenceGuard.provenanceStatusFor(
      DataClass.SUPPLEMENTARY_OBSERVATION,
      TemporalStatus.LIVE
    )
    assertEquals(ProvenanceStatus.SUPPLEMENTARY, status)
  }

  @Test
  fun `blocked official source reports unavailable`() {
    assertEquals(
      ProvenanceStatus.UNAVAILABLE,
      InferenceGuard.provenanceStatusFor(DataClass.BLOCKED_OFFICIAL, TemporalStatus.UNKNOWN)
    )
  }

  private fun assertNotEqualsOfficial(status: ProvenanceStatus) {
    assertTrue(
      "cached official alert must not claim OFFICIAL provenance",
      status != ProvenanceStatus.OFFICIAL
    )
  }

  // ---- Rule 11/12: historical is never presented as current ----

  @Test
  fun `historical data is not presently valid`() {
    assertFalse(InferenceGuard.isPresentlyValid(TemporalStatus.HISTORICAL))
  }

  @Test
  fun `simulated data is not presently valid`() {
    assertFalse(InferenceGuard.isPresentlyValid(TemporalStatus.SIMULATED))
  }

  @Test
  fun `live data is presently valid`() {
    assertTrue(InferenceGuard.isPresentlyValid(TemporalStatus.LIVE))
  }

  @Test
  fun `census 2011 is registered as historical`() {
    val census = IndiaSourceRegistry.byId("CENSUS_2011")
    assertNotNull(census)
    assertEquals(TemporalStatus.HISTORICAL, census!!.temporalClass)
    assertFalse(InferenceGuard.isPresentlyValid(census.temporalClass))
  }

  @Test
  fun `weather context is never an official warning`() {
    val weather = IndiaSourceRegistry.byId("OPEN_METEO")
    assertNotNull(weather)
    assertFalse(weather!!.dataClass.mayBeLabelledOfficial)
  }

  // ---- Rule 19: blocked official sources carry no silent substitute ----

  @Test
  fun `blocked official sources are registered as blocked`() {
    val ndem = IndiaSourceRegistry.byId("NDEM_NRSC")
    assertNotNull(ndem)
    assertEquals(SourceAccessibility.BLOCKED_LOGIN_REQUIRED, ndem!!.accessibility)
    assertFalse(ndem.accessibility == SourceAccessibility.AVAILABLE)
  }

  @Test
  fun `road closures are recorded as unavailable rather than assumed clear`() {
    val road = IndiaSourceRegistry.byId("ROAD_CLOSURES")
    assertNotNull(road)
    assertEquals(SourceAccessibility.BLOCKED_NO_OPEN_API, road!!.accessibility)
  }

  @Test
  fun `every registered source documents what it does not yield`() {
    IndiaSourceRegistry.ALL.forEach { source ->
      assertTrue(
        "${source.id} must document what it does not yield",
        source.doesNotYield.isNotBlank()
      )
    }
  }

  @Test
  fun `no source id is registered twice`() {
    val ids = IndiaSourceRegistry.ALL.map { it.id }
    assertEquals(ids.size, ids.toSet().size)
  }

  // ---- Rule 24: offline honesty ----

  @Test
  fun `freshness window classifies a recent fetch as fresh`() {
    val now = System.currentTimeMillis()
    val fresh = IndiaProvenance(
      source = "test",
      retrievedAtMillis = now - 1_000L,
      temporalStatus = TemporalStatus.LIVE
    )
    assertTrue(fresh.isFresh)
  }

  @Test
  fun `a day-old cache is not fresh`() {
    val dayAgo = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
    val stale = IndiaProvenance(
      source = "test",
      retrievedAtMillis = dayAgo,
      temporalStatus = TemporalStatus.CACHED
    )
    assertFalse(stale.isFresh)
  }

  @Test
  fun `a record with no timestamp is never fresh`() {
    val untimestamped = IndiaProvenance(source = "test", retrievedAtMillis = 0L)
    assertFalse(untimestamped.isFresh)
  }

  // ---- India-wide geography ----

  @Test
  fun `delhi lies inside india`() {
    assertEquals(IndiaRegion.MAINLAND, IndiaGeography.regionFor(GeoPoint(28.61, 77.21)))
  }

  @Test
  fun `mumbai lies inside india`() {
    assertEquals(IndiaRegion.MAINLAND, IndiaGeography.regionFor(GeoPoint(19.08, 72.88)))
  }

  @Test
  fun `kanyakumari lies inside india not below it`() {
    assertEquals(IndiaRegion.MAINLAND, IndiaGeography.regionFor(GeoPoint(8.08, 77.54)))
  }

  @Test
  fun `port blair is recognised as a separate region`() {
    assertEquals(IndiaRegion.ANDAMAN_NICOBAR, IndiaGeography.regionFor(GeoPoint(11.62, 92.73)))
  }

  @Test
  fun `kavaratti is recognised as lakshadweep`() {
    assertEquals(IndiaRegion.LAKSHADWEEP, IndiaGeography.regionFor(GeoPoint(10.57, 72.64)))
  }

  @Test
  fun `a point outside india is not silently accepted`() {
    assertEquals(IndiaRegion.OUTSIDE, IndiaGeography.regionFor(GeoPoint(40.71, -74.01)))
    assertFalse(IndiaGeography.isInIndia(GeoPoint(40.71, -74.01)))
  }

  @Test
  fun `a fetch box is clamped to india and never requests foreign data`() {
    val box = IndiaGeography.searchBoxAround(GeoPoint(28.61, 77.21), radiusKm = 50.0)

    assertTrue(box.minLon >= GeoBoundingBox.INDIA.minLon)
    assertTrue(box.maxLon <= GeoBoundingBox.INDIA.maxLon)
    assertTrue(box.minLat >= GeoBoundingBox.INDIA.minLat)
    assertTrue(box.maxLat <= GeoBoundingBox.INDIA.maxLat)
  }

  @Test
  fun `a fix just outside the border is clamped rather than discarded`() {
    val outside = GeoPoint(35.9, 77.5) // north of the mainland bbox
    val clamped = IndiaGeography.clampToIndia(outside)

    assertTrue(GeoBoundingBox.INDIA.contains(clamped))
  }

  @Test
  fun `a search box covers the requested ground distance at any latitude`() {
    // A longitude *degree* is wider on the ground near the equator than near
    // the pole, so raw degree spans are NOT comparable between latitudes.
    // What must hold is that the box spans the requested ground distance in
    // both directions, everywhere in India.
    listOf(
      "Kanyakumari" to GeoPoint(8.08, 77.54),
      "Mumbai" to GeoPoint(19.08, 72.88),
      "Hyderabad" to GeoPoint(17.39, 78.49),
      "Delhi" to GeoPoint(28.61, 77.21),
      "Jodhpur" to GeoPoint(26.24, 73.02)
    ).forEach { (name, point) ->
      val box = IndiaGeography.searchBoxAround(point, radiusKm = 50.0)
      val latSpanDeg = box.maxLat - box.minLat
      val lonSpanDeg = box.maxLon - box.minLon
      val cosLat = kotlin.math.cos(Math.toRadians(point.lat)).coerceAtLeast(0.05)

      val latKm = latSpanDeg * 111.0
      val lonKm = lonSpanDeg * 111.0 * cosLat

      assertTrue("$name lat span was $latKm km", latKm in 95.0..105.0)
      assertTrue("$name lon span was $lonKm km", lonKm in 95.0..105.0)
    }
  }

  @Test
  fun `degree spans shrink toward the equator while ground distance stays fixed`() {
    val delhi = IndiaGeography.searchBoxAround(GeoPoint(28.6, 77.2), 50.0)
    val kanyakumari = IndiaGeography.searchBoxAround(GeoPoint(8.1, 77.5), 50.0)

    val delhiLonSpan = delhi.maxLon - delhi.minLon
    val kkLonSpan = kanyakumari.maxLon - kanyakumari.minLon

    assertTrue(
      "at 8N a longitude degree covers less ground, so fewer degrees are needed",
      kkLonSpan < delhiLonSpan
    )
  }

  // ---- Grid cells are stable and deterministic ----

  @Test
  fun `the same point always yields the same grid cell`() {
    val p = GeoPoint(17.39, 78.49)
    assertEquals(GeoGrid.cellFor(p, GeoGrid.MEDIUM), GeoGrid.cellFor(p, GeoGrid.MEDIUM))
  }

  @Test
  fun `grid cells work at any indian location not just one pilot state`() {
    listOf(
      GeoPoint(28.61, 77.21), // Delhi
      GeoPoint(19.08, 72.88), // Mumbai
      GeoPoint(13.08, 80.27), // Chennai
      GeoPoint(22.57, 88.36), // Kolkata
      GeoPoint(17.39, 78.49), // Hyderabad
      GeoPoint(26.85, 80.95)  // Lucknow
    ).forEach { point ->
      val cell = GeoGrid.cellFor(point, GeoGrid.MEDIUM)
      assertTrue("${point.lat},${point.lon} must map inside India", IndiaGeography.isInIndia(point))
      assertTrue(cell.row >= 0)
    }
  }

  // ---- Distance correctness, used for the user-relative evacuation logic ----

  @Test
  fun `distance between two known indian cities is plausible`() {
    val delhiToMumbaiKm = GeoGrid.haversineKm(GeoPoint(28.61, 77.21), GeoPoint(19.08, 72.88))
    // Real-world ~1150-1200 km
    assertTrue("got $delhiToMumbaiKm", delhiToMumbaiKm in 1100.0..1250.0)
  }

  @Test
  fun `distance from a point to itself is zero`() {
    assertEquals(0.0, GeoGrid.haversineKm(GeoPoint(17.39, 78.49), GeoPoint(17.39, 78.49)), 0.0001)
  }

  @Test
  fun `haversine is symmetric`() {
    val a = GeoPoint(12.97, 77.59)
    val b = GeoPoint(15.30, 75.71)
    assertEquals(
      GeoGrid.haversineKm(a, b),
      GeoGrid.haversineKm(b, a),
      0.0001
    )
  }

  // ---- API request builders ----

  @Test
  fun `firms bbox is lon lat lon lat in the order the api requires`() {
    val box = GeoBoundingBox(minLon = 68.0, minLat = 6.0, maxLon = 98.0, maxLat = 37.0)
    assertEquals("68.0,6.0,98.0,37.0", IndiaGeography.firmsBbox(box))
  }

  @Test
  fun `usgs bbox emits lat before lon as that api requires`() {
    val box = GeoBoundingBox(minLon = 68.0, minLat = 6.0, maxLon = 98.0, maxLat = 37.0)
    val fragment = IndiaGeography.usgsBbox(box)

    assertTrue(fragment.contains("minlatitude=6.0"))
    assertTrue(fragment.contains("maxlatitude=37.0"))
    assertTrue(fragment.contains("minlongitude=68.0"))
    assertTrue(fragment.contains("maxlongitude=98.0"))
  }
}
