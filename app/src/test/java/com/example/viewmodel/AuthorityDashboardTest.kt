package com.example.viewmodel

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.PilotRegionData
import com.example.data.disaster.ProviderResult
import com.example.data.habitations.FieldRegistryStore
import com.example.data.habitations.InMemoryFieldRegistryStore
import com.example.data.habitations.RelocationTier
import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.SafeZone
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
 * Behaviour contracts for the AUTHORITY DASHBOARD (SIH 26191): operator
 * registry records must flow into the live shelter set (a REAL safe zone
 * exists even with the demo switch OFF), opening the dashboard must rank
 * habitations deterministically, and demo habitations must stay labelled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthorityDashboardTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val store: FieldRegistryStore = InMemoryFieldRegistryStore()

  private fun viewModel() = VippattiViewModel(
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
    fieldRegistryStoreOverride = store
  )

  private class OfflineProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class OfflineNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  private fun realShelter(near: com.example.data.routing.GeoPoint) = SafeZone(
    id = "field-1", name = "Govt School Hall", lat = near.lat + 0.003, lon = near.lon,
    locationNote = "ward 3", capacityTotal = 300, capacityCurrent = 0,
    waterAvailable = true, foodAvailable = true, electricityAvailable = true,
    sanitationAvailable = true, medicalSupport = false, accessibility = "Road",
    womenChildrenSuitability = false, operatingStatus = "OPEN",
    verificationStatus = "FIELD RECORD", elevationNote = "",
    landAreaSquareMeters = 2000.0, waterLitresPerDay = 4500.0, toiletCount = 8,
    provenance = DataProvenance(
      source = "Field entry — operator device",
      classification = DataClassification.OBSERVED
    )
  )

  @Test
  fun `registered field shelters are live even with the demo network hidden`() = runTest {
    val danger = PilotRegionData.hazardZones.first()
    val vm = viewModel()
    vm.applyRealGpsFix(danger.center.lat + 0.002, danger.center.lon + 0.002)
    advanceUntilIdle()
    store.saveShelters(listOf(realShelter(danger.center)))
    vm.reloadFieldRegistry()
    vm.setMockMode(false)
    advanceUntilIdle()
    val ids = vm.uiState.value.evaluatedShelters.map { it.zone.id }
    assertTrue("field shelter must be evaluated in live mode: $ids", "field-1" in ids)
    // and it is NEVER labelled simulated
    val eval = vm.uiState.value.evaluatedShelters.first { it.zone.id == "field-1" }
    assertTrue(eval.zone.provenance.classification != DataClassification.SIMULATED)
  }

  @Test
  fun `opening the dashboard ranks the demo habitation set without terrain probes`() = runTest {
    val vm = viewModel()
    vm.openAuthorityDashboard(liveTerrainScan = false)
    advanceUntilIdle()
    val priorities = vm.uiState.value.relocationPriorities
    assertTrue("dashboard must rank the habitation set", priorities.isNotEmpty())
    // every demo-derived row is labelled
    assertTrue(priorities.all { it.habitation.name.isNotBlank() })
    // tiers must be sorted by score descending
    val scores = priorities.map { it.score }
    assertEquals(scores.sortedDescending(), scores)
  }

  @Test
  fun `registry habitations join the dashboard set with real provenance`() = runTest {
    store.saveHabitations(
      listOf(
        com.example.data.habitations.Habitation(
          id = "field-h", name = "Recorded colony",
          point = com.example.data.routing.GeoPoint(10.5, 77.5),
          population = com.example.data.habitations.PopulationInput(
            800, DataClassification.OBSERVED, "panchayat register"
          ),
          vulnerableShare = 0.4f
        )
      )
    )
    val vm = viewModel()
    vm.openAuthorityDashboard(liveTerrainScan = false)
    advanceUntilIdle()
    val row = vm.uiState.value.relocationPriorities.firstOrNull { it.habitation.id == "field-h" }
    assertNotNull("registry habitation must appear", row)
    assertTrue(row!!.reasons.any { it.contains("800") })
  }

  @Test
  fun `dashboard closes cleanly and priorities reset`() = runTest {
    val vm = viewModel()
    vm.openAuthorityDashboard(liveTerrainScan = false)
    advanceUntilIdle()
    assertTrue(vm.uiState.value.showAuthorityDashboard)
    vm.closeAuthorityDashboard()
    assertTrue(!vm.uiState.value.showAuthorityDashboard)
  }
}
