package com.example.data.india

/**
 * ============================================================================
 * NORMALIZED INDIA-WIDE OFFICIAL ALERT (CAP 1.2)
 * ============================================================================
 *
 * Verified against the live NDMA SACHET feed during the Phase 1 source audit.
 *
 * Real structure discovered, which the model below reflects exactly:
 *   - The RSS is an INDEX, not CAP XML. Each <item> has title (often in a
 *     regional language), an empty description, and a link to
 *     .../FetchXMLFile?identifier=<id>. The full CAP document is at that link.
 *   - That CAP document may contain several <info> blocks — one per language
 *     (en-IN, BN, hi, te, ...). Only the requested language should be shown.
 *   - <cap:area> usually carries areaDesc + an LGD geocode (valueName/value
 *     pair), but typically NO inline polygon.
 *   - When a polygon exists it is published SEPARATELY and referenced by a
 *     <cap:parameter> whose valueName is "Polygon URL". This is the mechanism
 *     that makes official hazard areas possible at all.
 *
 * Rule 13 compliance: an alert is an ALERT. It carries an area only when the
 * issuing authority actually published one. We never synthesise geometry.
 */

data class OfficialAlert(
  /** CAP identifier — stable, assigned by the sender. Primary key. */
  val identifier: String,
  val sender: String,
  val senderName: String,
  val sent: String?,
  val effective: String?,
  val onset: String?,
  val expires: String?,
  val status: String?,
  val msgType: String?,
  val scope: String?,
  val language: String,
  val category: String?,
  val event: String?,
  val urgency: String?,
  val severity: String?,
  val certainty: String?,
  val headline: String?,
  val description: String?,
  val instruction: String?,
  val areas: List<AlertArea>,
  val provenance: IndiaProvenance
) {
  /**
   * An alert is expired when the source told us it expires in the past.
   * An alert with NO expiry is not treated as expired — it simply has no
   * stated end, which is a different thing and is labelled as such.
   */
  fun isExpired(nowMillis: Long): Boolean {
    val exp = expires ?: return false
    val parsed = CapTime.parse(exp) ?: return false
    return parsed < nowMillis
  }

  /** True when the source published a real, parseable area geometry. */
  val hasPublishedArea: Boolean get() = areas.any { it.hasUsableGeometry }

  /** Never infer: an alert with no published geometry exposes no geometry. */
  fun geometryForArea(): AlertGeometry? =
    areas.firstOrNull { it.hasGeometry }?.resolvedGeometry()
}

data class AlertArea(
  val areaDesc: String,
  /** LGD code pairs preserved verbatim, e.g. ("LGD District Code", "309"). */
  val geocodes: List<Geocode> = emptyList(),
  val polygon: String? = null,
  val circle: String? = null,
  val altitude: String? = null,
  val ceiling: String? = null
) {
  val hasGeometry: Boolean get() = !polygon.isNullOrBlank() || !circle.isNullOrBlank()

  /**
   * True only when published geometry actually parses into a drawable ring.
   * Prefer this over [hasGeometry] when deciding whether to render an area.
   */
  val hasUsableGeometry: Boolean get() = resolvedGeometry() != null

  /** Convenience: district-level LGD code when the sender supplied one. */
  fun lgdCode(): String? =
    geocodes.firstOrNull { it.name.equals("LGD District Code", ignoreCase = true) }?.value

  fun lgdStateCode(): String? =
    geocodes.firstOrNull { it.name.equals("LGD State Code", ignoreCase = true) }?.value

  /**
   * Geometry parsed from what the authority actually published.
   *
   * Returns null when the area declares geometry we cannot read. Presence of a
   * non-empty `polygon` string is not proof of usable geometry, so the caller
   * must test the resolved value, not [hasGeometry], before drawing.
   */
  fun resolvedGeometry(): AlertGeometry? =
    AlertGeometry.fromPublished(polygon ?: circle, "NDMA_SACHET")
}

data class Geocode(val name: String, val value: String)

