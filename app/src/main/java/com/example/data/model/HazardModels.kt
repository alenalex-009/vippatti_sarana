package com.example.data.model

import com.example.data.routing.GeoPoint

/**
 * Extensible disaster-type registry. New Indian disaster types (cyclone,
 * earthquake, heat wave, drought, tsunami...) can be appended without touching
 * any engine cod — very consumer is type-driven.
 */
enum class HazardType(val label: String) {
  FLOOD("Flood"),
  LANDSLIDE("Landslide"),
  FIRE("Fire"),
  HEAVY_RAINFALL("Heavy Rainfall"),
  EARTHQUAKE("Earthquake"),
  CYCLONE("Cyclone"),
  WEATHER_ALERT("Weather Alert"),
  OTHER("Other Disaster")
}


/** Standard 4-step severity scale, visually distinguishable on the map. */
enum class HazardSeverity(val label: String) {
  LOW("Low"),
  MODERATE("Moderate"),
  HIGH("High"),
  EXTREME("Extreme");

  /**
   * Rank weight for "which hazard dominates the verdict". NOTE (audit B6
   * review): this is ordinal-based, so DECLARATION ORDER IS THE MODEL - the
   * scale is intentionally monotonic (Low < Moderate < High < Extreme) and
   * reordering the entries would silently change risk outcomes. Any future
   * severity change must keep this order or replace weight with an explicit
   * value. (Documented, not changed: no verified defect requires it.)
   */
  val weight: Int get() = ordinal + 1
}

/** Directional freshness trend for hazard intelligence. */
enum class HazardTrend(val label: String) {
  IMPROVING("Improving"),
  STABLE("Stable"),
  WORSENING("Worsening")
}

/**
 * One active hazard area. Hazard zones are rendered as LARGE translucent
 * circular areas with a subtle expanding/fading puls — ever tiny dots.
 */
data class HazardZone(
  val id: String,
  val name: String,
  val type: HazardType,
  val severity: HazardSeverity,
  val center: GeoPoint,
  /** Affected-area radius in meter — rives the size of the translucent zone. */
  val radiusMeters: Double,
  val riskLevel: String,
  val trend: HazardTrend,
  /** Free-form source/status field (provenance label shown in UI). */
  val sourceStatus: String,
  /** Timestamp/freshnes — poch millis of last update; 0 = field record data. */
  val lastUpdatedMillis: Long,
  val provenance: DataProvenance
)

/**
 * A structured safe zone / relief shelter with full carrying-capacity and
 * resource intelligence. All shelter data is field-record data; the structure
 * is ready for a verified government shelter registry to replace it later.
 */
data class SafeZone(
  val id: String,
  val name: String,
  val lat: Double,
  val lon: Double,
  val locationNote: String,
  // --- Carrying capacity ---
  val capacityTotal: Int,
  val capacityCurrent: Int,
  // --- Resource availability flags ---
  val waterAvailable: Boolean,
  val foodAvailable: Boolean,
  val electricityAvailable: Boolean,
  val sanitationAvailable: Boolean,
  val medicalSupport: Boolean,
  val accessibility: String,
  val womenChildrenSuitability: Boolean,
  val operatingStatus: String,
  val verificationStatus: String,
  val elevationNote: String,
  /**
   * Carrying-capacity inputs beyond bed spaces. All three are OPTIONAL: a
   * registry record that does not state them leaves them null, which the
   * capacity engine reports as "not provided" instead of zero.
   */
  val landAreaSquareMeters: Double? = null,
  val waterLitresPerDay: Double? = null,
  val toiletCount: Int? = null,
  val provenance: DataProvenance
) {
  val point: GeoPoint get() = GeoPoint(lat, lon)
  val availableCapacity: Int get() = (capacityTotal - capacityCurrent).coerceAtLeast(0)

  /** Structured capacity status: Available / Near Capacity / Full / Overflow Required. */
  val capacityStatus: CapacityStatus
    get() = when {
      availableCapacity <= 0 -> CapacityStatus.FULL
      availableCapacity < CAPACITY_NEAR_THRESHOLD * capacityTotal -> CapacityStatus.NEAR_CAPACITY
      else -> CapacityStatus.AVAILABLE
    }

  companion object {
    const val CAPACITY_NEAR_THRESHOLD = 0.15
  }
}

/**
 * Carrying-capacity classification used by shelter intelligence and routing.
 */
enum class CapacityStatus(val label: String) {
  AVAILABLE("Available"),
  NEAR_CAPACITY("Near Capacity"),
  FULL("Full"),
  OVERFLOW_REQUIRED("Overflow Required");

  /** A shelter is recommendable only while capacity remains. */
  val acceptsNewOccupants: Boolean get() = this == AVAILABLE || this == NEAR_CAPACITY
}
