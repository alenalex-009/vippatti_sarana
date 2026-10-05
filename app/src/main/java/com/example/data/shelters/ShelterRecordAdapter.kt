package com.example.data.shelters

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.SafeZone

/**
 * ============================================================================
 * REGISTRY -> SafeZone ADAPTER — the bridge into the existing engine
 * ============================================================================
 *
 * [ShelterRegistry] holds the honest, fully-nullable record. [SafeZoneEvaluator]
 * consumes [SafeZone], which the rest of the app (map, sheets, routing, cards)
 * already uses. This file is the only place the two meet.
 *
 * The conversion is where fabrication is easiest to introduce by accident, so
 * every numeric field maps null -> null. In particular:
 *
 *   capacityTotal     null  (not 0)
 *   capacityCurrent   null  (not 0)
 *
 * An earlier version of the pipeline defaulted these to 0, which produced
 * availableCapacity = 0, capacityStatus = FULL, and therefore hard-rejected the
 * facility as "Full — no remaining capacity" — a factual claim about a real
 * shelter that no source had made. Unknown capacity now flows through as
 * UNKNOWN, ranks neutrally, and is stated plainly in the UI.
 *
 * Resource booleans stay Boolean? semantics by mapping an UNKNOWN [ResourceStatus]
 * to false for SCORING only, while the record's own provenance and the capacity
 * label keep telling the user the answer was never published. Scoring on absent
 * data is neutral; DISPLAYING it as absent data is not optional.
 */
object ShelterRecordAdapter {

  /**
   * Converts registry rows into engine candidates.
   *
   * [includeSimulated] is false for LIVE mode. Simulated rows are never
   * returned by [ShelterRegistry.realFacilities], but this guard makes the
   * LIVE/DEMO boundary explicit at the only place it matters.
   */
  fun toSafeZones(
    records: List<ShelterRecord>,
    includeSimulated: Boolean
  ): List<SafeZone> = records
    .filter { includeSimulated || it.isRealFacility }
    .map { toSafeZone(it) }

  fun toSafeZone(record: ShelterRecord): SafeZone = SafeZone(
    id = record.id,
    name = record.name,
    lat = record.latitude,
    lon = record.longitude,
    locationNote = record.authority
      ?: record.notes
      ?: record.source,
    // NULL when the registry published no figure. Never 0.
    capacityTotal = record.capacityTotal.toIntOrNull(),
    capacityCurrent = record.capacityOccupied.toIntOrNull(),
    // Resource flags: an unstated resource is NOT available. Scoring treats it
    // as absent evidence; capacityLabel()/label() keep reporting the gap.
    waterAvailable = record.water.available == true,
    foodAvailable = record.food.available == true,
    electricityAvailable = record.power.available == true,
    sanitationAvailable = record.sanitation.available == true,
    medicalSupport = record.healthcare.available == true,
    accessibility = record.accessibility ?: if (record.verification.countsAsReal) {
      "Not stated by source"
    } else {
      "SIMULATED"
    },
    womenChildrenSuitability = record.disasterSuitability.any {
      it.equals("women", true) || it.equals("children", true) ||
        it.equals("vulnerable", true) || it.equals("elderly", true)
    },
    operatingStatus = record.operatingStatus.label.uppercase(),
    verificationStatus = record.verification.label,
    elevationNote = "Not fetched from an elevation source for this facility",
    waterLitresPerDay = record.water.quantity.toDoubleOrNull(),
    toiletCount = record.sanitation.quantity.toIntOrNull(),
    provenance = DataProvenance(
      source = record.source,
      status = when {
        record.verification == ShelterVerification.SIMULATED ->
          DataProvenance.STATUS_FIELD
        record.sourceDate != null -> "${record.verification.label} • ${record.sourceDate}"
        else -> record.verification.label
      },
      // Verification, not trust: only a registry-confirmed row is verified, and
      // a simulated row is the LEAST confident thing in the system, never the
      // most.
      confidence = when (record.verification) {
        ShelterVerification.VERIFIED -> 0.95
        ShelterVerification.OFFICIAL_DERIVED -> 0.8
        ShelterVerification.COMMUNITY_REPORTED -> 0.4
        ShelterVerification.ESTIMATED -> 0.3
        ShelterVerification.SIMULATED -> 0.0
        ShelterVerification.UNKNOWN -> 0.2
      },
      isVerified = record.verification == ShelterVerification.VERIFIED,
      classification = record.classification,
      lastUpdatedMillis = record.lastUpdatedMillis
    )
  )

  /** A MaybeNumber -> Int only when the figure was actually published. */
  private fun com.example.data.india.MaybeNumber.toIntOrNull(): Int? =
    if (isKnown && value != null) value.toInt() else null

  private fun com.example.data.india.MaybeNumber.toDoubleOrNull(): Double? =
    if (isKnown) value else null
}