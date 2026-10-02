package com.example.data.capacity

import com.example.data.model.DataClassification
import com.example.data.model.SafeZone
import kotlin.math.floor

/**
 * ============================================================================
 * CARRYING CAPACITY ENGINE (SIH milestone)
 * ============================================================================
 *
 * Pure, deterministic maths over the site's OWN record. It never invents a
 * measurement:
 *
 *  - a resource with no input is reported NOT_PROVIDED and is excluded from the
 *    minimum (missing data is not zero);
 *  - a resource the record explicitly marks unavailable legitimately yields 0
 *    (a recorded absence is a real fact, and it becomes the limiting resource);
 *  - the effective capacity is the MINIMUM over every resource that has a
 *    value, and the limiting resource is the one that produced that minimum.
 *
 * [LAND_SQUARE_METERS_PER_PERSON], [WATER_LITRES_PER_PERSON_PER_DAY] and
 * [PERSONS_PER_TOILET] are CONFIGURED planning figures for this app (common
 * Sphere-style planning values), not an official Indian standard, and are
 * published here so they can be audited or replaced by a provider-supplied
 * figure the moment one exists.
 */
object CarryingCapacityEngine {

  /**
   * Which planning regime produced a floor-area figure (research doc §F.1,
   * approved 2026-10-02). CAMP_STYLE applies the international humanitarian
   * band (Sphere 2018: 3.5 m2/person minimum, 4.5 m2 cold climate - the app's
   * configured value). CYCLONE_SHELTER applies the Government-of-India
   * cyclone-shelter guidance (Handbook on Cyclone Shelters, NIDM-hosted:
   * ~3 sq ft/person on the floor, terrace counted as additional space -
   * multi-storey stacking is legitimate ONLY in that regime).
   */
  enum class Regime { CAMP_STYLE, CYCLONE_SHELTER }

  /** Camp-style configured m2 per person (within the cited Sphere band). */
  const val LAND_SQUARE_METERS_PER_PERSON = 4.5

  /** GoI cyclone-shelter floor area per person: 3 sq ft = 0.2787 m2. */
  const val CYCLONE_FLOOR_SQ_METERS_PER_PERSON = 0.2787

  /**
   * GoI guidance counts floor AND terrace as accommodation; the app models
   * that as x2 usable levels - and SAYS SO in the basis string, never as a
   * silently doubled area.
   */
  const val CYCLONE_USABLE_LEVELS = 2

  /** Configured planning drinking-water allowance per person per day. */
  const val WATER_LITRES_PER_PERSON_PER_DAY = 15.0

  /** Configured planning ratio of people per toilet. */
  const val PERSONS_PER_TOILET = 50.0

  const val NO_DEMAND_NOTE =
    "No population figure is available, so no feasibility verdict is issued. " +
      "A census/ward population dataset or relief-registry feed is required."

  const val NO_SITE_DATA_NOTE =
    "This site record provides no usable capacity input, so no feasibility " +
      "verdict is issued."

  /** Documented tie-break order when two resources produce the same minimum. */
  private val LIMITING_PRIORITY = listOf(
    CapacityResource.WATER,
    CapacityResource.SANITATION,
    CapacityResource.SHELTER_SPACES,
    CapacityResource.LAND_AREA,
    CapacityResource.FOOD,
    CapacityResource.POWER,
    CapacityResource.ACCESS
  )

  /** The record's own basis string, repeated per resource for auditability. */
  private fun resourceSource(zone: SafeZone): String =
    "${zone.verificationStatus} • ${zone.provenance.source}"

  /**
   * Capability of the site record itself: SIMULATED demo records are never
   * allowed to produce a "measured" verdict.
   */
  private fun recordState(zone: SafeZone): ResourceDataState =
    when (zone.provenance.classification) {
      DataClassification.SIMULATED -> ResourceDataState.SIMULATED
      DataClassification.OBSERVED -> ResourceDataState.MEASURED
      DataClassification.DERIVED,
      DataClassification.ESTIMATED,
      DataClassification.CONFIGURED -> ResourceDataState.ESTIMATED
      // An archived (EM-DAT) figure is a historical statement, not a measurement
      // of this site today, so it can never be MEASURED.
      DataClassification.HISTORICAL -> ResourceDataState.ESTIMATED
    }

