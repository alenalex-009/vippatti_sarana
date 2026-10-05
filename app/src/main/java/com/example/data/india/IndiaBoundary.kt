package com.example.data.india

/**
 * India-wide administrative hierarchy — generic, no hardcoded state names.
 * COUNTRY → STATE → DISTRICT → SUBDISTRICT → VILLAGE/TOWN/WARD
 */
enum class AdminLevel(val label: String) {
  COUNTRY("Country"),
  STATE("State"),
  DISTRICT("District"),
  SUBDISTRICT("Subdistrict / Taluk"),
  VILLAGE("Village / Town / Ward"),
  REGION("Region"),
  OTHER("Other")
}

/**
 * One administrative boundary record, generic across India.
 * Every record carries full provenance.
 */
data class IndiaBoundary(
  val id: String,
  val name: String,
  val level: AdminLevel,
  val country: String? = "India",
  val state: String? = null,
  val district: String? = null,
  val subdistrict: String? = null,
  val village: String? = null,
  val geometry: BoundaryGeometry? = null,
  val populationTotal: Long? = null,
  val provenance: IndiaProvenance
)

/** Geometry payload for a boundary — the actual shape or a representative point. */
sealed class BoundaryGeometry {
  abstract val type: String
  /** Well-known GeoJSON-style coordinates; interpretation depends on type. */
  abstract val coordinates: List<Any>

  data class Polygon(override val type: String = "Polygon", override val coordinates: List<Any>) :
    BoundaryGeometry()

  data class MultiPolygon(override val type: String = "MultiPolygon", override val coordinates: List<Any>) :
    BoundaryGeometry()

  data class Point(override val type: String = "Point", override val coordinates: List<Any>) :
    BoundaryGeometry()

  data class GeometryCollection(override val type: String = "GeometryCollection", override val coordinates: List<Any>) :
    BoundaryGeometry()
}

/** Result of an India-wide boundary lookup — honest when partial. */
data class BoundaryLookupResult(
  val country: List<IndiaBoundary> = emptyList(),
  val states: List<IndiaBoundary> = emptyList(),
  val districts: List<IndiaBoundary> = emptyList(),
  val subdistricts: List<IndiaBoundary> = emptyList(),
  val villages: List<IndiaBoundary> = emptyList(),
  val scopeNote: String? = null
) {
  val isComplete: Boolean get() = country.isNotEmpty() && states.isNotEmpty() && districts.isNotEmpty()
  val isPartial: Boolean get() = country.isEmpty() && states.isEmpty() && districts.isEmpty()
}

/**
 * Administrative hierarchy engine — pure Kotlin, no Android dependency.
 * Looks up the generic India-wide hierarchy by coordinates or place name.
 */
object IndiaHierarchy {

  /** Look up boundaries that contain a point — returns whatever the loaded data supports. */
  fun lookupByLocation(lat: Double, lon: Double, boundaries: List<IndiaBoundary>): BoundaryLookupResult {
    val country = boundaries.filter { it.level == AdminLevel.COUNTRY && contains(it, lat, lon) }
    val states = boundaries.filter { it.level == AdminLevel.STATE && contains(it, lat, lon) }
    val districts = boundaries.filter { it.level == AdminLevel.DISTRICT && contains(it, lat, lon) }
    val subdistricts = boundaries.filter { it.level == AdminLevel.SUBDISTRICT && contains(it, lat, lon) }
    val villages = boundaries.filter { it.level == AdminLevel.VILLAGE && contains(it, lat, lon) }
    return BoundaryLookupResult(
      country = country,
      states = states,
      districts = districts,
      subdistricts = subdistricts,
      villages = villages,
      scopeNote = when {
        country.isNotEmpty() -> "India-wide scope"
        states.isNotEmpty() -> "State scope: ${states.map { it.name }.joinToString(", ")}"
        districts.isNotEmpty() -> "District scope: ${districts.map { it.name }.joinToString(", ")}"
        else -> "National scope — location not matched to loaded boundaries"
      }
    )
  }

  /** Look up by administrative name — exact string match, case-insensitive for states/districts. */
  fun lookupByName(name: String, level: AdminLevel, boundaries: List<IndiaBoundary>): List<IndiaBoundary> {
    val q = name.trim().lowercase()
    return boundaries.filter {
      it.level == level && it.name.trim().lowercase() == q
    }
  }

  /** All records at a given level. */
  fun filterByLevel(level: AdminLevel, boundaries: List<IndiaBoundary>): List<IndiaBoundary> =
    boundaries.filter { it.level == level }

  private fun contains(b: IndiaBoundary, lat: Double, lon: Double): Boolean {
    val geom = b.geometry ?: return false
    return when (geom) {
      is BoundaryGeometry.Point -> geom.coordinates.size >= 2 &&
        (geom.coordinates[0] as? Number)?.toDouble() == lon &&
        (geom.coordinates[1] as? Number)?.toDouble() == lat
      is BoundaryGeometry.Polygon -> pointInPolygon(lat, lon, geom)
      is BoundaryGeometry.MultiPolygon -> geom.coordinates.any { ringList ->
        ringList is List<*> && ringList.any { ring ->
          ring is List<*> && pointInPolygonLatLon(lat, lon, ring)
        }
      }
      is BoundaryGeometry.GeometryCollection -> geom.coordinates.any { coords ->
        if (coords !is List<*>) return@any false
        coords.any { c ->
          val m = c as? Map<*, *> ?: return@any false
          val coordinateList = m["coordinates"] as? List<*> ?: return@any false
          m["type"] == "Point" &&
            coordinateList.size >= 2 &&
            (coordinateList[0] as? Number)?.toDouble() == lon &&
            (coordinateList[1] as? Number)?.toDouble() == lat
        }
      }
      else -> false
    }
  }

  private fun pointInPolygon(lat: Double, lon: Double, poly: BoundaryGeometry.Polygon): Boolean {
    val rings = poly.coordinates
    if (rings.isEmpty()) return false
    return rings.any { ring ->
      ring is List<*> && pointInPolygonLatLon(lat, lon, ring)
    }
  }

  private fun pointInPolygonLatLon(lat: Double, lon: Double, ring: List<*>): Boolean {
    val pts = ring.mapNotNull {
      if (it is List<*> && it.size >= 2) {
        val lng = it[0] as? Number ?: return@mapNotNull null
        val lt = it[1] as? Number ?: return@mapNotNull null
        Pair(lt.toDouble(), lng.toDouble())
      } else null
    }
    if (pts.size < 3) return false
    var inside = false
    var j = pts.size - 1
    for (i in pts.indices) {
      val (yi, xi) = pts[i]
      val (yj, xj) = pts[j]
      if ((xi > lon) != (xj > lon) && lat < (yj - yi) * (lon - xi) / (xj - xi) + yi) inside = !inside
      j = i
    }
    return inside
  }
}