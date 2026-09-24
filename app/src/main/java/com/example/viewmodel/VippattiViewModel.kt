package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.disaster.EmergencyContact
import com.example.data.disaster.GoBagItem
import com.example.data.disaster.MockDisasterRepository
import com.example.data.disaster.PilotRegionData
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
import com.example.data.capacity.CarryingCapacityEngine
import com.example.data.capacity.RelocationDemand
import com.example.data.location.PlaceResolver
import com.example.data.population.NoPopulationProvider
import com.example.data.population.PopulationDataProvider
import com.example.data.population.PopulationDemandPolicy
import com.example.data.population.PopulationDemandResolver
import com.example.data.population.PopulationRecord
import com.example.data.location.ResolvedPlace
import com.example.data.location.UnresolvedPlaceResolver
import com.example.data.news.NewsQueryFactory
import com.example.data.weather.OpenMeteoWeatherService
import com.example.data.weather.WeatherReading
import com.example.data.weather.dataStatus
import com.example.data.weather.reason
import com.example.data.model.GeoMath
import com.example.data.model.DataStatus
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
import kotlinx.coroutines.withTimeoutOrNull
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
private const val MAX_INCIDENT_REPORTS = 50

/** Siren auto-stop window, surfaced to the user as a visible countdown. */
const val SIREN_MAX_SECONDS = 60

/**
 * Place resolution guard: re-resolve the district/state only after the user has
 * actually moved this far, since the reverse geocoder is a network service.
 */
private const val PLACE_RESOLVE_MIN_MOVEMENT_METERS = 2_000.0

/** Reverse-geocode timeout; on expiry the app honestly stays national-scoped. */
private const val PLACE_RESOLVE_TIMEOUT_MS = 6_000L

/** Reason string returned when the CAMERA permission is required for the torch. */
const val TORCH_REASON_PERMISSION = "Camera permission is required to switch the light on."

/**
 * Weather refresh guard. A 1 Hz GPS stream used to trigger one Open-Meteo HTTP
 * request per fix; the reading is now reused for [WEATHER_MIN_REFRESH_MS] and
 * only re-fetched after meaningful movement or a manual sync.
 */
private const val WEATHER_MIN_REFRESH_MS = 10L * 60L * 1000L
private const val WEATHER_MIN_MOVEMENT_METERS = 2_000.0

/**
 * STAGE 5 — closure/traffic honesty note. OSRM computes road geometry and the
 * app checks it against known hazard geometry, but there is no road-closure
 * feed and no live-traffic feed in this build, so every READY road-route
 * message carries this sentence. It is a single constant so the cached, live
 * and alternatives messages can never drift apart.
 */
private const val ROUTE_LIMITATIONS_NOTE = "Road closures and live traffic are not verified."