  private fun constrained(
    resource: CapacityResource,
    zone: SafeZone,
    regime: Regime
  ): ResourceCapacity {
    val state = recordState(zone)
    val source = resourceSource(zone)
    return when (resource) {
      CapacityResource.SHELTER_SPACES -> {
        val capacity = zone.capacityTotal
        val occupied = zone.capacityCurrent
        // A record with no usable capacity figure (non-positive total) has no
        // value to report rather than a value of zero.
        if (capacity <= 0) {
          ResourceCapacity(
            resource = resource,
            peopleSupported = null,
            state = ResourceDataState.NOT_PROVIDED,
            basis = "Record shows no usable total capacity ($capacity)",
            source = source
          )
        } else {
          ResourceCapacity(
            resource = resource,
            peopleSupported = zone.availableCapacity,
            state = state,
            basis = "$capacity total - $occupied occupied = ${zone.availableCapacity} spaces",
            source = source
          )
        }
      }

      CapacityResource.LAND_AREA -> {
        val area = zone.landAreaSquareMeters
        when {
          area == null -> ResourceCapacity(
            resource = resource,
            peopleSupported = null,
            state = ResourceDataState.NOT_PROVIDED,
            basis = "No usable land/floor area in the record",
            source = source
          )
          area <= 0.0 -> ResourceCapacity(
            resource = resource,
            peopleSupported = 0,
            state = state,
            basis = "Recorded area is ${area} m² — no accommodation area",
            source = source,
            recordedAbsence = true
          )
          regime == Regime.CYCLONE_SHELTER -> {
            // GoI cyclone-shelter guidance: ~3 sq ft/person usable floor area,
            // terrace counted as an equivalent additional level. The regime is
            // NAMED in the basis; never applied outside cyclone scenarios.
            val usable = area * CYCLONE_USABLE_LEVELS
            ResourceCapacity(
              resource = resource,
              peopleSupported = floor(usable / CYCLONE_FLOOR_SQ_METERS_PER_PERSON).toInt(),
              state = state,
              basis = "${formatDouble(area)} m2 x $CYCLONE_USABLE_LEVELS usable levels (floor + terrace) / " +
                "$CYCLONE_FLOOR_SQ_METERS_PER_PERSON m2 (3 sq ft) per person - " +
                "GoI cyclone-shelter guidance, applied ONLY under cyclone",
              source = source
            )
          }
          else -> ResourceCapacity(
            resource = resource,
            peopleSupported = floor(area / LAND_SQUARE_METERS_PER_PERSON).toInt(),
            state = state,
            basis = "${formatDouble(area)} m² / $LAND_SQUARE_METERS_PER_PERSON m² per person " +
              "(camp-style planning; cited Sphere 2018 minimum 3.5 m2/person)",
            source = source
          )
        }
      }

      CapacityResource.WATER -> {
        val litres = zone.waterLitresPerDay
        when {
          litres == null && !zone.waterAvailable -> ResourceCapacity(
            resource = resource,
            peopleSupported = 0,
            state = state,
            basis = "Record states no drinking-water supply",
            source = source,
            recordedAbsence = true
          )
          litres == null -> ResourceCapacity(
            resource = resource,
            peopleSupported = null,
            state = ResourceDataState.NOT_PROVIDED,
            basis = "Water supply flagged available but no daily volume in the record",
            source = source
          )
          litres <= 0.0 -> ResourceCapacity(
            resource = resource,
            peopleSupported = 0,
            state = state,
            basis = "Recorded supply is ${formatDouble(litres)} L/day",
            source = source,
            recordedAbsence = true
          )
          else -> ResourceCapacity(
            resource = resource,
            peopleSupported =
              floor(litres / WATER_LITRES_PER_PERSON_PER_DAY).toInt(),
            state = state,
            basis = "${formatDouble(litres)} L/day / $WATER_LITRES_PER_PERSON_PER_DAY L per person per day",
            source = source
          )
        }
      }

      CapacityResource.SANITATION -> {
        val toilets = zone.toiletCount
        when {
          toilets == null && !zone.sanitationAvailable -> ResourceCapacity(
            resource = resource,
            peopleSupported = 0,
            state = state,
            basis = "Record states no sanitation facility",
            source = source,
            recordedAbsence = true
          )
          toilets == null -> ResourceCapacity(
            resource = resource,
            peopleSupported = null,
            state = ResourceDataState.NOT_PROVIDED,
            basis = "Sanitation flagged available but no unit count in the record",
            source = source
          )
          toilets <= 0 -> ResourceCapacity(
            resource = resource,
            peopleSupported = 0,
            state = state,
            basis = "Recorded toilet count is $toilets",
            source = source,
            recordedAbsence = true
          )
          else -> ResourceCapacity(
            resource = resource,
            peopleSupported = floor(toilets * PERSONS_PER_TOILET).toInt(),
            state = state,
            basis = "$toilets toilets x $PERSONS_PER_TOILET persons per toilet",
            source = source
          )
        }
      }

      CapacityResource.FOOD -> if (!zone.foodAvailable) {
        ResourceCapacity(
          resource = resource,
          peopleSupported = 0,
          state = state,
          basis = "Record states no food supply",
          source = source,
          recordedAbsence = true
        )
      } else {
        ResourceCapacity(
          resource = resource,
          peopleSupported = null,
          state = ResourceDataState.NOT_PROVIDED,
          basis = "Food supply flagged available; no rations figure in the record",
          source = source
        )
      }

      CapacityResource.POWER -> ResourceCapacity(
        resource = resource,
        peopleSupported = null,
        state = if (zone.electricityAvailable) {
          ResourceDataState.NOT_PROVIDED
        } else {
          state
        },
        basis = if (zone.electricityAvailable) {
          "Power flagged available; does not cap occupancy on its own"
        } else {
          "Record states no electricity — reported, not used to cap occupancy"
        },
        source = source
      )

      CapacityResource.ACCESS -> ResourceCapacity(
        resource = resource,
        peopleSupported = null,
        state = ResourceDataState.NOT_PROVIDED,
        // Access is reported as text until a road-capacity provider exists; a
        // number here would be invented.
        basis = "Accessibility recorded as \"${zone.accessibility}\" — " +
          "no route-capacity figure available to convert into people",
        source = source
      )
    }
  }

