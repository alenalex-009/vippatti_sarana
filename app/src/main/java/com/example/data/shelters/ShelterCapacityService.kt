package com.example.data.shelters

import com.example.data.india.MaybeNumber
import com.example.data.model.CapacityStatus
import com.example.data.model.SafeZone

/**
 * Carrying-capacity intelligence.
 *
 * Works on the current stored occupancy data today and is structured for
 * future dynamic capacity updates: occupancy flows in as SafeZone records
 * (from a shelter registry feed), and every derived calculation below adapts
 * automatically. No live feed is claimed at this stage.
 */
object ShelterCapacityService {

  /**
   * Capacity line items shown in the shelter detail sheet, e.g.
   * "Total Capacity: 500 / Current Occupancy: 320 / Available Capacity: 180".
   *
   * Every quantity is a [MaybeNumber]: a registry row that publishes no
   * capacity figure yields UNKNOWN, never 0. Collapsing to 0 would render the
   * facility as "0 / 0 occupied" — indistinguishable from a genuinely full
   * shelter, and a claim about a real facility that cannot be supported.
   */
  data class CapacityReport(
    val zone: SafeZone,
    val totalCapacity: MaybeNumber,
    val currentOccupancy: MaybeNumber,
    val availableCapacity: MaybeNumber,
    val status: CapacityStatus
  ) {
    val statusLabel: String get() = status.label
    val acceptsNewOccupants: Boolean get() = status.acceptsNewOccupants

    /** True when the source published enough to state occupancy. */
    val hasKnownFigures: Boolean
      get() = totalCapacity.isKnown && currentOccupancy.isKnown && availableCapacity.isKnown

    /** Occupancy as a whole-number percentage, or null when unstatable. */
    val occupancyPercent: Int?
      get() = if (hasKnownFigures && totalCapacity.value!! > 0.0) {
        (((currentOccupancy.value!! / totalCapacity.value!!) * 100.0).toInt()).coerceIn(0, 100)
      } else null

    /**
     * One-line capacity statement for the UI. Never prints "0 / 0" for an
     * unstated capacity — that reads as "full", a factual claim.
     */
    fun label(): String = when {
      !hasKnownFigures -> "Capacity unavailable"
      status == CapacityStatus.FULL -> "Full — no remaining capacity"
      else -> "${currentOccupancy.value!!.toInt()} / ${totalCapacity.value!!.toInt()} occupied"
    }
  }

  fun report(zone: SafeZone): CapacityReport = CapacityReport(
    zone = zone,
    totalCapacity = if (zone.capacityTotal != null) MaybeNumber.of(zone.capacityTotal!!.toDouble())
      else MaybeNumber.UNKNOWN,
    currentOccupancy = if (zone.capacityCurrent != null) MaybeNumber.of(zone.capacityCurrent!!.toDouble())
      else MaybeNumber.UNKNOWN,
    availableCapacity = zone.availableCapacity,
    status = zone.capacityStatus
  )

  /**
   * Overflow recommendation: when the primary shelter is full, the nearest
   * shelter with meaningful remaining capacity is suggested as the overflow
   * destination, and a redistribution note is produced. Returns null when the
   * whole network is saturated.
   */
  fun overflowRecommendation(
      primary: SafeZone,
      zones: List<SafeZone>,
      distanceTo: (SafeZone) -> Double
    ): OverflowRecommendation? {
      if (primary.capacityStatus.acceptsNewOccupants) return null
      // Only divert to an alternative whose remaining capacity is actually
      // PUBLISHED. Recommending a second facility on the strength of an
      // unstated number would repeat the same guess twice over.
      val alternative = zones
        .filter { it.id != primary.id && it.capacityStatus.acceptsNewOccupants }
        .filter { it.availableCapacity.isKnown && it.availableCapacity.value!! >= OVERFLOW_MIN_SPOTS }
        .minByOrNull { distanceTo(it) }
        ?: return null
      return OverflowRecommendation(
        fullShelter = primary,
        overflowShelter = alternative,
        redistributionNote = "${primary.name} is at capacity. Overflow evacuees are being " +
          "reassigned to ${alternative.name} (${alternative.availableCapacity.value!!.toInt()} spots open)."
      )
    }

  data class OverflowRecommendation(
    val fullShelter: SafeZone,
    val overflowShelter: SafeZone,
    val redistributionNote: String
  )

  /**
   * Predicted capacity pressure once [incomingPeople] more evacuees arrive at
   * [zone]. Returns the projected status — used by overflow planning. This is
   * an ESTIMATED projection, not a live occupancy reading.
   */
  fun projectedStatus(zone: SafeZone, incomingPeople: Int): CapacityStatus {
      val total = zone.capacityTotal
      val current = zone.capacityCurrent
      // Without a published total we cannot project anything. UNKNOWN, not
      // OVERFLOW_REQUIRED — "this will overflow" is an unsupportable claim.
      if (total == null || current == null) return CapacityStatus.UNKNOWN
      val projectedOccupancy = current + incomingPeople
      val projectedAvailable = total - projectedOccupancy
      return when {
        projectedAvailable < 0 -> CapacityStatus.OVERFLOW_REQUIRED
        projectedAvailable < SafeZone.CAPACITY_NEAR_THRESHOLD * total -> CapacityStatus.NEAR_CAPACITY
        else -> CapacityStatus.AVAILABLE
      }
    }

  private const val OVERFLOW_MIN_SPOTS = 10
}
