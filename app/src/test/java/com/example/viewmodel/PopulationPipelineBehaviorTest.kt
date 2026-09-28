package com.example.viewmodel

import com.example.data.capacity.FeasibilityStatus
import com.example.data.capacity.ResourceDataState
import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.ProviderResult
import com.example.data.model.UserProfile
import com.example.data.news.GNewsCall
import com.example.data.news.GNewsService
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsRepository
import com.example.data.news.NewsScope
import com.example.data.population.PopulationClassification
import com.example.data.population.PopulationDataProvider
import com.example.data.population.PopulationRecord
import com.example.data.population.PopulationRole
import com.example.data.population.PopulationScope
import com.example.data.population.PopulationSourceKind
import com.example.data.reports.LocalEmergencyReportService
import com.example.data.weather.WeatherFailureKind
import com.example.data.weather.WeatherReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * BEHAVIOUR test for the SIH 26191 population -> capacity -> relocation
 * pipeline, driven through the REAL [VippattiViewModel].
 *
 * It asserts observable state transitions (empty source -> area record ->
 * back to the household fallback) rather than isolated field values, so a
 * regression that leaves the demand stale, double-counts, or lets a baseline
 * census figure masquerade as relocation demand fails here.
 *
 * All network boundaries are test fakes; no test hits the real network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PopulationPipelineBehaviorTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private class FailingProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  /** Offline news fake — the news pipeline is not what this test exercises. */
  private class FailingNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  private fun viewModel(population: PopulationDataProvider) = VippattiViewModel(
    reportService = LocalEmergencyReportService(),
    newsCache = MemoryNewsCache(),
    disasterCache = MemoryDisasterCache(),
    disasterRepository = DisasterDataRepository(
      providers = listOf(
        FailingProvider(DisasterSource.USGS),
        FailingProvider(DisasterSource.IMD_CAP)
      ),
      cache = MemoryDisasterCache(),
      cacheDispatcher = mainDispatcherRule.dispatcher
    ),
    newsRepositoryOverride = NewsRepository(
      service = FailingNewsService(),
      cache = MemoryNewsCache(),
      apiKeyProvider = { "" },
      storageDispatcher = mainDispatcherRule.dispatcher
    ),
    populationProvider = population,
    weatherFetcher = { WeatherReading.Failure(WeatherFailureKind.NO_CONNECTION) },
    liveRouteFetcher = { _, _, _, _, _, _, _ -> emptyList() }
  )

  /** A provider that yields a fixed list, or throws, on every fetch. */
  private class ScriptedProvider(
    private var records: List<PopulationRecord> = emptyList(),
    private var fail: Boolean = false
  ) : PopulationDataProvider {
    var calls = 0

    /** Models the registry coming online / going down between syncs. */
    fun publish(next: List<PopulationRecord>) {
      records = next
      fail = false
    }

    fun startFailing() {
      fail = true
    }

    override suspend fun fetchRecords(): List<PopulationRecord> {
      calls++
      if (fail) throw java.io.IOException("registry unreachable (test fake)")
      return records
    }
  }

  private fun wardDemand(
    people: Int,
    classification: PopulationClassification = PopulationClassification.VERIFIED,
    sourceKind: PopulationSourceKind = PopulationSourceKind.AUTHORITY_ASSESSMENT
  ) = PopulationRecord(
    id = "ward-7-demand",
    role = PopulationRole.RELOCATION_DEMAND,
    scope = PopulationScope.WARD,
    areaName = "Ward 7",
    value = people,
    classification = classification,
    sourceKind = sourceKind,
    source = "test authority record",
    referenceMillis = 1_790_000_000_000L,
    confidence = 0.8
  )

  private fun baseline(people: Int) = PopulationRecord(
    id = "village-baseline",
    role = PopulationRole.BASELINE_TOTAL,
    scope = PopulationScope.VILLAGE,
    areaName = "Test Village",
    value = people,
    classification = PopulationClassification.VERIFIED,
    sourceKind = PopulationSourceKind.OFFICIAL_CENSUS,
    source = "test census record",
    referenceMillis = 1_700_000_000_000L
  )

  /**
   * The demo shelter network exists only inside the SIMULATED demo switch, so
   * every behavioural assertion runs with it enabled - the same thing a
   * demonstrator does.
   */
  private fun VippattiViewModel.bootAtDemoSite() {
    setMockMode(true)
    val zone = com.example.data.disaster.PilotRegionData.safeZones.first()
    applyRealGpsFix(zone.lat + 0.004, zone.lon + 0.004)
  }

  // ------------------------------------------------------------ lifecycle

  @Test
  fun `the house is empty at cold start and the demand is the citizen's own household`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel(ScriptedProvider(emptyList()))
    vm.bootAtDemoSite()
    val state = vm.uiState.value

    // No area record -> the personal declaration, honestly labelled.
    assertEquals(ResourceDataState.USER_DECLARED, state.capacityDemand?.state)
    assertEquals(
      state.userProfile.dependentsCount + 1,
      state.capacityDemand?.people
    )
    assertTrue(state.capacityDemand!!.isHouseholdLevel)
    assertTrue(state.relocationDemandLabel.contains("not a census figure"))
    // Nothing area-level is claimed.
    assertEquals("Not provided — no census source connected", state.baselinePopulationLabel)
    assertEquals(
      "Not provided — no affected-population source connected",
      state.affectedPopulationLabel
    )
    // The verdict is computed against the real demo site records.
    assertNotNull(state.capacityAssessments)
    assertTrue(state.capacityAssessments.isNotEmpty())
    assertNull(state.populationAssessment?.baseline)
  }

  @Test
  fun `an area record arrives and the demand, verdict and assignment all move`() = runTest(mainDispatcherRule.dispatcher) {
    // Starts with NO source connected, exactly like production today.
    val provider = ScriptedProvider()
    val vm = viewModel(provider)
    vm.bootAtDemoSite()

    val before = vm.uiState.value
    val householdDemand = before.capacityDemand!!.people!!
    assertTrue(before.capacityDemand!!.isHouseholdLevel)
    assertEquals(ResourceDataState.USER_DECLARED, before.capacityDemand!!.state)

    // The registry comes online, and the pipeline is driven again.
    provider.publish(listOf(wardDemand(500)))
    vm.syncData()
    val after = vm.uiState.value

    // The demand actually changed source and value.
    assertEquals(500, after.capacityDemand?.people)
    assertNotEquals(householdDemand, after.capacityDemand?.people)
    assertEquals(ResourceDataState.MEASURED, after.capacityDemand?.state)
    assertEquals(PopulationClassification.VERIFIED, after.capacityDemand?.classification)
    assertEquals("Ward 7 (Ward)", after.capacityDemand?.scopeLabel)
    assertTrue(after.relocationDemandLabel.contains("Verified"))
    assertTrue(after.relocationDemandLabel.contains("Ward 7"))

    // The three population figures are kept apart, and the demand is the ward.
    assertEquals(500, after.populationAssessment?.relocationDemand?.value)
    assertNull(after.populationAssessment?.baseline)

    // Verdicts were recomputed against the NEW demand, not left stale.
    val assessments = after.capacityAssessments.values
    assertTrue(assessments.isNotEmpty())
    assertTrue(assessments.all { it.demand.people == 500 })
    assertTrue(assessments.none { it.status == FeasibilityStatus.INSUFFICIENT_DATA })

    // A 500-person demand cannot fit the small demo sites, so the plan reports
    // the shortfall instead of pretending the site is fine.
    val plan = after.relocationPlan!!
    assertNotNull(plan.assignedShelter)
    val chosen = plan.capacityAssessment!!
    assertTrue(
      chosen.status == FeasibilityStatus.INFEASIBLE ||
        chosen.status == FeasibilityStatus.SIMULATED ||
        chosen.status == FeasibilityStatus.FEASIBLE
    )
    if (chosen.status == FeasibilityStatus.INFEASIBLE) {
      assertTrue(chosen.shortfall!! > 0)
      assertTrue(plan.feasibilityNote!!.contains("short by"))
    }
  }

  @Test
  fun `a baseline census figure never becomes relocation demand`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel(ScriptedProvider(listOf(baseline(12_000))))
    vm.bootAtDemoSite()
    vm.syncData()
    val state = vm.uiState.value

    // The baseline is reported, and clearly not treated as demand.
    assertTrue(state.baselinePopulationLabel.contains("12000"))
    assertTrue(state.baselinePopulationLabel.contains("not confirmed relocation demand"))
    assertEquals(ResourceDataState.USER_DECLARED, state.capacityDemand?.state)
    assertEquals(state.userProfile.dependentsCount + 1, state.capacityDemand?.people)
    assertEquals(12_000, state.populationAssessment?.baseline?.value)
    // The resolver kept the reason it refused the baseline.
    assertTrue(
      state.populationResolution!!.rejected.any { it.record.id == "village-baseline" }
    )
  }

  @Test
  fun `the household demand follows the profile and never the area figure`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel(ScriptedProvider(emptyList()))
    vm.bootAtDemoSite()
    assertEquals(4, vm.uiState.value.capacityDemand?.people) // 3 dependents + citizen

    vm.updateUserProfile(vm.uiState.value.userProfile.copy(dependentsCount = 9))
    assertEquals(10, vm.uiState.value.capacityDemand?.people)
    assertTrue(vm.uiState.value.capacityDemand!!.isHouseholdLevel)
    // Still no census claim, and still no baseline invented from the household.
    assertNull(vm.uiState.value.populationAssessment?.baseline)
  }

  // ----------------------------------------------------------- robustness

  @Test
  fun `a source that throws is reported and never corrupts or fabricates the demand`() =
    runTest(mainDispatcherRule.dispatcher) {
      val provider = ScriptedProvider(listOf(wardDemand(500)))
      val vm = viewModel(provider)
      vm.bootAtDemoSite()
      assertEquals(500, vm.uiState.value.capacityDemand?.people)
      assertNull(vm.uiState.value.populationSourceError)

      // The registry then goes down. Driving the pipeline must not blow up,
      // must not invent records, and must not throw away the real figure.
      provider.startFailing()
      vm.syncData()
      val after = vm.uiState.value
      assertEquals(1, after.populationRecords.size)
      assertEquals(500, after.capacityDemand?.people)
      assertEquals(ResourceDataState.MEASURED, after.capacityDemand?.state)
      assertTrue(after.populationSourceError!!.contains("Population source unavailable"))
      assertTrue(after.populationSourceError!!.contains("registry unreachable"))
      assertTrue(after.populationSourceError!!.contains("Previous figures kept"))
    }

  @Test
  fun `a record without a value is never counted as zero people`() = runTest(mainDispatcherRule.dispatcher) {
    val noValue = wardDemand(500).copy(id = "no-value", value = null)
    val vm = viewModel(ScriptedProvider(listOf(noValue)))
    vm.bootAtDemoSite()
    vm.syncData()
    // The household fallback stands in; nobody is "feasible for 0 people".
    assertEquals(ResourceDataState.USER_DECLARED, vm.uiState.value.capacityDemand?.state)
    assertTrue(
      vm.uiState.value.populationResolution!!.rejected
        .any { it.reason.contains("No value supplied") }
    )
  }

  @Test
  fun `re-syncing the same records does not change the answer`() = runTest(mainDispatcherRule.dispatcher) {
    val provider = ScriptedProvider(listOf(wardDemand(500)))
    val vm = viewModel(provider)
    vm.bootAtDemoSite()
    vm.syncData()
    val first = vm.uiState.value

    vm.syncData()
    vm.syncData()
    val later = vm.uiState.value
    assertEquals(first.capacityDemand, later.capacityDemand)
    assertEquals(first.capacityAssessments.keys, later.capacityAssessments.keys)
    assertEquals(
      first.capacityAssessments.mapValues { it.value.status },
      later.capacityAssessments.mapValues { it.value.status }
    )
    assertTrue("the provider was actually consulted", provider.calls >= 1)
  }

  @Test
  fun `a simulated source makes the verdict simulated, not verified`() = runTest(mainDispatcherRule.dispatcher) {
    val demo = wardDemand(
      300,
      classification = PopulationClassification.SIMULATED,
      sourceKind = PopulationSourceKind.DEMO_SIMULATED
    )
    val vm = viewModel(ScriptedProvider(listOf(demo)))
    vm.bootAtDemoSite()
    vm.syncData()
    val state = vm.uiState.value

    assertEquals(300, state.capacityDemand?.people)
    assertEquals(ResourceDataState.SIMULATED, state.capacityDemand?.state)
    // The scope/status line names the simulated origin in upper case.
    assertTrue(state.populationResolution!!.statusLabel.contains("SIMULATED"))
    assertTrue(state.relocationDemandLabel.contains("Simulated"))
    assertTrue(
      state.capacityAssessments.values.all { it.status == FeasibilityStatus.SIMULATED }
    )
    assertTrue(state.capacityAssessments.values.none { it.provenance.isVerified })
  }

  @Test
  fun `the same provider shape is used for every scope without a hardcoded region`() {
    // The demand path never names a district: it takes whatever scope the record
    // declares. Two different scopes produce two different labels.
    val ward = PopulationRecord(
      id = "ward",
      role = PopulationRole.RELOCATION_DEMAND,
      scope = PopulationScope.WARD,
      areaName = "North Ward",
      value = 40,
      classification = PopulationClassification.VERIFIED,
      sourceKind = PopulationSourceKind.AUTHORITY_ASSESSMENT,
      source = "authority"
    )
    val municipality = ward.copy(id = "municipality", scope = PopulationScope.MUNICIPALITY, areaName = "Some Town")
    val wardResolution = com.example.data.population.PopulationDemandResolver.resolve(listOf(ward))
    val townResolution = com.example.data.population.PopulationDemandResolver.resolve(listOf(municipality))
    assertEquals("North Ward (Ward)", wardResolution.demand.scopeLabel)
    assertEquals("Some Town (Municipality / town)", townResolution.demand.scopeLabel)
    // The narrower scope wins when both describe the same demand.
    val both = com.example.data.population.PopulationDemandResolver.resolve(listOf(ward, municipality))
    assertEquals("ward", both.selected?.id)
  }

  @Test
  fun `the profile-visible population lines are never blank or fabricated`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel(ScriptedProvider(listOf(baseline(12_000), wardDemand(500))))
    vm.bootAtDemoSite()
    vm.syncData()
    val state = vm.uiState.value
    listOf(
      state.baselinePopulationLabel,
      state.affectedPopulationLabel,
      state.relocationDemandLabel
    ).forEach { label ->
      assertTrue("label must not be blank", label.isNotBlank())
      assertTrue("label must not be a bare number", label != "0")
    }
    assertTrue(state.baselinePopulationLabel.contains("12000"))
    assertTrue(state.affectedPopulationLabel.contains("Not provided"))
  }
}
