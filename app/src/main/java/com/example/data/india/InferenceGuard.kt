package com.example.data.india

/**
 * ============================================================================
 * INFERENCE GUARD
 * ============================================================================
 *
 * Rules 13–17 are not style preferences; they are correctness constraints.
 * Enforcing them by discipline alone fails silently. This object makes each
 * forbidden inference an explicit, testable decision.
 *
 *   13. A hazard observation is NOT automatically an evacuation zone.
 *   14. An earthquake epicentre is NOT automatically a danger radius.
 *   15. A satellite fire detection is NOT automatically a complete fire perimeter.
 *   16. An OSRM route is NOT automatically a safe route.
 *   17. Missing live road-closure/traffic info must remain explicitly unavailable.
 */

sealed class InferenceResult<out T> {
  data class Allowed<T>(val value: T, val derivedClass: DataClass) : InferenceResult<T>()

  /**
   * Refused. [reason] must be shown to the user rather than swallowed —
   * a silent downgrade to a wrong-but-plausible number is the failure mode
   * this whole class exists to prevent.
   */
  data class Refused(val reason: String, val unknownField: String) : InferenceResult<Nothing>()

  val isAllowed: Boolean get() = this is Allowed
}

/**
 * What a route engine actually knows about a route. A computed path is not a
 * safety judgement unless a safety authority supplied one.
 */
data class RouteSafetyAssessment(
  val safetyStatus: RouteSafetyStatus,
  val basis: String,
  val closureDataAvailable: Boolean
)

enum class RouteSafetyStatus(val label: String) {
  /** An authority explicitly cleared this route. */
  AUTHORITY_CLEARED("Cleared by authority"),

  /** An authority explicitly warned against it. */
  AUTHORITY_ADVISED_AGAINST("Advisory: avoid"),

  /** No closure/traffic feed exists. Say so; do not imply the route is clear. */
  UNVERIFIED_NO_DATA("Unverified — no live closure data"),
  UNKNOWN("Unknown")
}

object InferenceGuard {

  /**
   * Rule 13: a hazard OBSERVATION must not become an evacuation zone.
   *
   * An observation tells us something is happening at a point. Evacuation
   * requires a published area that the responsible authority says to leave.
   */
  fun observationToEvacuationZone(observation: EntityKind): InferenceResult<Unit> =
    if (observation.isObservation) {
      InferenceResult.Refused(
        reason = "$observation is a point observation. It is not an evacuation area " +
          "and cannot be widened into one without a published geometry from the issuing authority.",
        unknownField = "evacuationZoneGeometry"
      )
    } else {
      InferenceResult.Allowed(Unit, DataClass.DERIVED)
    }

  /**
   * Rule 14: an epicentre does not supply a danger radius.
   *
   * Returns the radius ONLY if the source published one. Otherwise UNKNOWN.
   */
  fun dangerRadiusFromEpicentre(
    sourceRadiusKm: MaybeNumber,
    magnitude: MaybeNumber
  ): InferenceResult<MaybeNumber> =
    if (sourceRadiusKm.isKnown) {
      InferenceResult.Allowed(sourceRadiusKm, DataClass.OFFICIAL_ALERT)
    } else {
      // Explicitly refuse the tempting magnitude->radius formula.
      InferenceResult.Refused(
        reason = "No danger radius published by the source for this event. " +
          "Deriving one from magnitude alone would be an invented hazard boundary.",
        unknownField = "dangerRadiusKm"
      )
    }

  /**
   * Rule 15: a hotspot is not a fire perimeter.
   */
  fun hotspotToPerimeter(hotspotCount: Int, publishedPerimeterPresent: Boolean): InferenceResult<Unit> =
    if (publishedPerimeterPresent) {
      InferenceResult.Allowed(Unit, DataClass.OFFICIAL_ALERT)
    } else {
      InferenceResult.Refused(
        reason = "$hotspotCount satellite hotspot(s) are point detections from a single overpass. " +
          "They do not describe the extent, direction or intensity of any fire, and no burn " +
          "perimeter is published. Fire area remains unknown.",
        unknownField = "firePerimeter"
      )
    }

  /**
   * Rule 16: a computed route is not a safe route.
   *
   * OSRM solves a shortest path through the road graph. It has no knowledge of
   * collapses, washouts, barricades, debris, crowd crush or authority advice.
   */
  fun routeToSafeRoute(
    hasAuthorityClearance: Boolean,
    hasAuthorityWarning: Boolean,
    hasClosureFeed: Boolean
  ): InferenceResult<RouteSafetyAssessment> = when {
    hasAuthorityWarning ->
      InferenceResult.Allowed(
        RouteSafetyAssessment(
          safetyStatus = RouteSafetyStatus.AUTHORITY_ADVISED_AGAINST,
          basis = "Issuing authority advised against this route.",
          closureDataAvailable = hasClosureFeed
        ),
        DataClass.OFFICIAL_ALERT
      )

    hasAuthorityClearance ->
      InferenceResult.Allowed(
        RouteSafetyAssessment(
          safetyStatus = RouteSafetyStatus.AUTHORITY_CLEARED,
          basis = "Issuing authority cleared this route.",
          closureDataAvailable = hasClosureFeed
        ),
        DataClass.OFFICIAL_ALERT
      )

    // Rule 17: no closure feed means "unknown", which must be stated plainly.
    else ->
      InferenceResult.Allowed(
        RouteSafetyAssessment(
          safetyStatus = RouteSafetyStatus.UNVERIFIED_NO_DATA,
          basis = "This is a computed shortest path through the road network. " +
            "No live road-closure or traffic feed is available for India, so route " +
            "conditions are unknown — not verified clear.",
          closureDataAvailable = false
        ),
        DataClass.DERIVED
      )
  }

  /**
   * Rule 18: do not claim official/verified/live unless the source supports it.
   */
  fun provenanceStatusFor(dataClass: DataClass, freshness: TemporalStatus): ProvenanceStatus =
    when (dataClass) {
      DataClass.OFFICIAL_ALERT ->
        if (freshness == TemporalStatus.LIVE) ProvenanceStatus.OFFICIAL
        else ProvenanceStatus.OFFICIAL_DERIVED

      DataClass.STATIC_GEOGRAPHY -> ProvenanceStatus.OFFICIAL
      DataClass.BLOCKED_OFFICIAL -> ProvenanceStatus.UNAVAILABLE

      DataClass.SUPPLEMENTARY_OBSERVATION,
      DataClass.SATELLITE_OBSERVATION,
      DataClass.SUPPLEMENTARY_GEOGRAPHY -> ProvenanceStatus.SUPPLEMENTARY

      DataClass.WEATHER_CONTEXT -> ProvenanceStatus.DERIVED
      DataClass.HISTORICAL_BASELINE -> ProvenanceStatus.SUPPLEMENTARY
      DataClass.DERIVED -> ProvenanceStatus.DERIVED
      DataClass.ESTIMATED -> ProvenanceStatus.ESTIMATED
      DataClass.SIMULATED -> ProvenanceStatus.SIMULATED
      DataClass.UNKNOWN -> ProvenanceStatus.UNAVAILABLE
    }

  /**
   * Rule 11/12: historical data must never be presented as current.
   */
  fun isPresentlyValid(temporalStatus: TemporalStatus): Boolean =
    temporalStatus != TemporalStatus.HISTORICAL &&
      temporalStatus != TemporalStatus.SIMULATED &&
      temporalStatus != TemporalStatus.UNKNOWN
}
