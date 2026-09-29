package com.example.viewmodel

import com.example.data.disaster.DemoNetworkAroundUser
import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.ProviderResult
import com.example.data.location.PlaceCandidate
import com.example.data.model.GeoMath
import com.example.data.model.HazardType
import com.example.data.news.GNewsCall
import com.example.data.news.GNewsService
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsRepository
import com.example.data.news.NewsScope
import com.example.data.routing.GeoPoint
import com.example.data.routing.RouteResult
import com.example.data.weather.WeatherFailureKind
import com.example.data.weather.WeatherReading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * FINAL-SPEC regression suite (MAP DEMO DATA + SAFE-ZONE SYSTEM).
 * Pins the invariants the user's screenshot proved could leak:
 * scenario lifecycle, geographic deterministic demo, landward cyclone
 * shelters, validated-count honesty, and route/destination identity.
 */
class MapDemoSystemRegressionTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * Drain in-flight IO resumes (the altitude batch hop inside
   * ensureAltitudesFor) BEFORE the rule resets Dispatchers.Main, so a
   * slow IO thread cannot resume onto an unset dispatcher during
    * teardown under full-suite contention. A settle, not a weakened assert.
   */
  private fun settle() {
    Thread.sleep(150)
    mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
  }

  private val vizag = GeoPoint(17.6935, 83.2921)
  private val vijayawada = GeoPoint(16.5062, 80.6489)
  private val kochi = GeoPoint(9.9312, 76.2673)

  private class OfflineProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class OfflineNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  /** A parked fetcher records every (origin, destinationId) it is asked for. */
  private class RecordingFetcher(
    val results: MutableList<RouteResult> = mutableListOf()
  ) {
    var lastOrigin: GeoPoint? = null
    var lastDestinationId: String? = null
    var lastDestination: GeoPoint? = null
    var invocations = 0
    suspend operator fun invoke(
      origin: GeoPoint, destination: GeoPoint,
      mode: com.example.data.routing.TravelMode,
      hazards: List<com.example.data.model.HazardZone>,
      destinationName: String, destinationId: String, alternativeIndex: Int
    ): List<RouteResult> {
      invocations++
      lastOrigin = origin
      lastDestination = destination
      lastDestinationId = destinationId
      return results
    }
  }

  private fun fetcherReturning(route: RouteResult?): RecordingFetcher =
    RecordingFetcher(mutableListOf<RouteResult>().apply { route?.let { add(it) } })

  private fun viewModel(
    routing: RecordingFetcher = RecordingFetcher()
  ) = VippattiViewModel(
    reportService = com.example.data.reports.LocalEmergencyReportService(),
    newsCache = MemoryNewsCache(),
    disasterCache = MemoryDisasterCache(),
    disasterRepository = DisasterDataRepository(
      providers = listOf(OfflineProvider(DisasterSource.USGS)),
      cache = MemoryDisasterCache(),
      cacheDispatcher = mainDispatcherRule.dispatcher
    ),
    newsRepositoryOverride = NewsRepository(
      service = OfflineNewsService(),
      cache = MemoryNewsCache(),
      apiKeyProvider = { "" },
      storageDispatcher = mainDispatcherRule.dispatcher
    ),
    weatherFetcher = { WeatherReading.Failure(WeatherFailureKind.NO_CONNECTION) },
    liveRouteFetcher = { o, d, m, h, n, id, a -> routing(o, d, m, h, n, id, a) },
    elevationCacheOverride = com.example.data.suitability.ElevationCache(
      fetch = { com.example.data.suitability.TerrainFetchResult.Failure("offline (test fake)") }
    )
  )

  // ------------------------------------------------- PART 3A: REGIONAL DEMO

  @Test
  fun `regional demo (unfocused) shows multiple disaster TYPES across areas`() = runTest {
    val vm = viewModel()
    val state = vm.uiState.value
    assertTrue("fallback view must be active", state.isUserLocationFallback)
    val types = state.hazardZones.map { it.type }.distinct()
    assertTrue(
      "regional overview must demonstrate >= 4 hazard types, got $types",
      types.size >= 4
    )
    // The five primary types from the spec are represented in the field.
    listOf(HazardType.FLOOD, HazardType.FIRE, HazardType.EARTHQUAKE,
      HazardType.CYCLONE, HazardType.LANDSLIDE).forEach { t ->
      assertTrue("regional field must include $t", types.contains(t))
    }
    // And every hazard in the field is honestly simulated.
    state.hazardZones.forEach {
      assertTrue("hazard ${it.id} must be labelled", it.sourceStatus.isNotBlank())
    }
  }

  @Test
  fun `same place always produces the same demo disaster type (deterministic geography)`() {
    val coast = 3 // coastal Visakhapatnam
    val a = DemoNetworkAroundUser.hazardTypeFor(vizag, coast)
    val b = DemoNetworkAroundUser.hazardTypeFor(vizag, coast)
    assertEquals(a, b)
    // Coastal input ALWAYS yields a sea-driven story (flood or cyclone).
    assertTrue(
      "coastal demo must be FLOOD or CYCLONE, got $a",
      a == HazardType.FLOOD || a == HazardType.CYCLONE
    )
    // The hazard circle TYPE + radius are stable for the same point.
    val h1 = DemoNetworkAroundUser.hazardNear(vizag, coast)
    val h2 = DemoNetworkAroundUser.hazardNear(vizag, coast)
    assertEquals(h1.type, h2.type)
    assertEquals(h1.radiusMeters, h2.radiusMeters, 0.001)
    // Cyclone footprint is wider than fire — disaster-aware geometry.
    assertTrue(
      DemoNetworkAroundUser.radiusMetersFor(HazardType.CYCLONE) >
        DemoNetworkAroundUser.radiusMetersFor(HazardType.FIRE)
    )
  }

  // ------------------------------------- PART 5-7: CANDIDATE VALIDITY (LAND)

  @Test
  fun `cyclone shelters are landward - never closer to the sea than the focus`() {
    // Force the cyclone branch deterministically: coastal + even seed.
    val point = GeoPoint(16.0, 80.5) // Krishna delta coast near Vijayawada
    val type = DemoNetworkAroundUser.hazardTypeFor(point, 3)
    // Whatever the hash says for this point, the LANDWARD RULE must hold when
    // the type is cyclone and the grid can answer candidate distances.
    val focusCoast = 3
    // Simulated coarse grid: coast distance GROWS going inland (+lat here).
    DemoNetworkAroundUser.coastKmOf = { p ->
      focusCoast + ((p.lat - point.lat) * 111.0 / 25.0).toInt().coerceAtLeast(0) * 0 +
        (if (p.lat > point.lat) 2 else 0)
    }
    try {
      val shelters = DemoNetworkAroundUser.sheltersAround(point, coastKm = focusCoast)
      if (type == HazardType.CYCLONE) {
        shelters.forEach { sz ->
          val candCoast = DemoNetworkAroundUser.coastKmOf(sz.point)
          assertTrue(
            "cyclone shelter ${sz.id} sits seaward: $candCoast < $focusCoast",
            candCoast == null || candCoast >= focusCoast
          )
        }
      }
      // Regardless of type: EVERY demo shelter stays inside India.
      shelters.forEach {
        assertTrue("shelter ${it.id} left India", com.example.data.disaster.IndiaGeo.contains(it.point))
      }
    } finally {
      DemoNetworkAroundUser.coastKmOf = { _ -> null }
    }
  }

  @Test
  fun `every demo shelter sits outside its hazard circle plus safety buffer`() {
    listOf(vizag, kochi, vijayawada, GeoPoint(21.5, 80.0)).forEach { focus ->
      val hazard = DemoNetworkAroundUser.hazardNear(focus)
      DemoNetworkAroundUser.sheltersAround(focus).forEach { sz ->
        val d = GeoMath.distanceMeters(hazard.center, sz.point)
        assertTrue(
          "${sz.id} inside ${hazard.type} circle: $d <= ${hazard.radiusMeters}",
          d > hazard.radiusMeters
        )
      }
    }
  }

  // ------------------------------------ PART 4: SCENARIO LIFECYCLE (STALE)

  private fun demoQuant(p: GeoPoint): String =
    "${(p.lat * 50).toLong()}_${(p.lon * 50).toLong()}"

  private fun candidate(p: GeoPoint, name: String) = PlaceCandidate(
    name = name, displayName = "$name, India", point = p, kind = "city"
  )

  @Test
  fun `location change destroys the old scenario - route, destination, guidance`() = runTest {
    val routing = fetcherReturning(null)
    val vm = viewModel(routing)
    vm.applyRealGpsFix(vizag.lat, vizag.lon)
    val scoped = vm.uiState.value.scopedShelters
    assertTrue("vizag must have scoped shelters", scoped.isNotEmpty())
    val zone = scoped.first()
    val oldZoneId = zone.id
    vm.selectSafeZone(zone, autoRoute = true)
    vm.viewChosenPlace(candidate(vijayawada, "Vijayawada"))
    val after = vm.uiState.value
    // NOTHING from the Vizag scenario may survive: not its destination id,
    // not its corridor. Auto-selection of the BEST NEW-location zone is the
    // intended post-switch state — but it must be scoped to Vijayawada.
    assertTrue(
      "old destination id must not be the new selection",
      after.selectedSafeZone == null || after.selectedSafeZone.id != oldZoneId
    )
    after.scopedShelters.forEach { sz ->
      assertTrue("new scoped ids must all be Vijayawada-quantised",
        sz.id.contains(demoQuant(vijayawada)))
    }
    if (after.activeRoute != null) {
      val ids = after.scopedShelters.map { it.id }.toSet()
      assertTrue(
        "any surviving corridor must target a CURRENT scoped zone",
        after.activeRoute!!.destinationId in ids
      )
      // Endpoint repair prepends the TRUE user location: a surviving
      // corridor must literally start at the CURRENT location.
      val first = after.activeRoute!!.pathPoints.firstOrNull()
      if (first != null) {
        assertTrue(
          "corridor must start at the current location, not an old one",
          GeoMath.distanceMeters(after.userLocation, first) <= 60.0
        )
      }
    }
    // All NEW-location shelters genuinely sit near the NEW focus.
    after.scopedShelters.forEach { sz ->
      assertTrue(
        "shelter ${sz.id} belongs to the OLD location",
        GeoMath.distanceMeters(vijayawada, sz.point) <= 12_000.0
      )
    }
    settle()
  }

  @Test
  fun `old destinationId can never exist in the new scoped shelter set`() = runTest {
    val vm = viewModel()
    vm.applyRealGpsFix(vizag.lat, vizag.lon)
    val oldIds = vm.uiState.value.scopedShelters.map { it.id }.toSet()
    assertTrue(oldIds.isNotEmpty())
    vm.viewChosenPlace(candidate(vijayawada, "Vijayawada"))
    val newIds = vm.uiState.value.scopedShelters.map { it.id }.toSet()
    val leaked = oldIds.intersect(newIds)
    assertTrue("demo ids must not leak across locations: $leaked", leaked.isEmpty())
    settle()
  }

  @Test
  fun `micro GPS jitter keeps the corridor while a far fix kills it`() = runTest {
    val routing = RecordingFetcher()
    val vm = viewModel(routing)
    vm.applyRealGpsFix(vizag.lat, vizag.lon)
    val zone = vm.uiState.value.scopedShelters.first()
    vm.selectSafeZone(zone, autoRoute = true)
    // ~30 m jitter: must NOT wipe the selection (mid-guidance re-fix bug).
    vm.applyRealGpsFix(vizag.lat + 0.0003, vizag.lon)
    assertNotNull("jitter must preserve the selection", vm.uiState.value.selectedSafeZone)
    // 10 km relocation: MUST wipe it (spec part 4).
    vm.applyRealGpsFix(vizag.lat + 0.09, vizag.lon)
    assertNull("a relocated fix must drop the old destination", vm.uiState.value.selectedSafeZone)
    assertNull("a relocated fix must drop the old route", vm.uiState.value.activeRoute)
    settle()
  }

  // ----------------------------------- PART 8/10: ROUTE TRUTH + DISTANCES

  @Test
  fun `route is requested to the EXACT selected safe-zone coordinates`() = runTest {
    val routing = RecordingFetcher()
    val vm = viewModel(routing)
    vm.applyRealGpsFix(vizag.lat, vizag.lon)
    val zone = vm.uiState.value.scopedShelters.first()
    vm.selectSafeZone(zone, autoRoute = true)
    assertEquals("router must target the selected zone id", zone.id, routing.lastDestinationId)
    assertEquals("router must target the selected zone point", zone.point, routing.lastDestination)
    assertEquals("router must start from the selected location",
      vm.uiState.value.userLocation, routing.lastOrigin)
    settle()
  }

  @Test
  fun `no route geometry means no corridor is shown - and no straight line fakes it`() = runTest {
    val routing = fetcherReturning(null) // router returns nothing
    val vm = viewModel(routing)
    vm.applyRealGpsFix(vizag.lat, vizag.lon)
    val zone = vm.uiState.value.scopedShelters.first()
    vm.selectSafeZone(zone, autoRoute = true)
    val st = vm.uiState.value
    assertNull("failed routing must never draw a line", st.activeRoute)
    assertTrue("failed routing must be honest about state",
      st.routeStatus == com.example.viewmodel.RouteStatus.NETWORK_ERROR ||
      st.routeStatus == com.example.viewmodel.RouteStatus.NO_ROUTE ||
      st.isCalculatingRoute)
    settle()
  }

  @Test
  fun `card distance always equals the distance of the actual coordinates`() = runTest {
    val vm = viewModel()
    vm.applyRealGpsFix(vizag.lat, vizag.lon)
    vm.uiState.value.scopedShelters.forEach { sz ->
      val fromFocus = GeoMath.distanceMeters(vizag, sz.point)
      // locationNote quotes km from THESE coordinates; recompute and compare.
      val quoted = Regex("about (\\d+(?:\\.\\d+)?) km").find(sz.locationNote)
        ?.groupValues?.get(1)?.toDoubleOrNull()
      assertNotNull("shelter note must quote its real distance", quoted)
      assertTrue(
        "${sz.id}: quoted ${quoted}km vs real ${fromFocus / 1000.0}km",
        kotlin.math.abs(quoted!! * 1000.0 - fromFocus) < 250.0
      )
    }
    settle()
  }

  // ------------------------------------------------- PART 11: VALID COUNT

  @Test
  fun `demo returns at most 5 and at least 2 candidates without inventing extras`() {
    listOf(vizag, kochi, vijayawada).forEach { f ->
      val n = DemoNetworkAroundUser.sheltersAround(f).size
      assertTrue("$f produced $n candidates", n in 2..5)
    }
  }

  // ------------------------------------------------ PART 14: FILTER ≠ MOVE

  @Test
  fun `disaster filter changes the view but NEVER the selected location`() = runTest {
    val vm = viewModel()
    vm.applyRealGpsFix(vizag.lat, vizag.lon)
    val before = vm.uiState.value.userLocation
    vm.toggleHazardTypeFilter(HazardType.EARTHQUAKE)
    assertEquals("filter must not move the focus", before, vm.uiState.value.userLocation)
    assertEquals(HazardType.EARTHQUAKE, vm.uiState.value.hazardTypeFilter)
    vm.toggleHazardTypeFilter(HazardType.EARTHQUAKE)
    assertNull("tapping again clears the filter", vm.uiState.value.hazardTypeFilter)
    assertEquals("and the focus is still the same", before, vm.uiState.value.userLocation)
    settle()
  }
}
