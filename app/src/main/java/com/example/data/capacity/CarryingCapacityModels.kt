package com.example.data.capacity

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance

/**
 * ============================================================================
 * CARRYING CAPACITY - models (SIH milestone)
 * ============================================================================
 *
 * Candidate site -> capacity assessment -> feasibility result -> limiting
 * resource -> relocation planning.
 *
 * Honesty rules encoded here:
 *  - A missing measurement is NOT zero: [ResourceCapacity.peopleSupported] is
 *    null and the resource simply does not participate in the minimum.
 *  - A recorded ABSENCE is different from a missing value and may legitimately
 *    cap a site at zero (e.g. a site that records no water supply).
 *  - Every number carries where it came from, how it was produced
 *    ([ResourceDataState]) and what assumption, if any, produced it.
 *  - A verdict computed from simulated site records is never presented as a
 *    verified one; the assessment keeps its provenance.
 */

/** Resource constraints the data model can actually speak about. */
enum class CapacityResource(val label: String) {
  SHELTER_SPACES("Shelter spaces"),
  LAND_AREA("Land / floor area"),
  WATER("Drinking water"),
  SANITATION("Sanitation"),
  FOOD("Food supply"),
  ACCESS("Infrastructure access"),
  POWER("Power supply")
}

/** How a value was produced, so verified / estimated / simulated never blur. */
enum class ResourceDataState(val label: String) {
  /** Read from a record the project treats as observed/verified. */
  MEASURED("Measured"),
  /** Derived from other real inputs by a documented rule. */
  ESTIMATED("Estimated"),
  /** Demo/pilot value with SIMULATED provenance. */
  SIMULATED("Simulated"),
  /** Supplied by the citizen (e.g. household size); not a census figure. */
  USER_DECLARED("User-declared"),
  /** The source did not provide this input at all - never treated as zero. */
  NOT_PROVIDED("Not provided")
}

/**
 * One resource constraint for one candidate site.
 *
 * [peopleSupported] = how many people this resource can support, or null when
 * the input is unavailable. [recordedAbsence] marks the case where the site
 * record explicitly says the resource is not available - a real fact, which is
 * what allows a zero.-
 */
data class ResourceCapacity(
  val resource: CapacityResource,
  val peopleSupported: Int?,
  val state: ResourceDataState,
  /** The arithmetic in words, e.g. "300 total - 75 occupied = 225 spaces". */
  val basis: String,
  val source: String,
  val recordedAbsence: Boolean = false
) {
  val isAvailable: Boolean get() = peopleSupported != null
}

/**
 * How many people need relocation here.
 * [people] is null when the app has no population figure - the caller must not
 * substitute a guess.
 */
data class RelocationDemand(
  val people: Int?,
  val state: ResourceDataState,
  val source: String,
  val basis: String,
  val notes: List<String> = emptyList(),
  // --- Population provenance (SIH 26191) ---------------------------------
  /** Administrative area the figure describes (null = not stated). */
  val scope: com.example.data.population.PopulationScope? = null,
  val scopeName: String? = null,
  val classification: com.example.data.population.PopulationClassification? = null,
  /** What the figure counted before it became a demand. */
  val role: com.example.data.population.PopulationRole? = null,
  /** Set when the demand was DERIVED from affected/baseline population. */
  val derivedFromRole: com.example.data.population.PopulationRole? = null,
  /** When the source collected the figure; null = not stated. */
  val referenceMillis: Long? = null,
  val confidence: Double? = null
) {
  /** True when this is a personal household figure, not an area population. */
  val isHouseholdLevel: Boolean
    get() = scope == com.example.data.population.PopulationScope.HOUSEHOLD

  val scopeLabel: String
    get() = scopeName?.takeIf { it.isNotBlank() }?.let { name ->
      "$name (${scope?.label ?: "area"})"
    } ?: scope?.label ?: "Area not stated"

  /** Honest label for the demand line, e.g. "Estimated affected population". */
  val roleLabel: String
    get() = when {
      people == null -> "No population figure available"
      isHouseholdLevel -> "User-declared household size — not a census figure"
      derivedFromRole != null ->
        "${classification?.label ?: "Estimated"} demand derived from " +
          "${derivedFromRole.label.lowercase()}"
      role == com.example.data.population.PopulationRole.BASELINE_TOTAL ->
        "Baseline population — not confirmed relocation demand"
      role == com.example.data.population.PopulationRole.AFFECTED_POPULATION ->
        "Affected population — not confirmed relocation demand"
      else -> "${classification?.label ?: state.label} relocation demand"
    }

  companion object {
    /** No population figure available anywhere in the app. */
    fun unavailable(basis: String): RelocationDemand = RelocationDemand(
      people = null,
      state = ResourceDataState.NOT_PROVIDED,
      source = "No verified population dataset connected",
      basis = basis
    )

    /** Capacity-ready demand from a resolved population record. */
    fun from(resolution: com.example.data.population.PopulationDemandResolution): RelocationDemand =
      resolution.demand

    /**
     * Demand from the citizen's own declaration (profile household size) — the
     * personal, household-scoped path used when no population source exists.
     */
    fun fromUserProfile(householdSize: Int, source: String): RelocationDemand {
      val record = com.example.data.population.PopulationRecord.householdDeclaration(
        householdSize = householdSize,
        source = source
      )
      return RelocationDemand(
        people = record.value,
        state = ResourceDataState.USER_DECLARED,
        source = source,
        basis = "${record.value ?: 0} people declared in the citizen profile " +
          "(citizen + dependents); no census or ward dataset is connected",
        notes = record.notes,
        scope = record.scope,
        classification = record.classification,
        role = record.role
      )
    }
  }
}

