package com.example.data.shelters

import com.example.data.india.MaybeNumber
import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.routing.GeoPoint

/**
 * ============================================================================
 * SHELTER REGISTRY — import-ready schema with honest nullability
 * ============================================================================
 *
 * Section 14/16 requirements encoded here:
 *
 *  - EVERY numeric field is nullable. `capacity: Int` cannot express "the
 *    registry does not state a capacity", and a defaulted 0 would be
 *    indistinguishable from a genuinely full shelter. So capacity, occupancy
 *    and every resource quantity are [MaybeNumber].
 *  - Verification status is a first-class enum, not a free-text string, so a
 *    SIMULATED facility can never be filtered into the real list by accident.
 *  - CAPACITY / SUITABILITY / DEMAND stay separate concepts. This record
 *    carries facility capacity only; it never carries population or demand.
 *
 * No authoritative India-wide shelter registry is reachable from this
 * environment (NDEM is login-gated; OSM has no shelter designation at this
 * coverage). The schema below is therefore the IMPORT TARGET: when an official
 * registry is obtained, rows load into it without any model change. Until
 * then LIVE mode reports that no verified centre exists rather than promoting
 * a school POI.
 */

/** How much confidence we have that a facility is an evacuation centre. */
enum class ShelterVerification(val label: String, val countsAsReal: Boolean) {
  /** Confirmed against an official registry. */
  VERIFIED("Verified official centre", true),

  /** Derived from an authoritative layer that designates shelters. */
  OFFICIAL_DERIVED("Official-derived", true),

  /** Submitted by a user; unconfirmed. */
  COMMUNITY_REPORTED("Community-reported", false),

  /** A computed estimate. Never presented as a real facility. */
  ESTIMATED("Estimated", false),

  /** Demo-only. Must be visibly labelled and must never reach LIVE mode. */
  SIMULATED("SIMULATED", false),

  /** Verification state itself is unknown. */
  UNKNOWN("Unknown", false)
}

/** Facility type, as a registry would record it. */
enum class ShelterFacilityType(val label: String) {
  DEDICATED_SHELTER("Dedicated evacuation shelter"),
  SCHOOL_CONVERTED("School (converted)"),
  COMMUNITY_HALL("Community hall"),
  TEMPLE_OR_RELIGIOUS("Temple / religious building"),
  STADIUM("Stadium"),
  HEALTH_FACILITY("Health facility"),
  CAMP("Camp"),
  OTHER("Other")
}

enum class OperatingStatus(val label: String) {
  OPEN("Open"),
  CLOSED("Closed"),
  FULL("Full"),
  UNKNOWN("Status unknown")
}

/** One resource or constraint that may or may not be known. */
data class ResourceStatus(
  /** Null = the source did not state this. Never coerce to 0 or false. */
  val available: Boolean? = null,
  val quantity: MaybeNumber = MaybeNumber.UNKNOWN,
  val note: String? = null
)

