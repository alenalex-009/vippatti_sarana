package com.example.data.routing

/**
 * How the evacuee is travelling (audit B8). Previously a raw String ("foot" /
 * "driving") compared across 6 files — a typo silently fell back to walking.
 * The enum is the single source of truth; the OSRM URL segment strings live
 * ONLY here (apiSegment), so no call site ever hand-writes them again.
 */
enum class TravelMode(
  /** The segment in the OSRM route URL (profile-matched endpoints). */
  val apiSegment: String,
  /** Plain-language label for UI. */
  val label: String,
  /** Flat-Earth assumption speed for the honest offline ESTIMATE only
   * (never presented as live ETA — see durationLabel). m/s. */
  val estimateSpeedMps: Double
) {
  FOOT("foot", "On foot", 1.35),
  DRIVING("driving", "Vehicle", 8.33);

  companion object {
    /** Parse a persisted/legacy string safely; anything unknown -> FOOT
     * (the conservative, always-available mode). */
    fun fromApiSegment(raw: String?): TravelMode =
      entries.firstOrNull { it.apiSegment.equals(raw?.trim(), ignoreCase = true) } ?: FOOT
  }
}