/** Verdict of one site's carrying-capacity assessment. */
enum class FeasibilityStatus(val label: String) {
  FEASIBLE("Feasible"),
  INFEASIBLE("Infeasible"),
  /** Required or site data is missing: no verdict is issued. */
  INSUFFICIENT_DATA("Insufficient data"),
  /** A verdict exists, but it rests on SIMULATED site records. */
  SIMULATED("Simulated")
}

/**
 * Complete, explainable result for one candidate site.
 *
 * [effectiveCapacity] is the minimum over the resources that actually have a
 * value; [limitingResource] names the one that produced it. [meetsRequirement]
 * is the raw arithmetic outcome ([FeasibilityStatus.SIMULATED] still carries a
 * real verdict).
 */
/** Capacity-estimate confidence (research doc §F.5, approved). */
enum class CapacityConfidence(val label: String) {
  HIGH("High"),
  MEDIUM("Medium"),
  LOW("Low")
}

data class CapacityAssessment(
  val siteId: String,
  val siteName: String,
  val demand: RelocationDemand,
  val resources: List<ResourceCapacity>,
  val effectiveCapacity: Int?,
  val limitingResource: CapacityResource?,
  val status: FeasibilityStatus,
  /** Capacity minus requirement when it fits, else null. */
  val remainingCapacity: Int?,
  /** Requirement minus capacity when it does not fit, else null. */
  val shortfall: Int?,
  val explanation: String,
  val assumptions: List<String>,
  val assessedAtMillis: Long,
  val provenance: DataProvenance,
  /**
   * Transparency label for HOW MUCH of the bottleneck was actually measured
   * (approved research doc §F.5): HIGH = every capacity-relevant line the
   * model supports has data + record is verified; MEDIUM = at least one line
   * not assessed; LOW = most lines not assessed or simulated record.
   * Never a hidden pseudo-score - the un-assessed lines are listed beside it.
   */
  val confidence: CapacityConfidence = CapacityConfidence.LOW,
  /** Planning regime that produced the floor-area line, for the methodology. */
  val regime: CarryingCapacityEngine.Regime? = null
) {
  /** Raw arithmetic outcome; null when no verdict could be computed. */
  val meetsRequirement: Boolean?
    get() = when (status) {
      FeasibilityStatus.INSUFFICIENT_DATA -> null
      else -> shortfall == null
    }

  /** Resources that could not be assessed at all (shown, never zeroed). */
  val unavailableResources: List<CapacityResource>
    get() = resources.filter { !it.isAvailable }.map { it.resource }

  /** All resource states present, for the verified/estimated/simulated line. */
  val dataStates: Set<ResourceDataState> get() = resources.map { it.state }.toSet()

  val sourceLine: String
    get() = "Site record: ${provenance.source} • demand: ${demand.source}"

  companion object {
    /**
     * Provenance for the whole assessment: SIMULATED when every contributing
     * resource is simulated, otherwise the weakest real classification.
     */
    fun assessmentProvenance(
      contributing: List<ResourceCapacity>,
      siteProvenance: DataProvenance
    ): DataProvenance {
      val allSimulated = contributing.isNotEmpty() &&
        contributing.all { it.state == ResourceDataState.SIMULATED }
      val classification = when {
        contributing.isEmpty() -> DataClassification.SIMULATED
        allSimulated -> DataClassification.SIMULATED
        contributing.all { it.state == ResourceDataState.MEASURED } -> DataClassification.OBSERVED
        else -> DataClassification.ESTIMATED
      }
      return DataProvenance(
        source = siteProvenance.source,
        status = if (classification == DataClassification.SIMULATED) {
          DataProvenance.STATUS_FIELD
        } else {
          DataProvenance.STATUS_LIVE
        },
        confidence = siteProvenance.confidence,
        isVerified = classification == DataClassification.OBSERVED && siteProvenance.isVerified,
        classification = classification,
        recordedAtMillis = siteProvenance.recordedAtMillis,
        lastUpdatedMillis = siteProvenance.lastUpdatedMillis
      )
    }
  }
}