  /**
   * Assess one candidate site against a relocation demand.
   * [nowMillis] is the assessment time; nothing else is timestamped.
   */
  fun assess(
    site: SafeZone,
    demand: RelocationDemand,
    nowMillis: Long,
    /** Planning regime for the floor-area line (research doc §F.1). */
    regime: Regime = Regime.CAMP_STYLE
  ): CapacityAssessment {
    val resources = CapacityResource.entries.map { constrained(it, site, regime) }
    val contributing = resources.filter { it.isAvailable }
    val assumptions = buildList {
      add("Space planning: $LAND_SQUARE_METERS_PER_PERSON m² per person (configured app figure).")
      add("Water planning: $WATER_LITRES_PER_PERSON_PER_DAY L per person per day (configured app figure).")
      add("Sanitation planning: $PERSONS_PER_TOILET persons per toilet (configured app figure).")
      add("Configured figures are planning values, not an official standard.")
      if (site.landAreaSquareMeters == null || site.waterLitresPerDay == null || site.toiletCount == null) {
        add("Some resources have no value in this record and are excluded from the minimum rather than counted as zero.")
      }
    }

    val provenance = CapacityAssessment.assessmentProvenance(contributing, site.provenance)

    // ---- No population figure: no verdict, whatever the site can hold. -----
    val required = demand.people?.takeIf { it > 0 }
    if (required == null) {
      return CapacityAssessment(
        siteId = site.id,
        siteName = site.name,
        demand = demand,
        resources = resources,
        effectiveCapacity = contributing.minOfOrNull { it.peopleSupported!! },
        limitingResource = contributing.minOfOrNull { it.peopleSupported!! }
          ?.let { min -> limitingFor(contributing, min)?.resource },
        status = FeasibilityStatus.INSUFFICIENT_DATA,
        remainingCapacity = null,
        shortfall = null,
        explanation = NO_DEMAND_NOTE + " " + demand.basis,
        assumptions = assumptions,
        assessedAtMillis = nowMillis,
        provenance = provenance,
        confidence = confidenceFor(contributing, allSimulated = demand.state == ResourceDataState.SIMULATED || contributing.isEmpty(), verified = site.provenance.isVerified),
        regime = regime
      )
    }

    // ---- No usable site input: no verdict. --------------------------------
    if (contributing.isEmpty()) {
      return CapacityAssessment(
        siteId = site.id,
        siteName = site.name,
        demand = demand,
        resources = resources,
        effectiveCapacity = null,
        limitingResource = null,
        status = FeasibilityStatus.INSUFFICIENT_DATA,
        remainingCapacity = null,
        shortfall = null,
        explanation = "$NO_SITE_DATA_NOTE Requirement is $required people " +
          "(${demand.state.label}; ${demand.source}).",
        assumptions = assumptions,
        assessedAtMillis = nowMillis,
        provenance = provenance,
        confidence = confidenceFor(contributing, allSimulated = demand.state == ResourceDataState.SIMULATED || contributing.isEmpty(), verified = site.provenance.isVerified),
        regime = regime
      )
    }

    // ---- Effective capacity = minimum over available resources. -----------
    val effective = contributing.minOf { it.peopleSupported!! }
    val limiting = limitingFor(contributing, effective)!!
    val remaining = (effective - required).takeIf { it >= 0 }
    val shortfall = (required - effective).takeIf { it > 0 }

    val allSimulated = contributing.all { it.state == ResourceDataState.SIMULATED }
    // A SIMULATED demand makes the whole verdict simulated, even on real site
    // records: the answer rests on demonstration data either way.
    val demandSimulated = demand.state == ResourceDataState.SIMULATED
    val status = when {
      allSimulated || demandSimulated -> FeasibilityStatus.SIMULATED
      remaining != null -> FeasibilityStatus.FEASIBLE
      else -> FeasibilityStatus.INFEASIBLE
    }

    val explanation = buildString {
      append("Effective capacity $effective people, limited by ${limiting.resource.label.lowercase()} ")
      append("(${limiting.basis}). Required $required people (${demand.state.label.lowercase()}). ")
      if (remaining != null) {
        append("Remaining capacity $remaining people.")
      } else {
        append("Shortfall ${shortfall ?: 0} people.")
      }
      if (allSimulated) {
        append(" Computed from SIMULATED site records — not a verified verdict.")
      }
      if (demandSimulated) {
        append(" Demand is SIMULATED data — not a verified verdict.")
      }
      val unavailable = resources.filter { !it.isAvailable }.map { it.resource.label.lowercase() }
      if (unavailable.isNotEmpty()) {
        append(" Not assessed (no data): ${unavailable.joinToString(", ")}.")
      }
    }

    return CapacityAssessment(
      siteId = site.id,
      siteName = site.name,
      demand = demand,
      resources = resources,
      effectiveCapacity = effective,
      limitingResource = limiting.resource,
      status = status,
      remainingCapacity = remaining,
      shortfall = shortfall,
      explanation = explanation,
      assumptions = assumptions,
      assessedAtMillis = nowMillis,
      provenance = provenance,
      confidence = confidenceFor(
        contributing,
        allSimulated = allSimulated || demandSimulated,
        verified = site.provenance.isVerified
      ),
      regime = regime
    )
  }

