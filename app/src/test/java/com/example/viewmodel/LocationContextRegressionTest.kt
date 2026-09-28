package com.example.viewmodel

import com.example.data.disaster.DemoNetworkAroundUser
import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.PilotRegionData
import com.example.data.disaster.ProviderResult
import com.example.data.location.PlaceCandidate
import com.example.data.model.GeoMath
import com.example.data.news.GNewsCall
import com.example.data.news.GNewsService
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsRepository
import com.example.data.news.NewsScope
import com.example.data.reports.EmergencyReport
import com.example.data.reports.EmergencyReportService
import com.example.data.reports.LocalEmergencyReportService
import com.example.data.reports.ReportKind
import com.example.data.reports.ReportReceipt
import com.example.data.routing.GeoPoint
import com.example.data.routing.RouteResult
import com.example.data.routing.TravelMode
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
 * MAP CLEANUP + LOCATION-CONTEXT acceptance contracts (the user's numbered
 * test list):
 *
 *   GPS / SEARCH LOCATION -> LOCAL DISASTER -> NEARBY SAFE ZONES
 *   -> SELECT THAT SAFE ZONE -> ROUTE TO THAT EXACT SAFE ZONE
 *
 * All assertions run on the production pipeline; distances come from the same
 * SafeZone objects the markers render and routing targets. The Unconfined
 * dispatcher makes viewModelScope work synchronous, so no pumps are needed.
 */
class LocationContextRegressionTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val vizag = GeoPoint(17.6935, 83.2921)
  private val vijayawada = GeoPoint(16.5062, 80.6480)

  private class OfflineProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class OfflineNewsService : GNewsService {
        override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  /** Fetcher spy: records exactly what the routing layer asked for. */
  private class RouteSpy(
    val routes: (GeoPoint, GeoPoint, String, String) -> List<RouteResult> = { _, _, _, _ -> emptyList() }
  ) {
    var origin: GeoPoint? = null
    var destination: GeoPoint? = null
    var destinationName: String? = null
    var destinationId: String? = null
    var calls = 0
    suspend fun fetch(
      o: GeoPoint, d: GeoPoint,
      name: String, id: String
    ): List<RouteResult> {
      calls++
      origin = o; destination = d; destinationName = name; destinationId = id
      return routes(o, d, name, id)
    }
  }

  private fun viewModel(
    reportService: EmergencyReportService = LocalEmergencyReportService(),
    spy: RouteSpy? = null
  ) = VippattiViewModel(
    reportService = reportService,
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
    liveRouteFetcher = { o, d, _, _, name, id, _ ->
      spy?.fetch(o, d, name, id) ?: emptyList()
    }
  )

  // ---------------------------------------------------------------- 1, 2
  @Test
  fun `GPS and a searched place both generate a LOCAL disaster over that location`() {
    runTest {
      for ((place, label) in listOf(vizag to "gps", vijayawada to "search")) {
        val vm = viewModel()
        if (label == "gps") {
          vm.applyRealGpsFix(place.lat, place.lon)
        } else {
          vm.viewChosenPlace(
            PlaceCandidate(
              name = "test-$label", displayName = "Test $label, India",
              point = place, kind = "city"
            )
          )
        }
        val state = vm.uiState.value
        val localHazard = state.hazardZones.firstOrNull { it.id.startsWith("demo-hz-") }
        assertNotNull("local demo hazard generated for $label", localHazard)
        assertTrue("hazard circle covers the focus ($label)",
          GeoMath.distanceMeters(place, localHazard!!.center) < localHazard.radiusMeters)
      }
    }
  }

  // ---------------------------------------------------------------- 4, 10
  @Test
  fun `only nearby safe zones are scoped - no other-state records anywhere`() {
    runTest {
      val vm = viewModel()
      vm.viewChosenPlace(
        PlaceCandidate(name = "v", displayName = "Vizag", point = vizag, kind = "city")
      )
      val scoped = vm.uiState.value.scopedShelters
      assertTrue("scenario generated shelters", scoped.isNotEmpty())
      scoped.forEach { sz ->
        assertTrue(
          "scoped shelter ${sz.id} is %.0fm from the focus".format(
            GeoMath.distanceMeters(vizag, sz.point)),
          GeoMath.distanceMeters(vizag, sz.point) <= 60_000.0
        )
      }
      // The all-India PilotRegionData list (source of the 14.8 km bug) must
      // not leak into the scoped set while focused on one place.
      val pilotIds = PilotRegionData.safeZones.map { it.id }.toSet()
      assertTrue("no all-India demo leftovers in the scoped set",
        scoped.none { it.id in pilotIds })
    }
  }

  // ---------------------------------------------------------------- 3
  @Test
  fun `changing location regenerates everything - old coordinates never reused`() {
    val vizagShelters = DemoNetworkAroundUser.sheltersAround(vizag)
    val vjaShelters = DemoNetworkAroundUser.sheltersAround(vijayawada)
    assertTrue("ids differ between places",
      vizagShelters.map { it.id }.intersect(vjaShelters.map { it.id }.toSet()).isEmpty())
    vjaShelters.forEach { sz ->
      assertTrue("Vijayawada shelter ${sz.id} near Vijayawada",
        GeoMath.distanceMeters(vijayawada, sz.point) < 6_000.0)
      assertTrue("a new-place scenario must not reuse Vizag coordinates",
        GeoMath.distanceMeters(vizag, sz.point) > 100_000.0)
    }
    assertTrue("hazard regenerates too",
      DemoNetworkAroundUser.hazardNear(vizag).id !=
        DemoNetworkAroundUser.hazardNear(vijayawada).id)
  }

  // ---------------------------------------------------------------- 5, 6
  @Test
  fun `demo shelters are deterministic and their displayed distance is real`() {
    val a = DemoNetworkAroundUser.sheltersAround(vizag)
    val b = DemoNetworkAroundUser.sheltersAround(vizag)
    assertEquals("same place -> same ids", a.map { it.id }, b.map { it.id })
    assertEquals("same place -> same coordinates", a.map { it.point }, b.map { it.point })
    assertTrue("at least 2 nearby safe zones (spec: 2-4+)", a.size >= 2)
    a.forEach { sz ->
      val d = GeoMath.distanceMeters(vizag, sz.point)
      assertTrue("shelter ${sz.id} at %.0fm must be within walking range".format(d),
        d < 6_000.0)
    }
  }

  // ---------------------------------------------------------------- 7, 8  (the 799 m vs 14.8 km regression test)
  @Test
  fun `route origin is the focus and destination is the EXACT tapped safe zone`() {
    runTest {
      val spy = RouteSpy { o, d, name, id ->
        listOf(
          RouteResult(
            distanceMeters = 1_000.0, durationSeconds = 800.0,
            pathPoints = listOf(o, d), steps = emptyList(),
            isLiveOsrm = true, summary = "test corridor", travelMode = TravelMode.FOOT,
            destinationName = name, destinationId = id
          )
        )
      }
      val vm = viewModel(spy = spy)
      vm.applyRealGpsFix(vizag.lat, vizag.lon)
      val state = vm.uiState.value
      val zone = state.scopedShelters.first()

      // The card's displayed distance recomputed from the SAME object.
      val cardDistance = GeoMath.distanceMeters(state.userLocation, zone.point)
      // Tap-route that exact card.
      vm.selectSafeZone(zone, autoRoute = true)

      assertEquals("route origin = selected location", vizag, spy.origin)
      assertEquals("route destination = tapped zone coordinates", zone.point, spy.destination)
      assertEquals("route carries the zone ID (identity, not a colliding name)",
        zone.id, spy.destinationId)

      val active = vm.uiState.value.activeRoute!!
      assertEquals(zone.id, active.destinationId)
      assertEquals("drawn corridor ends at the shelter door", zone.point, active.pathPoints.last())
      // The evaluation published for that zone agrees with the card number.
      val eval = vm.uiState.value.evaluatedShelters.first { it.zone.id == zone.id }
      assertEquals(cardDistance, eval.distanceMeters, 1.0)
    }
  }

  // ---------------------------------------------------------------- 9
  @Test
  fun `id-based route matching never collides with a same-named distant twin`() {
    val a = DemoNetworkAroundUser.sheltersAround(vizag).first()
    val b = DemoNetworkAroundUser.sheltersAround(vijayawada).first()
    val routeToA = RouteResult(
      distanceMeters = 799.0, durationSeconds = 600.0,
      pathPoints = listOf(vizag, a.point), steps = emptyList(),
      isLiveOsrm = true, summary = "t", travelMode = TravelMode.FOOT,
      destinationName = a.name, destinationId = a.id
    )
    assertEquals(a.id, routeToA.destinationId)
    assertTrue("same demo NAME at another city is a DIFFERENT id - the highlight" +
      " and camera can never bind the Vizag route to the Vijayawada twin",
      routeToA.destinationId != b.id)
  }

  // ---------------------------------------------------------------- 11 (camera fit is a MapView concern; assert the contract at state level)
  @Test
  fun `guidance start keeps the route visible and raises the zoom token`() {
    runTest {
      val spy = RouteSpy { o, d, name, id ->
        listOf(
          RouteResult(
            distanceMeters = 900.0, durationSeconds = 700.0,
            pathPoints = listOf(o, d), steps = emptyList(),
            isLiveOsrm = true, summary = "t", travelMode = TravelMode.FOOT,
            destinationName = name, destinationId = id
          )
        )
      }
      val vm = viewModel(spy = spy)
      vm.applyRealGpsFix(vizag.lat, vizag.lon)
      val zone = vm.uiState.value.scopedShelters.first()
      vm.selectSafeZone(zone, autoRoute = true)
      val tokenBefore = vm.uiState.value.guidanceZoomToken
      val routeBefore = vm.uiState.value.activeRoute
      assertNotNull("route on screen", routeBefore)
      vm.startEvacuationRoute()
      val st = vm.uiState.value
      assertTrue("guidance armed", st.isNavigatingLive)
      assertTrue("camera fit requested for the route",
        st.guidanceZoomToken > tokenBefore)
      assertEquals("the SAME corridor survived the button press (no teardown)",
        routeBefore, st.activeRoute)
    }
  }

  // ---------------------------------------------------------------- 12
  @Test
  fun `SOS save persists the record BEFORE the UI claims it is saved`() {
    runTest {
      val saved = mutableListOf<EmergencyReport>()
      val recording = object : EmergencyReportService {
        override suspend fun submit(report: EmergencyReport): ReportReceipt {
          saved += report
          return ReportReceipt("LOCAL-TEST", accepted = true, relayChannel = "local test",
            etaMinutes = null, note = "saved (test)")
        }
      }
      val vm = viewModel(reportService = recording)
      vm.triggerSosBroadcast()
      assertTrue("confirm dialog opens", vm.uiState.value.showSosConfirmDialog)
      vm.confirmSosBroadcast()
      assertEquals("exactly one persisted SOS record", 1, saved.size)
      assertEquals(ReportKind.SOS_BROADCAST, saved[0].kind)
      val st = vm.uiState.value
      assertTrue("SAVED state shown only after successful persist", st.isSosActive)
      assertTrue(st.showSosBroadcastDialog)
      assertNotNull(st.lastReportReceipt)
      assertTrue(st.lastReportReceipt!!.accepted)

      // A failing persist must never claim the record exists.
      val failing = object : EmergencyReportService {
        override suspend fun submit(report: EmergencyReport): ReportReceipt =
          ReportReceipt("LOCAL-ERR", accepted = false, relayChannel = "local test",
            etaMinutes = null, note = "disk full")
      }
      val vm2 = viewModel(reportService = failing)
      vm2.triggerSosBroadcast()
      vm2.confirmSosBroadcast()
      assertTrue("failed save never activates SOS", !vm2.uiState.value.isSosActive)
      assertTrue("failed save never opens the SAVED dialog",
        !vm2.uiState.value.showSosBroadcastDialog)
      assertNull(vm2.uiState.value.lastReportReceipt?.takeIf { it.accepted })
    }
  }
}