/** A polygon as published by the authority. Coordinates are lat,lon pairs. */
data class AlertGeometry(
  /** Raw source text, preserved verbatim for provenance. */
  val rawPolygon: String,
  val ring: List<GeoPoint>,
  val source: String
) {
  /** Centroid of the bounding box — a derived value, for map centring only. */
  fun bboxCenter(): GeoPoint {
    val lats = ring.map { it.lat }
    val lons = ring.map { it.lon }
    return GeoPoint(
      (lats.min() + lats.max()) / 2.0,
      (lons.min() + lons.max()) / 2.0
    )
  }

  fun bboxKm(): Double {
    val lats = ring.map { it.lat }
    val lons = ring.map { it.lon }
    val h = GeoGrid.haversineKm(GeoPoint(lats.min(), lons.min()), GeoPoint(lats.max(), lons.max()))
    val w = GeoGrid.haversineKm(GeoPoint(lats.min(), lons.min()), GeoPoint(lats.max(), lons.min()))
    return maxOf(h, w)
  }

  companion object {
    /**
     * Parse the CAP polygon format published by SACHET:
     * whitespace-separated "lat,lon" pairs, exactly as observed in the live
     * FetchPolygonXMLFile payload.
     *
     * Returns an EMPTY list rather than throwing or substituting on malformed
     * input. A polygon we cannot read is a polygon we do not have — the caller
     * must treat the geometry as absent rather than approximate it.
     */
    fun parsePolygon(raw: String?): List<GeoPoint> {
      if (raw.isNullOrBlank()) return emptyList()
      return raw.trim()
        .split(Regex("\\s+"))
        .mapNotNull { token ->
          val parts = token.split(",")
          if (parts.size != 2) return@mapNotNull null
          val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
          val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
          if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
          GeoPoint(lat, lon)
        }
    }

    /** Build geometry from published text, or null when it cannot be read. */
    fun fromPublished(raw: String?, source: String): AlertGeometry? {
      val ring = parsePolygon(raw)
      if (ring.size < 3) return null
      return AlertGeometry(raw!!, ring, source)
    }
  }
}

/**
 * CAP 1.2 timestamp handling.
 *
 * Observed real formats: "2026-10-04T21:06:31+05:30" (full offset) and the
 * RSS "Sun, 04 Oct 2026 15:36:33 GMT" RFC-822 form.
 *
 * Deliberately NOT parsed with java.time at the call site: the audit runs on a
 * plain JVM test path and we need deterministic behaviour on partial strings.
 */
object CapTime {
  fun parse(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val trimmed = value.trim()

    // ISO-8601 with offset: 2026-10-04T21:06:31+05:30
    val iso = Regex(
      """^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})?$"""
    ).matchEntire(trimmed)
    if (iso != null) {
      val (y, mo, d, h, mi, s, off) = iso.destructured
      // utcMillis returns NULL for an impossible calendar date. Using 0L as a
      // failure sentinel here would be arithmetic on a fake: subtracting the
      // offset from 0 produced 1969-12-31T18:30Z, a timestamp that looks real.
      val base = utcMillis(
        y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt()
      ) ?: return null
      val offsetMinutes = parseOffset(off)
      return if (offsetMinutes == null) base else base - offsetMinutes * 60_000L
    }

    // RFC-822 as used by the RSS pubDate field.
    val rfc = Regex(
      """^(?:\w{3},\s*)?(\d{2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})\s+(GMT|UTC)$"""
    ).matchEntire(trimmed)
    if (rfc != null) {
      val (d, mon, y, h, mi, s, tz) = rfc.destructured
      val mo = MONTHS.indexOf(mon) + 1
      if (mo < 1 || mo > 12) return null
      return utcMillis(y.toInt(), mo, d.toInt(), h.toInt(), mi.toInt(), s.toInt())
    }

    return null
  }

  private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
  )

  /** Returns offset in minutes east of UTC, or null if absent. */
  private fun parseOffset(off: String): Int? {
    if (off.isEmpty()) return null
    if (off == "Z") return 0
    val sign = if (off.startsWith("-")) -1 else 1
    // Strip the sign AND the ':' separator. The previous version kept the colon,
    // so digits became "05" and ":3", toIntOrNull() failed, and every
    // offset-bearing timestamp silently fell back to UTC.
    val digits = off.substring(1).replace(":", "")
    if (digits.length != 4 || !digits.all { it.isDigit() }) return null
    val hh = digits.substring(0, 2).toIntOrNull() ?: return null
    val mm = digits.substring(2, 4).toIntOrNull() ?: return null
    return sign * (hh * 60 + mm)
  }

  /**
   * Days-from-civil algorithm — avoids java.time and is exact for valid dates.
   * Returns NULL for an impossible calendar date so callers cannot mistake a
   * failure sentinel for the Unix epoch.
   */
  private fun utcMillis(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Long? {
    // Validate the real calendar before converting. Rolling an invalid date
    // (month 13, Feb 29 in a common year) into a plausible-looking timestamp is
    // exactly the kind of quiet fabrication this project must not do.
    if (mo !in 1..12) return null
    if (d !in 1..daysInMonth(y, mo)) return null
    if (h !in 0..23 || mi !in 0..59 || s !in 0..60) return null

    val yAdj = if (mo <= 2) y - 1 else y
    val era = (if (yAdj >= 0) yAdj else yAdj - 399) / 400
    val yoe = yAdj - era * 400
    val mp = (mo + 9) % 12
    val doy = (153 * mp + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era.toLong() * 146_097L + doe - 719_468L
    return ((days * 24L + h) * 60L + mi) * 60_000L + s * 1000L
  }

  private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeap(year)) 29 else 28
    else -> 0
  }

  private fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}
