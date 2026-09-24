package com.example.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.LocationManager
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

/**
 * Regression tests for the rotation / tab-switch crash.
 *
 * MainActivity no longer claims android:configChanges, so rotating restores
 * the real Activity recreation lifecycle. That means the map engine must be
 * torn down EXACTLY once, after the view leaves the window, without a
 * duplicate osmdroid detach (MapView.onDetachedFromWindow auto-detach) and
 * without leaking the GPS LocationListener into the app's LocationManager.
 *
 * These tests exercise OsmMapControllerHolder directly (the same object the
 * composable drives) — no window rendering, so the pulsing-overlay animation
 * cannot deadlock the Compose test clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapRotationLifecycleTest {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private fun initMap(
    holder: OsmMapControllerHolder,
    hazardZones: List<HazardZone> = emptyList(),
    safeZones: List<SafeZone> = emptyList()
  ) {
    holder.initMapView(
      context = context,
      hazardZones = hazardZones,
      safeZones = safeZones,
      onHazardZoneTapped = {},
      onSafeZoneTapped = {},
      onRealGpsFix = { _, _ -> }
    )
  }

  /**
   * One archived EM-DAT record that carries the dataset's own coordinates, so
   * it is genuinely mappable (see HistoricalSpatialPrecision).
   */
  private val historicalEvent = com.example.data.historical.HistoricalDisasterEvent(
    id = "1996-0123-IND",
    group = "Natural",
    subgroup = "Meteorological",
    type = "Storm",
    subtype = "Tropical cyclone",
    country = "India",
    locationText = "Odisha",
    startDate = com.example.data.historical.HistoricalDate(1996, 6, 6),
    impacts = com.example.data.historical.HistoricalImpacts(totalDeaths = 1000),
    latitude = 20.2,
    longitude = 85.7,
    source = "EM-DAT, CRED / UCLouvain, Brussels, Belgium",
    datasetVersion = "2026-09-11",
    spatialPrecision = com.example.data.historical.HistoricalSpatialPrecision.SOURCE_COORDINATES
  )

  /**
   * The HISTORICAL (EM-DAT) markers are pulsing overlays as well. `cleanup()`
   * used to stop and remove only the hazard / safe-zone / live-event overlays,
   * so a released map kept the archive layer animating — the same 66 ms
   * postInvalidate() loop on an already-detached MapView that this teardown
   * exists to prevent.
   */
  @Test
  fun `cleanup stops and removes the historical em-dat marker overlays`() {
    val holder = OsmMapControllerHolder(
      appContext = context,
      onLiveNavStatusChanged = {}
    )
    initMap(holder)
    val view = requireNotNull(holder.mapView)

    holder.deployHistoricalEvents(listOf(historicalEvent), {})

    val rendered = view.overlays.filterIsInstance<PulsingZoneOverlay>()
    assertEquals("the archive marker must be drawn before teardown", 1, rendered.size)
    assertTrue("a drawn pulse overlay animates", rendered.first().isAnimating)

    holder.cleanup()

    assertEquals(
      "cleanup must remove EVERY pulsing overlay, the EM-DAT layer included",
      0,
      view.overlays.count { it is PulsingZoneOverlay }
    )
    assertTrue(
      "no overlay may keep scheduling redraws after the map is released",
      rendered.none { it.isAnimating }
    )
  }

  /**
   * Each rotation previously destroyed the MapView at the WRONG time (while
   * still attached) and then the framework destroyed it AGAIN from
   * onDetachedFromWindow, while the GPS listener kept firing into the dead
   * map. The fix makes cleanup idempotent: a second call is a no-op and every
   * map operation degrades safely during and after release.
   */
  @Test
  fun `map engine survives repeated init-cleanup rotation cycles without double teardown`() {
    val holder = OsmMapControllerHolder(
      appContext = context,
      onLiveNavStatusChanged = {}
    )
    repeat(3) { cycle ->
      initMap(holder)
      assertNotNull("cycle $cycle: mapView must exist after init", holder.mapView)
      holder.enableLocationTracking()
      holder.deploySafeZones(emptyList(), {})
      holder.deployHazardZones(emptyList(), {})
      holder.recenterUser()
      holder.zoomIn()
      holder.zoomOut()
      holder.cycleTileSource()

      holder.cleanup()

      assertNull("cycle $cycle: mapView must be released after cleanup", holder.mapView)
      // A second teardown (the framework's onDetachedFromWindow) must be a no-op.
      holder.cleanup()
      // And the live update lambda runs during recomposition, possibly after release.
      holder.enableLocationTracking()
      holder.deploySafeZones(emptyList(), {})
      holder.deployHazardZones(emptyList(), {})
    }
  }

  @Test
  fun `cleanup removes the gps location listener from the location manager`() {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    shadowOf(locationManager).setProviderProperties(
      LocationManager.GPS_PROVIDER,
      ShadowLocationManager.ProviderProperties(
        false, true, false, false, true, true, true,
        PowerManager.PARTIAL_WAKE_LOCK,
        Criteria.ACCURACY_FINE
      )
    )
    shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)

    val holder = OsmMapControllerHolder(
      appContext = context,
      onLiveNavStatusChanged = {}
    )
    initMap(holder)

    val registeredAfterInit = shadowOf(locationManager).getRequestLocationUpdateListeners()
    assertTrue(
      "GPS provider must register a listener after enableMyLocation",
      registeredAfterInit.isNotEmpty()
    )

    holder.cleanup()

    val afterCleanup = shadowOf(locationManager).getRequestLocationUpdateListeners()
    assertTrue(
      "cleanup must deregister every location listener so recreation never registers a duplicate",
      afterCleanup.isEmpty()
    )
  }

  /** Guards the "don't suppress orientation changes as a workaround" rule. */
  @Test
  fun `main activity does not suppress orientation-change recreation`() {
    val packageInfo = context.packageManager.getPackageInfo(
      context.packageName,
      PackageManager.GET_ACTIVITIES
    )
    val mainActivity = packageInfo.activities!!.first { it.name.endsWith("MainActivity") }
    // The forbidden workaround was android:configChanges="orientation|screenSize|
    // smallestScreenSize|keyboard|keyboardHidden". Assert none of those flags are
    // claimed (Robolectric injects ORIENTATION|SCREEN_LAYOUT by default, so a raw
    // ==0 comparison would false-positive here; the mask encodes the real rule).
    val forbiddenMask =
      android.content.pm.ActivityInfo.CONFIG_ORIENTATION or
        android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE or
        android.content.pm.ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
        android.content.pm.ActivityInfo.CONFIG_KEYBOARD or
        android.content.pm.ActivityInfo.CONFIG_KEYBOARD_HIDDEN
    assertEquals(
      "MainActivity must NOT claim the configChanges workaround; rotation must recreate it",
      0,
      mainActivity.configChanges and forbiddenMask
    )
  }
}