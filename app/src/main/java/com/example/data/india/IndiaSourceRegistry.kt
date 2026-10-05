package com.example.data.india

/**
 * ============================================================================
 * SOURCE ACCESS REGISTRY — code-side mirror of the provenance JSON under
 * data/india/provenance/
 * ============================================================================
 *
 * Rule 19: do not replace inaccessible official data with random third-party
 * data without documenting and justifying the fallback.
 *
 * Rule 17/24: offline capability with honest freshness labels.
 *
 * Every source declares: can we reach it, does it need a key/login, what class
 * of data it yields, and — critically — what it does NOT give us.
 */

enum class SourceAccessibility {
  /** Open endpoint, verified reachable, no credentials. */
  AVAILABLE,

  /** Open endpoint, but not verified this session. */
  UNVERIFIED,

  /** Requires a key. Key handled via local.properties; never hardcoded. */
  REQUIRES_KEY,

  /** Requires a login we do not have. Not usable, not scrapable. */
  BLOCKED_LOGIN_REQUIRED,

  /** Reachable only through a portal UI. Manual acquisition. */
  MANUAL_ONLY,

  /** No open programmatic access identified. */
  BLOCKED_NO_OPEN_API
}

data class IndiaSource(
  val id: String,
  val displayName: String,
  val url: String,
  val accessibility: SourceAccessibility,
  val dataClass: DataClass,
  val temporalClass: TemporalStatus,
  val yields: String,
  /** The single most important honesty constraint for this source. */
  val doesNotYield: String,
  val licenseStatus: LicenseStatus = LicenseStatus.UNVERIFIED
)

enum class LicenseStatus {
  /** Redistribution in a bundled APK explicitly permitted. */
  REDISTRIBUTABLE,

  /** Attribution required; redistribution typically permitted. */
  ATTRIBUTION_REQUIRED,

  /** Non-commercial only. */
  NON_COMMERCIAL_ONLY,

  /** Terms not confirmed — do not bundle. */
  UNVERIFIED,

  /** Government work; public alert dissemination, redistribution unconfirmed. */
  PUBLIC_GOVERNMENT
}

/** The India-wide source catalogue, as verified during the source audit. */
object IndiaSourceRegistry {