data class ShelterRecord(
  val id: String,
  val name: String,
  val latitude: Double,
  val longitude: Double,
  val facilityType: ShelterFacilityType,
  val authority: String? = null,
  val verification: ShelterVerification,
  val disasterSuitability: Set<String> = emptySet(),

  // ---- capacity / occupancy: all nullable, never defaulted ----
  /** Maximum accommodation the facility can hold. UNKNOWN when unstated. */
  val capacityTotal: MaybeNumber = MaybeNumber.UNKNOWN,
  /** Current occupancy. UNKNOWN is NOT zero. */
  val capacityOccupied: MaybeNumber = MaybeNumber.UNKNOWN,
  // ---- resources ----
  val water: ResourceStatus = ResourceStatus(),
  val sanitation: ResourceStatus = ResourceStatus(),
  val food: ResourceStatus = ResourceStatus(),
  val healthcare: ResourceStatus = ResourceStatus(),
  val power: ResourceStatus = ResourceStatus(),
  val accessibility: String? = null,

  val operatingStatus: OperatingStatus = OperatingStatus.UNKNOWN,

  // ---- provenance ----
  val source: String,
  val sourceUrl: String? = null,
  val sourceDate: String? = null,
  val lastVerified: String? = null,
  val notes: String? = null,

  val classification: DataClassification = DataClassification.OBSERVED,
  val lastUpdatedMillis: Long = 0L
) {
  val point: GeoPoint get() = GeoPoint(latitude, longitude)

  /** True when this record may be presented as a real evacuation facility. */
  val isRealFacility: Boolean get() = verification.countsAsReal

  /**
   * Remaining capacity, derived ONLY when both inputs are known.
   * A missing occupancy must not read as an empty shelter.
   */
  val capacityAvailable: MaybeNumber
    get() = if (capacityTotal.isKnown && capacityOccupied.isKnown) {
      MaybeNumber.of(
        (capacityTotal.value!! - capacityOccupied.value!!).coerceAtLeast(0.0)
      )
    } else {
      MaybeNumber.UNKNOWN
    }

  /**
   * Human-readable capacity statement. Never prints "0 / 0" for an unknown
   * capacity — that reads as "full", which is a factual claim we cannot make.
   */
  fun capacityLabel(): String {
    val total = capacityTotal
    if (!total.isKnown) return "Capacity unavailable"
    val occupied = capacityOccupied
    return if (occupied.isKnown) {
      "${occupied.value!!.toInt()} / ${total.value!!.toInt()} occupied"
    } else {
      "${total.value!!.toInt()} capacity, occupancy unknown"
    }
  }

  /**
   * Effective capacity across all known constraints.
   *
   * Takes the MINIMUM only when every constraint is known. If any component is
   * unknown the result is UNKNOWN — never the minimum of the known subset,
   * which would silently overstate how many people the facility can take.
   * [unknownConstraints] lets the UI say exactly how many inputs are missing.
   */
  fun effectiveCapacity(constraints: Map<String, MaybeNumber>): CapacityBottleneck {
    if (constraints.isEmpty()) {
      return CapacityBottleneck(
        effective = MaybeNumber.UNKNOWN,
        limiting = null,
        unknownConstraints = emptyList()
      )
    }
    val unknown = constraints.filterValues { !it.isKnown }.keys.sorted()
    if (unknown.isNotEmpty()) {
      return CapacityBottleneck(
        effective = MaybeNumber.UNKNOWN,
        limiting = null,
        unknownConstraints = unknown
      )
    }
    val limiting = constraints.minByOrNull { it.value.value!! }!!
    return CapacityBottleneck(
      effective = limiting.value,
      limiting = limiting.key,
      unknownConstraints = emptyList()
    )
  }
}

data class CapacityBottleneck(
  val effective: MaybeNumber,
  /** Which constraint set the limit, when all are known. */
  val limiting: String?,
  /** Named constraints that were unavailable (never silently dropped). */
  val unknownConstraints: List<String>
) {
  val isIncomplete: Boolean get() = unknownConstraints.isNotEmpty()

  fun label(): String = when {
    unknownConstraints.isNotEmpty() ->
      "Capacity incomplete — ${unknownConstraints.size} constraint" +
        "${if (unknownConstraints.size == 1) "" else "s"} unavailable"
    limiting != null -> "Effective capacity $effective limited by $limiting"
    else -> "Capacity unavailable"
  }
}

/**
 * The registry itself. In LIVE mode it is empty until an official source is
 * connected, which is the honest state — the app says no verified centre was
 * found instead of inventing one.
 */
object ShelterRegistry {

  /** Rows loaded from an official registry. Empty in this build. */
  private val official = mutableListOf<ShelterRecord>()

  /** Demo rows. NEVER returned by [realFacilities]. */
  private val demo = mutableListOf<ShelterRecord>()

  fun registerOfficial(records: List<ShelterRecord>) {
    official.clear()
    official.addAll(records)
  }

  fun registerDemo(records: List<ShelterRecord>) {
    demo.clear()
    demo.addAll(records)
  }

  /** Real facilities only. Simulated records can never appear here. */
  fun realFacilities(): List<ShelterRecord> =
    official.filter { it.isRealFacility }

  /** Everything, tagged. For demo mode and debug tooling. */
  fun all(): List<ShelterRecord> = official + demo

  fun demoFacilities(): List<ShelterRecord> = demo

  val hasVerifiedFacility: Boolean get() = realFacilities().isNotEmpty()

  /**
   * The statement shown when nothing authoritative exists. Phrased as a fact
   * about the data, not as an apology.
   */
  fun unavailableMessage(): String =
    if (official.isEmpty()) {
      "No verified evacuation centre found for this area."
    } else {
      "No verified evacuation centre found for this area. " +
        "${official.size} unverified record(s) exist and are not shown as evacuation facilities."
    }
}