package com.example.viewmodel

import com.example.data.disaster.DemoNetworkAroundUser
import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.GeoMath
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.data.routing.RouteResult
import com.example.data.routing.RouteSafetyStatus
import com.example.data.routing.TravelMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * P0 fix #2 contracts (safe-zone / routing destination):
 *
 *  1. The map + carousel render ONLY shelters the current evaluation accepts
 *     as feasible — a CLOSED/FULL/hazard-inside shelter is no longer an
 *     actionable green pin.
 *  2. A selected destination ALWAYS stays rendered (its corridor is on the
 *     map) even if a recompute just rejected it.
 *  3. A routing response whose destinationId differs from the selected
 *     shelter id is DISCARDED — a route can never silently substitute
 *     another zone (the 799 m vs 14.8 km class of bugs).
 *  4. The rendered route's endpoints are literally the selected origin +
 *     the selected shelter's exact coordinates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SafeZoneRoutingIntegrityTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private fun settle() {
    Thread.sleep(150)
    mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
  }

  private val vizag = GeoPoint(17.6935, 83.2925)

  private class OfflineProvider(
    override val providerId: com.example.data.disaster.DisasterSource
  ) : com.example.data.disaster.DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): com.example.data.disaster.ProviderResult =
      com.example.data.disaster.ProviderResult.Failure("No connection (test fake).")
  }

  private class OfflineNewsService : com.example.data.news.GNewsService {
    override suspend fun search(
      scope: com.example.data.news.NewsScope, query: String, apiKey: String): com.example.data.news.GNewsCall =
      com.example.data.news.GNewsCall.Failure(
        com.example.data.news.NewsError(
          com.example.data.news.NewsErrorKind.NETWORK, "offline (test fake)"
        )
      )
  }

  private class RecordingFetcher(
    val results: MutableList<RouteResult> = mutableListOf()
  ) {
    var lastOrigin: GeoPoint? = null
    var lastDestinationId: String? = null
    var lastDestination: GeoPoint? = null
    var invocations = 0
    suspend operator fun invoke(
      origin: GeoPoint, destination: GeoPoint,
      mode: TravelMode,
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

  private fun viewModel(routing: RecordingFetcher) = VippattiViewModel(
    reportService = com.example.data.reports.LocalEmergencyReportService(),
    newsCache = com.example.data.news.MemoryNewsCache(),
    disasterCache = com.example.data.disaster.MemoryDisasterCache(),
    disasterRepository = com.example.data.disaster.DisasterDataRepository(
      providers = listOf(
        OfflineProvider(com.example.data.disaster.DisasterSource.USGS)
      ),
      cache = com.example.data.disaster.MemoryDisasterCache(),
      cacheDispatcher = mainDispatcherRule.dispatcher
    ),
    newsRepositoryOverride = com.example.data.news.NewsRepository(
      service = OfflineNewsService(),
      cache = com.example.data.news.MemoryNewsCache(),
      apiKeyProvider = { "" },
      storageDispatcher = mainDispatcherRule.dispatcher
    ),
    weatherFetcher = { com.example.data.weather.WeatherReading.Failure(com.example.data.weather.WeatherFailureKind.NO_CONNECTION) },
    liveRouteFetcher = { o, d, m, h, n, id, a -> routing(o, d, m, h, n, id, a) },
    elevationCacheOverride = com.example.data.suitability.ElevationCache(
      fetch = { com.example.data.suitability.TerrainFetchResult.Failure("offline (test fake)") }
    )
  )

  private fun fieldShelter(id: String, lat: Double, lon: Double, status: String,
                           total: Int, occupied: Int) = SafeZone(
    id = id, name = "Field shelter $id", lat = lat, lon = lon,
    locationNote = "test", capacityTotal = total, capacityCurrent = occupied,
    waterAvailable = true, foodAvailable = true, electricityAvailable = true,
    sanitationAvailable = true, medicalSupport = true, accessibility = "Road",
    womenChildrenSuitability = true, operatingStatus = status,
    verificationStatus = "FIELD RECORD", elevationNote = "",
    provenance = DataProvenance(source = "test", classification = DataClassification.OBSERVED)
  )

  private fun routeTo(destId: String, destName: String, points: List<GeoPoint>) =
    RouteResult(
      distanceMeters = 900.0, durationSeconds = 700.0,
      pathPoints = points, steps = emptyList(), isLiveOsrm = true,
      summary = "test corridor", travelMode = TravelMode.FOOT,
      routeSafetyStatus = RouteSafetyStatus.SAFE, routeSafetyScore = 90,
      destinationName = destName, destinationId = destId
    )

  @Test
  fun `rejected shelters are not actionable pins - feasible set renders`() = runTest {
    val routing = RecordingFetcher()
    val vm = viewModel(routing)
    vm.applyRealGpsFix(vizag.lat, vizag.lon)
    settle()
    // Deterministic rejections, no dependence on demo geometry: one CLOSED
    // and one FULL field shelter next to Vizag, plus the demo set.
    vm.saveFieldShelter(fieldShelter("closed-1", vizag.lat + 0.02, vizag.lon, "CLOSED", 100, 10))
    vm.saveFieldShelter(fieldShelter("full-1", vizag.lat, vizag.lon + 0.02, "OPEN", 100, 100))
    settle()
    val st = vm.uiState.value
    assertTrue("scoped must carry the rejected records",
      st.scopedShelters.any { it.id == "closed-1" } &&
        st.scopedShelters.any { it.id == "full-1" })
    assertTrue("rejected shelters must NOT render as pins",
      st.visibleSafeZones.none { it.id == "closed-1" || it.id == "full-1" })
    // Feasible demo shelters still render (no over-hiding):
    assertTrue(st.visibleSafeZones.any { it.id.startsWith("demo-sz-") })
  }

  @Test
  fun `a selected destination stays rendered even if just rejected`() = runTest {
    val routing = RecordingFetcher()
    val vm = viewModel(routing)
    vm.applyRealGpsFix(vizag.lat, vizag.lon)
    settle()
    val doomed = fieldShelter("doomed-1", vizag.lat - 0.02, vizag.lon, "OPEN", 100, 0)
    vm.saveFieldShelter(doomed)
    settle()
    vm.selectSafeZone(doomed, autoRoute = false)
    // Now close it: a recompute rejects it while it is the selected zone.
    vm.saveFieldShelter(doomed.copy(operatingStatus = "CLOSED"))
    settle()
    val st = vm.uiState.value
    assertEquals("selection preserved", "doomed-1", st.selectedSafeZone?.id)
    assertTrue("the selected destination must remain the visible pin",
      st.visibleSafeZones.any { it.id == "doomed-1" })
  }

  @Test
  fun `a response targeting another shelter id is discarded - never drawn`() = runTest {
    val routing = RecordingFetcher()
    val vm = viewModel(routing)
    vm.applyRealGpsFix(vizag.lat, vizag.lon)
    settle()
    val zone = vm.uiState.value.visibleSafeZones.first()
    // The (fake) router answers with a corridor to a DIFFERENT zone id.
    routing.results.clear()
    routing.results.add(
      routeTo("impostor-9", "Some other shelter",
        listOf(vm.uiState.value.userLocation, zone.point))
    )
    vm.selectSafeZone(zone, autoRoute = true)
    settle()
    assertNull("impostor corridor must never be published", vm.uiState.value.activeRoute)
    assertEquals(com.example.viewmodel.RouteStatus.NO_ROUTE, vm.uiState.value.routeStatus)
  }

  @Test
  fun `route endpoints equal the selected shelter and origin exactly`() = runTest {
    val routing = RecordingFetcher()
    val vm = viewModel(routing)
    vm.applyRealGpsFix(vizag.lat, vizag.lon)
    settle()
    val zone = vm.uiState.value.visibleSafeZones.first()
    val origin = vm.uiState.value.userLocation
    routing.results.clear()
    routing.results.add(routeTo(zone.id, zone.name, listOf(origin, zone.point)))
    vm.selectSafeZone(zone, autoRoute = true)
    settle()
    val route = vm.uiState.value.activeRoute
    assertNotNull("legit corridor published", route)
    assertEquals(zone.id, route!!.destinationId)
    assertEquals("request went to the EXACT shelter coordinate",
      0.0, GeoMath.distanceMeters(zone.point, routing.lastDestination!!), 0.0001)
    assertTrue("first path point is the true origin",
      GeoMath.distanceMeters(origin, route.pathPoints.first()) <= 60.0)
    assertTrue("last path point is the true destination door",
      GeoMath.distanceMeters(zone.point, route.pathPoints.last()) <= 60.0)
    // And the same object the carousel shows is the destination it routes to.
    assertTrue(vm.uiState.value.visibleSafeZones.map { it.id }.contains(zone.id))
  }

}
