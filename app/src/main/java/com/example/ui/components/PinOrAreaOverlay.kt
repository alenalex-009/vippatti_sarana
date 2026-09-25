package com.example.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.example.data.disaster.MapFocus
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.sqrt

/** Shared base so every marker overlay (pin-or-dot and classic zone) can be
 * stopped + removed uniformly by the map controller. */
abstract class MarkerOverlay : Overlay() {
  /** Permanently stop animation/invalidation. Must be idempotent. */
  abstract fun stop()

  /** True while the overlay still schedules pulse redraws (teardown test). */
  abstract val isAnimating: Boolean
}

/**
 * ============================================================================
 * PIN-OR-AREA OVERLAY — the map's honest visual language
 * ============================================================================
 *
 * One hazard / event / safe place is drawn ONE way depending on zoom:
 *
 *   city+ zoom (>= [areaFromZoom])  -> its REAL ground area: translucent fill
 *                                      + ring + a single soft expanding pulse
 *                                      (honest size, real-world radius).
 *   below that zoom                -> a small Google-style PIN DOT: solid
 *                                      colour, 2 dp white ring, fixed screen
 *                                      size. A dot claims "something is HERE",
 *                                      never "it covers this whole region" —
 *                                      which is exactly what giant faded
 *                                      circles lied about at country zoom.
 *
 * Tapping either style fires [onTapped] with the same behaviour, so detail
 * sheets are reachable everywhere. The class reads the live zoom on every
 * draw: zooming in morphs dots into areas with no re-deploy.
 */
class PinOrAreaOverlay(
  val center: GeoPoint,
  private val radiusMeters: Double,
  private val colorArgb: Int,
  private val pulsePeriodMs: Long,
  private val areaFromZoom: Double = MapFocus.RADIUS_CIRCLE_MIN_ZOOM,
  /** Dot screen radius in dp-ish pixels (kept constant across zooms). */
  private val dotRadiusPx: Float = 13f,
  private val showHalo: Boolean = false,
  private val onTapped: (() -> Unit)? = null
) : MarkerOverlay() {

  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = colorArgb
    style = Paint.Style.FILL
  }
  private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = colorArgb
    style = Paint.Style.STROKE
    strokeWidth = 2.5f
    alpha = 200
  }
  private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = colorArgb
    style = Paint.Style.STROKE
    strokeWidth = 4f
  }
  private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = colorArgb
    style = Paint.Style.FILL
  }
  private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFFFFFFFF.toInt()
    style = Paint.Style.STROKE
    strokeWidth = 3f
  }
  private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = colorArgb
    alpha = 56
    style = Paint.Style.FILL
  }

  private var startMillis = -1L
  private var lastInvalidateMillis = -1L

  @Volatile
  private var active = true

  /** Stops animation + redraw scheduling forever. Idempotent (rotation-safe). */
  override fun stop() {
    active = false
  }

  override val isAnimating: Boolean get() = active

  companion object {
    private const val PULSE_EXPANSION = 0.35f
    private const val INVALIDATE_INTERVAL_MS = 66L // ~15 fps

    /** Pure decision, unit-tested: which visual does a given zoom deserve? */
    fun drawsArea(zoom: Double, areaFromZoom: Double = MapFocus.RADIUS_CIRCLE_MIN_ZOOM): Boolean =
      zoom >= areaFromZoom
  }

  private fun baseRadiusPixels(p: Projection): Float {
    val centerPx = p.toPixels(center, null)
    val edge = GeoPoint(center.latitude + radiusMeters / 111_139.0, center.longitude)
    val edgePx = p.toPixels(edge, null)
    val dx = (edgePx.x - centerPx.x).toDouble()
    val dy = (edgePx.y - centerPx.y).toDouble()
    return sqrt(dx * dx + dy * dy).toFloat().coerceAtLeast(2f)
  }

  override fun draw(c: Canvas?, mapView: MapView?, shadow: Boolean) {
    if (c == null || mapView == null) return
    val centerPx = mapView.projection.toPixels(center, null)
    val cx = centerPx.x.toFloat()
    val cy = centerPx.y.toFloat()

    if (drawsArea(mapView.zoomLevelDouble, areaFromZoom)) {
      // REAL ground area, Google-Flood severity style: soft fill + crisp ring.
      fillPaint.alpha = 40
      c.drawCircle(cx, cy, baseRadiusPixels(mapView.projection), fillPaint)
      c.drawCircle(cx, cy, baseRadiusPixels(mapView.projection), ringPaint)
      if (pulsePeriodMs > 0 && active) {
        val now = System.currentTimeMillis()
        if (startMillis < 0) startMillis = now
        val t = ((now - startMillis) % pulsePeriodMs).toFloat() / pulsePeriodMs
        val wave = (1 - Math.cos((t * 2 * Math.PI).toDouble())).toFloat() / 2f
        if (wave > 0.05f) {
          pulsePaint.alpha = ((1f - wave) * 150).toInt().coerceIn(15, 150)
          c.drawCircle(
            cx, cy,
            baseRadiusPixels(mapView.projection) * (1f + wave * PULSE_EXPANSION),
            pulsePaint
          )
        }
        if (now - lastInvalidateMillis >= INVALIDATE_INTERVAL_MS) {
          lastInvalidateMillis = now
          mapView.postInvalidate()
        }
      }
    } else {
      // PIN DOT at overview zoom: constant screen size, white ring, optional
      // soft halo for the most urgent items only.
      val r = dotRadiusPx
      if (showHalo) c.drawCircle(cx, cy, r * 2.1f, haloPaint)
      c.drawCircle(cx, cy, r + 1.5f, dotRingPaint)
      c.drawCircle(cx, cy, r, dotPaint)
    }
  }

  override fun onSingleTapUp(e: MotionEvent?, mapView: MapView?): Boolean {
    if (e == null || mapView == null || onTapped == null) return false
    val centerPx = mapView.projection.toPixels(center, null)
    val distPx = sqrt(
      Math.pow((e.x - centerPx.x).toDouble(), 2.0) +
        Math.pow((e.y - centerPx.y).toDouble(), 2.0)
    )
    val hitRadius = if (drawsArea(mapView.zoomLevelDouble, areaFromZoom)) {
      baseRadiusPixels(mapView.projection)
    } else {
      dotRadiusPx * 2.2f // generous finger target around small dots
    }
    if (distPx <= hitRadius) {
      onTapped.invoke()
      return true
    }
    return false
  }
}
