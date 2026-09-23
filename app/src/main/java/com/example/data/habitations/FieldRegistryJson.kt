package com.example.data.habitations

import com.example.data.disaster.IndiaGeo
import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import org.json.JSONArray
import org.json.JSONObject

/**
 * ============================================================================
 * FIELD REGISTRY — wire format + store boundary (SIH 26191 "authority input")
 * ============================================================================
 *
 * Lets an operator enter REAL shelter and habitation records on the device
 * (survey data), persist them, and feed them into the SAME tested engines
 * (SafeZoneEvaluator, CarryingCapacityEngine, HabitationPriorityEngine).
 *
 * Honesty rules:
 * - every stored record keeps its FIELD-RECORD provenance; nothing here can
 *   produce a "verified government" label;
 * - geometry outside India is REJECTED with a stated reason, never silently
 *   dropped and never auto-corrected;
 * - a corrupt file decodes to empty + reason — the app keeps working.
 */
data class DecodedRegistry<T>(
  val records: List<T>,
  /** "<id>: <reason>" per rejected entry, for honest surfacing. */
  val rejected: List<String>
)

interface FieldRegistryStore {
  fun loadShelters(): List<SafeZone>
  fun saveShelters(zones: List<SafeZone>)
  fun loadHabitations(): List<Habitation>
  fun saveHabitations(habitations: List<Habitation>)
}

/** Simple in-memory implementation (tests + pre-Android fallback). */
class InMemoryFieldRegistryStore : FieldRegistryStore {
  private var shelters: List<SafeZone> = emptyList()
  private var habitations: List<Habitation> = emptyList()
  override fun loadShelters(): List<SafeZone> = shelters
  override fun saveShelters(zones: List<SafeZone>) { shelters = zones }
  override fun loadHabitations(): List<Habitation> = habitations
  override fun saveHabitations(habitations: List<Habitation>) {
    this.habitations = habitations
  }
}

object FieldRegistryJson {

  // ---- shelters ------------------------------------------------------------

  fun encodeShelters(zones: List<SafeZone>): String = JSONArray().apply {
    zones.forEach { zone ->
      put(JSONObject().apply {
        put("id", zone.id)
        put("name", zone.name)
        put("lat", zone.lat)
        put("lon", zone.lon)
        put("locationNote", zone.locationNote)
        put("capacityTotal", zone.capacityTotal)
        put("capacityCurrent", zone.capacityCurrent)
        put("waterAvailable", zone.waterAvailable)
        put("foodAvailable", zone.foodAvailable)
        put("electricityAvailable", zone.electricityAvailable)
        put("sanitationAvailable", zone.sanitationAvailable)
        put("medicalSupport", zone.medicalSupport)
        put("accessibility", zone.accessibility)
        put("womenChildrenSuitability", zone.womenChildrenSuitability)
        put("operatingStatus", zone.operatingStatus)
        put("verificationStatus", zone.verificationStatus)
        put("elevationNote", zone.elevationNote)
        zone.landAreaSquareMeters?.let { put("landAreaSquareMeters", it) }
        zone.waterLitresPerDay?.let { put("waterLitresPerDay", it) }
        zone.toiletCount?.let { put("toiletCount", it) }
        put("source", zone.provenance.source)
        put("classification", zone.provenance.classification.name)
      })
    }
  }.toString()