  val ALL: List<IndiaSource> = listOf(
    IndiaSource(
      id = "NDMA_SACHET",
      displayName = "NDMA SACHET",
      url = "https://sachet.ndma.gov.in/",
      accessibility = SourceAccessibility.AVAILABLE,
      dataClass = DataClass.OFFICIAL_ALERT,
      temporalClass = TemporalStatus.LIVE,
      yields = "CAP 1.2 national alerts: event, severity, certainty, sender, send/effective/expiry times, area when the alert carries one",
      doesNotYield = "A complete national hazard map. Alerts are per-event and expire; absence of an alert is not absence of risk."
    ),
    IndiaSource(
      id = "IMD_CAP",
      displayName = "IMD alerts",
      url = "https://cap-sources.s3.amazonaws.com/in-imd-en/rss.xml",
      accessibility = SourceAccessibility.UNVERIFIED,
      dataClass = DataClass.OFFICIAL_ALERT,
      temporalClass = TemporalStatus.LIVE,
      yields = "CAP weather warnings (cyclone, heavy rainfall, thunderstorm) with severity and certainty",
      doesNotYield = "Gridded warning polygons unless the CAP alert itself publishes one. Never synthesise a polygon."
    ),
    IndiaSource(
      id = "USGS_EARTHQUAKE",
      displayName = "USGS earthquakes",
      url = "https://earthquake.usgs.gov/fdsnws/event/1/query",
      accessibility = SourceAccessibility.AVAILABLE,
      dataClass = DataClass.SUPPLEMENTARY_OBSERVATION,
      temporalClass = TemporalStatus.LIVE,
      yields = "Earthquake point observations: magnitude, depth, coordinates, time, felt reports, alert level",
      doesNotYield = "India-specific damage assessment, building collapse, or a danger radius. This is not an Indian government source."
    ),
    IndiaSource(
      id = "NASA_FIRMS",
      displayName = "NASA FIRMS hotspots",
      url = "https://firms.modaps.eosdis.nasa.gov/",
      accessibility = SourceAccessibility.REQUIRES_KEY,
      dataClass = DataClass.SATELLITE_OBSERVATION,
      temporalClass = TemporalStatus.LIVE,
      yields = "Satellite fire hotspot points with acquisition time, brightness, FRP, confidence, satellite/instrument",
      doesNotYield = "Fire perimeters, fire cause, evacuation zones, or whether a detection is a structure fire vs crop burning."
    ),
    IndiaSource(
      id = "OPEN_METEO",
      displayName = "Open-Meteo",
      url = "https://api.open-meteo.com/v1/forecast",
      accessibility = SourceAccessibility.AVAILABLE,
      dataClass = DataClass.WEATHER_CONTEXT,
      temporalClass = TemporalStatus.LIVE,
      yields = "Temperature, rainfall, wind — current and forecast",
      doesNotYield = "Official warnings. Open-Meteo is not a weather authority and must never be labelled as one."
    ),
    IndiaSource(
      id = "CENSUS_2011",
      displayName = "Census of India 2011",
      url = "https://censusindia.gov.in/",
      accessibility = SourceAccessibility.AVAILABLE,
      dataClass = DataClass.HISTORICAL_BASELINE,
      temporalClass = TemporalStatus.HISTORICAL,
      yields = "Population at state/district/subdistrict/village as of 2011",
      doesNotYield = "Current population, current occupancy, or current evacuation demand."
    ),
    IndiaSource(
      id = "NWIC_BOUNDARIES",
      displayName = "NWIC admin boundaries",
      url = "https://bhuvan.nrsc.gov.in/",
      accessibility = SourceAccessibility.AVAILABLE,
      dataClass = DataClass.STATIC_GEOGRAPHY,
      temporalClass = TemporalStatus.STATIC,
      yields = "India-wide state, district and subdistrict polygon boundaries",
      doesNotYield = "Village boundaries outside Andhra Pradesh. Terrain, elevation or inundation."
    ),
    IndiaSource(
      id = "OPENSTREETMAP",
      displayName = "OpenStreetMap",
      url = "https://www.openstreetmap.org/",
      accessibility = SourceAccessibility.AVAILABLE,
      dataClass = DataClass.SUPPLEMENTARY_GEOGRAPHY,
      temporalClass = TemporalStatus.CACHED,
      yields = "Road network for routing, building footprints, settlement points",
      doesNotYield = "Designated evacuation shelters at India-wide coverage. A school/hospital POI is not a shelter unless tagged as one.",
      licenseStatus = LicenseStatus.ATTRIBUTION_REQUIRED
    ),
    IndiaSource(
      id = "EMDAT",
      displayName = "EM-DAT",
      url = "https://www.emdat.be/",
      accessibility = SourceAccessibility.AVAILABLE,
      dataClass = DataClass.HISTORICAL_BASELINE,
      temporalClass = TemporalStatus.HISTORICAL,
      yields = "Archive of past disaster events with dates, fatalities, affected counts",
      doesNotYield = "Current or upcoming hazard. Purely retrospective.",
      licenseStatus = LicenseStatus.NON_COMMERCIAL_ONLY
    ),

    // ---- Official sources that are NOT reachable. No fallback substituted. ----
    IndiaSource(
      id = "NDEM_NRSC",
      displayName = "NDEM (NRSC)",
      url = "https://ndem.nrsc.gov.in/",
      accessibility = SourceAccessibility.BLOCKED_LOGIN_REQUIRED,
      dataClass = DataClass.BLOCKED_OFFICIAL,
      temporalClass = TemporalStatus.UNKNOWN,
      yields = "Would give official multi-hazard layers, inundation and shelter data — IF authorised",
      doesNotYield = "Anything at all, without an authorised login. Not scraped, not approximated, not replaced with a third-party substitute.",
      licenseStatus = LicenseStatus.UNVERIFIED
    ),
    IndiaSource(
      id = "BHUVAN_THEMATIC",
      displayName = "NRSC Bhuvan thematic layers",
      url = "https://bhuvan.nrsc.gov.in/",
      accessibility = SourceAccessibility.MANUAL_ONLY,
      dataClass = DataClass.BLOCKED_OFFICIAL,
      temporalClass = TemporalStatus.UNKNOWN,
      yields = "Flood inundation / landslide susceptibility layers, if manually downloaded per layer",
      doesNotYield = "Automated bulk access. Any manual download must be registered with layer, date and license before use.",
      licenseStatus = LicenseStatus.UNVERIFIED
    ),
    IndiaSource(
      id = "CWC_GAUGES",
      displayName = "Central Water Commission gauges",
      url = "https://cwc.gov.in/en/hydrology",
      accessibility = SourceAccessibility.BLOCKED_NO_OPEN_API,
      dataClass = DataClass.BLOCKED_OFFICIAL,
      temporalClass = TemporalStatus.UNKNOWN,
      yields = "Would give live river levels and flood levels",
      doesNotYield = "Live gauge readings. River level therefore stays UNKNOWN and is never estimated from rainfall.",
      licenseStatus = LicenseStatus.PUBLIC_GOVERNMENT
    ),
    IndiaSource(
      id = "ROAD_CLOSURES",
      displayName = "Live road closures / traffic",
      url = "",
      accessibility = SourceAccessibility.BLOCKED_NO_OPEN_API,
      dataClass = DataClass.UNKNOWN,
      temporalClass = TemporalStatus.UNKNOWN,
      yields = "Nothing — no pan-India open feed identified in the source audit",
      doesNotYield = "Road condition, closures, congestion. A computed route is never presented as verified safe.",
      licenseStatus = LicenseStatus.UNVERIFIED
    )
  )

  val AVAILABLE: List<IndiaSource> = ALL.filter { it.accessibility == SourceAccessibility.AVAILABLE }
  val BLOCKED: List<IndiaSource> = ALL.filter {
    it.accessibility == SourceAccessibility.BLOCKED_LOGIN_REQUIRED ||
      it.accessibility == SourceAccessibility.BLOCKED_NO_OPEN_API ||
      it.accessibility == SourceAccessibility.MANUAL_ONLY
  }

  fun byId(id: String): IndiaSource? = ALL.firstOrNull { it.id == id }

  /**
   * Sources whose data must never be presented as current.
   */
  fun historicalOnly(): List<IndiaSource> = ALL.filter { it.temporalClass == TemporalStatus.HISTORICAL }

  /**
   * Datasets that must not be bundled into a shipped APK until redistribution
   * terms are confirmed.
   */
  fun doNotBundle(): List<IndiaSource> =
    ALL.filter { it.licenseStatus == LicenseStatus.UNVERIFIED && it.accessibility == SourceAccessibility.AVAILABLE }
}
