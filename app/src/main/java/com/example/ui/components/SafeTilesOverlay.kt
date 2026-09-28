package com.example.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.TilesOverlay

/**
 * FREEZE KILL-SWITCH + FIELD PROBE for osmdroid issue #2028 (open since
 * 6.1.14, still present in 6.1.20; on-device symptom: the app spins until
 * Android says "app not responding" — every ANR stack ends in
 * `MapTileArea.cleanValue <- TilesOverlay.protectDisplayedTilesForCache`).
 *
 * The stock base-map overlay re-normalises the displayed-tile rectangle on
 * EVERY drawn frame with a hand-rolled while-loop modulo against
 * `upperBound = 1 shl floorToInt(zoom)`. That loop only terminates quickly
 * when the bound is positive AND the coordinates are near it. An animated
 * camera move against a degenerate projection produces zoom = NaN:
 * `floorToInt(NaN)` = -1, `1 shl -1` = Integer.MIN_VALUE — a NEGATIVE bound —
 * and the modulo ping-pongs between two wrapped values forever. A pinch at a
 * huge integer scale (issue #2028's report) produces the same spin with
 * finite coordinates far outside [0, bound). Either way the MAIN THREAD hangs
 * for minutes and Android kills the UI.
 *
 * This subclass does the identical cache-keepalive step but normalises the
 * tile rectangle with O(1) arithmetic modulo first, and refuses the frame
 * entirely for non-finite / out-of-range zooms. Refusing only skips a
 * cache-keepalive hint for one frame — tiles still draw and the map self-heals
 * the moment the projection is sane.
 */
class SafeTilesOverlay(
  tileProvider: MapTileProviderBase,
  context: Context
) : TilesOverlay(tileProvider, context) {

  private val protectedTilesRect = Rect()

  override fun protectDisplayedTilesForCache(
    pCanvas: Canvas?,
    pProjection: Projection?
  ) {
    if (pCanvas == null || pProjection == null) return
    if (!setViewPort(pCanvas, pProjection)) return
    val zoom = pProjection.zoomLevel
    // Guard 1 (the NaN case of #2028): only an integer-ish 1..30 zoom can
    // produce the positive shift bound the stock modulo loop needs. A NaN
    // zoom also draws NOTHING (black canvas), but refusing the frame is
    // safe: the camera is repaired upstream (fit margins are capped so a
    // degenerate zoom is never requested) and the next good frame redraws.
    if (!zoom.isFinite() || zoom < 1.0 || zoom > 30.0) {
      logRefusal("zoom=$zoom")
      return
    }
    TileSystem.getTileFromMercator(mViewPort, TileSystem.getTileSize(zoom), protectedTilesRect)
    if (protectedTilesRect.isEmpty) return
    val inputZoom = TileSystem.getInputTileZoomLevel(zoom)
    // Guard 2 (the huge-coordinate case): normalise with exact O(1) modulo
    // BEFORE handing the rect to MapTileArea — the stock add/subtract loop
    // would crawl for billions of iterations on far-out coordinates.
    val bound = 1L shl inputZoom
    val l = normalize(protectedTilesRect.left, bound)
    val t = normalize(protectedTilesRect.top, bound)
    var r = normalize(protectedTilesRect.right, bound)
    var b = normalize(protectedTilesRect.bottom, bound)
    // Wraps across the world edge: keep the span honest for the area math.
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
      Log.w(TAG, "skipped tile-cache protection frame: $detail")
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
      val safe = SafeTilesOverlay(map.tileProvider, context)
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
