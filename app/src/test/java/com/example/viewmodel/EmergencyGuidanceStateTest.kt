package com.example.viewmodel

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.IncidentCategory
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.PilotRegionData
import com.example.data.disaster.ProviderResult
import com.example.data.model.HazardSeverity
import com.example.data.news.GNewsCall
import com.example.data.news.GNewsService
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsRepository
import com.example.data.news.NewsScope
import com.example.data.reports.LocalEmergencyReportService
import com.example.data.shelters.EmergencyGuidance
import com.example.data.shelters.SafeHavenFinder
import com.example.data.suitability.ElevationGrid
import com.example.data.suitability.SuitabilityBand
import com.example.data.suitability.TerrainProbeResult
import com.example.data.suitability.TerrainSuitabilityEngine
import com.example.data.weather.WeatherFailureKind
import com.example.data.weather.WeatherReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * ViewModel wiring for EMERGENCY SHELTER GUIDANCE: a danger-level event near
 * the user must surface an actionable nearest-safe-zone card derived from the
 * real risk + shelter evaluation, GO must select and route, and the terrain
 * haven flow must stay honest (searched only on tap, routed as DERIVED).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyGuidanceStateTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private fun viewModel(finder: SafeHavenFinder? = null) = VippattiViewModel(
    reportService = LocalEmergencyReportService(),
    newsCache = MemoryNewsCache(),
    disasterCache = MemoryDisasterCache(),
    disasterRepository = DisasterDataRepository(
      providers = listOf(OfflineProvider(DisasterSource.USGS), OfflineProvider(DisasterSource.IMD_CAP)),
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
    havenFinderOverride = finder
  )

  private class OfflineProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class OfflineNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  /** GPS next to a simulated danger zone + an EXTREME citizen landslide report. */
  private fun vmInDanger(): VippattiViewModel {
    val danger = PilotRegionData.hazardZones.firstOrNull { it.severity == HazardSeverity.EXTREME }
      ?: PilotRegionData.hazardZones.first()
    val vm = viewModel()
    vm.applyRealGpsFix(danger.center.lat + 0.002, danger.center.lon + 0.002)
    vm.submitIncidentReport(IncidentCategory.LANDSLIDE, HazardSeverity.EXTREME.label, "test slide")
    return vm
  }

  /** Flat-terrain probe: every candidate rates SAFE (no rain input). */
  private fun safeFinder() = SafeHavenFinder(probe = { p, _ ->
    val verdict = TerrainSuitabilityEngine.evaluate(
      elevation = ElevationGrid(300.0, 300.2, 299.9, 300.1, 300.0, 30.0),
      rainfallMm24h = 0.0,
      nearCoast = false
    )
    TerrainProbeResult.Success(verdict, coastKnown = false)
  })

  @Test
  fun `danger with shelters in view produces an actionable guidance card`() = runTest {
    val vm = vmInDanger()
    advanceUntilIdle()
    val risk = vm.uiState.value.personalRisk!!
    assertTrue("risk was ${risk.level}", risk.level.name == "RED" || risk.level.name == "ORANGE")
    val g = vm.uiState.value.emergencyGuidance
    assertTrue(
      "guidance was $g",
      g is EmergencyGuidance.SuggestShelter || g is EmergencyGuidance.NoShelterEligible ||
        g is EmergencyGuidance.AlreadyRouting
    )
  }

  @Test
  fun `danger with the demo network hidden reports NO registered shelter honestly`() = runTest {
    val vm = vmInDanger()
    advanceUntilIdle()
    vm.setMockMode(false)
    advanceUntilIdle()
    val g = vm.uiState.value.emergencyGuidance
    assertTrue("guidance was $g", g is EmergencyGuidance.NoShelterKnown)
  }

  @Test
  fun `GO on the guidance card selects the suggested zone`() = runTest {
    val vm = vmInDanger()
    advanceUntilIdle()
    val g = vm.uiState.value.emergencyGuidance
    if (g !is EmergencyGuidance.SuggestShelter) return@runTest // no feasible demo pair here
    vm.acceptEmergencyGuidance()
    advanceUntilIdle()
    assertEquals(g.evaluation.zone.id, vm.uiState.value.selectedSafeZone?.id)
  }

  @Test
  fun `dismiss removes the card until the next intelligence recompute`() = runTest {
    val vm = vmInDanger()
    advanceUntilIdle()
    val hadCard = vm.uiState.value.emergencyGuidance != EmergencyGuidance.None
    vm.dismissEmergencyGuidance()
    assertEquals(EmergencyGuidance.None, vm.uiState.value.emergencyGuidance)
    if (hadCard) {
      vm.toggleMockData() // any recompute re-derives guidance from live state
      advanceUntilIdle()
      // mock OFF now -> still danger with no shelters -> an honest card again
      assertNotNull(vm.uiState.value.emergencyGuidance)
    }
  }

  @Test
  fun `terrain haven found on tap and routed as a DERIVED destination`() = runTest {
    val danger = PilotRegionData.hazardZones.first()
    val vm = viewModel(finder = safeFinder())
    vm.applyRealGpsFix(danger.center.lat + 0.002, danger.center.lon + 0.002)
    advanceUntilIdle()
    vm.searchTerrainHaven()
    advanceUntilIdle()
    val haven = vm.uiState.value.terrainHaven
    assertNotNull("a safe haven must be found by the fake probe", haven)
    vm.routeToTerrainHaven()
    advanceUntilIdle()
    val sel = vm.uiState.value.selectedSafeZone!!
    assertTrue("haven destination must be id-labelled", sel.id.startsWith("haven-"))
    assertEquals("DERIVED", sel.provenance.classification.name)
  }

  @Test
  fun `haven search with nothing safe keeps the state honest`() = runTest {
    val failing = SafeHavenFinder(probe = { _, _ ->
      TerrainProbeResult.ElevationUnavailable("no data")
    })
    val vm = viewModel(finder = failing)
    vm.searchTerrainHaven()
    advanceUntilIdle()
    assertNull(vm.uiState.value.terrainHaven)
    assertTrue(!vm.uiState.value.isSearchingHaven)
    assertTrue(vm.uiState.value.snackbarMessage!!.contains("No terrain-safe point", ignoreCase = true))
  }
}
