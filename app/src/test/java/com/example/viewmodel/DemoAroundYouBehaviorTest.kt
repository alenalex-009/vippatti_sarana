package com.example.viewmodel

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.ProviderResult
import com.example.data.risk.RiskLevel
import com.example.data.news.GNewsCall
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsRepository
import com.example.data.news.NewsScope
import com.example.data.news.GNewsService
import com.example.data.shelters.EmergencyGuidance
import com.example.data.weather.WeatherFailureKind
import com.example.data.weather.WeatherReading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * THE headline contract for demo mode (the user's #2 complaint):
 * a GPS fix anywhere in India, with the DEMO switch on, must honestly show
 * danger around the user AND at least one eligible safe zone with the
 * guidance card ready to route — no far-away-only demo network anymore.
 */
class DemoAroundYouBehaviorTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private class OfflineProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class OfflineNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  private fun viewModel() = VippattiViewModel(
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
    liveRouteFetcher = { _, _, _, _, _, _ -> emptyList() }
  )

  @Test
  fun `GPS fix with demo ON produces personal danger at that place`() = runTest {
    val vm = viewModel()
    // demo switch defaults ON (isMockDataVisible = true in VippattiUiState)
    vm.applyRealGpsFix(17.6935, 83.2921) // Visakhapatnam
    val state = vm.uiState.value
    val risk = state.personalRisk
    assertNotNull("risk must be assessed", risk)
    assertTrue(
      "demo hazard around the GPS fix must raise ORANGE/RED, got ${risk!!.level}",
      risk.level == RiskLevel.ORANGE || risk.level == RiskLevel.RED
    )
  }

  @Test
  fun `danger shows an eligible safe zone and the GO guidance card`() = runTest {
    val vm = viewModel()
    vm.applyRealGpsFix(9.9312, 76.2673) // Kochi
    val state = vm.uiState.value
    assertTrue(
      "the demo shelter opposite the hazard must rank as feasible",
      state.rankedShelters.isNotEmpty()
    )
    val guidance = state.emergencyGuidance
    assertTrue(
      "guidance must suggest a shelter, got $guidance",
      guidance is EmergencyGuidance.SuggestShelter
    )
    val suggestion = (guidance as EmergencyGuidance.SuggestShelter)
    assertTrue(suggestion.evaluation.zone.id.startsWith("demo-sz-"))
  }

  @Test
  fun `demo records stay honestly labelled SIMULATED`() = runTest {
    val vm = viewModel()
    vm.applyRealGpsFix(17.6935, 83.2921)
    val state = vm.uiState.value
    val demoHazard = state.hazardZones.first { it.id.startsWith("demo-hz-") }
    assertTrue(demoHazard.sourceStatus.contains("DEMO"))
    val demoShelter = state.evaluatedShelters.first { it.zone.id.startsWith("demo-sz-") }
    assertTrue(demoShelter.zone.verificationStatus.contains("SIMULATED"))
  }

  @Test
  fun `demo OFF removes the generated network - live feeds untouched`() = runTest {
    val vm = viewModel()
    vm.applyRealGpsFix(17.6935, 83.2921)
    assertTrue(vm.uiState.value.hazardZones.any { it.id.startsWith("demo-hz-") })
    vm.setMockMode(false)
    val state = vm.uiState.value
    assertTrue(state.hazardZones.none { it.id.startsWith("demo-hz-") })
    assertTrue(state.evaluatedShelters.none { it.zone.id.startsWith("demo-sz-") })
  }

  @Test
  fun `focused places see ONLY their local demo network, not other states`() = runTest {
    val vm = viewModel()
    vm.applyRealGpsFix(17.6935, 83.2921) // Vizag
    val state = vm.uiState.value
    // The India-wide 14-district pilot set must NOT appear once focused...
    val pilotOnly = state.hazardZones.filter { it.id.startsWith("hz-") }
    org.junit.Assert.assertTrue(
      "other districts' demo zones leaked into a focused view: $pilotOnly",
      pilotOnly.isEmpty()
    )
    // ...and the ONLY demo hazard is the local one covering the user.
    org.junit.Assert.assertTrue(
      state.hazardZones.any { it.id.startsWith("demo-hz-") }
    )
    val farShelters = state.evaluatedShelters.filter { it.zone.id.startsWith("sz-") }
    org.junit.Assert.assertTrue(
      "far-state demo shelters leaked into a focused view",
      farShelters.isEmpty()
    )
  }

  @Test
  fun `unfocused fallback keeps the India-wide demo set (nothing local to scope)`() = runTest {
    val vm = viewModel()
    // no GPS fix: fallback state
    org.junit.Assert.assertTrue(vm.uiState.value.isUserLocationFallback)
    val state = vm.uiState.value
    org.junit.Assert.assertTrue(
      "fallback view should still show the India-wide demo network",
      state.hazardZones.any { it.id.startsWith("hz-") }
    )
  }
}
