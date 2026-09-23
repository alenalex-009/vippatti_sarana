package com.example.data.suitability

import com.example.data.routing.GeoPoint
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Offline coast-distance grid: one byte per cell holding the approximate
 * distance from that cell to the coastline in whole kilometers, over the
 * India bbox. Built by `tools/coast_distance_prepare.py` from the PUBLIC
 * DOMAIN Natural Earth 1:110m land polygons (source:
 * https://www.naturalearthdata.com). Sea cells and cells on the coast are 0;
 * >=255 km inland saturates at 255.
 *
 * This is a DERIVED, coarse (0.25 deg ~ 27 km) terrain fact, used only to
 * answer "is this site near the sea?" honestly. Outside the covered bbox the
 * answer is null — never an extrapolated guess.
 */
class CoastDistanceGrid private constructor(
  private val lonMin: Double,
  private val latMin: Double,
  private val lonMax: Double,
  private val latMax: Double,
  private val cols: Int,
  private val rows: Int,
  private val data: ByteArray
) {

  /** Whole-km distance to the coast at [point], or null outside coverage. */
  fun distanceKm(point: GeoPoint): Int? {
    if (point.lat < latMin || point.lat > latMax || point.lon < lonMin || point.lon > lonMax) {
      return null
    }
    val latStep = if (rows > 1) (latMax - latMin) / (rows - 1) else 0.0
    val lonStep = if (cols > 1) (lonMax - lonMin) / (cols - 1) else 0.0
    val r = if (latStep == 0.0) 0 else ((point.lat - latMin) / latStep).roundToInt()
    val c = if (lonStep == 0.0) 0 else ((point.lon - lonMin) / lonStep).roundToInt()
    return data[r * cols + c].toInt() and 0xFF
  }

  /**
   * True when the nearest-coast distance at the point's grid cell is within
   * [maxKm]. The grid is coarse (~27 km cells), so this is a terrain-level
   * signal, not a parcel-level claim. Null when the grid cannot answer — the
   * suitability engine treats null as an EXCLUDED factor, never as
   * "not coastal".
   */
  fun isNearCoast(point: GeoPoint, maxKm: Int = NEAR_COAST_KM): Boolean? =
    distanceKm(point)?.let { it <= maxKm }

  companion object {
    const val ASSET = "geo/coast_distance_india.bin"
    const val NEAR_COAST_KM = 5

    /** Header: 4 floats (lonMin latMin lonMax latMax) + 2 shorts (cols rows), LE. */
    fun load(stream: InputStream): CoastDistanceGrid? {
      return try {
        val all = stream.readBytes()
        if (all.size < 20) return null
        val buf = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN)
        val lonMin = buf.float.toDouble()
        val latMin = buf.float.toDouble()
        val lonMax = buf.float.toDouble()
        val latMax = buf.float.toDouble()
        val cols = buf.short.toInt() and 0xFFFF
        val rows = buf.short.toInt() and 0xFFFF
        if (cols <= 0 || rows <= 0 || all.size - 20 < cols * rows) return null
        val data = all.copyOfRange(20, 20 + cols * rows)
        CoastDistanceGrid(lonMin, latMin, lonMax, latMax, cols, rows, data)
      } catch (_: Exception) {
        null
      }
    }
  }
}
