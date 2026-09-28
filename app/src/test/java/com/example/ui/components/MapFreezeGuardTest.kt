package com.example.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ApplicationProvider as AP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FREEZE REGRESSION (user report: "the app keeps crashing and not working").
 * Fifteen on-device ANRs, every stack ending at
 * `MapTileArea.cleanValue <- TilesOverlay.protectDisplayedTilesForCache`
 * — osmdroid issue #2028: with a NaN/degenerate projection the stock
 * per-frame cache-protection step spins the MAIN THREAD forever.
 *
 * Pins:
 *  1. the map is created with the guarded SafeTilesOverlay (never the stock
 *     unprotected base overlay),
 *  2. the guarded overlay survives a NaN-zoom projection call without the
 *     infinite loop the stock code has (it returns instead of spinning),
 *  3. normal-zoom protection still works (a real frame marks tiles protected
 *     and the cache is maintained).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapFreezeGuardTest {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private fun laidOutMap(): MapView {
    val map = MapView(context)
    map.measure(
      ViewMeasureSpec(1080),
      ViewMeasureSpec(1920)
    )
    map.layout(0, 0, 1080, 1920)
    return map
  }

  private fun ViewMeasureSpec(size: Int) =
    android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)

  @Test
  fun `holder install swaps the stock base overlay for the guarded one`() {
    val map = laidOutMap()
    val before = map.overlayManager.tilesOverlay
    assertNotNull("osmdroid must start with a base tile overlay", before)
    val safe = SafeTilesOverlay.install(map, context)
    assertSame("the draw list must now use the guarded overlay",
      safe, map.overlayManager.tilesOverlay)
    // Idempotent: installing again returns the same instance, never stacks.
    assertSame(safe, SafeTilesOverlay.install(map, context))
  }

  @Test
  fun `degenerate NaN projection frame returns instead of spinning the main thread`() {
    val map = laidOutMap()
    val provider = map.tileProvider
    val safe = SafeTilesOverlay(provider, context)
    val canvas = Canvas(android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888))
    // A Projection with NaN zoom is exactly what issue #2028 turns into an
    // unbounded loop inside MapTileArea.cleanValue. This call MUST return.
    val nanProjection = Projection(
      Double.NaN, 1080, 1920,
      OsmGeoPoint(22.0, 79.0), 1f, false, false, 256, 256
    )
    val started = System.nanoTime()
    safe.protectDisplayedTilesForCache(canvas, nanProjection)
    val elapsedMs = (System.nanoTime() - started) / 1_000_000
    assertTrue("NaN frame must be refused instantly, took ${elapsedMs}ms", elapsedMs < 1000)
    // null projection: also refused, never thrown.
    safe.protectDisplayedTilesForCache(canvas, null)
  }

  @Test
  fun `poisoned camera heals on the next draw instead of blanking the map`() {
    val map = laidOutMap()
    // install() gives the overlay its map handle for repair.
    val safe = SafeTilesOverlay.install(map, context)
    val canvas = Canvas(
      android.graphics.Bitmap.createBitmap(1080, 1920, android.graphics.Bitmap.Config.ARGB_8888)
    )
    // A good frame records the camera.
    val good = Projection(
      12.0, 1080, 1920,
      OsmGeoPoint(17.6868, 83.2921), 1f, false, false, 256, 256
    )
    safe.draw(canvas, good)
    // Poison the camera exactly like #2028 does. MapView builds every draw
    // projection from mZoomLevel, so a poisoned map hands the overlay a NaN
    // projection — that draw must REPAIR (no throw, no blank) instead.
    map.setZoomLevel(Double.NaN)
    assertTrue(map.zoomLevelDouble.isNaN())
    val started = System.nanoTime()
    val poisoned = Projection(
      Double.NaN, 1080, 1920,
      OsmGeoPoint(17.6868, 83.2921), 1f, false, false, 256, 256
    )
    safe.draw(canvas, poisoned)
    val elapsedMs = (System.nanoTime() - started) / 1_000_000
    assertTrue("heal must be instant, took ${elapsedMs}ms", elapsedMs < 1000)
    assertTrue("camera must be finite again", map.zoomLevelDouble.isFinite())
  }

  @Test
  fun `normal zoom protection still marks the cache area`() {
    val map = laidOutMap()
    map.controller.setZoom(12.0)
    map.controller.setCenter(OsmGeoPoint(17.6868, 83.2921))
    val provider = map.tileProvider
    val safe = SafeTilesOverlay(provider, context)
    val canvas = Canvas(android.graphics.Bitmap.createBitmap(1080, 1920, android.graphics.Bitmap.Config.ARGB_8888))
    val projection = Projection(
      12.0, 1080, 1920,
      OsmGeoPoint(17.6868, 83.2921), 1f, false, false, 256, 256
    )
    safe.protectDisplayedTilesForCache(canvas, projection)
    val area = provider.tileCache.mapTileArea
    assertEquals("protection must run at zoom 12", 12, area.zoom)
    assertTrue("a real viewport must protect a non-empty tile area", area.size() > 0)
  }
}
