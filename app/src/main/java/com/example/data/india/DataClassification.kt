package com.example.data.india

/**
 * ============================================================================
 * DATA-CLASSIFICATION VOCABULARY
 * ============================================================================
 *
 * Central vocabulary describing what a dataset/observation ACTUALLY is, versus
 * what it may legitimately be used for. This is the guard rail for the
 * "hazard observation is NOT an evacuation zone" family of rules.
 */

enum class DataClass(val label: String, val isAuthoritative: Boolean) {
  /** Government alert feed (NDMA SACHET, IMD CAP). Authoritative as an ALERT. */
  OFFICIAL_ALERT("Official alert", true),

  /** Government-published static geography. */
  STATIC_GEOGRAPHY("Official geography", true),

  /** Government dataset requiring authenticated/authorised access. Not usable here. */
  BLOCKED_OFFICIAL("Official but inaccessible", true),

  /** Observation from a scientific agency (USGS, NASA FIRMS). Real, but not an alert. */
  SUPPLEMENTARY_OBSERVATION("Scientific observation", false),

  /** Satellite-derived point observation. */
  SATELLITE_OBSERVATION("Satellite observation", false),

  /** Community-mapped geography. Not an official designation. */
  SUPPLEMENTARY_GEOGRAPHY("Community-sourced geography", false),

  /** Weather values from a non-authoritative endpoint. Context only. */
  WEATHER_CONTEXT("Weather context", false),

  /** Historical archive (Census 2011, EM-DAT). */
  HISTORICAL_BASELINE("Historical", false),

  /** Computed in-app from other data. Must be labelled derived. */
  DERIVED("Derived", false),

  /** Approximation. */
  ESTIMATED("Estimated", false),

  /** Demo-only. Never rendered in live mode. */
  SIMULATED("Simulated", false),

  /** Genuinely unknown. Must stay null/unknown, never defaulted. */
  UNKNOWN("Unknown", false);

  /** Only these classes may be described as "official" in the UI. */
  val mayBeLabelledOfficial: Boolean get() = isAuthoritative
}

/**
 * What an entity legally represents. Used to block invalid inferences.
 *
 * e.g. an EpicentreObservation may NOT become a HazardZone.
 *      a HotspotPoint may NOT become a FirePerimeter.
 *      a Route may NOT claim to be safe.
 */
enum class EntityKind(val label: String) {
  ALERT("Alert"),
  EARTHQUAKE_OBSERVATION("Earthquake observation"),
  FIRE_HOTSPOT("Fire hotspot"),
  HAZARD_ZONE("Hazard zone"),
  FLOOD_ZONE("Flood zone"),
  SAFE_ZONE("Safe zone / shelter"),
  ROUTE("Route"),
  BOUNDARY("Administrative boundary"),
  POPULATION("Population figure"),
  INFRASTRUCTURE("Infrastructure");

  /**
   * Observation kinds are raw detections. They are NOT zones and may not be
   * widened into zones without an explicitly published geometry from the source.
   */
  val isObservation: Boolean
    get() = this == EARTHQUAKE_OBSERVATION || this == FIRE_HOTSPOT || this == ALERT

  /**
   * True when the source published an actual area/polygon. Only these may be
   * rendered as an area on the map.
   */
  val supportsAreaGeometry: Boolean
    get() = this == HAZARD_ZONE || this == FLOOD_ZONE || this == SAFE_ZONE || this == BOUNDARY
}

/**
 * A value that may legitimately be absent.
 *
 * Rule 6/8: "DO NOT convert missing values into zero. Preserve NULL/UNKNOWN."
 *
 * Kotlin's non-null Int cannot represent "unknown". This wrapper can, and it
 * refuses to invent a number. Declared as a data class rather than a value
 * class so the arithmetic operators can be members with a real receiver.
 */
data class MaybeNumber(val value: Double?) {
  val isKnown: Boolean get() = value != null && !value.isNaN()
  val isUnknown: Boolean get() = !isKnown

  /** Returns the value only if known; caller must handle null. */
  fun orNull(): Double? = if (isKnown) value else null

  /** Explicitly refuse to fabricate a zero. */
  fun valueOrThrow(reason: String): Double =
    value ?: throw IllegalStateException("Value is UNKNOWN; refusing to substitute. $reason")

  /** Arithmetic only when both operands are known; otherwise UNKNOWN. */
  operator fun plus(other: MaybeNumber): MaybeNumber =
    if (isKnown && other.isKnown) MaybeNumber(value!! + other.value!!) else UNKNOWN

  operator fun minus(other: MaybeNumber): MaybeNumber =
    if (isKnown && other.isKnown) MaybeNumber(value!! - other.value!!) else UNKNOWN

  companion object {
    fun of(v: Double?): MaybeNumber = MaybeNumber(v)
    fun of(v: Int?): MaybeNumber = MaybeNumber(v?.toDouble())
    val UNKNOWN: MaybeNumber = MaybeNumber(null)
  }
}
