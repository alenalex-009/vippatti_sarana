package com.example.viewmodel

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.ProviderResult
import com.example.data.disaster.providers.FirmsFireProvider
import com.example.data.news.GNewsCall
import com.example.data.news.GNewsService
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsRepository
import com.example.data.news.NewsScope
import com.example.data.reports.LocalEmergencyReportService
import com.example.data.weather.WeatherFailureKind
import com.example.data.weather.WeatherReading
import com.example.viewmodel.RouteStatus.IDLE
import com.example.viewmodel.RouteStatus.NETWORK_ERROR
import com.example.viewmodel.RouteStatus.REQUESTING
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
/**
 * Behaviour tests for the explicit emergency-tool and routing contracts
 * (device tools + route state machine + offline honesty). All network
 * boundaries are test fakes â€” no test hits the real network.
 *
 * Uses the shared [MainDispatcherRule] (unconfined Main): `ViewModel.viewModelScope`
 * is hard-bound to `Dispatchers.Main.immediate`, and the rule eagerly runs that
 * work so no coroutine is left racing the Main-dispatcher swap at teardown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceToolsAndRoutingTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * Selects a genuinely ranked shelter by first placing the (real-GPS) user next
   * to a simulated shelter. Without a fix the location is the India centre, from
   * which every shelter is >40 km away and correctly rejected as UNREACHABLE â€”
   * so a fixture that skips this step has no feasible shelter to route to.
   */
  private fun firstFeasibleShelter(vm: VippattiViewModel) =
    com.example.data.disaster.PilotRegionData.safeZones.firstNotNullOfOrNull { zone ->
      vm.applyRealGpsFix(zone.lat + 0.004, zone.lon + 0.004)
      // DEMO-AROUND-YOU note: with demo ON the generated danger circle now
      // honestly swallows shelters that sit inside it near the user, so the
      // fixture asks for ANY genuinely ranked (feasible) shelter instead of
      // pinning one specific pilot id — the routing tests only need a real
      // reachable destination, and the demo shelter opposite the hazard
      // always qualifies.
      vm.uiState.value.rankedShelters.firstOrNull()
    } ?: error("no feasible shelter ranked near any simulated site")

  private fun viewModel(
    liveRouteFetcher: suspend (
      origin: com.example.data.routing.GeoPoint,
      destination: com.example.data.routing.GeoPoint,
      mode: String,
      hazards: List<com.example.data.model.HazardZone>,
      destinationName: String,
      wantAlternatives: Int
    ) -> List<com.example.data.routing.RouteResult> =
      { _, _, _, _, _, _ -> emptyList() } // deterministic failure: no real OSRM is ever hit
  ) = VippattiViewModel(
    reportService = LocalEmergencyReportService(),
    newsCache = MemoryNewsCache(),
    disasterCache = MemoryDisasterCache(),
    disasterRepository = DisasterDataRepository(
      providers = listOf(FailingProvider(DisasterSource.USGS), FailingProvider(DisasterSource.IMD_CAP)),
      cache = MemoryDisasterCache(),
      cacheDispatcher = mainDispatcherRule.dispatcher
    ),
    newsRepositoryOverride = NewsRepository(
      service = FailingNewsService(),
      cache = MemoryNewsCache(),
      apiKeyProvider = { "" },
      storageDispatcher = mainDispatcherRule.dispatcher
    ),
    weatherFetcher = { WeatherReading.Failure(WeatherFailureKind.NO_CONNECTION) },
    liveRouteFetcher = liveRouteFetcher
  )

  private class FailingProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class FailingNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  // ---------------------------------------------------------- DEVICE TOOLS

  @Test
  fun `siren state is explicit and counts down`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel()

    assertEquals(SirenState.IDLE, vm.uiState.value.sirenState)
    assertTrue(vm.uiState.value.isSirenOn.not())

    vm.startSiren()
    assertEquals(SirenState.PLAYING, vm.uiState.value.sirenState)
    assertTrue(vm.uiState.value.isSirenOn)
    assertEquals(SIREN_MAX_SECONDS, vm.uiState.value.sirenSecondsLeft)

    advanceTimeBy(5_001)
    // NOTE: deliberately NOT advanceUntilIdle â€” that would run the whole 60 s
    // countdown to completion. We assert the mid-countdown value instead.
    assertEquals(SIREN_MAX_SECONDS - 5, vm.uiState.value.sirenSecondsLeft)
    assertEquals(SirenState.PLAYING, vm.uiState.value.sirenState)
  }

  @Test
  fun `second tap always stops the siren`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel()
    vm.toggleSiren()
    assertEquals(SirenState.PLAYING, vm.uiState.value.sirenState)

    vm.toggleSiren()
    advanceUntilIdle()
    assertEquals(SirenState.IDLE, vm.uiState.value.sirenState)
    assertEquals(0, vm.uiState.value.sirenSecondsLeft)
    assertTrue(vm.uiState.value.isSirenOn.not())
  }

  @Test
  fun `torch reflects the reported platform outcome`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel()

    vm.toggleFlashlight()
    assertTrue(vm.uiState.value.isFlashlightOn)

    vm.onTorchResult(false, "Camera service refused (test fake).")
    assertEquals(TorchState.UNAVAILABLE, vm.uiState.value.torchState)
    assertTrue(vm.uiState.value.isFlashlightOn.not())
    assertNotNull(vm.uiState.value.torchMessage)

    vm.toggleFlashlight()
    vm.onTorchResult(false, TORCH_REASON_PERMISSION, permissionDenied = true)
    assertEquals(TorchState.PERMISSION_DENIED, vm.uiState.value.torchState)
    assertNotNull(vm.uiState.value.torchMessage)
  }

  @Test
  fun `global stop ends every device tool`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel()
    vm.toggleFlashlight()
    vm.toggleSiren()
    vm.stopAllDeviceTools()
    advanceUntilIdle()

    assertEquals(SirenState.IDLE, vm.uiState.value.sirenState)
    assertEquals(TorchState.OFF, vm.uiState.value.torchState)
    assertTrue(vm.uiState.value.hasActiveDeviceTool.not())
  }

  @Test
  fun `offline-first flag defaults to off and never claims a download`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel()
    assertEquals(false, vm.uiState.value.isOfflineFirstMode)

    vm.toggleOfflineCache(true)
    assertEquals(true, vm.uiState.value.isOfflineFirstMode)
    assertTrue(vm.uiState.value.snackbarMessage!!.contains("no bulk offline download"))
  }

  // ROUTING TESTS CONTINUE BELOW (marker)
  @Test
  fun `route request starts in REQUESTING with zero geometry drawn`() = runTest(mainDispatcherRule.dispatcher) {
    val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
    val vm = viewModel { _, _, _, _, _, _ -> gate.await(); emptyList() }
    val best = firstFeasibleShelter(vm)
    vm.selectSafeZone(best.zone, autoRoute = false)
    vm.calculateRouteToSelectedZone()

    // The fetcher is parked on the gate: the only observable state is REQUESTING
    // with nothing drawable.
    val requesting = vm.uiState.value
    assertEquals(REQUESTING, requesting.routeStatus)
    assertNull("no straight-line placeholder may be drawn while requesting", requesting.activeRoute)
    assertTrue("request loading state must be observable", requesting.isCalculatingRoute)

    // Release the gate: the deterministic failure lands in NETWORK_ERROR, still
    // with nothing drawn.
    gate.complete(Unit)
    advanceUntilIdle()
    val failed = vm.uiState.value
    assertEquals(NETWORK_ERROR, failed.routeStatus)
    assertNull("nothing is drawn when the router cannot be reached", failed.activeRoute)
    assertTrue(failed.isCalculatingRoute.not())
  }

  @Test
  fun `requesting then clearing never resurrects a route`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel()
    val best = firstFeasibleShelter(vm)
    vm.selectSafeZone(best.zone, autoRoute = false)
    vm.calculateRouteToSelectedZone()
    vm.clearActiveRoute()

    // Deterministic failure implies NETWORK_ERROR (no geometry) on the test dispatcher.
    advanceUntilIdle()
    val state = vm.uiState.value
    assertEquals(IDLE, state.routeStatus)
    assertNull(state.activeRoute)
    assertNull(state.routeStatusMessage)
  }

  @Test
  fun `clearing a route keeps destination selection so a fresh request can reuse it`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel()
    val best = firstFeasibleShelter(vm)
    vm.selectSafeZone(best.zone, autoRoute = false)
    vm.calculateRouteToSelectedZone()
    vm.clearActiveRoute()

    advanceUntilIdle()
    // A fresh request must run cleanly on the untouched zone and end in the
    // deterministic failure state with no geometry drawn.
    vm.calculateRouteToSelectedZone()
    advanceUntilIdle()

    val state = vm.uiState.value
    assertEquals(RouteStatus.NETWORK_ERROR, state.routeStatus)
    assertNull("nothing is drawn when the router cannot be reached", state.activeRoute)
    assertTrue(state.isCalculatingRoute.not())
  }

  @Test
  fun `explicit offline estimate is labelled unverified, never safe`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel()
    val best = firstFeasibleShelter(vm)
    vm.selectSafeZone(best.zone, autoRoute = false)

    vm.requestOfflineFallbackRoute()

    val state = vm.uiState.value
    assertEquals(com.example.viewmodel.RouteStatus.FALLBACK_UNVERIFIED, state.routeStatus)
    assertNotNull("the explicitly requested estimate IS drawable", state.activeRoute)
    assertTrue("estimate is not a live road route", state.activeRoute!!.isLiveOsrm.not())
    assertTrue(
      "estimate must say it is unverified: ${state.routeStatusMessage}",
      state.routeStatusMessage!!.contains("NOT", ignoreCase = true)
    )
    assertTrue("estimate must never count as validated", state.hasValidatedRoute.not())
  }

  @Test
  fun `ready road routes disclose unverified closures and traffic, never overclaim`() = runTest(mainDispatcherRule.dispatcher) {
    val live = com.example.data.routing.RouteResult(
      distanceMeters = 2500.0,
      durationSeconds = 1800.0,
      pathPoints = listOf(
        com.example.data.routing.GeoPoint(9.85, 76.94),
        com.example.data.routing.GeoPoint(9.86, 76.95)
      ),
      steps = emptyList(),
      isLiveOsrm = true,
      summary = "OSRM test route",
      travelMode = "foot"
    )
    val vm = viewModel(liveRouteFetcher = { _, _, _, _, _, _ -> listOf(live) })
    val best = firstFeasibleShelter(vm)
    vm.selectSafeZone(best.zone, autoRoute = false)
    vm.calculateRouteToSelectedZone()
    advanceUntilIdle()

    // Live road geometry: hazard-checked, with the closure/traffic limit stated.
    val first = vm.uiState.value
    assertEquals(com.example.viewmodel.RouteStatus.READY, first.routeStatus)
    val liveMessage = first.routeStatusMessage!!
    assertTrue("live message states the hazard check: $liveMessage", liveMessage.contains("hazard-checked"))
    assertTrue(
      "live message discloses the limit: $liveMessage",
      liveMessage.contains("Road closures and live traffic are not verified.")
    )
    for (overclaim in listOf(
      "closure-verified", "closure verified", "live-traffic",
      "traffic validated", "fully safe", "emergency-authorized", "guarantee"
    )) {
      assertFalse(
        "READY message must not overclaim ($overclaim): $liveMessage",
        liveMessage.contains(overclaim, ignoreCase = true)
      )
    }

    // Second request serves the cached road route — the same disclosure applies.
    vm.calculateRouteToSelectedZone()
    advanceUntilIdle()
    val second = vm.uiState.value
    assertEquals(com.example.viewmodel.RouteStatus.READY, second.routeStatus)
    assertTrue(
      "cached message discloses the limit: ${second.routeStatusMessage}",
      second.routeStatusMessage!!.contains("Road closures and live traffic are not verified.")
    )
  }

  /**
   * START EVACUATION ROUTE pressed while no corridor exists yet: the button
   * cannot start guidance on empty geometry (the HUD would have nothing behind
   * it), so it requests the route and PROMISES "guidance starts when it
   * arrives". The arriving route must therefore arm guidance by itself.
   */
  @Test
  fun `starting evacuation arms guidance as soon as the requested route arrives`() = runTest(mainDispatcherRule.dispatcher) {
    val live = com.example.data.routing.RouteResult(
      distanceMeters = 2500.0,
      durationSeconds = 1800.0,
      pathPoints = listOf(
        com.example.data.routing.GeoPoint(9.85, 76.94),
        com.example.data.routing.GeoPoint(9.86, 76.95)
      ),
      steps = emptyList(),
      isLiveOsrm = true,
      summary = "OSRM test route",
      travelMode = "foot"
    )
    val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
    val vm = viewModel { _, _, _, _, _, _ -> gate.await(); listOf(live) }
    val best = firstFeasibleShelter(vm)
    vm.selectSafeZone(best.zone, autoRoute = false)

    vm.startEvacuationRoute()
    // Guidance must never start on an empty corridor, but the request must be
    // visibly in flight with nothing drawable yet.
    assertEquals(REQUESTING, vm.uiState.value.routeStatus)
    assertNull("no geometry may be drawn while requesting", vm.uiState.value.activeRoute)
    assertFalse("guidance cannot start before the route exists", vm.uiState.value.isNavigatingLive)

    gate.complete(Unit)
    advanceUntilIdle()

    val arrived = vm.uiState.value
    assertEquals(com.example.viewmodel.RouteStatus.READY, arrived.routeStatus)
    assertNotNull(arrived.activeRoute)
    assertTrue(
      "the promised start must happen by itself when the route arrives",
      arrived.isNavigatingLive
    )

    // The intent is consumed exactly once: a second arrival (travel-mode
    // change, GPS re-route) never re-arms guidance after the user stopped it.
    vm.stopLiveNavigation()
    assertFalse(vm.uiState.value.isNavigatingLive)
    vm.startEvacuationRoute() // route already exists -> starts immediately
    assertTrue(vm.uiState.value.isNavigatingLive)
  }

  /**
   * Selecting a shelter and letting the route compute (without ever pressing
   * START EVACUATION ROUTE) must never start guidance by itself — only the
   * explicit start requests the automatic start.
   */
  @Test
  fun `a plain route request never starts guidance on its own`() = runTest(mainDispatcherRule.dispatcher) {
    val live = com.example.data.routing.RouteResult(
      distanceMeters = 2500.0,
      durationSeconds = 1800.0,
      pathPoints = listOf(
        com.example.data.routing.GeoPoint(9.85, 76.94),
        com.example.data.routing.GeoPoint(9.86, 76.95)
      ),
      steps = emptyList(),
      isLiveOsrm = true,
      summary = "OSRM test route",
      travelMode = "foot"
    )
    val vm = viewModel(liveRouteFetcher = { _, _, _, _, _, _ -> listOf(live) })
    val best = firstFeasibleShelter(vm)
    vm.selectSafeZone(best.zone, autoRoute = true)
    advanceUntilIdle()

    assertEquals(com.example.viewmodel.RouteStatus.READY, vm.uiState.value.routeStatus)
    assertFalse(
      "browsing/routing to a shelter must not silently start turn-by-turn guidance",
      vm.uiState.value.isNavigatingLive
    )
  }

  @Test
  fun `validated live geometry passes the hasValidatedRoute gate`() {
    val live = com.example.data.routing.RouteResult(
      distanceMeters = 1234.0,
      durationSeconds = 900.0,
      pathPoints = listOf(
        com.example.data.routing.GeoPoint(9.85, 76.94),
        com.example.data.routing.GeoPoint(9.86, 76.95)
      ),
      steps = emptyList(),
      isLiveOsrm = true,
      summary = "OSRM test route",
      travelMode = "foot"
    )
    val state = VippattiUiState(activeRoute = live, routeStatus = com.example.viewmodel.RouteStatus.READY)
    assertTrue(state.hasValidatedRoute)
    assertEquals(false, VippattiUiState(activeRoute = live, routeStatus = IDLE).hasValidatedRoute)
  }

  @Test
  fun `switching demo data off keeps real provider states`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel()
    // Let the init sync finish so the real per-provider states exist to inspect.
    // Unconfined Main runs eagerly, but the repository body awaits on its own
    // dispatchers — the explicit drain waits for it deterministically.
    advanceUntilIdle()
    advanceUntilIdle() // second pass: the refresh completes after the first idle

    vm.toggleMockData()
    assertEquals(false, vm.uiState.value.isMockDataVisible)
    assertTrue(
      "live feed states survive the toggle: ${vm.uiState.value.providerStates}",
      vm.uiState.value.providerStates.isNotEmpty()
    )

    // And it is reversible.
    vm.toggleMockData()
    assertEquals(true, vm.uiState.value.isMockDataVisible)
  }
}