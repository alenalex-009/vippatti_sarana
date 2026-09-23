package com.example.viewmodel

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.PilotRegionData
import com.example.data.disaster.ProviderResult
import com.example.data.news.GNewsCall
import com.example.data.news.GNewsService
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsRepository
import com.example.data.news.NewsScope
import com.example.data.reports.LocalEmergencyReportService
import com.example.data.suitability.ElevationGrid
import com.example.data.suitability.SuitabilityBand
import com.example.data.suitability.TerrainFetchResult
import com.example.data.suitability.TerrainProbeResult
import com.example.data.suitability.TerrainProbeService
import com.example.data.suitability.TerrainVerdict
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
 * Contracts for the LIVE TERRAIN SELF-ASSESSMENT (red-zone check of the user's
 * own location): explicit action only, honest unavailable state, and the
 * verdict survives until replaced — never cleared silently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TerrainSelfAssessmentTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /** Elevation probe fake: steep stencil -> RED ZONE verdict. */
  private fun steepProbe() = TerrainProbeService(fetch = { url ->
    if (url.contains("/v1/elevation")) {
      TerrainFetchResult.Ok("""{"elevation":[800.0, 830.0, 799.0, 800.5, 800.0]}""")
    } else {
      TerrainFetchResult.Ok("""{"daily":{"precipitation_sum":[0.0, 5.0]}}""")
    }
  })

  /** Flat stencil + rain = SAFE (300 m, no coast input). */
  private fun flatProbe() = TerrainProbeService(fetch = { url ->
    if (url.contains("/v1/elevation")) {
      TerrainFetchResult.Ok("""{"elevation":[300.0, 300.2, 299.9, 300.1, 300.0]}""")
    } else {
      TerrainFetchResult.Ok("""{"daily":{"precipitation_sum":[0.0, 1.0]}}""")
    }
  })

  private fun offlineProbe() = TerrainProbeService(fetch = { TerrainFetchResult.Failure("offline") })

  private fun viewModel(probe: TerrainProbeService) = VippattiViewModel(
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
    terrainProbeOverride = probe
  )

  private class OfflineProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class OfflineNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  @Test
  fun `no assessment exists before the user asks for one`() = runTest {
    val vm = viewModel(flatProbe())
    assertNull(vm.uiState.value.terrainSelfAssessment)
    assertTrue(!vm.uiState.value.isAssessingTerrain)
  }

  @Test
  fun `steep ground at my location is reported as a RED ZONE verdict`() = runTest {
    val danger = PilotRegionData.hazardZones.first()
    val vm = viewModel(steepProbe())
    vm.applyRealGpsFix(danger.center.lat, danger.center.lon)
    vm.assessTerrainHere()
    advanceUntilIdle()
    val a = vm.uiState.value.terrainSelfAssessment
    assertNotNull(a)
    assertTrue("expected SUCCESS got $a", a is TerrainSelfAssessment.Result)
    val v = (a as TerrainSelfAssessment.Result).verdict
    assertEquals(SuitabilityBand.RED_ZONE, v.band)
    assertTrue(!vm.uiState.value.isAssessingTerrain)
  }

  @Test
  fun `a flat safe location assesses SAFE`() = runTest {
    val vm = viewModel(flatProbe())
    vm.assessTerrainHere()
    advanceUntilIdle()
    val a = vm.uiState.value.terrainSelfAssessment as? TerrainSelfAssessment.Result
    assertNotNull(a)
    assertEquals(SuitabilityBand.SAFE, a!!.verdict.band)
  }

  @Test
  fun `offline elevation keeps the state honest - an Unavailable, not a fake SAFE`() = runTest {
    val vm = viewModel(offlineProbe())
    vm.assessTerrainHere()
    advanceUntilIdle()
    val a = vm.uiState.value.terrainSelfAssessment
    assertTrue("expected Unavailable got $a", a is TerrainSelfAssessment.Unavailable)
    assertTrue((a as TerrainSelfAssessment.Unavailable).detail.isNotBlank())
  }

  @Test
  fun `dismiss clears only the assessment`() = runTest {
    val vm = viewModel(flatProbe())
    vm.assessTerrainHere()
    advanceUntilIdle()
    assertNotNull(vm.uiState.value.terrainSelfAssessment)
    vm.dismissTerrainAssessment()
    assertNull(vm.uiState.value.terrainSelfAssessment)
  }
}
