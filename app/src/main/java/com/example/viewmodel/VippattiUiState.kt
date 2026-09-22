package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.disaster.EmergencyContact
import com.example.data.disaster.GoBagItem
import com.example.data.disaster.MockDisasterRepository
import com.example.data.disaster.PilotRegionData
import com.example.data.disaster.dataStatus
import com.example.data.model.DataStatus
import com.example.data.model.UserProfile
import com.example.data.disaster.WeatherMetrics
import com.example.data.reports.EmergencyReport
import com.example.data.reports.EmergencyReportService
import com.example.data.reports.LocalEmergencyReportService
import com.example.data.reports.ReportKind
import com.example.data.reports.ReportReceipt
import com.example.BuildConfig
import com.example.data.news.GNewsServiceImpl
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsArticle
import com.example.data.news.NewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsFeed
import com.example.data.news.NewsRepository
import com.example.data.news.NewsTtsBulletin
import com.example.data.weather.OpenMeteoWeatherService
import com.example.data.model.GeoMath
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.data.routing.LiveRouteCache
import com.example.data.routing.OsrmRoutingService
import com.example.data.routing.RouteResult
import com.example.data.risk.ActionAdvisor
import com.example.data.risk.PersonalRiskAssessment
import com.example.data.risk.RecommendedAction
import com.example.data.risk.RelocationPlan
import com.example.data.risk.RelocationPlanner
import com.example.data.risk.RiskAssessmentEngine
import com.example.data.shelters.SafeZoneEvaluation
import com.example.data.shelters.SafeZoneEvaluator
import com.example.data.disaster.DisasterCache
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterEvent
import com.example.data.disaster.DisasterLayer
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.providers.FirmsFireProvider
import com.example.data.disaster.providers.ImdCapProvider
import com.example.data.disaster.IncidentCategory
import com.example.data.disaster.IncidentReport
import com.example.data.disaster.IndiaGeo
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.ProviderState
import com.example.data.disaster.providers.UsgsEarthquakeProvider
import com.example.data.disaster.defaultSeverity
import com.example.data.disaster.dedupeBySourceEventId
import com.example.data.disaster.toHazardZones
import com.example.data.disaster.isValid
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimum movement (meters) from the last route origin before GPS fixes may
 * trigger re-routing. Fixes arrive ~1/sec with sub-metre jitter; without this
 * guard every fix would cancel the in-flight OSRM request and the map would
 * starve (a route could never finish computing, so nothing was ever drawn).
 */
private const val REROUTE_MIN_MOVEMENT_METERS = 50.0

/** Cap on locally-held citizen reports (device memory guard). */

enum class ScreenTab {
  RADAR_MAP,
  NEWS_DISPATCHES,
  INSTRUCTIONS,
  PROFILE
}

/**
 * Explicit device-tool states. The old `Boolean` pair could not express "the
 * platform refused the torch", which is why the UI claimed the light was ON
 * while nothing happened.
 */
enum class TorchState { OFF, ON, UNAVAILABLE, PERMISSION_DENIED }

enum class SirenState { IDLE, PLAYING }

/**
 * Explicit route lifecycle. A route is only ever DRAWN in [READY]; a straight
 * hazard-skirting corridor is never presented as a computed safe route.
 */
enum class RouteStatus {
  /** No destination selected yet. */
  IDLE,
  /** Request in flight — nothing is drawn. */
  REQUESTING,
  /** A routing response was received. */
  RECEIVED,
  /** Received geometry is being checked against the live hazard picture. */
  VALIDATING_HAZARDS,
  /** Validated road route ready to draw. */
  READY,
  /** The router answered but found no usable corridor. */
  NO_ROUTE,
  /** The router could not be reached (offline / server error). */
  NETWORK_ERROR,
  /** User explicitly asked for the offline straight-line estimate. */
  FALLBACK_UNVERIFIED
}

/**
 * Central UI state. All intelligence results flow into this single state,
 * preserving the single primary flow:
 *   Compose UI -> ViewModel -> Repository/Services -> Location ->
 *   Hazard/Safe-Zone Intelligence -> OSRM Routing -> OSMDroid Map
 */