  fun decodeShelters(json: String?): DecodedRegistry<SafeZone> {
    if (json.isNullOrBlank()) return DecodedRegistry(emptyList(), emptyList())
    val records = mutableListOf<SafeZone>()
    val rejected = mutableListOf<String>()
    try {
      val array = JSONArray(json)
      for (i in 0 until array.length()) {
        val o = array.optJSONObject(i)
        if (o == null) {
          rejected += "entry-$i: not an object"
          continue
        }
        val id = o.optString("id").ifBlank { "entry-$i" }
        val lat = o.optDouble("lat", Double.NaN)
        val lon = o.optDouble("lon", Double.NaN)
        if (lat.isNaN() || lon.isNaN() || !IndiaGeo.contains(GeoPoint(lat, lon))) {
          rejected += "$id: coordinates $lat,$lon are outside India — not imported"
          continue
        }
        records += SafeZone(
          id = id,
          name = o.optString("name", "Unnamed shelter"),
          lat = lat, lon = lon,
          locationNote = o.optString("locationNote", ""),
          capacityTotal = o.optInt("capacityTotal", 0),
          capacityCurrent = o.optInt("capacityCurrent", 0),
          waterAvailable = o.optBoolean("waterAvailable", false),
          foodAvailable = o.optBoolean("foodAvailable", false),
          electricityAvailable = o.optBoolean("electricityAvailable", false),
          sanitationAvailable = o.optBoolean("sanitationAvailable", false),
          medicalSupport = o.optBoolean("medicalSupport", false),
          accessibility = o.optString("accessibility", "Road"),
          womenChildrenSuitability = o.optBoolean("womenChildrenSuitability", false),
          operatingStatus = o.optString("operatingStatus", "OPEN"),
          verificationStatus = o.optString("verificationStatus", "FIELD RECORD"),
          elevationNote = o.optString("elevationNote", ""),
          landAreaSquareMeters = o.takeIf { it.has("landAreaSquareMeters") }?.optDouble("landAreaSquareMeters")
            ?.takeIf { !it.isNaN() },
          waterLitresPerDay = o.takeIf { it.has("waterLitresPerDay") }?.optDouble("waterLitresPerDay")
            ?.takeIf { !it.isNaN() },
          toiletCount = o.takeIf { it.has("toiletCount") }?.optInt("toiletCount")?.takeIf { it > 0 },
          provenance = DataProvenance(
            source = o.optString("source", "Field entry — operator device"),
            classification = runCatching { DataClassification.valueOf(o.optString("classification")) }
              .getOrDefault(DataClassification.OBSERVED)
          )
        )
      }
    } catch (_: Exception) {
      return DecodedRegistry(emptyList(), listOf("registry file unreadable — treated as empty"))
    }
    return DecodedRegistry(records, rejected)
  }

  // ---- habitations -----------------------------------------------------------

  fun encodeHabitations(habitations: List<Habitation>): String = JSONArray().apply {
    habitations.forEach { hab ->
      put(JSONObject().apply {
        put("id", hab.id)
        put("name", hab.name)
        put("lat", hab.point.lat)
        put("lon", hab.point.lon)
        hab.population?.let {
          put("population", JSONObject().apply {
            put("value", it.value)
            put("classification", it.classification.name)
            put("source", it.source)
          })
        }
        hab.vulnerableShare?.let { put("vulnerableShare", it.toDouble()) }
        put("historicalEventCount", hab.historicalEventCount)
      })
    }
  }.toString()

  fun decodeHabitations(json: String?): DecodedRegistry<Habitation> {
    if (json.isNullOrBlank()) return DecodedRegistry(emptyList(), emptyList())
    val records = mutableListOf<Habitation>()
    val rejected = mutableListOf<String>()
    try {
      val array = JSONArray(json)
      for (i in 0 until array.length()) {
        val o = array.optJSONObject(i)
        if (o == null) {
          rejected += "entry-$i: not an object"
          continue
        }
        val id = o.optString("id").ifBlank { "entry-$i" }
        val lat = o.optDouble("lat", Double.NaN)
        val lon = o.optDouble("lon", Double.NaN)
        if (lat.isNaN() || lon.isNaN() || !IndiaGeo.contains(GeoPoint(lat, lon))) {
          rejected += "$id: coordinates $lat,$lon are outside India — not imported"
          continue
        }
        val popObj = o.optJSONObject("population")
        val population = popObj?.let {
          val v = it.optInt("value", 0)
          if (v <= 0) null else PopulationInput(
            value = v,
            classification = runCatching { DataClassification.valueOf(it.optString("classification")) }
              .getOrDefault(DataClassification.ESTIMATED),
            source = it.optString("source", "Field entry")
          )
        }
        records += Habitation(
          id = id,
          name = o.optString("name", "Unnamed habitation"),
          point = GeoPoint(lat, lon),
          population = population,
          vulnerableShare = o.takeIf { it.has("vulnerableShare") }
            ?.optDouble("vulnerableShare")?.takeIf { !it.isNaN() }?.toFloat()?.coerceIn(0f, 1f),
          terrainVerdict = null, // assessed live, never stored
          historicalEventCount = o.optInt("historicalEventCount", 0).coerceAtLeast(0)
        )
      }
    } catch (_: Exception) {
      return DecodedRegistry(emptyList(), listOf("registry file unreadable — treated as empty"))
    }
    return DecodedRegistry(records, rejected)
  }
}