  /**
   * The resource that produced [minimum], using the documented tie-break order
   * so the answer is stable rather than dependent on list order.
   */
  private fun limitingFor(
    contributing: List<ResourceCapacity>,
    minimum: Int
  ): ResourceCapacity? {
    val tied = contributing.filter { it.peopleSupported == minimum }
    return LIMITING_PRIORITY.firstNotNullOfOrNull { resource ->
      tied.firstOrNull { it.resource == resource }
    } ?: tied.firstOrNull()
  }

  private fun formatDouble(value: Double): String =
    String.format(java.util.Locale.US, "%.1f", value)

  /**
   * Confidence from measurement coverage (approved research doc section F.5).
   * Denominator = the five lines that can actually cap occupancy (spaces,
   * land, water, sanitation). POWER and ACCESS are reported but never
   * cap by design, so they must not trap every site below HIGH forever.
   *  - HIGH   = all four cap lines carry values AND the record is verified;
   *  - MEDIUM = one of the four is not assessed, or the record is unverified;
   *  - LOW    = two or more cap lines missing, or the verdict rests on
   *    simulated data.
   * The missing lines themselves remain listed on the assessment - the label
   * never hides them.
   */
  fun confidenceFor(
    contributing: List<ResourceCapacity>,
    allSimulated: Boolean,
    verified: Boolean
  ): CapacityConfidence {
    val capping = setOf(
      CapacityResource.SHELTER_SPACES,
      CapacityResource.LAND_AREA,
      CapacityResource.WATER,
      CapacityResource.SANITATION
    )
    // The four lines the model can quantify with a number. FOOD/POWER/ACCESS
    // are reported but have no numeric field on a record, so counting them in
    // the denominator would make HIGH unreachable.
    val assessed = contributing.count { it.resource in capping }
    return when {
      contributing.isEmpty() || allSimulated || assessed <= 2 -> CapacityConfidence.LOW
      assessed < 4 || !verified -> CapacityConfidence.MEDIUM
      else -> CapacityConfidence.HIGH
    }
  }

}
