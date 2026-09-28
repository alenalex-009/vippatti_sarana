package com.example.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.TilesOverlay

/**
 * FREEZE + BLANK-MAP KILL-SWITCH for osmdroid issue #2028 (open since
 * 6.1.14, still present in 6.1.20). Two on-device symptoms, one cause —
 * the map camera's zoom going NaN/invalid:
 *
 *  1. THE FREEZE: the stock base overlay re-normalises the displayed-tile
 *     rectangle EVERY frame with a hand-rolled while-loop modulo against
 *     `upperBound = 1 shl floorToInt(zoom)`. With zoom = NaN, floorToInt
 *     gives -1, the bound becomes Integer.MIN_VALUE, and the loop never
 *     terminates: the main thread spins in MapTileArea.cleanValue and
 *     Android repeatedly reports "app is not responding".
 *  2. THE BLANK MAP: once mZoomLevel is NaN, the stock drawTiles throws
 *     "MapTileIndex: Zoom (-1) is too big" on every frame and MapView
 *     swallows the exception — so the canvas never paints tiles again.
 *
 * This guarded overlay (a) refuses the cache-keepalive frame for invalid
 * zooms, (b) normalises coordinates with O(1) modulo so far-out finite
 * values can never spin the stock loop, and (c) HEALS the camera: the first
 * invalid draw repairs the map's zoom/center to the last good values, so a
 * poisoned projection costs one frame instead of the whole map.
 */
class SafeTilesOverlay @JvmOverloads constructor(
  tileProvider: MapTileProviderBase,
  context: Context,
  private val map: MapView? = null
) : TilesOverlay(tileProvider, context) {

  private val protectedTilesRect = Rect()

  /** Last valid camera we saw — the repair target for a poisoned frame. */
  private var lastGoodZoom = -1.0
  private var lastGoodCenter: OsmGeoPoint? = null

  /**
   * THE BLANK-MAP HEALER (symptom 2). Intercept every draw: a valid zoom
   * draws normally (stock path) and records the camera; a NaN/out-of-range
   * zoom would make the stock code throw and paint nothing — refuse it,
   * repair the camera instead, and request the next frame.
   */
  override fun draw(pCanvas: Canvas?, pProjection: Projection?) {
    if (pCanvas == null || pProjection == null) return
    val zoom = pProjection.zoomLevel
    if (zoom.isFinite() && zoom >= 1.0 && zoom <= 30.0) {
      lastGoodZoom = zoom
      lastGoodCenter =
        runCatching { pProjection.currentCenter as? OsmGeoPoint }.getOrNull()
      super.draw(pCanvas, pProjection)
      return
    }
    // Degenerate camera: the stock draw would throw (and the map would stay
    // black forever) — refuse it and heal.
    val m = map ?: return
    val target = if (lastGoodZoom >= 1.0) lastGoodZoom else m.minZoomLevel + 2.0
    logRefusal("draw zoom=$zoom -> repairing camera to $target")
    runCatching {
      m.setZoomLevel(target)
      lastGoodCenter?.let { m.controller.setCenter(it) }
      m.invalidate()
    }
  }

  override fun protectDisplayedTilesForCache(
    pCanvas: Canvas?,
    pProjection: Projection?
  ) {
    if (pCanvas == null || pProjection == null) return
    if (!setViewPort(pCanvas, pProjection)) return
    val zoom = pProjection.zoomLevel
    // THE FREEZE GUARD (symptom 1): only a 1..30 zoom produces the positive
    // shift bound the stock modulo loop needs; anything else is refused.
    if (!zoom.isFinite() || zoom < 1.0 || zoom > 30.0) {
      logRefusal("protection zoom=$zoom")
      return
    }
    TileSystem.getTileFromMercator(mViewPort, TileSystem.getTileSize(zoom), protectedTilesRect)
    if (protectedTilesRect.isEmpty) return
    val inputZoom = TileSystem.getInputTileZoomLevel(zoom)
    // O(1) normalisation so far-out finite coordinates cannot drag the stock
    // add/subtract loop through billions of iterations either.
    val bound = 1L shl inputZoom
    val l = normalize(protectedTilesRect.left, bound)
    val t = normalize(protectedTilesRect.top, bound)
    var r = normalize(protectedTilesRect.right, bound)
    var b = normalize(protectedTilesRect.bottom, bound)
    if (r <= l) r += bound.toInt()
    if (b <= t) b += bound.toInt()
    val cache = mTileProvider?.tileCache ?: return
    cache.mapTileArea.set(inputZoom, l, t, r, b)
    cache.maintenance()
  }

  private fun normalize(v: Int, positiveBound: Long): Int {
    val m = ((v % positiveBound) + positiveBound) % positiveBound
    return m.toInt()
  }

  private fun logRefusal(detail: String) {
    val now = System.currentTimeMillis()
    if (now - lastRefusalLogMs > 1000) {
      lastRefusalLogMs = now
      Log.w(TAG, "refused degenerate frame: $detail")
    }
  }

  companion object {
    private const val TAG = "MapFreezeProbe"
    private var lastRefusalLogMs = 0L

    /**
     * Replaces the map's base tile overlay with the guarded one. Idempotent;
     * call right after MapView construction and after any tile-source switch
     * (osmdroid re-creates the stock overlay there). The previous overlay is
     * unhooked so tiles are never drawn twice.
     */
    fun install(map: MapView, context: Context): SafeTilesOverlay {
      val current = map.overlayManager.tilesOverlay
      if (current is SafeTilesOverlay) return current
      val safe = SafeTilesOverlay(map.tileProvider, context, map)
      map.overlayManager.setTilesOverlay(safe)
      if (current != null) {
        while (map.overlayManager.overlays().contains(current)) {
          map.overlayManager.overlays().remove(current)
        }
      }
      Log.i(TAG, "SafeTilesOverlay installed on new map")
      return safe
    }
  }
}
