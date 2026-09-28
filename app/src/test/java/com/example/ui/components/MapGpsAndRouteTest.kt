package com.example.ui.components

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.data.disaster.PilotRegionData
import com.example.data.routing.GeoPoint
import com.example.data.routing.RouteResult
import com.example.data.routing.RouteSafetyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.overlay.Polyline
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager
import java.time.Duration

/**
 * Regression tests for Issues 2, 3 and 4 — exercised against the real
 * [OsmMapControllerHolder] (no window rendering, so the pulsing-overlay
 * animation cannot deadlock the test clock):
 *
 * - GPS locate states: permission, provider-off, last-known fast path,
 *   one-shot fix, timeout, duplicate-listener guard, cleanup cancellation,
 *   and the "no silent fallback jump" honesty rule.
 * - Route tap: the polyline never opens osmdroid's default blank
 *   bonuspack_bubble InfoWindow, and the tap falls through to zones below.
 * - Route color: the recommended/active polyline is always green, including
 *   DANGER/CAUTION routes; live-vs-offline dash semantics are preserved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapGpsAndRouteTest {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private fun locationManager(): LocationManager =
    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

  private fun holder(): OsmMapControllerHolder =
    OsmMapControllerHolder(appContext = context, onLiveNavStatusChanged = {})

  private fun initMap(holder: OsmMapControllerHolder): org.osmdroid.views.MapView {
    return holder.initMapView(
      context = context,
      hazardZones = emptyList(),
      safeZones = emptyList(),
      onHazardZoneTapped = {},
      onSafeZoneTapped = {},
      onRealGpsFix = { _, _ -> }
    )
  }

  private fun enableGpsProvider() {
    shadowOf(locationManager()).setProviderProperties(
      LocationManager.GPS_PROVIDER,
      ShadowLocationManager.ProviderProperties(
        false, true, false, false, true, true, true,
        PowerManager.PARTIAL_WAKE_LOCK,
        Criteria.ACCURACY_FINE
      )
    )
    shadowOf(locationManager()).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
  }

  private fun deviceLocation(lat: Double, lon: Double): Location =
    Location(LocationManager.GPS_PROVIDER).apply {
      latitude = lat
      longitude = lon
      time = System.currentTimeMillis()
      accuracy = 10f
    }

  private fun pressRecenter(
    holder: OsmMapControllerHolder,
    hasPermission: Boolean = true,
    permanentlyDenied: Boolean = false,
    onPermissionRequest: () -> Unit = {},
    onFix: (Double, Double) -> Unit = { _, _ -> }
  ) {
    holder.requestRecenter(
      context = context,
      hasPermission = hasPermission,
      permissionPermanentlyDenied = permanentlyDenied,
      onPermissionRequest = onPermissionRequest,
      onFix = onFix
    )
  }

  private fun dangerRoute(): RouteResult {
    val center = PilotRegionData.DEFAULT_MAP_CENTER
    return RouteResult(
      distanceMeters = 1200.0,
      durationSeconds = 900.0,
      pathPoints = listOf(
        GeoPoint(center.lat - 0.01, center.lon - 0.01),
        GeoPoint(center.lat, center.lon),
        GeoPoint(center.lat + 0.01, center.lon + 0.01)
      ),
      steps = emptyList(),
      isLiveOsrm = true,
      summary = "Test corridor",
      travelMode = com.example.data.routing.TravelMode.FOOT,
      routeSafetyStatus = RouteSafetyStatus.DANGER,
      routeSafetyScore = 12
    )
  }

  // ---------------------------------------------------------- Issue 2: GPS

  @Test
  fun `recenter without permission reports NO_PERMISSION and asks once`() {
    val holder = holder()
    initMap(holder)
    var asked = false
    pressRecenter(holder, hasPermission = false, onPermissionRequest = { asked = true })
    assertEquals(GpsRequestState.NO_PERMISSION, holder.gpsRequestState)
    assertNotNull(holder.gpsStatusMessage)
    assertTrue("permission must be requested", asked)
    // Only the overlay's own listener exists — no one-shot was registered.
    assertEquals(1, shadowOf(locationManager()).getRequestLocationUpdateListeners().size)
  }

  @Test
  fun `permanently denied never launches a permission request`() {
    val holder = holder()
    initMap(holder)
    var asked = false
    pressRecenter(
      holder, hasPermission = false, permanentlyDenied = true,
      onPermissionRequest = { asked = true }
    )
    assertEquals(GpsRequestState.PERMANENTLY_DENIED, holder.gpsRequestState)
    assertFalse("must not re-request when blocked", asked)
    assertTrue(holder.gpsStatusMessage!!.contains("Settings"))
  }

  @Test
  fun `disabled providers report PROVIDER_DISABLED`() {
    shadowOf(locationManager()).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
    shadowOf(locationManager()).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    val holder = holder()
    initMap(holder)
    pressRecenter(holder, hasPermission = true)
    assertEquals(GpsRequestState.PROVIDER_DISABLED, holder.gpsRequestState)
    assertNotNull(holder.gpsStatusMessage)
  }

  @Test
  fun `last-known fix centers immediately and is reported as a real fix`() {
    enableGpsProvider()
    shadowOf(locationManager()).setLastKnownLocation(
      LocationManager.GPS_PROVIDER, deviceLocation(12.9716, 77.5946)
    )
    val holder = holder()
    // Laid out so the projection/center math resolves (a 0-size view reads 0,0);
    // street zoom so center asserts are precise; animation idled to completion.
    val mapView = laidOutMap(initMap(holder))
    mapView.controller.setZoom(15.0)
    var reported: Pair<Double, Double>? = null
    pressRecenter(holder, hasPermission = true, onFix = { lat, lon -> reported = lat to lon })
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
    assertEquals(GpsRequestState.SUCCESS, holder.gpsRequestState)
    assertEquals(12.9716 to 77.5946, reported)
    assertEquals(12.9716, mapView.mapCenter.latitude, 1e-4)
    assertEquals(77.5946, mapView.mapCenter.longitude, 1e-4)
  }

  @Test
  fun `one-shot fix centers on arrival and deregisters the listener`() {
    enableGpsProvider()
    val holder = holder()
    val mapView = laidOutMap(initMap(holder))
    mapView.controller.setZoom(15.0)
    var reported: Pair<Double, Double>? = null
    pressRecenter(holder, hasPermission = true, onFix = { lat, lon -> reported = lat to lon })
    assertEquals(GpsRequestState.REQUESTING, holder.gpsRequestState)
    assertEquals(
      "overlay listener + exactly one one-shot listener",
      2, shadowOf(locationManager()).getRequestLocationUpdateListeners().size
    )
    shadowOf(locationManager()).simulateLocation(
      LocationManager.GPS_PROVIDER, deviceLocation(13.0827, 80.2707)
    )
    // Delivery is posted on the main looper (we registered with an explicit
    // Looper) and centering animates — idle both to completion, exactly like
    // the framework would.
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
    assertEquals(GpsRequestState.SUCCESS, holder.gpsRequestState)
    assertEquals(13.0827 to 80.2707, reported)
    assertEquals(13.0827, mapView.mapCenter.latitude, 1e-4)
    assertEquals(80.2707, mapView.mapCenter.longitude, 1e-4)
    assertEquals(
      "one-shot listener must be removed after the fix",
      1, shadowOf(locationManager()).getRequestLocationUpdateListeners().size
    )
  }

  @Test
  fun `one-shot times out with a clear message and no camera move`() {
    enableGpsProvider()
    val holder = holder()
    val mapView = laidOutMap(initMap(holder))
    mapView.controller.setZoom(15.0)
    val centerBefore = mapView.mapCenter.latitude to mapView.mapCenter.longitude
    var reported: Pair<Double, Double>? = null
    pressRecenter(holder, hasPermission = true, onFix = { lat, lon -> reported = lat to lon })
    assertEquals(GpsRequestState.REQUESTING, holder.gpsRequestState)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21))
    assertEquals(GpsRequestState.TIMEOUT, holder.gpsRequestState)
    assertTrue(holder.gpsStatusMessage!!.contains("20s"))
    assertNull("no fix may be reported on timeout", reported)
    assertEquals("camera must not move without a valid fix", centerBefore,
      mapView.mapCenter.latitude to mapView.mapCenter.longitude)
    assertEquals(1, shadowOf(locationManager()).getRequestLocationUpdateListeners().size)
  }

  @Test
  fun `repeat presses while locating register a single one-shot listener`() {
    enableGpsProvider()
    val holder = holder()
    initMap(holder)
    pressRecenter(holder, hasPermission = true)
    pressRecenter(holder, hasPermission = true)
    pressRecenter(holder, hasPermission = true)
    assertEquals(GpsRequestState.REQUESTING, holder.gpsRequestState)
    assertEquals(
      2, shadowOf(locationManager()).getRequestLocationUpdateListeners().size
    )
  }

  @Test
  fun `cleanup cancels a pending locate without timing out later`() {
    enableGpsProvider()
    val holder = holder()
    initMap(holder)
    pressRecenter(holder, hasPermission = true)
    assertEquals(GpsRequestState.REQUESTING, holder.gpsRequestState)
    holder.cleanup()
    assertEquals(GpsRequestState.IDLE, holder.gpsRequestState)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21))
    assertEquals("cancelled timeout must never fire", GpsRequestState.IDLE, holder.gpsRequestState)
    assertTrue(shadowOf(locationManager()).getRequestLocationUpdateListeners().isEmpty())
  }

  @Test
  fun `recenter without a fix never jumps the camera to fallback coordinates`() {
    enableGpsProvider()
    val holder = holder()
    val mapView = laidOutMap(initMap(holder))
    // FALLBACK_USER_LOCATION and DEFAULT_MAP_CENTER are the same India-centre
    // point by design, so coordinates alone cannot prove "no jump" — capture
    // the pre-call center and require it to be byte-identical afterwards.
    val centerBefore = mapView.mapCenter.latitude to mapView.mapCenter.longitude
    holder.recenterUser()
    assertEquals(GpsRequestState.UNAVAILABLE, holder.gpsRequestState)
    assertNotNull(holder.gpsStatusMessage)
    assertEquals(
      "camera must not move without a valid fix",
      centerBefore, mapView.mapCenter.latitude to mapView.mapCenter.longitude
    )
  }

  // ------------------------------------------------- Issue 3: blank bubble

  private fun laidOutMap(mapView: org.osmdroid.views.MapView): org.osmdroid.views.MapView {
    mapView.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
    )
    mapView.layout(0, 0, 1080, 1920)
    return mapView
  }

  @Test
  fun `tapping the route never opens an info window and falls through`() {
    val holder = holder()
    val mapView = laidOutMap(initMap(holder))
    holder.displayRoute(dangerRoute())
    val polyline = mapView.overlays.filterIsInstance<Polyline>().single()
    val mid = dangerRoute().pathPoints[1]
    val px = mapView.projection.toPixels(OsmGeoPoint(mid.lat, mid.lon), null)
    val tap = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, px.x.toFloat(), px.y.toFloat(), 0)
    try {
      val consumed = polyline.onSingleTapConfirmed(tap, mapView)
      assertFalse("route tap must fall through to zones below", consumed)
      assertFalse("no blank bonuspack_bubble may open", polyline.isInfoWindowOpen)
    } finally {
      tap.recycle()
    }
  }

  @Test
  fun `zone taps still reach their handler through the route`() {
    val holder = holder()
    val mapView = laidOutMap(initMap(holder))
    holder.displayRoute(dangerRoute())
    var tapped = false
    val overlay = PulsingZoneOverlay(
      center = OsmGeoPoint(
        PilotRegionData.DEFAULT_MAP_CENTER.lat, PilotRegionData.DEFAULT_MAP_CENTER.lon
      ),
      radiusMeters = 900.0,
      baseColorArgb = 0xFF00E297.toInt(),
      pulsePeriodMs = 2200L,
      onZoneTapped = { tapped = true }
    )
    val px = mapView.projection.toPixels(
      OsmGeoPoint(PilotRegionData.DEFAULT_MAP_CENTER.lat, PilotRegionData.DEFAULT_MAP_CENTER.lon),
      null
    )
    val tap = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, px.x.toFloat(), px.y.toFloat(), 0)
    try {
      assertTrue(overlay.onSingleTapUp(tap, mapView))
      assertTrue("zone interaction must be preserved", tapped)
    } finally {
      tap.recycle()
    }
  }

  // --------------------------------------------------- Issue 4: route color

  @Test
  fun `recommended route draws green even when its safety status is DANGER`() {
    val holder = holder()
    val mapView = initMap(holder)
    holder.displayRoute(dangerRoute())
    val polyline = mapView.overlays.filterIsInstance<Polyline>().single()
    assertEquals(0xFF00E297.toInt(), polyline.outlinePaint.color)
  }

  @Test
  fun `recommended route draws green for every safety status`() {
    val holder = holder()
    val mapView = initMap(holder)
    for (status in RouteSafetyStatus.entries) {
      holder.displayRoute(dangerRoute().copy(routeSafetyStatus = status))
      val polylines = mapView.overlays.filterIsInstance<Polyline>()
      assertEquals("reroute must replace, not stack, the polyline", 1, polylines.size)
      assertEquals("status $status must still draw green", 0xFF00E297.toInt(), polylines.single().outlinePaint.color)
    }
  }

  @Test
  fun `live route is solid and offline corridor stays dashed`() {
    val holder = holder()
    val mapView = initMap(holder)
    holder.displayRoute(dangerRoute().copy(isLiveOsrm = true))
    assertNull(
      "live OSRM route draws solid",
      mapView.overlays.filterIsInstance<Polyline>().single().outlinePaint.pathEffect
    )
    holder.displayRoute(dangerRoute().copy(isLiveOsrm = false))
    assertNotNull(
      "offline corridor keeps its dashed preview style",
      mapView.overlays.filterIsInstance<Polyline>().single().outlinePaint.pathEffect
    )
  }
}
