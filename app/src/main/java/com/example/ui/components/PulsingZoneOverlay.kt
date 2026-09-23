package com.example.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/**
 * A LARGE translucent circular zone (hazard or safe-zone area) with a subtle
 * expanding/fading pulse animation — the primary visual language of the app:
 *
 *   HAZARD     = large faded danger area
 *   SAFE ZONE  = large faded safe area
 *
 * The pulse ring expands from the base radius and fades out as it grows,
 * communicating an active area without tiny dot markers.
 */
class PulsingZoneOverlay(
  private val center: OsmGeoPoint,
  private val radiusMeters: Double,
  baseColorArgb: Int,
  private val pulsePeriodMs: Long,
  private val onZoneTapped: (() -> Unit)? = null
) : MarkerOverlay() {

  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = baseColorArgb
    alpha = 46            // large, heavily faded translucent fill
    style = Paint.Style.FILL
  }
  private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = baseColorArgb
    alpha = 200
    style = Paint.Style.STROKE
    strokeWidth = 2.5f
  }
  private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = baseColorArgb
    style = Paint.Style.STROKE
    strokeWidth = 4f
  }

  private var startMillis = -1L

  /** Coarse redraw throttle so many pulsing overlays never spam invalidations. */
  private var lastInvalidateMillis = -1L

  /**
   * False once [stop] has been called.
   *
   * ROTATION/CRASH FIX: the pulse loop used to call `mapView.postInvalidate()`
   * unconditionally every 66 ms forever. During Activity recreation the map tree
   * is disposed while invalidate callbacks are still queued, so osmdroid ended up
   * drawing against an already-detached tile provider. Every overlay is now
   * stopped BEFORE the MapView is torn down (see
   * OsmMapControllerHolder.cleanup), and a stopped overlay stops invalidating.
   */
  @Volatile
  private var active = true

  /** Permanently stops this overlay's animation + invalidations. Idempotent. */
  override fun stop() {
    active = false
  }

  /**
   * Base-circle pixel radius derived from two projected points (center and a
   * point radius meters due north) so the zone keeps its real-world size
   * across zooms — a large ground-truth area, never a tiny dot.
   */
  private fun baseRadiusPixels(p: Projection): Float {
    val centerPx = p.toPixels(center, null)
    val edgeGeo = offsetMetersNorth(center, radiusMeters)
    val edgePx = p.toPixels(edgeGeo, null)
    val dx = (edgePx.x - centerPx.x).toDouble()
    val dy = (edgePx.y - centerPx.y).toDouble()
    return Math.sqrt(dx * dx + dy * dy).toFloat().coerceAtLeast(30f)
  }

  private fun offsetMetersNorth(origin: OsmGeoPoint, meters: Double): OsmGeoPoint {
    val dLat = meters / 111_139.0
    return OsmGeoPoint(origin.latitude + dLat, origin.longitude)
  }

  override fun draw(c: Canvas?, mapView: MapView?, shadow: Boolean) {
    if (c == null || mapView == null) return
    // A stopped overlay still paints its static circle (so the released frame
    // is not blank) but never schedules another redraw.
    val centerPx = mapView.projection.toPixels(center, null)
    val baseRadiusPx = baseRadiusPixels(mapView.projection)

    // Base translucent fill + outline (large faded area).
    fillPaint.alpha = 46
    c.drawCircle(centerPx.x.toFloat(), centerPx.y.toFloat(), baseRadiusPx, fillPaint)
    c.drawCircle(centerPx.x.toFloat(), centerPx.y.toFloat(), baseRadiusPx, strokePaint)

    // Expanding/fading pulse ring — subtle "active zone" animation.
    if (pulsePeriodMs > 0 && active) {
      val now = System.currentTimeMillis()
      if (startMillis < 0) startMillis = now
      val t = ((now - startMillis) % pulsePeriodMs).toFloat() / pulsePeriodMs
      // Smooth 0 -> 1 -> 0 wave.
      val wave = (1 - Math.cos((t * 2 * Math.PI).toDouble())).toFloat() / 2f
      if (wave > 0.05f) {
        val pulseRadius = baseRadiusPx * (1f + wave * PULSE_EXPANSION)
        pulsePaint.alpha = ((1f - wave) * 170).toInt().coerceIn(20, 170)
        c.drawCircle(centerPx.x.toFloat(), centerPx.y.toFloat(), pulseRadius, pulsePaint)
      }
      // Continuous animation — request redraws at ~15 fps (never every
      // frame) so large multi-zone maps stay smooth on low-end devices.
      val nowSync = System.currentTimeMillis()
      if (active && nowSync - lastInvalidateMillis >= INVALIDATE_INTERVAL_MS) {
        lastInvalidateMillis = nowSync
        mapView.postInvalidate()
      }
    }
  }

  override fun onSingleTapUp(e: MotionEvent?, mapView: MapView?): Boolean {
    if (e == null || mapView == null) return false
    val proj = mapView.projection
    val centerPx = proj.toPixels(center, null)
    val radiusPx = baseRadiusPixels(proj)
    val distPx = Math.sqrt(
      Math.pow((e.x - centerPx.x).toDouble(), 2.0) +
        Math.pow((e.y - centerPx.y).toDouble(), 2.0)
    )
    if (distPx <= radiusPx) {
      onZoneTapped?.invoke()
      return true
    }
    return false
  }

  companion object {
    private const val PULSE_EXPANSION = 0.35f
    private const val INVALIDATE_INTERVAL_MS = 66L // ~15 fps
  }
}
