package com.example.data.disaster

/**
 * ============================================================================
 * MAP LAYER REGISTRY — user-togglable layers with zoom-based visibility.
 * ============================================================================
 *
 * The India overview must stay usable: at national zoom only aggregated,
 * high-signal layers render; detail layers appear progressively as the user
 * zooms into state/district/city level. Off by default: heavy point layers.
 */
enum class DisasterLayer(
  val label: String,
  val defaultOn: Boolean,
  /** Below this osmdroid zoom level the layer is hidden (zoom-based LOD). */
  val minZoom: Double
) {
  OFFICIAL_ALERTS("Official Alerts", true, 3.5),
  EARTHQUAKES("Earthquakes", true, 3.5),
  // FIRMS FIX: visible by default (was false — real hotspots were fetched but
  // never drawn). Clustering keeps the national view readable.
  ACTIVE_FIRES("Active Fires", true, 5.0),
  USER_REPORTS("User Reports", true, 8.0),
  SAFE_ZONES("Safe Zones", true, 8.0),
  EVACUATION_ROUTE("Evacuation Route", true, 0.0),
  MY_LOCATION("My GPS Location", true, 0.0);

  fun isVisibleAt(zoomLevel: Double, enabled: Boolean): Boolean =
    enabled && zoomLevel >= minZoom
}

/**
 * Point-cluster generalization for India overview: at low zooms, dense fire
 * detections collapse into aggregated cells so the national view stays
 * readable. Pure geometry — unit-testable.
 */
object MarkerGeneralizer {

  /**
   * One aggregate marker: the event that REPRESENTS the grid cell plus what the
   * cell contains. [maxFrpMegawatts] is the strongest real measurement among
   * the merged detections (null when none of them carried one).
   */
  data class DetectionCluster(
    val representative: DisasterEvent,
    val memberCount: Int,
    val maxFrpMegawatts: Double?
  )

  /**
   * Grid-cluster [events] at [zoomLevel]: events inside one grid cell merge
   * into a single aggregate marker. The representative is chosen by INTENSITY:
   * fire detections rank by their provider-measured FRP, everything else by
   * hazard severity (FRP breaks ties across types). At high zooms
   * (>= [DETAIL_ZOOM]) nothing is clustered.
   */
  fun clusters(events: List<DisasterEvent>, zoomLevel: Double): List<DetectionCluster> {
    if (zoomLevel >= DETAIL_ZOOM || events.size < 2) {
      return events.map { DetectionCluster(it, memberCount = 1, maxFrpMegawatts = FireIntensityScale.frpOf(it)) }
    }
    val cellSize = cellDegrees(zoomLevel)
    val counts = LinkedHashMap<String, Int>()
    val grid = LinkedHashMap<String, DisasterEvent>()
    val cellsWithOtherGeometry = mutableListOf<DetectionCluster>()
    for (event in events) {
      val point = when (val g = event.geometry) {
        is EventGeometry.Point -> g
        else -> {
          // Polygons/lines are rendered directly, not clustered.
          cellsWithOtherGeometry += DetectionCluster(
            representative = event,
            memberCount = 1,
            maxFrpMegawatts = FireIntensityScale.frpOf(event)
          )
          continue
        }
      }
      val key = "${Math.floor(point.lat / cellSize)}:${Math.floor(point.lon / cellSize)}"
      counts[key] = (counts[key] ?: 0) + 1
      val existing = grid[key]
      if (existing == null || intensityRank(event) > intensityRank(existing)) {
        grid[key] = event
      }
    }
    val clustered = grid.map { (key, representative) ->
      DetectionCluster(
        representative = representative,
        memberCount = counts[key] ?: 1,
        maxFrpMegawatts = maxFrpInCell(events, key, cellSize)
      )
    }
    return cellsWithOtherGeometry + clustered
  }

  /**
   * Ordering used to pick the cell representative, from real fields only:
   * hazard severity first (so a severe quake is never hidden behind a weak
   * fire), then the provider's own FRP measurement inside the same severity
   * band, then the most recent observation. Nothing is chosen arbitrarily.
   */
  private fun intensityRank(event: DisasterEvent): Double {
    val frpNormalized = (FireIntensityScale.frpOf(event) ?: 0.0)
      .div(FireIntensityScale.EXTREME_MW)
      .coerceIn(0.0, 1.0)
    return event.severity.weight * 1_000.0 +
      frpNormalized * 100.0 +
      event.observedAtMillis / 1.0e15
  }

  private fun maxFrpInCell(events: List<DisasterEvent>, key: String, cellSize: Double): Double? =
    events.mapNotNull { event ->
      val point = event.geometry as? EventGeometry.Point ?: return@mapNotNull null
      val cell = "${Math.floor(point.lat / cellSize)}:${Math.floor(point.lon / cellSize)}"
      if (cell != key) null else FireIntensityScale.frpOf(event)
    }.maxOrNull()

  fun cellDegrees(zoomLevel: Double): Double = when {
    zoomLevel < 4.0 -> 2.0
    zoomLevel < 5.0 -> 1.0
    zoomLevel < 6.0 -> 0.5
    zoomLevel < 7.0 -> 0.25
    else -> 0.1
  }

  const val DETAIL_ZOOM = 8.0
}
