package com.example.viewmodel

import com.example.data.disaster.DisasterCache
import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.ProviderResult
import com.example.data.location.PlaceCandidate
import com.example.data.reports.LocalEmergencyReportService
import com.example.data.location.PlaceSearchResult
import com.example.data.location.PlaceSearcher
import com.example.data.news.GNewsCall
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsRepository
import com.example.data.news.NewsScope
import com.example.data.news.GNewsService
import com.example.data.routing.GeoPoint
import com.example.data.weather.WeatherFailureKind
import com.example.data.weather.WeatherReading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * PLACE VIEW MODE (the "choose a state/city" feature) state machine:
 *  picking a place scopes the app to it and arms a one-shot camera jump;
 *  GPS fixes must NOT yank a deliberate view back to the device position;
 *  exiting restores the previous view honestly.
 */
class PlaceViewModeTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val vizag = PlaceCandidate(
    name = "Visakhapatnam",
    displayName = "Visakhapatnam, Visakhapatnam, Andhra Pradesh, India",
    point = GeoPoint(17.6935, 83.2921),
    kind = "city"
  )

  private class FakeSearcher(private val result: PlaceSearchResult) : PlaceSearcher {
    var lastQuery: String? = null
    override suspend fun search(query: String): PlaceSearchResult {
      lastQuery = query
      return result
    }
  }

  private class OfflineProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class OfflineNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  private fun viewModel(searcher: PlaceSearcher = FakeSearcher(PlaceSearchResult.Found(listOf(vizag)))) =
    VippattiViewModel(
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
      liveRouteFetcher = { _, _, _, _, _, _ -> emptyList() },
      placeSearcher = searcher
    )

  @Test
  fun `typing a query returns candidates and opens the picker flow`() = runTest {
    val vm = viewModel()
    vm.openPlacePicker()
    assertTrue(vm.uiState.value.showPlacePicker)
    vm.setPlaceQuery("vizag")
    val state = vm.uiState.value
    assertFalse(state.isSearchingPlace)
    assertEquals(1, state.placeCandidates.size)
    assertTrue(state.placeCandidates.first().name.startsWith("Visa"))
  }

  @Test
  fun `picking a place scopes the app and arms one camera jump`() = runTest {
    val vm = viewModel()
    vm.openPlacePicker()
    vm.setPlaceQuery("vizag")
    vm.viewChosenPlace(vm.uiState.value.placeCandidates.first())
    val state = vm.uiState.value
    assertTrue(state.isViewingChosenPlace)
    assertFalse(state.showPlacePicker)
    assertEquals(17.6935, state.userLocation.lat, 1e-6)
    assertEquals(83.2921, state.userLocation.lon, 1e-6)
    assertTrue(state.viewedPlaceLabel!!.contains("Andhra Pradesh"))
    assertEquals(state.userLocation.lat, state.cameraJumpTarget!!.lat, 1e-6)
    assertFalse(state.isUserLocationFallback)
    // Derived per-location state is cleared until refreshed for the new place.
    assertNull(state.terrainSelfAssessment)
    // The one-shot is consumed exactly once.
    vm.consumeCameraJump()
    assertNull(vm.uiState.value.cameraJumpTarget)
    assertTrue(vm.uiState.value.isViewingChosenPlace) // still viewing
  }

  @Test
  fun `hardware GPS fixes cannot hijack a chosen-place view`() = runTest {
    val vm = viewModel()
    vm.viewChosenPlace(vizag)
    vm.applyRealGpsFix(13.0827, 80.2707) // a real fix in Chennai
    val state = vm.uiState.value
    assertTrue(state.isViewingChosenPlace)
    assertEquals(17.6935, state.userLocation.lat, 1e-6) // still Vizag
  }

  @Test
  fun `exiting place view restores the previous position and label state`() = runTest {
    val vm = viewModel()
    val before = vm.uiState.value.userLocation
    val beforeFallback = vm.uiState.value.isUserLocationFallback
    vm.viewChosenPlace(vizag)
    vm.exitPlaceView()
    val state = vm.uiState.value
    assertFalse(state.isViewingChosenPlace)
    assertNull(state.viewedPlaceLabel)
    assertEquals(before, state.userLocation)
    assertEquals(beforeFallback, state.isUserLocationFallback)
  }

  @Test
  fun `GPS fixes still work normally when NOT in place view`() = runTest {
    val vm = viewModel()
    vm.applyRealGpsFix(13.0827, 80.2707)
    assertEquals(13.0827, vm.uiState.value.userLocation.lat, 1e-6)
  }

  @Test
  fun `search failure surfaces as honest inline error with no candidates`() = runTest {
    val vm = viewModel(FakeSearcher(PlaceSearchResult.Failure("No place in India matched \"xyz\".")))
    vm.openPlacePicker()
    vm.setPlaceQuery("xyz")
    val state = vm.uiState.value
    assertTrue(state.placeCandidates.isEmpty())
    assertTrue(state.placeSearchError!!.contains("No place in India"))
  }
}
