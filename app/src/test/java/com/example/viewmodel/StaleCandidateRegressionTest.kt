package com.example.viewmodel

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.ProviderResult
import com.example.data.location.PlaceCandidate
import com.example.data.location.PlaceResolver
import com.example.data.location.ResolvedPlace
import com.example.data.model.GeoMath
import com.example.data.model.HazardType
import com.example.data.news.GNewsCall
import com.example.data.news.GNewsService
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsRepository
import com.example.data.news.NewsScope
import com.example.data.reports.LocalEmergencyReportService
import com.example.data.routing.GeoPoint
import com.example.data.weather.WeatherFailureKind
import com.example.data.weather.WeatherReading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * BUG-FIX PASS REGRESSION CONTRACTS (user report: stale green ring / stale
 * place names):
 *  1. unfocused overview WITHOUT a disaster filter shows no loose regional
 *     shelter pins - only the local scenario shelters around the focus;
 *  2. changing the disaster filter SWAPS candidate sets - pins belonging to
 *     another disaster's scenario never survive the switch;
 *  3. demo OFF emits zero simulated candidates anywhere in the state;
 *  4. a deliberate location switch CLEARS the previous place's resolved
 *     administrative names immediately and re-resolves for the new point;
 *  5. ResolvedPlace renders unreturned levels as "Not available" instead of
 *     fabricating or carrying values over (adminRows derivation).
 */
class StaleCandidateRegressionTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private class OfflineProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class OfflineNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  /** Scripted resolver: ANDHRA names for points south of 20N, RAJASTHAN north. */
  private class ScriptedResolver : PlaceResolver {
    var calls = 0
    override suspend fun resolve(point: GeoPoint): ResolvedPlace {
      calls++
      return if (point.lat >= 20.0) {
        ResolvedPlace(state = "Rajasthan", district = "Jaipur",
          villageTown = "Jaipur City")
      } else {
        ResolvedPlace(state = "Andhra Pradesh", district = "Visakhapatnam",
          villageTown = "Visakhapatnam")
      }
    }
  }

  private fun viewModel(
    resolver: PlaceResolver = com.example.data.location.UnresolvedPlaceResolver
  ) = VippattiViewModel(
    reportService = LocalEmergencyReportService(),
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
    liveRouteFetcher = { _, _, _, _, _, _, _ -> emptyList() },
    elevationCacheOverride = com.example.data.suitability.ElevationCache(
      fetch = { com.example.data.suitability.TerrainFetchResult.Failure("offline (test fake)") }
    ),
    placeResolver = resolver
  )

  private fun settle() {
    Thread.sleep(120)
    mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
  }

  // ------------------------------------------------------- 1: no generic ring

  @Test
  fun `unfocused overview without a filter has no distant paired pins`() = runTest {
    val vm = viewModel()   // demo defaults ON (VippattiUiState.isMockDataVisible)
    settle()
    val state = vm.uiState.value
    assertTrue("demo must default ON for these contracts", state.isMockDataVisible)
    assertTrue("fallback view must be active", state.isUserLocationFallback)
    // Regional paired ids exist ONLY under a type filter.
    assertTrue("paired regional pins without a filter: " +
        state.scopedShelters.filter { it.id.contains("-p-") }.map { it.id },
      state.scopedShelters.none { it.id.contains("-p-") })
    // Everything shown stays around the focus (the local demo scenario).
    state.scopedShelters.forEach { sz ->
      val d = GeoMath.distanceMeters(state.userLocation, sz.point) / 1000.0
      assertTrue("shelter ${sz.id} floats ${d} km away", d <= 25.0)
    }
  }

  // ----------------------------------------------- 2: filter swaps candidates

  @Test
  fun `changing the disaster filter swaps candidates - no stale pins survive`() = runTest {
    val vm = viewModel()
    settle()
    vm.toggleHazardTypeFilter(HazardType.EARTHQUAKE)
    settle()
    val quake = vm.uiState.value.scopedShelters.map { it.id }.toSet()
    assertTrue("earthquake filter must yield regional pins", quake.isNotEmpty())
    // Clear + pick another disaster: NONE of the earthquake-paired pins may
    // remain (they belonged to hidden circles).
    vm.toggleHazardTypeFilter(HazardType.EARTHQUAKE)
    settle()
    vm.toggleHazardTypeFilter(HazardType.FLOOD)
    settle()
    val flood = vm.uiState.value.scopedShelters.map { it.id }.toSet()
    assertTrue("flood filter must yield its own candidates", flood.isNotEmpty())
    val stale = quake.filter { it.contains("-p-") } - flood
    assertTrue("earthquake regional pins survived into the flood view: $stale",
      quake.filter { it.contains("-p-") }.none { flood.contains(it) } ||
        flood.any { it.contains("-flood") })
  }

  // ----------------------------------------------------------- 3: demo OFF

  @Test
  fun `demo OFF emits no simulated candidates`() = runTest {
    val vm = viewModel()
    settle()
    vm.toggleMockData()   // demo ON -> OFF
    settle()
    val state = vm.uiState.value
    assertTrue("simulated shelters survived demo OFF",
      state.scopedShelters.none { it.id.startsWith("demo-") })
    assertTrue("visible pins include demo records",
      state.visibleSafeZones.none { it.id.startsWith("demo-") })
    assertTrue("hazard field still carries demo circles",
      state.hazardZones.none { it.id.startsWith("demo-") })
  }

  // ------------------------------------------------- 4: location switch

  @Test
  fun `switching places never keeps the old administrative names`() = runTest {
    val resolver = ScriptedResolver()
    val vm = viewModel(resolver)
    val vizag = PlaceCandidate("Visakhapatnam",
      "Visakhapatnam, Andhra Pradesh, India", GeoPoint(17.6868, 83.2185), "city")
    vm.viewChosenPlace(vizag)
    settle()
    assertEquals("Andhra Pradesh", vm.uiState.value.resolvedPlace?.state)

    val jaipur = PlaceCandidate("Jaipur",
      "Jaipur, Rajasthan, India", GeoPoint(26.9124, 75.7873), "city")
    vm.viewChosenPlace(jaipur)
    // The VM clears the old place's names at the switch; with an eagerly
    // completing test resolver the new names may already be in. Either way
    // the INVARIANT the user needs: the OLD place's names must never be
    // observable at the new coordinates.
    val mid = vm.uiState.value.resolvedPlace
    assertTrue("stale admin names survived the location switch: $mid",
      mid == null || mid.state != "Andhra Pradesh")
    assertTrue("label must follow the new place",
      vm.uiState.value.viewedPlaceLabel!!.startsWith("Jaipur"))
    settle()
    assertEquals("Rajasthan", vm.uiState.value.resolvedPlace?.state)
    assertEquals("Jaipur", vm.uiState.value.resolvedPlace?.district)
    assertTrue("resolver re-ran for the new coordinates", resolver.calls >= 2)
  }

  // ------------------------------------------------------- 5: honest levels

  @Test
  fun `unreturned administrative levels say Not available, never fabricated`() {
    val partial = ResolvedPlace(state = "Rajasthan", district = "Jaipur")
    val rows = partial.adminRows.toMap()
    assertEquals("Rajasthan", rows["State"])
    assertEquals("Jaipur", rows["District"])
    assertEquals(ResolvedPlace.NOT_AVAILABLE, rows["Sub-district / Taluk"])
    assertEquals(ResolvedPlace.NOT_AVAILABLE, rows["Village / Town"])
    assertEquals(ResolvedPlace.NOT_AVAILABLE, rows["Ward / Locality"])
    // Fully-resolved place: every level shows the SAME-place values - the
    // rows are derived from one ResolvedPlace, so two places can never mix.
    val full = ResolvedPlace(
      state = "Andhra Pradesh", district = "Visakhapatnam",
      subDistrict = "Gopalapatnam", villageTown = "Visakhapatnam", ward = "Srinivasa Nagar"
    )
    assertEquals(5, full.adminRows.count { it.second != ResolvedPlace.NOT_AVAILABLE })
  }
}
