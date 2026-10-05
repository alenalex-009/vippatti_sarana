package com.example.data.india

/**
 * ============================================================================
 * PHASE 1 — INDIA-WIDE NORMALIZED DATA CONTRACT
 * ============================================================================
 *
 * Every dataset/entity imported into Vippatti Sarana preserves:
 * - id
 * - source
 * - sourceUrl
 * - retrievedAt
 * - observedAt
 * - effectiveAt
 * - expiresAt where applicable
 * - temporalStatus
 * - provenanceStatus
 * - confidence if actually supported
 * - geometry
 * - geographicScope
 * - raw/source identifier
 *
 * Temporal status vocabulary (every imported record carries one):
 *   LIVE       — observed/retrieved right now from a live feed
 *   RECENT     — fetched within the recent window, still usable
 *   CACHED     — served from on-device cache, labelled as such
 *   STALE      — older than recent but still usable offline
 *   HISTORICAL — archive record (Census 2011, EM-DAT) — NEVER current risk
 *   STATIC     — bundled/static asset, not time-varying
 *   DERIVED    — computed from other data (e.g. hazard radius)
 *   ESTIMATED  — approximate, labelled as such
 *   SIMULATED  — demo-only, never shown in live mode
 *   UNKNOWN    — temporal status genuinely unavailable
 */

enum class TemporalStatus(val label: String) {
  LIVE("LIVE"),
  RECENT("RECENT"),
  CACHED("CACHED"),
  STALE("STALE"),
  HISTORICAL("HISTORICAL"),
  STATIC("STATIC"),
  DERIVED("DERIVED"),
  ESTIMATED("ESTIMATED"),
  SIMULATED("SIMULATED"),
  UNKNOWN("UNKNOWN")
}

enum class ProvenanceStatus(val label: String) {
  VERIFIED("Verified"),
  OFFICIAL("Official"),
  OFFICIAL_DERIVED("Official-derived"),
  SUPPLEMENTARY("Supplementary"),
  DERIVED("Derived"),
  ESTIMATED("Estimated"),
  SIMULATED("Simulated"),
  UNKNOWN("Unknown"),
  UNAVAILABLE("Unavailable")
}

/**
 * Normalized provenance carried by every India-wide entity.
 * Source metadata is preserved — never destroyed during normalization.
 */
data class IndiaProvenance(
  val source: String,
  val sourceUrl: String? = null,
  val retrievedAtMillis: Long = 0L,
  val observedAtMillis: Long = 0L,
  val effectiveAtMillis: Long? = null,
  val expiresAtMillis: Long? = null,
  val temporalStatus: TemporalStatus = TemporalStatus.UNKNOWN,
  val provenanceStatus: ProvenanceStatus = ProvenanceStatus.UNKNOWN,
  val confidence: Double? = null,
  val geographicScope: String? = null,
  val rawSourceId: String? = null
) {
  /** A record with no timestamps is temporally unknown, never fresh. */
  val isFresh: Boolean
    get() = retrievedAtMillis > 0L &&
      (System.currentTimeMillis() - retrievedAtMillis) in 0 until FRESHNESS_WINDOW_MS

  /** Honest label for display. */
  val statusLabel: String
    get() = when (temporalStatus) {
      TemporalStatus.LIVE -> "Live"
      TemporalStatus.RECENT -> "Recent"
      TemporalStatus.CACHED -> "Cached"
      TemporalStatus.STALE -> "Stale"
      TemporalStatus.HISTORICAL -> "Historical"
      TemporalStatus.STATIC -> "Static"
      TemporalStatus.DERIVED -> "Derived"
      TemporalStatus.ESTIMATED -> "Estimated"
      TemporalStatus.SIMULATED -> "Simulated"
      TemporalStatus.UNKNOWN -> "Time unknown"
    }

  companion object {
    const val FRESHNESS_WINDOW_MS = 6L * 60L * 60L * 1000L
    const val RECENT_WINDOW_MS = 30L * 60L * 1000L
  }
}