data class VippattiUiState(
  // --- App chrome ---
  val currentTab: ScreenTab = ScreenTab.RADAR_MAP,
  val isDarkTheme: Boolean = true,
  /**
   * Offline-first display preference. There is NO bulk offline download in this
   * build, so this flag only says "I intend to work offline"; the Profile pack
   * reports the REAL measured tile-cache size and says "Not measured yet"
   * until it exists. Defaults to false (no "Ready" on a fresh install).
   */
  val isOfflineFirstMode: Boolean = false,
  val lastSyncTime: String = "Not synced yet — tap Sync to fetch live GNews disaster news",
  val isSyncing: Boolean = false,
  val isAudioPlaying: Boolean = false,
  val audioPlaybackSeconds: Int = 0,
  val selectedNewsCategory: String = "All",
  // --- REAL GNews disaster-news pipeline (articles are never fabricated) ---
  val newsArticles: List<NewsArticle> = emptyList(),
  val newsHero: NewsArticle? = null,
  val isNewsFromCache: Boolean = false,
  val newsLastFetchedAtMillis: Long? = null,
  val newsEverLoaded: Boolean = false,
  val newsError: NewsError? = null,
  /** Spoken-bulletin text — composed from real state, consumed by real TTS. */
  val audioBulletinText: String = "",
  val goBagItems: List<GoBagItem> = MockDisasterRepository.defaultGoBagItems,
  val userIsSafe: Boolean = true,
  val isSosActive: Boolean = false,
  val showSosBroadcastDialog: Boolean = false,
  val showInteractiveBagDialog: Boolean = false,
  val showAddContactDialog: Boolean = false,
  /**
   * Device-tool state (torch/siren). Replaces the old boolean pair so the UI can
   * show an EXPLICIT, honest ON/OFF state — and the reason it failed — instead
   * of feedback that claims the torch is on when the platform refused it.
   */
  val torchState: TorchState = TorchState.OFF,
  /** Human-readable reason shown under the Light control when it cannot turn on. */
  val torchMessage: String? = null,
  val sirenState: SirenState = SirenState.IDLE,
  /** Seconds left before the siren auto-stops (drives the visible countdown). */
  val sirenSecondsLeft: Int = 0,
  val contactsList: List<EmergencyContact> = MockDisasterRepository.emergencyContacts,

  // --- Editable citizen profile + REAL device battery (replaces hardcoded 84%) ---
  val userProfile: UserProfile = UserProfile(),
  val showEditProfileDialog: Boolean = false,
  val batteryPercent: Int? = null,
  val isBatteryCharging: Boolean = false,
  val showSosConfirmDialog: Boolean = false,
  val showSituationReportDialog: Boolean = false,
  val isSubmittingReport: Boolean = false,
  val lastReportReceipt: ReportReceipt? = null,
  val weather: WeatherMetrics = WeatherMetrics(),
  /**
   * PHASE 2: weather carries the shared status vocabulary instead of a bare
   * boolean. LOADING until the first attempt settles, SUCCESS for a reading
   * fetched in this session, STALE when a live refresh failed but an earlier
   * real reading is still shown, UNAVAILABLE when there is nothing to show.
   */
  val weatherStatus: DataStatus = DataStatus.LOADING,
  /** When the shown reading was actually retrieved; null = never measured. */
  val weatherRetrievedAtMillis: Long? = null,
  /**
   * Honest reason the last weather refresh failed (provider/exception detail),
   * or null when it succeeded. Never a generic "error" placeholder.
   */
  val weatherErrorMessage: String? = null,
  val snackbarMessage: String? = null,

  /**
   * Place resolved AT RUNTIME from the user's coordinates (district/state names
   * for query scoping and honest labels). Null = not resolved, never assumed.
   */
  val resolvedPlace: com.example.data.location.ResolvedPlace? = null,

  // --- Location (REAL GPS preferred; India-centre view until a fix arrives) ---
  val userLocation: GeoPoint = GeoPoint(
    com.example.data.disaster.IndiaGeo.CENTER_LAT,
    com.example.data.disaster.IndiaGeo.CENTER_LON
  ),
  /**
   * true -> NO GPS fix yet; the user location is just the India map centre
   * (NEVER a district-level fallback). false -> real device GPS fix.
   */
  val isUserLocationFallback: Boolean = true,

  // --- REAL India-wide disaster pipeline (USGS + FIRMS + IMD CAP) ---
  /** Live, provider-sourced events (normalized, validated, deduped). */
  val disasterEvents: List<DisasterEvent> = emptyList(),
  /** Per-source freshness/status (Live / Recent / Cached / Unavailable). */
  val providerStates: List<ProviderState> = emptyList(),
  /** True while a disaster-data sync is in flight. */
  val isDisasterSyncing: Boolean = false,
  /** Aggregated label: LIVE when any provider is live, else cached/unavailable. */
  val isDisasterDataLive: Boolean = false,
  /** Latest sync time across providers (for "Last updated" display). */
  val disasterLastSyncMillis: Long? = null,
  /** Map layer toggles (user-controlled; zoom rules applied at render time). */
  val enabledLayers: Set<DisasterLayer> = setOf(
    DisasterLayer.OFFICIAL_ALERTS, DisasterLayer.EARTHQUAKES,
    DisasterLayer.USER_REPORTS, DisasterLayer.SAFE_ZONES,
    DisasterLayer.EVACUATION_ROUTE, DisasterLayer.MY_LOCATION
  ),
  /** Mock-data compat flag — mirrors the radar "SIMULATED DEMO" toggle. */
  val isMockMode: Boolean = true,
  /**
   * SIMULATED demo-data visibility — the radar "SIMULATED DEMO" toggle.
   * ON (default) shows the labelled India multi-state network; OFF removes
   * ONLY the simulated zones (live provider events and citizen reports keep
   * flowing to the map and every engine).
   */
  val isMockDataVisible: Boolean = true,
  /** Citizen-submitted incident reports (unverified, TTL'd). */
  val userIncidentReports: List<IncidentReport> = emptyList(),
  /** Selected disaster event for the tap detail panel. */
  val disasterEventDetail: DisasterEvent? = null,
  /** Dialog for the "Add a Report" incident flow. */
  val showIncidentReportDialog: Boolean = false,

  // --- Intelligence results (recomputed on every location change) ---
  val hazardZones: List<HazardZone> = emptyList(),
  val safeZones: List<SafeZone> = PilotRegionData.safeZones,
  /** EVERY shelter evaluated (feasible AND rejected) — UI must show rejection reasons. */
  val evaluatedShelters: List<SafeZoneEvaluation> = emptyList(),
  /** Feasible-only, best-first — feeds routing/assignment (unchanged contract). */
  val rankedShelters: List<SafeZoneEvaluation> = emptyList(),
  val personalRisk: PersonalRiskAssessment? = null,
  val recommendedAction: RecommendedAction? = null,
  val relocationPlan: RelocationPlan? = null,

  // --- EMERGENCY SHELTER GUIDANCE (SIH 26191) ---
  /**
   * "Disaster happens -> show where to go": derived from risk level + the
   * evaluated shelters + the current destination on every recompute.
   */
  val emergencyGuidance: com.example.data.shelters.EmergencyGuidance =
    com.example.data.shelters.EmergencyGuidance.None,
  /** Nearest terrain-rated-safe haven from an explicit user search (never automatic). */
  val terrainHaven: com.example.data.shelters.SafeHaven? = null,
  /** True while the haven rings are being probed. */
  val isSearchingHaven: Boolean = false,

  // --- Carrying capacity (SIH milestone) ---
  /** How many people need relocation here; null = no population figure. */
  val capacityDemand: com.example.data.capacity.RelocationDemand? = null,
  /** Capacity verdict per evaluated site id (all candidates, not only ranked). */
  val capacityAssessments: Map<String, com.example.data.capacity.CapacityAssessment> = emptyMap(),

  // --- Population (SIH 26191) ---
  /** Population records from the connected source; empty = no source. */
  val populationRecords: List<com.example.data.population.PopulationRecord> = emptyList(),
  /** How the relocation demand was resolved, with the reasons for the choice. */
  val populationResolution: com.example.data.population.PopulationDemandResolution? = null,
  /**
   * The three population figures kept in one place: baseline, affected and
   * relocation demand. Null when nothing was aggregated yet.
   */
  val populationAssessment: com.example.data.population.PopulationAssessment? = null,
  /**
   * Honest error from the population source, when the fetch itself failed.
   * Null means the last fetch succeeded (or no source is configured).
   */
  val populationSourceError: String? = null,


  // --- HISTORICAL DISASTER INTELLIGENCE (EM-DAT archive) ---
  /**
   * The loaded historical catalog, or null when nothing is attached/readable.
   * This data is NEVER live: see [historicalStatus].
   */
  val historicalCatalog: com.example.data.historical.HistoricalDisasterCatalog? = null,
  /** HISTORICAL on success, NOT_CONFIGURED / UNAVAILABLE / ERROR otherwise. */
  val historicalStatus: DataStatus = DataStatus.LOADING,
  val historicalError: String? = null,
  /** Area-level historical evidence for the resolved place (context only). */
  val historicalContext: com.example.data.historical.HistoricalContext? = null,
  val historicalFilters: com.example.data.historical.HistoricalFilters =
    com.example.data.historical.HistoricalFilters(),
  /** Archived records are hidden from the map unless the user enables them. */
  val isHistoricalLayerOn: Boolean = false,
  /** Record open in the historical detail sheet. */
  val historicalDetailEvent: com.example.data.historical.HistoricalDisasterEvent? = null,

  // --- REAL tile-cache size (computed from disk, never fabricated) ---
  val tileCacheBytes: Long? = null,

  // --- Routing ---
  val selectedSafeZone: SafeZone? = null,
  val selectedEvaluation: SafeZoneEvaluation? = null,
  val activeRoute: RouteResult? = null,
  val alternativeRoutes: List<RouteResult> = emptyList(),
  val isCalculatingRoute: Boolean = false,
  /** Explicit routing lifecycle — drives the "no straight line" rule. */
  val routeStatus: RouteStatus = RouteStatus.IDLE,
  /** Honest, user-facing explanation of the current [routeStatus]. */
  val routeStatusMessage: String? = null,
  /** True when the drawn route is the explicitly-requested offline estimate. */
  val travelMode: String = "foot", // "foot" or "driving"
  val isNavigatingLive: Boolean = false,
  val currentNavigationStepIndex: Int = 0,

  // --- Detail sheets ---
  val hazardDetailZone: HazardZone? = null,
  val safeZoneDetail: SafeZone? = null
) {

  // --- Derived broadcast labels (REAL battery/GPS/relays - no hardcoded 84%) ---

  /**
   * True only for a weather reading fetched live in this session. A stale or
   * unavailable reading never reads as live.
   */
  val isWeatherLive: Boolean get() = weatherStatus == DataStatus.SUCCESS

  // --- Historical (EM-DAT) derived views: filtering and summarising only ---

  /** Records matching the active filters, newest first. */
  val historicalFilteredEvents: List<com.example.data.historical.HistoricalDisasterEvent>
    get() = historicalCatalog
      ?.apply(historicalFilters)
      ?.sortedByDescending { it.startDate.sortKey }
      .orEmpty()

  val historicalImpactSummary: com.example.data.historical.HistoricalImpactSummary
    get() = historicalCatalog?.impactSummary(historicalFilteredEvents)
      ?: com.example.data.historical.HistoricalImpactSummary.EMPTY

  val historicalYearlyTrend: List<com.example.data.historical.HistoricalTrendPoint>
    get() = historicalCatalog?.yearlyTrend(historicalFilteredEvents).orEmpty()

  val historicalDecadeTrend: List<com.example.data.historical.HistoricalTrendPoint>
    get() = historicalCatalog?.decadeTrend(historicalFilteredEvents).orEmpty()

  val historicalTypeFacets: List<Pair<String, Int>>
    get() = historicalCatalog?.typeFacets(historicalFilteredEvents).orEmpty()

  /**
   * The ONLY records the map may draw: the layer is on AND the record carries
   * its own source coordinates. Everything else stays area-level context.
   */
  val historicalMappableEvents: List<com.example.data.historical.HistoricalDisasterEvent>
    get() = if (!isHistoricalLayerOn) {
      emptyList()
    } else {
      historicalFilteredEvents.filter { it.isMappable }
    }

  /** Honest one-line status for the historical panel header. */
  val historicalStatusLine: String
    get() = when (historicalStatus) {
      DataStatus.HISTORICAL -> listOfNotNull(
        "HISTORICAL DATA",
        historicalCatalog?.info?.version?.let { "EM-DAT $it" },
        historicalCatalog?.let { "${it.totalCount} records" },
        historicalCatalog?.yearRange?.let { "${it.first}–${it.last}" }
      ).joinToString(" • ")
      DataStatus.LOADING -> "Loading historical dataset"
      DataStatus.NOT_CONFIGURED -> historicalError
        ?: "No historical disaster dataset is configured"
      DataStatus.ERROR -> historicalError ?: "Historical dataset could not be read"
      DataStatus.UNAVAILABLE -> historicalError ?: "Historical dataset unavailable"
      else -> historicalError ?: historicalStatus.label
    }

  /** Carrying-capacity verdict for the shelter open in the detail sheet. */
  val selectedCapacityAssessment: com.example.data.capacity.CapacityAssessment?
    get() = selectedSafeZone?.let { capacityAssessments[it.id] }

  /** Carrying-capacity verdict for the assigned relocation destination. */
  val assignedCapacityAssessment: com.example.data.capacity.CapacityAssessment?
    get() = relocationPlan?.capacityAssessment

  // --- Population lines, kept strictly separate and honestly labelled ---

  /** Baseline census/registrar figure, if a source provided one. */
  val baselinePopulationLabel: String
    get() = populationAssessment?.baseline?.let { record ->
      "${record.value} (${record.classification.label.lowercase()}, ${record.scopeLabel})" +
        " — not confirmed relocation demand"
    } ?: "Not provided — no census source connected"

  /** Currently affected population, if a source provided one. */
  val affectedPopulationLabel: String
    get() = populationAssessment?.affected?.let { record ->
      "${record.value} (${record.classification.label.lowercase()}, ${record.scopeLabel})" +
        " — not confirmed relocation demand"
    } ?: "Not provided — no affected-population source connected"

  /** The figure used for capacity assessment, with its honest label. */
  val relocationDemandLabel: String
    get() = populationResolution?.let { resolution ->
      val demand = resolution.demand
      demand.people?.let { "$it — ${demand.roleLabel} (${demand.scopeLabel})" }
        ?: "Not available — ${resolution.statusLabel}"
    } ?: "Not available"

  /** Explicit torch state exposed to the UI. Never a "maybe on" boolean. */
  val isFlashlightOn: Boolean get() = torchState == TorchState.ON

  /** Explicit siren state exposed to the UI. */
  val isSirenOn: Boolean get() = sirenState == SirenState.PLAYING

  /** True when a device tool is active (drives the global active-tools bar). */
  val hasActiveDeviceTool: Boolean get() = isFlashlightOn || isSirenOn

  /** True while a route request is in flight (spinner + "nothing drawn yet"). */
  val isRouteInFlight: Boolean
    get() = routeStatus == RouteStatus.REQUESTING ||
      routeStatus == RouteStatus.RECEIVED ||
      routeStatus == RouteStatus.VALIDATING_HAZARDS

  /**
   * True only when the drawn geometry is a validated, live road route. Any
   * offline estimate is NOT considered a safe route.
   */
  val hasValidatedRoute: Boolean
    get() = routeStatus == RouteStatus.READY && activeRoute?.isLiveOsrm == true


  /** Live device battery reading for SOS/report payloads. */
  val batteryLabel: String
    get() = batteryPercent?.let { percent ->
      "$percent%" + if (isBatteryCharging) " (Charging)" else " (Discharging)"
    } ?: "Reading device battery..."

  /** Real coordinates (GPS fix or India-centre view) shown in the SOS dialogs. */
  val sosLocationLabel: String
    get() = String.format(
      "%.4f N, %.4f E (%s)",
      userLocation.lat,
      userLocation.lon,
      if (isUserLocationFallback) "NO GPS FIX" else "LIVE GPS"
    )

  /**
   * What an SOS actually reaches in THIS build. There is no relief-network
   * backend, so this must never claim an authority was notified.
   */
  val priorityRelaysLabel: String
    get() = "NOT TRANSMITTED — local record only • " + contactsList.size + " kin contacts saved"

  /**
   * Honest aggregate disaster-data status for the map UI. Derived ONLY from
   * real provider states — a cached source is never labeled LIVE.
   */
  val providerStatuses: List<Pair<ProviderState, DataStatus>>
    get() = providerStates.map { state -> state to state.dataStatus() }

  val disasterDataStatusLabel: String
    get() {
      if (providerStates.isEmpty()) return "NOT SYNCED"
      val statuses = providerStatuses
      val live = statuses.count { (state, status) ->
        status == DataStatus.SUCCESS && !state.isFromCache
      }
      val cached = statuses.count { (state, status) ->
        state.isFromCache && status != DataStatus.ERROR
      }
      // Three separate facts: a source that FAILED is not the same as one that
      // was never CONFIGURED in this build, and neither is "unavailable" cache.
      val failed = statuses.count { (_, status) -> status == DataStatus.ERROR }
      val unavailable = statuses.count { (_, status) -> status == DataStatus.UNAVAILABLE }
      val unconfigured = statuses.count { (_, status) -> status == DataStatus.NOT_CONFIGURED }
      val last = disasterLastSyncMillis
      val stale = last != null && !com.example.data.disaster.DisasterCachePolicy.isRecent(last, System.currentTimeMillis())
      val parts = mutableListOf<String>()
      if (live > 0) parts += "LIVE"
      if (cached > 0) parts += "CACHED"
      if (failed > 0) parts += "$failed FAILED"
      if (unavailable > 0) parts += "$unavailable UNAVAILABLE"
      if (unconfigured > 0) parts += "$unconfigured NOT CONFIGURED"
      if (parts.isEmpty()) parts += if (stale) "STALE" else "NO DATA"
      return parts.joinToString(" • ")
    }

  /** Human-readable tile-cache size (e.g. "48.3 MB") or null until measured. */
  val tileCacheSizeLabel: String?
    get() = tileCacheBytes?.let { bytes ->
      when {
        bytes >= 1_000_000L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
      }
    }

  /**
   * Honest news-connection state for the Dispatches banner — derived from the
   * real sync label the news pipeline produced, never hardcoded "OFFLINE".
   */
  val newsStatus: DataStatus
    get() = when {
      isSyncing -> DataStatus.LOADING
      newsArticles.isNotEmpty() && isNewsFromCache -> DataStatus.STALE
      newsArticles.isNotEmpty() -> DataStatus.SUCCESS
      newsError != null -> DataStatus.ERROR
      else -> DataStatus.EMPTY
    }

  /**
   * Honest description of how the news feed was scoped: the resolved place
   * names when they exist, otherwise an explicit national-only statement. It
   * never names a district or state the app did not resolve.
   */
  val newsScopeNote: String
    get() {
      val rings = listOfNotNull(
        resolvedPlace?.district?.takeIf { it.isNotBlank() },
        resolvedPlace?.state?.takeIf { it.isNotBlank() }
      )
      return if (rings.isEmpty()) {
        "News scope: India-wide - district/state not resolved from location"
      } else {
        "News scope: ${rings.joinToString(", ")} + India" +
          (resolvedPlace?.source?.let { " ($it)" } ?: "")
      }
    }

  val newsConnectionStateLabel: String
    get() = when (newsStatus) {
      DataStatus.LOADING -> "SYNCING…"
      DataStatus.SUCCESS -> "ONLINE • LIVE GNEWS FEED"
      DataStatus.STALE -> "OFFLINE • CACHED FEED"
      DataStatus.ERROR -> "ERROR • NEWS FEED UNREACHABLE"
      else -> "NOT SYNCED"
    }
}
