package com.example.data.routing

import com.example.data.model.HazardZone
import kotlin.math.roundToInt

/**
 * In-memory cache of LIVE OSRM road routes so repeat views render the exact
 * road pathway instantly instead of flashing the straight preview corridor
 * while the network re-answers.
 *
 * The key binds destination + travel mode + origin grid (~500 m cells) +
 * hazard picture: moving to another grid cell, switching mode, picking
 * another shelter, or any change in the hazard set is a cache miss and the
 * caller revalidates over the network. Entries hold live road geometry only.
 *
 * Eviction semantics (audit B7 fix): a genuine LRU (least-recently-USED,
 * not least-recently-inserted — accessOrder LinkedHashMap makes get() mark
 * recency) plus a TTL. Routes are road geometry rather than fast-changing
 * telemetry, so a generous 20-minute TTL bounds staleness of the hazard
 * picture baked into a stored safety verdict without thrashing the shared
 * OSRM server on repeat views.
 */
class LiveRouteCache(
  private val maxEntries: Int = 30,
  /** How long a stored route stays valid. Non-positive disables expiry. */
  private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
  /** Injectable clock (tests pin expiry without sleeping). */
  private val now: () -> Long = System::currentTimeMillis
) {

  private data class Entry(val route: RouteResult, val storedAtMillis: Long)

  // accessOrder = true => get() re-inserts at the tail; keys.first() is
  // then the LEAST recently used entry, exactly what eviction must drop.
  private val map = LinkedHashMap<String, Entry>(16, 0.75f, true)

  @Synchronized
  fun get(key: String): RouteResult? {
    val entry = map[key] ?: return null
    if (ttlMillis > 0L && now() - entry.storedAtMillis > ttlMillis) {
      map.remove(key) // expired: drop and force revalidation
      return null
    }
    return entry.route
  }

  @Synchronized
  fun put(key: String, route: RouteResult) {
    if (!route.isLiveOsrm || route.pathPoints.isEmpty()) return
    map.remove(key)
    map[key] = Entry(route, now())
    while (map.size > maxEntries) {
      map.remove(map.keys.first())
    }
  }

  @Synchronized fun clear() = map.clear()

  @Synchronized fun size(): Int = map.size

  companion object {
    /** Origin grid cell size in degrees (~500 m) — jitter inside a cell reuses. */
    private const val GRID_DEGREES = 0.005

    /** Stored road geometry stays trusted for this long. */
    const val DEFAULT_TTL_MILLIS = 20L * 60L * 1000L

    fun key(
      zoneId: String,
      mode: TravelMode,
      origin: GeoPoint,
      hazards: List<HazardZone>
    ): String {
      val gridLat = (origin.lat / GRID_DEGREES).roundToInt()
      val gridLon = (origin.lon / GRID_DEGREES).roundToInt()
      val hazardSig = hazards.map { it.id }.sorted().hashCode()
      return "$zoneId|$mode|$gridLat|$gridLon|$hazardSig"
    }
  }
}
