package com.example.data.model

import com.example.data.india.MaybeNumber
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
  /**
   * Maximum accommodation. `null` means the source does not STATE a capacity,
   * which is a different fact from "capacity is zero" or "the shelter is full".
   *
   * Defaults to [DEFAULT_CAPACITY_TOTAL] so the many existing constructor calls
   * (pilot data, demo networks, tests) keep their current meaning. Only an
   * explicitly registry-sourced record passes null, which is the honest state
   * for a facility whose capacity was never published.
   */
  val capacityTotal: Int? = DEFAULT_CAPACITY_TOTAL,
  val capacityCurrent: Int? = DEFAULT_CAPACITY_CURRENT,
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

  /** True when the source published both a total and a current occupancy. */
  val hasKnownCapacity: Boolean get() = capacityTotal != null && capacityCurrent != null

  /**
   * Remaining capacity, or UNKNOWN.
   *
   * Never collapses to 0: a shelter whose occupancy was not published has not
   * been shown to be full, and reporting 0 would reject it during ranking as
   * "Full — no remaining capacity", a factual claim about a real facility that
   * we cannot support.
   */
  val availableCapacity: MaybeNumber
    get() = if (hasKnownCapacity) {
      MaybeNumber.of((capacityTotal!! - capacityCurrent!!).coerceAtLeast(0).toDouble())
    } else {
      MaybeNumber.UNKNOWN
    }

  /** Structured capacity status: Available / Near Capacity / Full / Unknown. */
  val capacityStatus: CapacityStatus
    get() {
      val total = capacityTotal
      val current = capacityCurrent
      if (total == null || current == null) return CapacityStatus.UNKNOWN
      val available = (total - current).coerceAtLeast(0)
      return when {
        available <= 0 -> CapacityStatus.FULL
        available < CAPACITY_NEAR_THRESHOLD * total -> CapacityStatus.NEAR_CAPACITY
        else -> CapacityStatus.AVAILABLE
      }
    }

  companion object {
    const val CAPACITY_NEAR_THRESHOLD = 0.15

    /**
     * Default capacity used by every pre-existing constructor call (pilot and
     * demo data). It is the status quo value, not a fact about any facility.
     */
    const val DEFAULT_CAPACITY_TOTAL = 200
    const val DEFAULT_CAPACITY_CURRENT = 60
  }
}

/**
 * Carrying-capacity classification used by shelter intelligence and routing.
 *
 * [UNKNOWN] is a first-class state, not an error: it means the source published
 * no capacity figure. Such a facility is still a valid evacuation destination —
 * it is simply not ranked on capacity, and it is never rejected for being full.
 */
enum class CapacityStatus(val label: String) {
  AVAILABLE("Available"),
  NEAR_CAPACITY("Near Capacity"),
  FULL("Full"),
  OVERFLOW_REQUIRED("Overflow Required"),
  UNKNOWN("Capacity unknown");

  /** A shelter is recommendable only while capacity remains or is unstated. */
  val acceptsNewOccupants: Boolean
    get() = this == AVAILABLE || this == NEAR_CAPACITY || this == UNKNOWN
}
