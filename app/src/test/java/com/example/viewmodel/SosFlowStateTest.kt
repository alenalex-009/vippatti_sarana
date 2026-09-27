package com.example.viewmodel

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.ProviderResult
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * BUG-1 REGRESSION — the Local SOS record flow must fully reset.
 *
 * Reproduction of the reported defect: complete the SOS flow
 * (confirm -> the record dialog appears -> tap "Keep The Local SOS Active").
 * The old `dismissSosDialog()` only closed the dialog and left
 * `isSosActive = true` and `userIsSafe = false` set forever, so MainActivity's
 * global ActiveToolsBar stayed injected above the screen content on every tab —
 * the "Home screen becomes broken after the SOS flow" report.
 *
 * Every path out of the flow (keep/Done, cancel, back dismissal, confirm-dialog
 * dismissal, error) must end in a clean, usable screen state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SosFlowStateTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private fun viewModel() = VippattiViewModel(
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
    // These tests never route: keep the network boundary faked and deterministic.
    liveRouteFetcher = { _, _, _, _, _, _ -> emptyList() }
  )

  private class OfflineProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class OfflineNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  /** Drives the full happy path and returns the ViewModel mid-dialog. */
  private fun completeToRecordDialog(): VippattiViewModel {
    val vm = viewModel()
    vm.triggerSosBroadcast()
    assertTrue(vm.uiState.value.showSosConfirmDialog)

    vm.confirmSosBroadcast()
    mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    assertTrue(vm.uiState.value.showSosBroadcastDialog)
    assertTrue(vm.uiState.value.isSosActive)
    return vm
  }

  private fun assertFlowFullyReset(state: VippattiUiState) {
    assertFalse("confirm dialog must be closed", state.showSosConfirmDialog)
    assertFalse("record dialog must be closed", state.showSosBroadcastDialog)
    assertFalse(
      "SOS flag must reset — a stale flag keeps the global ActiveToolsBar on screen",
      state.isSosActive
    )
    assertTrue("safety switch must not stay armed", state.userIsSafe)
    assertTrue("no report dialog may remain open", state.showSituationReportDialog.not())
  }

  @Test
  fun `completing the flow (keep button) fully resets the home screen state`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = completeToRecordDialog()

    vm.dismissSosDialog() // "Keep The Local SOS Active" / back / outside tap
    advanceUntilIdle()

    assertFlowFullyReset(vm.uiState.value)
    val receipt = vm.uiState.value.lastReportReceipt
    assertNotNull("the saved local record must be reported", receipt)
    assertTrue(receipt!!.accepted)
    assertTrue(receipt.note.contains("THIS DEVICE"))
    // The completion message must summarise the local record, not claim delivery.
    assertFalse(
      "no authority/delivery claim may appear: ${receipt.note}",
      receipt.note.lowercase().contains("ndrf")
    )
    assertEquals(receipt.note, vm.uiState.value.snackbarMessage)
  }

  @Test
  fun `cancelling the flow fully resets the home screen state`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = completeToRecordDialog()

    vm.cancelSosBroadcast()
    advanceUntilIdle()

    assertFlowFullyReset(vm.uiState.value)
    assertTrue(
      "cancel message must say nothing was transmitted: ${vm.uiState.value.snackbarMessage}",
      vm.uiState.value.snackbarMessage!!.contains("canceled")
    )
  }

  @Test
  fun `dismissing the confirm gate arms nothing`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = viewModel()
    vm.triggerSosBroadcast()
    vm.dismissSosConfirmDialog()
    advanceUntilIdle()

    assertFlowFullyReset(vm.uiState.value)
    assertNull(vm.uiState.value.lastReportReceipt)
  }

  @Test
  fun `the active-tools overlay disappears once the flow completes`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = completeToRecordDialog()
    assertTrue("overlay must be visible while the record is armed", vm.uiState.value.isSosActive)

    vm.dismissSosDialog()
    advanceUntilIdle()

    assertTrue(
      "overlay must be gone after completion — this is what broke the home screen",
      vm.uiState.value.hasActiveDeviceTool.not() && vm.uiState.value.isSosActive.not()
    )
  }

  @Test
  fun `the SOS dialog can never leave an armed state after dismissal`() = runTest(mainDispatcherRule.dispatcher) {
    val vm = completeToRecordDialog()
    // Simulate a rapid double-completion (Keep then system back) — the state must
    // stay reset, never re-arm.
    vm.dismissSosDialog()
    vm.dismissSosDialog()
    advanceUntilIdle()
    assertFlowFullyReset(vm.uiState.value)
  }
}