class VippattiViewModel(
  /** Local emergency-report recorder (no backend relay exists in this build). */
  private val reportService: EmergencyReportService = LocalEmergencyReportService(),
  /**
   * Real GNews disaster-news cache — file-backed in production (MainActivity
   * injects NewsFileCache so cached articles survive process restarts),
   * in-memory by default (unit tests).
   */
  newsCache: NewsCache = MemoryNewsCache(),
  /**
   * REAL disaster-data cache — file-backed in production (MainActivity
   * injects DisasterFileCache so provider shards survive process restarts),
   * in-memory by default (unit tests).
   */
  disasterCache: DisasterCache = MemoryDisasterCache(),
  /**
   * Directory of the osmdroid tile cache — injected by MainActivity so the
   * offline map-cache size shown on the Profile screen is a REAL disk
   * measurement (no fabricated "48 MB / 64 MB" values).
   */
  private val tileCacheDirProvider: () -> java.io.File? = { null },
  /**
   * REAL India-wide disaster data repository. Default: live USGS (keyless),
   * NASA FIRMS (free MAP_KEY via app/.env) and IMD official CAP alerts
   * (keyless). Tests inject a repository with fake providers.
   */
  private val disasterRepository: DisasterDataRepository = DisasterDataRepository(
    providers = listOf(
      UsgsEarthquakeProvider(),
      ImdCapProvider(),
      FirmsFireProvider(mapKeyProvider = { BuildConfig.FIRMS_MAP_KEY })
    ),
    cache = disasterCache
  ),
  /** News pipeline override — tests inject a failing/fake service (no real network). */
  private val newsRepositoryOverride: NewsRepository? = null,
  /**
   * Runtime place resolver (dynamic-data rule): district/state names for news
   * scoping come from the user's own coordinates, never from constants. The
   * default resolves nothing, so a caller without a resolver stays national.
   */
  private val placeResolver: PlaceResolver = UnresolvedPlaceResolver,
  /**
   * EMERGENCY GUIDANCE wiring (SIH 26191): live terrain probe + offline coast
   * grid for the terrain-derived safe-haven finder. Production MainActivity
   * injects the asset-backed grid; tests inject a fake finder. Null finder =
   * haven search stays honest-off (reported as unavailable).
   */
  private val coastGridProvider: () -> com.example.data.suitability.CoastDistanceGrid? = { null },
  private val havenFinderOverride: com.example.data.shelters.SafeHavenFinder? = null,
  /**
   * Live terrain probe for self-assessment + haven search (keyless Open-Meteo
   * elevation + rainfall). Tests inject a fake transport; production uses the
   * real service. Exposed so both features share ONE transport boundary.
   */
  private val terrainProbeOverride: com.example.data.suitability.TerrainProbeService? = null,
  /**
   * AUTHORITY FIELD REGISTRY (SIH 26191): operator-entered shelter +
   * habitation records. Field shelters are REAL records and stay in scope
   * even with the simulated demo network hidden. Tests inject an in-memory
   * store via [fieldRegistryStoreOverride]; production uses the file-backed
   * store below the app's private cache dir.
   */
  private val fieldRegistryStoreOverride: com.example.data.habitations.FieldRegistryStore? = null,
  /** Directory for the file-backed registry (production; null in plain-JVM tests). */
  private val registryDirProvider: () -> java.io.File? = { null },
  /**
   * Population source boundary (SIH 26191). No census/relief-registry API is
   * connected in this build, so the default returns nothing and the demand
   * resolution honestly reports INSUFFICIENT_DATA until a real source is wired.
   */
  private val populationProvider: PopulationDataProvider = NoPopulationProvider,
  /**
   * HISTORICAL DISASTER INTELLIGENCE (EM-DAT). Bundled as an asset in
   * production; tests inject a fake string. Nothing here is a live feed, so the
   * dataset is loaded once per process and never reported as live.
   */
  private val historicalProvider: com.example.data.historical.HistoricalDataProvider =
    com.example.data.historical.NoHistoricalDataProvider,
  /**
   * Weather fetcher — production reads live Open-Meteo; tests inject a fake so
   * unit tests never perform real HTTP. Returns a [WeatherReading] so a failure
   * arrives with its real reason instead of a bare null.
   */
  private val weatherFetcher: suspend (GeoPoint) -> WeatherReading =
    OpenMeteoWeatherService::fetchReading,
  /**
   * Live road-route fetcher — production calls the OSRM service over HTTP on a
   * worker thread; tests inject a fake (success or failure) so the explicit
   * route states are covered deterministically without any real network.
   */
  private val liveRouteFetcher: suspend (
    origin: GeoPoint,
    destination: GeoPoint,
    mode: String,
    hazards: List<HazardZone>,
    destinationName: String,
    wantAlternatives: Int
  ) -> List<RouteResult> = OsrmRoutingService::fetchLiveRoutesAsync
) : ViewModel() {

  /** Real GNews disaster-news pipeline (live API + offline cache). */
  private val newsRepository: NewsRepository = newsRepositoryOverride
    ?: NewsRepository(
      service = GNewsServiceImpl(),
      cache = newsCache,
      apiKeyProvider = { BuildConfig.GNEWS_API_KEY }
    )

  private val _uiState = MutableStateFlow(VippattiUiState())
  val uiState: StateFlow<VippattiUiState> = _uiState.asStateFlow()

  private var audioJob: Job? = null
  private var sirenJob: Job? = null
  private var routingJob: Job? = null
  private var newsJob: Job? = null
  private var disasterJob: Job? = null
  private var weatherJob: Job? = null

  /** Location the current/last route was computed from — guards GPS re-routing. */
  private var lastRouteOrigin: GeoPoint? = null

  /**
   * Destination the user asked "START EVACUATION ROUTE" for while no route
   * geometry existed yet. The button cannot start guidance on an empty corridor
   * (the HUD would have no geometry behind it), so it requests the road route
   * and remembers the intent — the intent is consumed by the next route that
   * actually arrives for that zone, which is what makes the button's own promise
   * ("guidance starts when it arrives") true. A plain route request never sets
   * it, so browsing shelters can never start guidance on its own.
   */
  private var pendingGuidanceZoneId: String? = null

  /** Weather reading guard: TTL + movement anchor (see [refreshWeather]). */
  private var weatherFetchedAtMillis = 0L
  private var weatherAnchor: GeoPoint? = null

  /** Place-resolution guard: only re-resolve on meaningful movement. */
  private var placeJob: Job? = null
  private var placeAnchor: GeoPoint? = null

  /** Population source job (SIH 26191) — no source connected by default. */
  private var populationJob: Job? = null

  /** Historical dataset job — loaded once per process (it is an archive). */
  private var historicalJob: Job? = null

  /** Live road routes by destination/mode/origin-grid/hazards — instant exact roads on repeat views. */
  private val liveRouteCache = LiveRouteCache()

  /**
   * Injected test store, or the production file-backed store, or an in-memory
   * fallback. EAGER and declared before `init` (the cold-start registry load
   * needs it; a `by lazy` declared later would still have a null delegate).
   */
  private val fieldRegistryStore: com.example.data.habitations.FieldRegistryStore =
    fieldRegistryStoreOverride
      ?: registryDirProvider()?.let { dir ->
        com.example.data.habitations.FileFieldRegistryStore(dir)
      }
      ?: com.example.data.habitations.InMemoryFieldRegistryStore()

  init {
    // Cold start: run the intelligence pipeline once on the India-centre view
    // so risk is assessed (GREEN without GPS — no fake hazards), then pull
    // the REAL India-wide disaster feeds (cache-first when offline).
    reloadFieldRegistry(silent = true)
    recomputeIntelligence(selectInitialShelter = false)
    disasterJob = viewModelScope.launch {
      val cached = disasterRepository.loadCachedOnly()
      if (cached != null) {
        applyDisasterFeed(cached)
      } else {
        applyDisasterFeed(disasterRepository.refresh())
      }
    }
    // Cold start of the REAL GNews pipeline: a fresh cache (< 30 min) serves
    // instantly (offline survival + quota protection); otherwise it fetches.
    newsJob = viewModelScope.launch { applyNewsFeed(newsRepository.ensureLoaded(currentNewsQueries())) }
    // Cold start of LIVE weather (Open-Meteo, keyless) for the radar bar.
    refreshWeather()
    // Population records for the demand pipeline (no source connected by
    // default; an empty result keeps the demand at INSUFFICIENT_DATA).
    refreshPopulation()
    // HISTORICAL archive (EM-DAT): loaded once. It never feeds the live hazard
    // picture, the risk score or an official zone - only context and evidence.
    refreshHistoricalDataset()
  }

  // ================================================= HISTORICAL (EM-DAT)

  /**
   * Loads the bundled historical archive. Historic data is NEVER live: the
   * status is HISTORICAL on success, UNAVAILABLE when nothing is attached and
   * ERROR when an attached dataset cannot be read.
   */
  fun refreshHistoricalDataset() {
    historicalJob?.cancel()
    _uiState.update { it.copy(historicalStatus = DataStatus.LOADING) }
    historicalJob = viewModelScope.launch {
      val result = try {
        historicalProvider.load(System.currentTimeMillis())
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        com.example.data.historical.HistoricalLoadResult.Failed(
          error.message ?: error.javaClass.simpleName
        )
      }
      when (result) {
        is com.example.data.historical.HistoricalLoadResult.Loaded -> {
          _uiState.update {
            it.copy(
              historicalCatalog = result.catalog,
              historicalStatus = DataStatus.HISTORICAL,
              historicalError = null
            )
          }
        }
        is com.example.data.historical.HistoricalLoadResult.Unavailable -> {
          _uiState.update {
            it.copy(
              historicalCatalog = null,
              historicalStatus = DataStatus.NOT_CONFIGURED,
              historicalError = result.reason
            )
          }
        }
        is com.example.data.historical.HistoricalLoadResult.Failed -> {
          _uiState.update {
            it.copy(
              historicalCatalog = null,
              historicalStatus = DataStatus.ERROR,
              historicalError = result.reason
            )
          }
        }
      }
      refreshHistoricalContext()
    }
  }

  /**
   * Rebuilds the area-level historical context from the RESOLVED place (never a
   * hardcoded region). It is displayed as supporting evidence with its own
   * limitations and does not touch the risk score.
   */
  private fun refreshHistoricalContext() {
    val state = _uiState.value
    val place = state.resolvedPlace
    _uiState.update {
      it.copy(
        historicalContext = com.example.data.historical.HistoricalContextService
          .contextFor(
            district = place?.district,
            state = place?.state,
            catalog = state.historicalCatalog
          )
      )
    }
  }

  /** Applies the user's historical filters (type/year/area/country). */
  fun setHistoricalFilters(filters: com.example.data.historical.HistoricalFilters) {
    _uiState.update { it.copy(historicalFilters = filters) }
  }

  fun clearHistoricalFilters() {
    _uiState.update { it.copy(historicalFilters = com.example.data.historical.HistoricalFilters()) }
  }

  /**
   * The historical map layer is OFF by default so archived events never clutter
   * the live hazard map. Only records with their own dataset coordinates are
   * ever drawn - no coordinate is ever inferred from location text.
   */
  fun toggleHistoricalLayer() {
    val next = !_uiState.value.isHistoricalLayerOn
    _uiState.update {
      it.copy(
        isHistoricalLayerOn = next,
        snackbarMessage = if (next) {
          "Historical layer ON — EM-DAT archive records with source coordinates; " +
            "these are past events, not current hazards"
        } else {
          "Historical layer OFF"
        }
      )
    }
  }

  fun openHistoricalEventDetail(event: com.example.data.historical.HistoricalDisasterEvent) {
    _uiState.update { it.copy(historicalDetailEvent = event) }
  }

  fun closeHistoricalEventDetail() {
    _uiState.update { it.copy(historicalDetailEvent = null) }
  }

  /**
   * Pulls LIVE temperature / rainfall / wind + 3-hour trend for the current
   * map location. Offline or API failure keeps the previous reading (or the
   * honest empty state on first run) — weather is never invented.
   */
  fun refreshWeather(force: Boolean = false) {
    // PERFORMANCE FIX: this used to fire one HTTP request per GPS fix (1 Hz).
    // A reading is now reused until the TTL expires or the user has moved
    // meaningfully; a manual Sync still forces a fresh reading.
    val anchor = _uiState.value.userLocation
    val stillFresh = System.currentTimeMillis() - weatherFetchedAtMillis < WEATHER_MIN_REFRESH_MS
    val movedFarEnough = weatherAnchor?.let { previous ->
      GeoMath.distanceMeters(previous, anchor) >= WEATHER_MIN_MOVEMENT_METERS
    } ?: true
    if (!force && stillFresh && !movedFarEnough) return

    weatherJob?.cancel()
    weatherJob = viewModelScope.launch {
      when (val reading = weatherFetcher(anchor)) {
        is WeatherReading.Success -> {
          weatherFetchedAtMillis = System.currentTimeMillis()
          weatherAnchor = anchor
          _uiState.update {
            it.copy(
              weather = reading.metrics,
              weatherStatus = DataStatus.SUCCESS,
              weatherRetrievedAtMillis = weatherFetchedAtMillis,
              weatherErrorMessage = null
            )
          }
        }
        is WeatherReading.Failure -> {
          // Honest failure: a previously fetched real reading stays visible but
          // is marked STALE; with nothing to show the status is UNAVAILABLE or
          // ERROR depending on what actually went wrong. The provider/exception
          // reason travels to the UI, and no weather value is ever invented.
          val hasUsableReading = _uiState.value.weather != WeatherMetrics()
          _uiState.update {
            it.copy(
              weatherStatus = reading.dataStatus(hasUsableReading),
              weatherErrorMessage = reading.reason
            )
          }
        }
      }
    }
  }

  /**
   * Pulls population records from the connected source. The default provider
   * returns nothing (no census/registry API is wired), so the stored list stays
   * empty and the demand resolution reports INSUFFICIENT_DATA honestly rather
   * than substituting a figure.
   */
  private fun refreshPopulation() {
    populationJob?.cancel()
    populationJob = viewModelScope.launch {
      // A population source is a network/registry boundary: it CAN fail. A
      // failure must never escape the coroutine (which would crash the app),
      // never erase records already fetched, and never invent a figure.
      val records = try {
        populationProvider.fetchRecords()
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        val reason = error.message?.takeIf { it.isNotBlank() }
          ?: error.javaClass.simpleName
        _uiState.update {
          it.copy(
            populationSourceError =
              "Population source unavailable: $reason. Previous figures kept."
          )
        }
        return@launch
      }
      if (records != _uiState.value.populationRecords ||
        _uiState.value.populationSourceError != null
      ) {
        _uiState.update {
          it.copy(populationRecords = records, populationSourceError = null)
        }
        recomputeIntelligence()
      }
    }
  }

  // ============================================================ INTELLIGENCE

  /**
   * The heart of the decision pipeline:
   * GPS/India-centre location -> hazard analysis (REAL live events + user
   * reports + explicitly-labeled mock data) -> safe-zone discovery ->
   * capacity check -> ranked options -> personal risk -> recommended action ->
   * relocation plan -> evacuation route.
   */
  private fun recomputeIntelligence(selectInitialShelter: Boolean = false) {
    val state = _uiState.value
    val location = state.userLocation
    val now = System.currentTimeMillis()

    // Hazard picture assembly.
    // LIVE provider events and citizen reports are ALWAYS included: the
    // "simulated demo" switch must never hide real hazards. Previously it emptied
    // the whole picture, which silently produced a GREEN risk verdict and empty
    // routing anywhere in India whenever demo data was switched off.
    val liveZones = toHazardZones(state.disasterEvents.filter { it.isValid(now) })
    val reportZones = toHazardZones(
      state.userIncidentReports
        .map { it.toDisasterEvent(now) }
        .filter { it.isValid(now) }
    )
    val mockZones = if (state.isMockDataVisible) {
      // India-only guard so a record outside IndiaGeo never reaches the map.
      PilotRegionData.hazardZones.filter { IndiaGeo.contains(it.center) }
    } else {
      emptyList()
    }
    val hazards = (liveZones + reportZones + mockZones).distinctBy { it.id }

    // The shelter network today: SIMULATED demo zones (only while the demo
    // switch is on) PLUS any REAL operator-entered field registry records,
    // which stay in scope in every mode — they are field data, not demo data.
    val demoZones = if (state.isMockDataVisible) {
      state.safeZones.filter { IndiaGeo.contains(it.point) }
    } else {
      emptyList()
    }
    val zonesInScope = (state.fieldShelters.filter { IndiaGeo.contains(it.point) } + demoZones)
      .distinctBy { it.id }

    val risk = RiskAssessmentEngine.assess(
      location = location,
      hazards = hazards,
      provenanceNote = when {
        state.isMockDataVisible ->
          "Location: ${if (state.isUserLocationFallback) "India centre (no GPS)" else "device GPS"} • " +
            "Hazards: live provider feeds + citizen reports + labelled SIMULATED demo zones • " +
            "Shelters: SIMULATED demo records (no shelter registry connected)"
        state.isUserLocationFallback ->
          "Location: India centre (no GPS yet) • Hazards: live provider feeds + citizen reports only"
        else ->
          "Location: device GPS • Hazards: live provider feeds + citizen reports only"
      }
    )

    val ctx = SafeZoneEvaluator.RequestContext(
      origin = location,
      hazards = hazards,
      hasVulnerableMembers = state.userProfile.vulnerableCategoryIds.isNotEmpty(),
      needsMedicalSupport = state.userProfile.needsMedicalSupport
    )
    // Evaluate EVERY candidate: feasible shelters are ranked; rejected ones
    // carry their rejection reason so the UI can never present a full or
    // hazard-trapped shelter as an eligible destination.
    val evaluated = SafeZoneEvaluator.evaluateAll(zonesInScope, ctx)
    val ranked = evaluated.filter { it.isFeasible }.sortedByDescending { it.score }
    val action = ActionAdvisor.recommend(risk, ranked)

    // ---- CARRYING CAPACITY (SIH 26191) ------------------------------------
    // Demand is RESOLVED from population records, in the documented priority
    // order: verified relocation demand > authority/field assessment > affected
    // population (needs explicit approval) > baseline population (needs explicit
    // approval) > simulated demo demand > the citizen's own household
    // declaration. No source connected and no approval => INSUFFICIENT_DATA.
    // The declared household size is NEVER used as the area population.
    val populationResolution = PopulationDemandResolver.resolve(
      records = state.populationRecords,
      householdDeclaration = PopulationRecord.householdDeclaration(
        householdSize = state.userProfile.dependentsCount + 1, // citizen + dependents
        source = "Citizen profile (editable, on device)"
      ),
      policy = PopulationDemandPolicy(
        // Both derivations stay off until an authority workflow approves them.
        allowAffectedAsDemand = false,
        allowBaselineAsDemand = false
      )
    )
    val demand = populationResolution.demand
    // Aggregate the three distinct population figures (baseline, affected and
    // the resolved demand) so the UI never has to re-derive them - and so they
    // cannot be conflated.
    val populationAssessment = com.example.data.population.PopulationAssessment.of(
      records = state.populationRecords,
      resolution = populationResolution
    )
    // Assess EVERY evaluated candidate (not only the ranked ones) so the detail
    // sheet can be honest about rejected sites too.
    val capacityAssessments = evaluated.associate { evaluation ->
      evaluation.zone.id to CarryingCapacityEngine.assess(evaluation.zone, demand, now)
    }
    val plan = RelocationPlanner.plan(
      risk,
      ranked,
      vulnerableCategoryIds = state.userProfile.vulnerableCategoryIds,
      capacityAssessments = capacityAssessments
    )

    _uiState.update {
      val selectedStillVisible = it.selectedSafeZone?.let { sel ->
        // A terrain haven is a derived destination, not part of the shelter
        // network: it stays valid until the user clears it or searches again.
        sel.id.startsWith("haven-") || zonesInScope.any { z -> z.id == sel.id }
      } ?: true
      // Hiding demo data invalidates any simulated destination + its corridor —
      // the map must never keep routing to a zone that just disappeared.
      //
      // EMERGENCY GUIDANCE: derived from THIS recompute's risk + evaluations.
      // The destination check uses the post-visibility state, so a hidden
      // route correctly re-opens the guidance instead of deferring to a dead
      // destination.
      val hasActiveDestination = selectedStillVisible && it.selectedSafeZone != null
      val guidance = com.example.data.shelters.EmergencyGuidance.forSituation(
        riskLevel = risk.level,
        evaluations = evaluated,
        hasActiveDestination = hasActiveDestination
      )
      it.copy(
        personalRisk = risk,
        hazardZones = hazards,
        evaluatedShelters = evaluated,
        rankedShelters = ranked,
        recommendedAction = action,
        relocationPlan = plan,
        emergencyGuidance = guidance,
        capacityDemand = demand,
        capacityAssessments = capacityAssessments,
        populationRecords = state.populationRecords,
        populationResolution = populationResolution,
        populationAssessment = populationAssessment,
        selectedSafeZone = if (selectedStillVisible) it.selectedSafeZone else null,
        selectedEvaluation = if (selectedStillVisible) it.selectedEvaluation else null,
        activeRoute = if (selectedStillVisible) it.activeRoute else null,
        alternativeRoutes = if (selectedStillVisible) it.alternativeRoutes else emptyList(),
        routeStatus = if (selectedStillVisible) it.routeStatus else RouteStatus.IDLE,
        routeStatusMessage = if (selectedStillVisible) it.routeStatusMessage else null,
        isNavigatingLive = if (selectedStillVisible) it.isNavigatingLive else false
      )
    }

    if (selectInitialShelter) {
      val initial = ranked.firstOrNull()?.zone
      if (initial != null) {
        selectSafeZone(initial, autoRoute = true)
      }
    }
  }

  /**
   * Applies a REAL hardware GPS fix (reported by the osmdroid location overlay).
   * Real fixes replace the India-centre placeholder and re-run every decision;
   * re-routing is movement-guarded so 1 Hz fixes never starve the OSRM request.
   */
  fun applyRealGpsFix(latitude: Double, longitude: Double) {
    val state = _uiState.value
    if (state.isUserLocationFallback || state.userLocation.lat != latitude || state.userLocation.lon != longitude) {
      _uiState.update {
        it.copy(
          userLocation = GeoPoint(latitude, longitude),
          isUserLocationFallback = false
        )
      }
      recomputeIntelligence()
      maybeRecalculateRouteForNewLocation()
      refreshWeather()
      refreshResolvedPlace(GeoPoint(latitude, longitude))
    }
  }

  /**
   * Resolves the CURRENT coordinates to a district/state at runtime and stores
   * it for honest scoping labels and search rings. Nothing is assumed when the
   * platform cannot resolve the place: the app stays national and says so.
   */
  private fun refreshResolvedPlace(point: GeoPoint, force: Boolean = false) {
    val anchor = placeAnchor
    val movedFarEnough = anchor == null ||
      GeoMath.distanceMeters(anchor, point) >= PLACE_RESOLVE_MIN_MOVEMENT_METERS
    if (!force && !movedFarEnough) return
    placeJob?.cancel()
    placeJob = viewModelScope.launch {
      val resolved = withTimeoutOrNull(PLACE_RESOLVE_TIMEOUT_MS) {
        placeResolver.resolve(point)
      }
      placeAnchor = point
      val previous = _uiState.value.resolvedPlace
      _uiState.update { it.copy(resolvedPlace = resolved) }
      // Historical evidence is area-scoped, so it follows the resolved place.
      refreshHistoricalContext()
      // Re-scope the news feed only when the rings that actually name a place
      // changed; the repository is cache-first, so this costs no extra call
      // when nothing moved.
      if (scopeKey(previous) != scopeKey(resolved)) {
        newsJob?.cancel()
        newsJob = viewModelScope.launch { applyNewsFeed(newsRepository.ensureLoaded(currentNewsQueries())) }
      }
    }
  }

  private fun scopeKey(place: ResolvedPlace?): String =
    listOf(place?.district, place?.state).joinToString("|")

  /** Search rings for the place resolved RIGHT NOW (national ring always). */
  private fun currentNewsQueries(): List<NewsQueryFactory.ScopedNewsQuery> =
    NewsQueryFactory.buildQueries(_uiState.value.resolvedPlace)

  // ====================================================== DISASTER PIPELINE

  /**
   * REAL disaster-data refresh: queries every live provider, updates the
   * cache, and re-runs the intelligence pipeline against the fresh events.
   */
  fun syncDisasterData() {
    if (_uiState.value.isDisasterSyncing) return
    disasterJob?.cancel()
    disasterJob = viewModelScope.launch {
      _uiState.update { it.copy(isDisasterSyncing = true) }
      val feed = disasterRepository.refresh()
      applyDisasterFeed(feed)
      _uiState.update { it.copy(isDisasterSyncing = false) }
    }
  }

  /** Publishes a disaster feed into state with honest per-source labels. */
  private fun applyDisasterFeed(feed: com.example.data.disaster.DisasterFeed) {
    val now = System.currentTimeMillis()
    _uiState.update {
      it.copy(
        disasterEvents = feed.events.filter { e -> e.isValid(now) },
        providerStates = feed.providerStates,
        isDisasterDataLive = feed.isAnyLive,
        disasterLastSyncMillis = feed.providerStates
          .mapNotNull { s -> s.fetchedAtMillis.takeIf { t -> t > 0 } }
          .maxOrNull()
      )
    }
    recomputeIntelligence()
  }

  /** Toggles one map layer (map layer panel). */
  fun toggleLayer(layer: DisasterLayer) {
    _uiState.update {
      val next = it.enabledLayers.toMutableSet()
      if (layer in next) next.remove(layer) else next.add(layer)
      it.copy(enabledLayers = next)
    }
  }

  /**
   * Simulated-demo-data switch. ON -> the labelled SIMULATED India network is
   * shown; OFF -> the simulated network disappears while LIVE provider events
   * and citizen reports keep reaching the map and every engine.
   * Kept in sync with the radar "SIMULATED DEMO" toggle (same dataset).
   */
  fun setMockMode(enabled: Boolean) {
    _uiState.update {
      it.copy(
        isMockMode = enabled,
        isMockDataVisible = enabled,
        snackbarMessage = if (enabled) {
          "Simulated demo ON — labelled India zones shown; live data unchanged"
        } else {
          "Simulated demo OFF — live data still shown"
        }
      )
    }
    recomputeIntelligence()
  }

  /**
   * Radar "SIMULATED DEMO" toggle: ON adds the labelled mock danger + safe
   * zones; OFF removes ONLY the simulated network — live provider events,
   * citizen reports, risk assessment and routing keep working.
   * Single source of truth for simulated-data visibility.
   */
  fun toggleMockData() {
    val next = !_uiState.value.isMockDataVisible
    _uiState.update {
      it.copy(
        isMockDataVisible = next,
        isMockMode = next,
        snackbarMessage = if (next) {
          "Simulated demo ON — labelled India zones shown; live data unchanged"
        } else {
          "Simulated demo OFF — live data still shown"
        }
      )
    }
    recomputeIntelligence()
  }

  // ==================================================== USER INCIDENT REPORTS

  /** Opens the "Add a Report" incident dialog. */
  fun openIncidentReportDialog() {
    _uiState.update { it.copy(showIncidentReportDialog = true) }
  }

  fun closeIncidentReportDialog() {
    _uiState.update { it.copy(showIncidentReportDialog = false) }
  }

  /**
   * Submits a citizen incident report: stored locally, TTL-expired after
   * [com.example.data.disaster.INCIDENT_TTL_MILLIS], always displayed as an
   * UNVERIFIED user report — never as confirmed fact.
   */
  fun submitIncidentReport(
    category: IncidentCategory,
    severityLabel: String,
    description: String
  ) {
    val state = _uiState.value
    if (state.isUserLocationFallback) {
      _uiState.update {
        it.copy(
          showIncidentReportDialog = false,
          snackbarMessage = "Location unavailable — enable device GPS before reporting an incident."
        )
      }
      return
    }
    val severity = HazardSeverity.entries.firstOrNull { it.label == severityLabel }
      ?: category.defaultSeverity()
    val report = IncidentReport(
      id = "user-${System.currentTimeMillis()}",
      category = category,
      description = description.trim(),
      location = state.userLocation,
      reportedAtMillis = System.currentTimeMillis(),
      severity = severity,
      reporterName = state.userProfile.fullName
    )
    _uiState.update {
      it.copy(
        showIncidentReportDialog = false,
        userIncidentReports = (it.userIncidentReports + report).takeLast(MAX_INCIDENT_REPORTS)
      )
    }
    recomputeIntelligence()
    _uiState.update {
      it.copy(snackbarMessage = "Report added: ${category.label} — UNVERIFIED user report shown on the map for others to verify.")
    }
  }

  /** Opens the tap-detail panel for a disaster event. */
  fun openDisasterEventDetail(event: DisasterEvent) {
    _uiState.update { it.copy(disasterEventDetail = event) }
  }

  fun closeDisasterEventDetail() {
    _uiState.update { it.copy(disasterEventDetail = null) }
  }

  /**
   * Re-routes only when the user has moved at least [REROUTE_MIN_MOVEMENT_METERS]
   * from the origin of the displayed route. GPS fixes arrive ~1/sec with
   * sub-metre jitter — re-routing on every fix would keep cancelling the
   * in-flight OSRM request (route starvation: no corridor could ever finish
   * computing, so no polyline was ever drawn). A route the user explicitly
   * cleared is never resurrected by movement alone.
   */
  private fun maybeRecalculateRouteForNewLocation() {
    val state = _uiState.value
    if (state.selectedSafeZone == null) return
    if (state.activeRoute == null && lastRouteOrigin == null) return
    val routeOrigin = lastRouteOrigin
    val movedFarEnough = routeOrigin == null ||
      GeoMath.distanceMeters(routeOrigin, state.userLocation) >= REROUTE_MIN_MOVEMENT_METERS
    if (movedFarEnough) calculateRouteToSelectedZone()
  }

  // ================================================================ ROUTING

  /** Selects a safe zone (usually from the ranked list or a map tap). */
  fun selectSafeZone(zone: SafeZone, autoRoute: Boolean = true) {
    val evaluation = _uiState.value.rankedShelters.firstOrNull { it.zone.id == zone.id }
    _uiState.update {
      it.copy(
        selectedSafeZone = zone,
        selectedEvaluation = evaluation,
        safeZoneDetail = null,
        currentNavigationStepIndex = 0
      )
    }
    if (autoRoute) calculateRouteToSelectedZone()
  }

  // ==================================================== EMERGENCY GUIDANCE ==

  /** User tapped the guidance card's GO: select + route to the suggested zone. */
  fun acceptEmergencyGuidance() {
    val suggestion = (_uiState.value.emergencyGuidance
      as? com.example.data.shelters.EmergencyGuidance.SuggestShelter) ?: return
    selectSafeZone(suggestion.evaluation.zone, autoRoute = true)
  }

  /** Explicit dismissal of the guidance card until the next recompute. */
  fun dismissEmergencyGuidance() {
    _uiState.update { it.copy(emergencyGuidance = com.example.data.shelters.EmergencyGuidance.None) }
  }

  /** The one shared terrain-probe transport for self-assessment + haven search. */
  private val terrainProbe: com.example.data.suitability.TerrainProbeService
    get() = terrainProbeOverride ?: com.example.data.suitability.TerrainProbeService()

  private var terrainAssessJob: Job? = null

  /**
   * "Is MY spot a red zone?" — explicit user action only. Probes the live
   * SRTM stencil + rainfall at the CURRENT location; a failure yields an
   * honest Unavailable, never an invented verdict.
   */
  fun assessTerrainHere() {
    if (_uiState.value.isAssessingTerrain) return
    val origin = _uiState.value.userLocation
    terrainAssessJob?.cancel()
    terrainAssessJob = viewModelScope.launch {
      _uiState.update { it.copy(isAssessingTerrain = true) }
      val result = try {
        terrainProbe.probe(origin, coastGrid = coastGridProvider())
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        com.example.data.suitability.TerrainProbeResult.ElevationUnavailable(
          error::class.java.simpleName
        )
      }
      val assessment = when (result) {
        is com.example.data.suitability.TerrainProbeResult.Success ->
          TerrainSelfAssessment.Result(result.verdict, result.coastKnown)
        is com.example.data.suitability.TerrainProbeResult.ElevationUnavailable ->
          TerrainSelfAssessment.Unavailable(result.detail)
      }
      _uiState.update {
        it.copy(isAssessingTerrain = false, terrainSelfAssessment = assessment)
      }
    }
  }

  fun dismissTerrainAssessment() {
    terrainAssessJob?.cancel()
    _uiState.update {
      it.copy(terrainSelfAssessment = null, isAssessingTerrain = false)
    }
  }

  // ============================================== AUTHORITY FIELD REGISTRY ==

  /**
   * Reloads operator-entered registry records from the store. Field shelters
   * join the live evaluated set; rejected entries surface verbatim. Called on
   * cold start and after every save.
   */
  fun reloadFieldRegistry(silent: Boolean = false) {
    val shelters = fieldRegistryStore.loadShelters()
    val habitations = fieldRegistryStore.loadHabitations()
    val rejections = (fieldRegistryStore as? com.example.data.habitations.FileFieldRegistryStore)
      ?.lastRejections() ?: emptyList()
    _uiState.update {
      it.copy(
        fieldShelters = shelters,
        fieldHabitations = habitations,
        registryRejections = rejections,
        snackbarMessage = if (silent) it.snackbarMessage else
          "Field registry reloaded: ${shelters.size} shelter record(s), ${habitations.size} habitation record(s)."
      )
    }
    recomputeIntelligence()
  }

  /** Saves one field-entered shelter (replace-by-id semantics). */
  fun saveFieldShelter(zone: SafeZone) {
    if (!com.example.data.disaster.IndiaGeo.contains(zone.point)) {
      _uiState.update {
        it.copy(snackbarMessage = "Shelter not saved: coordinates ${zone.lat},${zone.lon} are outside India.")
      }
      return
    }
    val current = fieldRegistryStore.loadShelters()
    val next = current.filterNot { it.id == zone.id } + zone
    fieldRegistryStore.saveShelters(next)
    reloadFieldRegistry()
  }

  /** Saves one field-entered habitation (replace-by-id semantics). */
  fun saveFieldHabitation(habitation: com.example.data.habitations.Habitation) {
    if (!com.example.data.disaster.IndiaGeo.contains(habitation.point)) {
      _uiState.update {
        it.copy(snackbarMessage = "Habitation not saved: coordinates are outside India.")
      }
      return
    }
    val current = fieldRegistryStore.loadHabitations()
    val next = current.filterNot { it.id == habitation.id } + habitation
    fieldRegistryStore.saveHabitations(next)
    reloadFieldRegistry()
  }

  fun deleteFieldShelter(id: String) {
    fieldRegistryStore.saveShelters(fieldRegistryStore.loadShelters().filterNot { it.id == id })
    reloadFieldRegistry()
  }

  fun deleteFieldHabitation(id: String) {
    fieldRegistryStore.saveHabitations(fieldRegistryStore.loadHabitations().filterNot { it.id == id })
    reloadFieldRegistry()
  }

  private var priorityJob: Job? = null

  /**
   * Opens the RELOCATION PRIORITIZATION DASHBOARD: ranks the dashboard input
   * set (real registry records + the labelled demo network) against the live
   * hazard picture and the shelter network. [liveTerrainScan] additionally
   * probes each habitation's terrain via the keyless elevation service —
   * OFF by default so opening the dashboard never fires a request storm.
   */
  fun openAuthorityDashboard(liveTerrainScan: Boolean = false) {
    _uiState.update { it.copy(showAuthorityDashboard = true) }
    runRanking(liveTerrainScan)
  }

  fun closeAuthorityDashboard() {
    priorityJob?.cancel()
    _uiState.update {
      it.copy(showAuthorityDashboard = false, isRankingPriorities = false)
    }
  }

  fun rerunAuthorityRanking(liveTerrainScan: Boolean) {
    runRanking(liveTerrainScan)
  }

  private fun runRanking(liveTerrainScan: Boolean) {
    priorityJob?.cancel()
    priorityJob = viewModelScope.launch {
      _uiState.update { it.copy(isRankingPriorities = true) }
      val state = _uiState.value
      val hazards = state.hazardZones
      val shelters = (state.fieldShelters + state.safeZones).distinctBy { it.id }
      var inputs = com.example.data.habitations.DemoHabitations.forDashboard(
        fieldRecords = state.fieldHabitations,
        includeDemo = state.isMockDataVisible
      )
      if (liveTerrainScan) {
        val probe = terrainProbe
        val grid = coastGridProvider()
        inputs = inputs.map { hab ->
          val result = try {
            probe.probe(hab.point, grid)
          } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            null
          }
          if (result is com.example.data.suitability.TerrainProbeResult.Success) {
            hab.copy(terrainVerdict = result.verdict)
          } else hab
        }
      }
      val ranked = com.example.data.habitations.HabitationPriorityEngine.rank(inputs, hazards, shelters)
      _uiState.update {
        it.copy(relocationPriorities = ranked, isRankingPriorities = false)
      }
    }
  }

  /**
   * Last-resort terrain search: probe outward from the user for the nearest
   * location the habitability engine rates SAFE, then offer it as a labelled
   * DERIVED destination (never presented as a shelter). Runs only on an
   * explicit tap; the honest unavailable state stays visible when the probe
   * cannot reach the elevation service.
   */
  private var havenJob: Job? = null

  fun searchTerrainHaven() {
    if (_uiState.value.isSearchingHaven) return
    val origin = _uiState.value.userLocation
    val finder = havenFinderOverride
      ?: com.example.data.shelters.SafeHavenFinder.live(terrainProbe)
    havenJob?.cancel()
    havenJob = viewModelScope.launch {
      _uiState.update { it.copy(isSearchingHaven = true) }
      val haven = try {
        finder.find(origin, coastGrid = coastGridProvider(), maxCandidates = 24)
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        null
      }
      _uiState.update {
        it.copy(
          isSearchingHaven = false,
          terrainHaven = haven,
          snackbarMessage = haven?.let { h ->
            "Terrain-safe point found about ${com.example.data.model.GeoMath.formatKm(h.distanceMeters)} away — " +
              "open terrain, not a registered shelter."
          } ?: "No terrain-safe point could be verified nearby (elevation service unavailable or no safe candidate found)."
        )
      }
    }
  }

  /** Routes to the found terrain haven as an explicit DERIVED destination. */
  fun routeToTerrainHaven() {
    val haven = _uiState.value.terrainHaven ?: return
    val pseudo = SafeZone(
      id = "haven-${haven.point.lat}-${haven.point.lon}",
      name = "Terrain-safe open point",
      lat = haven.point.lat,
      lon = haven.point.lon,
      locationNote = "DERIVED from SRTM slope + rainfall + coast analysis — open terrain, not a registered shelter.",
      capacityTotal = 0,
      capacityCurrent = 0,
      waterAvailable = false,
      foodAvailable = false,
      electricityAvailable = false,
      sanitationAvailable = false,
      medicalSupport = false,
      accessibility = "Open terrain — no road guarantee",
      womenChildrenSuitability = false,
      operatingStatus = "OPEN",
      verificationStatus = "DERIVED (unverified facility status)",
      elevationNote = "",
      provenance = haven.verdict.provenance(System.currentTimeMillis())
    )
    // selectSafeZone finds no ranked evaluation for the haven (evaluation =
    // null), which every consumer handles; the route is computed to the zone
    // itself. The haven is labelled DERIVED everywhere it renders.
    selectSafeZone(pseudo, autoRoute = true)
  }

  /**
   * Instant corridor first, live road route as an upgrade:
   *  1. A cached live road route (same shelter/mode/area/hazards) paints the
   *     EXACT road pathway immediately with no straight-line flash at all.
   *  2. Otherwise the offline hazard-skirting corridor draws instantly as a
   *     DASHED preview (never mistaken for surveyed roads).
   *  3. The live OSRM road pathway swaps in when it arrives — the badge
   *     flips OFFLINE EST. -> OSRM VALIDATED.
   * Stale results (newer selection, cleared route, hidden mock) never
   * overwrite current state: the upgrade applies only to the zone + origin
   * this call was made for.
   */
  fun calculateRouteToSelectedZone() {
    val zone = _uiState.value.selectedSafeZone ?: return
    routingJob?.cancel()
    val origin = _uiState.value.userLocation
    val mode = _uiState.value.travelMode
    val hazards = _uiState.value.hazardZones
    lastRouteOrigin = origin
    val cacheKey = LiveRouteCache.key(zone.id, mode, origin, hazards)

    // A cached LIVE road route for the exact destination/mode/area/hazard set is
    // real road geometry and can be shown immediately.
    val cached = liveRouteCache.get(cacheKey)
    if (cached != null) {
      val armGuidance = consumePendingGuidanceFor(zone.id)
      _uiState.update {
        it.copy(
          activeRoute = cached,
          alternativeRoutes = emptyList(),
          isCalculatingRoute = false,
          routeStatus = RouteStatus.READY,
          routeStatusMessage = "Cached OSRM road route to ${zone.name} — hazard-checked. $ROUTE_LIMITATIONS_NOTE",
          currentNavigationStepIndex = 0,
          isNavigatingLive = it.isNavigatingLive || armGuidance
        )
      }
      return
    }

    // NO straight line is drawn while we wait. The map stays clean until a real
    // road response arrives; the previous behaviour painted a 3-point
    // origin→midpoint→destination corridor, which reads as a real safe route.
    _uiState.update {
      it.copy(
        activeRoute = null,
        alternativeRoutes = emptyList(),
        isCalculatingRoute = true,
        routeStatus = RouteStatus.REQUESTING,
        routeStatusMessage = "Requesting a road route to ${zone.name}…",
        currentNavigationStepIndex = 0
      )
    }

    routingJob = viewModelScope.launch {
      val live = liveRouteFetcher(origin, zone.point, mode, hazards, zone.name, 1).firstOrNull()

      // Staleness guard: a newer selection/origin must never be overwritten.
      if (lastRouteOrigin != origin || _uiState.value.selectedSafeZone?.id != zone.id) return@launch

      if (live == null) {
        _uiState.update {
          it.copy(
            activeRoute = null,
            isCalculatingRoute = false,
            routeStatus = RouteStatus.NETWORK_ERROR,
            routeStatusMessage = "No road route received (offline or router unavailable). " +
              "Nothing is drawn — an offline estimate is only offered if you ask for it."
          )
        }
        return@launch
      }
      if (live.pathPoints.isEmpty()) {
        _uiState.update {
          it.copy(
            activeRoute = null,
            isCalculatingRoute = false,
            routeStatus = RouteStatus.NO_ROUTE,
            routeStatusMessage = "The router returned no usable corridor to ${zone.name}."
          )
        }
        return@launch
      }

      // Road geometry received -> validate it against the live hazard picture
      // (hazardWarnings / routeSafetyStatus are produced by that check), then
      // publish. Only a READY route is ever drawn by the map.
      val armGuidance = consumePendingGuidanceFor(zone.id)
      _uiState.update { it.copy(routeStatus = RouteStatus.VALIDATING_HAZARDS) }
      liveRouteCache.put(cacheKey, live)
      _uiState.update {
        it.copy(
          activeRoute = live,
          isCalculatingRoute = false,
          routeStatus = RouteStatus.READY,
            routeStatusMessage = "Live OSRM road route to ${zone.name} — " +
              "hazard-checked: ${live.routeSafetyStatus.label}. $ROUTE_LIMITATIONS_NOTE",
          currentNavigationStepIndex = 0,
          isNavigatingLive = it.isNavigatingLive || armGuidance
        )
      }
    }
  }

  /**
   * Consumes the "start guidance when the route arrives" intent for [zoneId].
   *
   * Called exactly once per arriving route (from [calculateRouteToSelectedZone]),
   * OUTSIDE the `_uiState.update {}` lambda so a retried atomic update can never
   * observe the flag twice. Returns true only when the user had asked to start
   * guidance for this same destination.
   */
  private fun consumePendingGuidanceFor(zoneId: String): Boolean {
    val armed = pendingGuidanceZoneId == zoneId
    pendingGuidanceZoneId = null
    return armed
  }

  /**
   * Explicit opt-in for the OFFLINE straight-line estimate. This geometry does
   * not follow roads, so it is published only on a direct user request and is
   * labelled [RouteStatus.FALLBACK_UNVERIFIED] — never as a safe route.
   */
  fun requestOfflineFallbackRoute() {
    val zone = _uiState.value.selectedSafeZone ?: return
    val origin = _uiState.value.userLocation
    val mode = _uiState.value.travelMode
    val hazards = _uiState.value.hazardZones
    lastRouteOrigin = origin
    val fallback = OsrmRoutingService.calculateOfflineTacticalRoute(
      origin = origin,
      destination = zone.point,
      mode = mode,
      hazards = hazards,
      destinationName = zone.name
    )
    _uiState.update {
      it.copy(
        activeRoute = fallback,
        alternativeRoutes = emptyList(),
        isCalculatingRoute = false,
        routeStatus = RouteStatus.FALLBACK_UNVERIFIED,
        routeStatusMessage = "UNVERIFIED ESTIMATE — this is a direct hazard-skirting " +
          "line, NOT a road route and NOT validated against road closures. Use with caution.",
        currentNavigationStepIndex = 0
      )
    }
  }

  /**
   * Computes alternative corridors to the selected shelter so the user can
   * compare safety vs distance. Never silently dead: with no destination yet
   * it auto-selects the best-ranked shelter first, and with no feasible
   * shelter at all it says so instead of doing nothing.
   */
  fun loadAlternativeRoutes() {
    var zone = _uiState.value.selectedSafeZone
    if (zone == null) {
      val best = _uiState.value.rankedShelters.firstOrNull()
      if (best == null) {
        _uiState.update {
          it.copy(snackbarMessage = "No safe zone to route to — switch the simulated demo data on to see the India shelter network, then pick a green shelter")
        }
        return
      }
      selectSafeZone(best.zone, autoRoute = false)
      zone = best.zone
    }
    val target = zone
    routingJob?.cancel()
    val origin = _uiState.value.userLocation
    val mode = _uiState.value.travelMode
    val hazards = _uiState.value.hazardZones
    lastRouteOrigin = origin
    // Nothing is drawn until verified road corridors arrive (no straight-line
    // placeholder). Synthetic offline detours are filtered out below.
    _uiState.update {
      it.copy(
        activeRoute = null,
        alternativeRoutes = emptyList(),
        isCalculatingRoute = true,
        routeStatus = RouteStatus.REQUESTING,
        routeStatusMessage = "Requesting alternative road corridors to ${target.name}…",
        currentNavigationStepIndex = 0
      )
    }
    routingJob = viewModelScope.launch {
      val alternatives = OsrmRoutingService.calculateAlternativeRoutes(
        origin = origin,
        destination = target.point,
        mode = mode,
        hazards = hazards,
        destinationName = target.name,
        maxAlternatives = 2
      )
      if (lastRouteOrigin != origin ||
        _uiState.value.selectedSafeZone?.id != target.id
      ) return@launch

      // Only real road geometry is offered as an alternative. The offline
      // detour variants are not roads, so they are never presented as options.
      val roadAlternatives = alternatives.filter { it.isLiveOsrm }
      if (roadAlternatives.isEmpty()) {
        _uiState.update {
          it.copy(
            activeRoute = null,
            alternativeRoutes = emptyList(),
            isCalculatingRoute = false,
            routeStatus = RouteStatus.NETWORK_ERROR,
            routeStatusMessage = "No verified road alternatives available. Nothing is drawn."
          )
        }
        return@launch
      }
      _uiState.update {
        it.copy(
          activeRoute = roadAlternatives.first(),
          alternativeRoutes = roadAlternatives,
          isCalculatingRoute = false,
          routeStatus = RouteStatus.READY,
          routeStatusMessage = if (roadAlternatives.size > 1) {
            "${roadAlternatives.size} verified road corridors to ${target.name} — safest first. $ROUTE_LIMITATIONS_NOTE"
          } else {
            "One verified road corridor found to ${target.name}. $ROUTE_LIMITATIONS_NOTE"
          },
          currentNavigationStepIndex = 0
        )
      }
    }
  }

  /**
   * Clears the computed evacuation corridor (map "Clear Route" control).
   * Single source of truth: state resets here and the OSMDroid layer removes
   * its polyline because activeRoute becomes null — never a map-only removal
   * that would leave state and map disagreeing.
   */
  fun clearActiveRoute() {
    routingJob?.cancel()
    routingJob = null
    lastRouteOrigin = null
    // "Start guidance when it arrives" must not survive an explicit Clear Route.
    pendingGuidanceZoneId = null
    _uiState.update {
      it.copy(
        activeRoute = null,
        alternativeRoutes = emptyList(),
        isCalculatingRoute = false,
        routeStatus = RouteStatus.IDLE,
        routeStatusMessage = null,
        isNavigatingLive = false,
        currentNavigationStepIndex = 0,
        snackbarMessage = "Evacuation route cleared"
      )
    }
  }

  fun setTravelMode(mode: String) {
    if (_uiState.value.travelMode != mode) {
      _uiState.update { it.copy(travelMode = mode) }
      calculateRouteToSelectedZone()
    }
  }

  /**
   * One-tap "best safe zone" decision: selects the top-ranked feasible shelter
   * (NOT simply the nearest) and routes to it.
   */
  fun selectBestSafeZone() {
    val best = _uiState.value.rankedShelters.firstOrNull()
    if (best == null) {
      _uiState.update {
        it.copy(snackbarMessage = "No feasible shelter right now — switch the simulated demo data on to see the India demo network")
      }
      return
    }
    selectSafeZone(best.zone, autoRoute = true)
    _uiState.update {
      it.copy(snackbarMessage = "Best safe zone selected: ${best.zone.name} — ${best.rankExplanation}")
    }
  }

  // =========================================================== NAVIGATION

  fun startEvacuationRoute() {
    var zone = _uiState.value.selectedSafeZone
    if (zone == null) {
      val best = _uiState.value.rankedShelters.firstOrNull()
      if (best == null) {
        _uiState.update {
          it.copy(snackbarMessage = "No safe zone to route to — switch the simulated demo data on to see the India shelter network, then pick a green shelter")
        }
        return
      }
      selectSafeZone(best.zone, autoRoute = false)
      zone = best.zone
    }
    val target = zone

    // Guidance may only start on a route that actually exists. Starting it on an
    // empty corridor produced a HUD with no geometry behind it — so the request
    // is remembered instead of silently promised: the arriving route for THIS
    // zone arms guidance (see consumePendingGuidanceFor).
    if (_uiState.value.activeRoute == null) {
      pendingGuidanceZoneId = target.id
      _uiState.update {
        it.copy(
          currentTab = ScreenTab.RADAR_MAP,
          snackbarMessage = "Requesting the road route to ${target.name}… guidance starts when it arrives"
        )
      }
      calculateRouteToSelectedZone()
      return
    }

    _uiState.update {
      it.copy(
        isNavigatingLive = true,
        currentTab = ScreenTab.RADAR_MAP,
        snackbarMessage = "Guidance to ${target.name} started"
      )
    }
  }

  fun stopLiveNavigation() {
    _uiState.update {
      it.copy(
        isNavigatingLive = false,
        snackbarMessage = "Live evacuation guidance ended"
      )
    }
  }

  fun nextNavigationStep() {
    val currentRoute = _uiState.value.activeRoute ?: return
    val nextIndex = _uiState.value.currentNavigationStepIndex + 1
    if (nextIndex < currentRoute.steps.size) {
      _uiState.update { it.copy(currentNavigationStepIndex = nextIndex) }
    } else {
      _uiState.update {
        it.copy(
          isNavigatingLive = false,
          snackbarMessage = "You have arrived safely at ${_uiState.value.selectedSafeZone?.name ?: "the safe zone"}!"
        )
      }
    }
  }

  // ================================================================= TABS

  fun setTab(tab: ScreenTab) {
    _uiState.update { it.copy(currentTab = tab) }
  }

  fun toggleTheme() {
    _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
  }

  fun toggleOfflineCache(active: Boolean) {
    _uiState.update {
      it.copy(
        isOfflineFirstMode = active,
        snackbarMessage = if (active) {
          "Offline-first mode ON — keep browsing to keep tiles; there is no bulk offline download in this build"
        } else {
          "Offline-first mode OFF"
        }
      )
    }
  }

  /**
   * REAL refresh: pulls live disaster news from GNews (all three scopes,
   * quota-aware) AND re-syncs the India-wide disaster providers, then
   * re-labels the sync banner from the actual results.
   */
  fun syncData() {
    syncDisasterData()
    refreshWeather(force = true)
    // Re-pull population records alongside the other live feeds (no-op with the
    // default no-source provider).
    refreshPopulation()
    newsJob?.cancel()
    newsJob = viewModelScope.launch {
      _uiState.update { it.copy(isSyncing = true) }
      val feed = newsRepository.refresh(currentNewsQueries())
      applyNewsFeed(feed)
      _uiState.update { state ->
        state.copy(
          isSyncing = false,
          snackbarMessage = when {
            feed.articles.isNotEmpty() && feed.error == null ->
              "Disaster intelligence refreshed — ${feed.articles.size} live GNews articles"
            feed.error != null -> feed.error.userMessage
            else -> "No GNews articles matched the search queries — try again later"
          }
        )
      }
    }
  }

  /** Publishes a real GNews feed into state with an honest sync label. */
  private fun applyNewsFeed(feed: NewsFeed) {
    val fetchedLabel = feed.lastFetchedAtMillis?.let { millis ->
      SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))
    }
    _uiState.update {
      it.copy(
        newsArticles = feed.articles,
        newsHero = feed.hero,
        isNewsFromCache = feed.isFromCache,
        newsLastFetchedAtMillis = feed.lastFetchedAtMillis,
        newsEverLoaded = feed.articles.isNotEmpty(),
        newsError = feed.error,
        lastSyncTime = when {
          feed.articles.isNotEmpty() && feed.isFromCache ->
            "CACHED • ${feed.articles.size} articles • fetched $fetchedLabel"
          feed.articles.isNotEmpty() ->
            "ONLINE • ${feed.articles.size} articles • fetched $fetchedLabel"
          feed.error != null -> feed.error.userMessage
          else -> "No disaster news cached — tap Sync to fetch live articles"
        }
      )
    }
  }

  /**
   * Arms the REAL spoken bulletin: state carries the composed text and the
   * MainActivity TextToSpeech engine speaks it; the on-screen ticker runs
   * until the engine reports completion (or unavailability).
   */
  fun toggleAudioBulletin() {
    val willPlay = !_uiState.value.isAudioPlaying
    _uiState.update {
      it.copy(
        isAudioPlaying = willPlay,
        audioBulletinText = if (willPlay) buildAudioBulletin() else "",
        audioPlaybackSeconds = 0
      )
    }

    audioJob?.cancel()
    if (willPlay) {
      audioJob = viewModelScope.launch {
        var seconds = 0
        while (_uiState.value.isAudioPlaying) {
          delay(1000)
          seconds++
          _uiState.update { it.copy(audioPlaybackSeconds = seconds) }
        }
      }
    }
  }

  /** Bulletin text built ONLY from real current state — nothing invented. */
  private fun buildAudioBulletin(): String {
    val state = _uiState.value
    return NewsTtsBulletin.compose(
      riskLevelLabel = state.personalRisk?.level?.label ?: "not yet assessed",
      recommendedActionTitle = state.recommendedAction?.title,
      actionExplanation = state.recommendedAction?.explanation,
      articles = state.newsArticles,
      nowMillis = System.currentTimeMillis(),
      isFromCache = state.isNewsFromCache
    )
  }

  /** TextToSpeech finished (or failed) — stop the ticker honestly. */
  fun onTtsBulletinFinished() {
    audioJob?.cancel()
    _uiState.update { it.copy(isAudioPlaying = false, audioPlaybackSeconds = 0) }
  }

  /** No TextToSpeech engine on this device — say so instead of faking playback. */
  fun onTtsUnavailable() {
    audioJob?.cancel()
    _uiState.update {
      it.copy(
        isAudioPlaying = false,
        audioPlaybackSeconds = 0,
        snackbarMessage = "Text-to-speech unavailable on this device — bulletin cannot be spoken"
      )
    }
  }

  fun setNewsCategory(category: String) {
    _uiState.update { it.copy(selectedNewsCategory = category) }
  }

  fun toggleGoBagItem(itemId: String) {
    _uiState.update { state ->
      val updated = state.goBagItems.map {
        if (it.id == itemId) it.copy(isChecked = !it.isChecked) else it
      }
      state.copy(goBagItems = updated)
    }
  }

  // ================================================== SAFETY / SOS / TOOLS

  fun setUserSafety(isSafe: Boolean) {
    if (!isSafe) {
      // NEED ASSISTANCE arms a distress broadcast - an explicit "Are you
      // sure?" confirmation is required before anything is transmitted.
      _uiState.update { it.copy(showSosConfirmDialog = true) }
      return
    }
    _uiState.update {
      it.copy(
        userIsSafe = true,
        snackbarMessage = "Status updated on this device: marked SAFE"
      )
    }
  }

  /**
   * Any SOS entry point (radar SOS icon, hero broadcast button, NEED
   * ASSISTANCE switch) first opens the "Are you sure?" confirmation gate -
   * nothing is broadcast until the user explicitly confirms.
   */
  fun triggerSosBroadcast() {
    _uiState.update {
      it.copy(showSosConfirmDialog = true)
    }
  }

  fun dismissSosDialog() {
    // BUG-1 FIX — the Local SOS flow now has a real COMPLETION state.
    //
    // Previously "Keep The Local SOS Active" (and back / outside-tap dismissal)
    // only closed the dialog and left `isSosActive = true` forever, so the global
    // ActiveToolsBar stayed injected above the screen content on every tab — the
    // reported "Home screen becomes broken after completing the SOS flow".
    // Dismissing the record dialog is now an explicit completion: the dialog
    // closes, the SOS flag and the safety switch reset, and the saved LOCAL
    // record is summarised exactly once.
    _uiState.update {
      it.copy(
        showSosBroadcastDialog = false,
        isSosActive = false,
        userIsSafe = true,
        snackbarMessage = it.lastReportReceipt?.note
          ?: "Local SOS record closed. Nothing was transmitted."
      )
    }
  }

  fun cancelSosBroadcast() {
    _uiState.update {
      it.copy(
        showSosBroadcastDialog = false,
        isSosActive = false,
        userIsSafe = true,
        snackbarMessage = "Local SOS canceled — nothing was transmitted or recorded as active"
      )
    }
  }

  fun dismissSosConfirmDialog() {
    _uiState.update { it.copy(showSosConfirmDialog = false) }
  }

  /**
   * Confirms the "Are you sure?" gate: saves the LOCAL SOS record (this build has
   * no relief-network backend, so nothing is transmitted to NDRF or any other
   * authority) and shows the record dialog.
   */
  fun confirmSosBroadcast() {
    _uiState.update {
      it.copy(
        showSosConfirmDialog = false,
        showSosBroadcastDialog = true,
        isSosActive = true,
        userIsSafe = false
      )
    }
    val profile = _uiState.value.userProfile
    submitEmergencyReport(
      ReportKind.SOS_BROADCAST,
      message = "NEED ASSISTANCE - SOS distress broadcast from ${profile.fullName}" +
        (if (profile.medicalTag.isNotBlank()) " | Medical tag: ${profile.medicalTag}" else "")
    )
  }

  // =========================== PROFILE / BATTERY ===========================

  fun openEditProfileDialog() {
    _uiState.update { it.copy(showEditProfileDialog = true) }
  }

  fun closeEditProfileDialog() {
    _uiState.update { it.copy(showEditProfileDialog = false) }
  }

  /**
   * Saves the edited citizen profile. Vulnerable categories and the medical
   * flag change shelter ranking and the relocation priority band, so the
   * intelligence pipeline is re-run immediately.
   */
  fun updateUserProfile(profile: UserProfile) {
    _uiState.update {
      it.copy(
        userProfile = profile,
        showEditProfileDialog = false,
        snackbarMessage = "Profile saved: ${profile.fullName} | ${profile.bloodGroupLabel} | ${profile.dependentsLabel}"
      )
    }
    recomputeIntelligence()
  }

  /** Real device battery reading pushed by the BatteryManager receiver in MainActivity. */
  fun onBatteryChanged(percent: Int, isCharging: Boolean) {
    _uiState.update { it.copy(batteryPercent = percent, isBatteryCharging = isCharging) }
  }

  /**
   * Torch toggle. The state is EXPLICIT ([TorchState]) and the platform outcome
   * is reported back by MainActivity through [onTorchResult], so the UI can never
   * claim "ON" when the camera service refused. Deliberately NO snackbar: the
   * button's own ON/OFF state plus the global active-tools bar carry the state,
   * which is what made the old feedback look like an undismissable popup.
   */
  fun toggleFlashlight() {
    _uiState.update {
      if (it.torchState == TorchState.ON) {
        it.copy(torchState = TorchState.OFF, torchMessage = null)
      } else {
        it.copy(torchState = TorchState.ON, torchMessage = null)
      }
    }
  }

  /**
   * Real platform result for the torch request made by MainActivity.
   * [permissionDenied] distinguishes "ask again after granting" from
   * "this device cannot do it", both shown in place under the Light control.
   */
  fun onTorchResult(success: Boolean, reason: String? = null, permissionDenied: Boolean = false) {
    _uiState.update { state ->
      if (success && state.torchState == TorchState.ON) {
        state.copy(torchState = TorchState.ON, torchMessage = null)
      } else if (success) {
        // The user switched it off while the request was in flight.
        state.copy(torchState = TorchState.OFF, torchMessage = null)
      } else {
        state.copy(
          torchState = if (permissionDenied) TorchState.PERMISSION_DENIED else TorchState.UNAVAILABLE,
          torchMessage = reason ?: "This device did not allow the torch to turn on."
        )
      }
    }
  }

  /** Explicit start/stop for the siren; a second tap always stops it. */
  fun toggleSiren() {
    if (_uiState.value.sirenState == SirenState.PLAYING) stopSiren() else startSiren()
  }

  /**
   * Starts the siren with a live countdown and a hard auto-stop, so the tool can
   * never keep running unseen after the user leaves the Instructions screen.
   */
  fun startSiren() {
    sirenJob?.cancel()
    _uiState.update {
      it.copy(sirenState = SirenState.PLAYING, sirenSecondsLeft = SIREN_MAX_SECONDS)
    }
    sirenJob = viewModelScope.launch {
      var left = SIREN_MAX_SECONDS
      while (left > 0) {
        delay(1000)
        left--
        _uiState.update { it.copy(sirenSecondsLeft = left) }
      }
      // Auto-stop: state and hardware both return to IDLE together.
      _uiState.update { it.copy(sirenState = SirenState.IDLE, sirenSecondsLeft = 0) }
    }
  }

  /** Stops the siren immediately (used by the button and the global stop). */
  fun stopSiren() {
    sirenJob?.cancel()
    sirenJob = null
    _uiState.update { it.copy(sirenState = SirenState.IDLE, sirenSecondsLeft = 0) }
  }

  /** Global "stop everything" used by the always-visible active-tools bar. */
  fun stopAllDeviceTools() {
    stopSiren()
    _uiState.update { it.copy(torchState = TorchState.OFF, torchMessage = null) }
  }

  // ======================= SITUATION REPORTS (NDRF) ========================

  /** Opens the "Report My Situation" voice/form/photo reporter. */
  fun openSituationReportDialog() {
    _uiState.update { it.copy(showSituationReportDialog = true) }
  }

  fun closeSituationReportDialog() {
    _uiState.update { it.copy(showSituationReportDialog = false) }
  }

  /**
   * Submits the citizen's situation report (voice/typed description +
   * optional photo evidence) through the EmergencyReportService to the
   * NDRF ward dispatcher.
   */
  fun submitSituationReport(message: String, photoUri: String?) {
    if (message.isBlank()) {
      _uiState.update {
        it.copy(snackbarMessage = "Describe your situation (type or dictate) before reporting")
      }
      return
    }
    _uiState.update {
      it.copy(showSituationReportDialog = false, isSubmittingReport = true)
    }
    submitEmergencyReport(ReportKind.SITUATION_REPORT, message.trim(), photoUri)
  }

  /** Shared NDRF funnel for SOS broadcasts and situation reports. */
  private fun submitEmergencyReport(kind: ReportKind, message: String, photoUri: String? = null) {
    val snapshot = _uiState.value
    val profile = snapshot.userProfile
    viewModelScope.launch {
      val receipt = try {
        reportService.submit(
          EmergencyReport(
            kind = kind,
            reporterId = profile.citizenId,
            reporterName = profile.fullName,
            message = message,
            photoUri = photoUri,
            location = snapshot.userLocation,
            batteryPercent = snapshot.batteryPercent,
            isCharging = snapshot.isBatteryCharging,
            medicalTag = profile.medicalTag
          )
        )
      } catch (e: Exception) {
        ReportReceipt(
          reportId = "LOCAL-ERR",
          accepted = false,
          relayChannel = LocalEmergencyReportService.RELAY_CHANNEL,
          etaMinutes = null,
          note = "Could not save the local report: ${e.message ?: "unexpected error"}. Nothing was transmitted or recorded."
        )
      }
      _uiState.update {
        it.copy(
          isSubmittingReport = false,
          lastReportReceipt = receipt,
          snackbarMessage = receipt.note
        )
      }
    }
  }

  /** Measures the REAL osmdroid tile-cache size from disk (Profile honesty). */
  fun updateTileCacheBytes() {
    val dir = tileCacheDirProvider() ?: return
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      val bytes = runCatching {
        dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
      }.getOrDefault(0L)
      kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        _uiState.update { it.copy(tileCacheBytes = bytes) }
      }
    }
  }

  // ============================================================= DIALOGS

  fun openInteractiveBagDialog() {
    _uiState.update { it.copy(showInteractiveBagDialog = true) }
  }

  fun closeInteractiveBagDialog() {
    _uiState.update { it.copy(showInteractiveBagDialog = false) }
  }

  fun openAddContactDialog() {
    _uiState.update { it.copy(showAddContactDialog = true) }
  }

  fun closeAddContactDialog() {
    _uiState.update { it.copy(showAddContactDialog = false) }
  }

  fun addContact(name: String, relation: String, phone: String, location: String) {
    val newContact = EmergencyContact(
      id = "c-${System.currentTimeMillis()}",
      name = name,
      role = relation,
      phone = phone,
      locationNote = location,
      initials = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
      colorHex = 0xFF4F46E5
    )
    _uiState.update {
      it.copy(
        contactsList = it.contactsList + newContact,
        showAddContactDialog = false,
        snackbarMessage = "Added $name to emergency kin network"
      )
    }
  }

  // ========================================================= DETAIL SHEETS

  /** Opens the hazard detail sheet from a map-zone tap. */
  fun openHazardDetail(zone: HazardZone) {
    _uiState.update { it.copy(hazardDetailZone = zone) }
  }

  fun closeHazardDetail() {
    _uiState.update { it.copy(hazardDetailZone = null) }
  }

  /** Opens the safe-zone detail sheet from a map-zone tap. */
  fun openSafeZoneDetail(zone: SafeZone) {
    _uiState.update { it.copy(safeZoneDetail = zone) }
  }

  fun closeSafeZoneDetail() {
    _uiState.update { it.copy(safeZoneDetail = null) }
  }

  fun clearSnackbar() {
    _uiState.update { it.copy(snackbarMessage = null) }
  }
}
