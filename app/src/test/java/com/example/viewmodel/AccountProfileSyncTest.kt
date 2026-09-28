package com.example.viewmodel

import com.example.data.auth.AuthRepository
import com.example.data.auth.InMemoryAuthStorage
import com.example.data.auth.RegistrationRequest
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
import com.example.data.reports.LocalEmergencyReportService
import com.example.data.weather.WeatherFailureKind
import com.example.data.weather.WeatherReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The account -> Profile data flow, driven through the REAL
 * [VippattiViewModel] and the REAL [AuthRepository] (in-memory store).
 *
 * What this guards:
 *  - the Profile tab's state comes from the SIGNED-IN account's stored profile;
 *  - an account that never saved a profile gets a BLANK profile, never the
 *    model's legacy sample identity and never another user's values;
 *  - switching accounts replaces the identity (no leakage between accounts);
 *  - a Profile-editor save is persisted to that account and read back on the
 *    next launch;
 *  - signing out clears the identity from the UI state.
 *
 * All network boundaries are test fakes; no test hits the real network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountProfileSyncTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private class FailingProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class FailingNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  private fun viewModel(repository: AuthRepository) = VippattiViewModel(
    reportService = LocalEmergencyReportService(),
    newsCache = MemoryNewsCache(),
    disasterCache = MemoryDisasterCache(),
    disasterRepository = DisasterDataRepository(
      providers = listOf(FailingProvider(DisasterSource.USGS)),
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
    liveRouteFetcher = { _, _, _, _, _, _, _ -> emptyList() },
    elevationCacheOverride = com.example.data.suitability.ElevationCache(
      fetch = { com.example.data.suitability.TerrainFetchResult.Failure("offline (test fake)") }
    ),
    accountRepository = repository
  )

  private fun filledProfile(name: String, citizenId: String, phone: String) = UserProfile(
    fullName = name,
    citizenId = citizenId,
    phone = phone,
    bloodGroup = "O-",
    medicalTag = "Asthma / Inhaler",
    medicalNotes = "Carries an inhaler",
    dependentsCount = 1,
    dependentsDetail = "1 Elder",
    vulnerableCategoryIds = setOf("elderly"),
    needsMedicalSupport = false
  )

  @Test
  fun `a signed-in account opens on its own stored profile`() {
    val repository = AuthRepository(InMemoryAuthStorage())
    repository.register(
      RegistrationRequest(
        "meera@example.com",
        "longenough7",
        staySignedIn = true,
        profile = filledProfile("Meera Krishnan", "SARANA-AP-10231", "+91 98470 11223")
      )
    )

    val vm = viewModel(repository)

    assertEquals("Meera Krishnan", vm.uiState.value.userProfile.fullName)
    assertEquals("SARANA-AP-10231", vm.uiState.value.userProfile.citizenId)
    assertEquals("+91 98470 11223", vm.uiState.value.userProfile.phone)
    assertEquals("O-", vm.uiState.value.userProfile.bloodGroup)
  }

  @Test
  fun `an account with no stored profile opens BLANK, never on a sample identity`() {
    val repository = AuthRepository(InMemoryAuthStorage()).apply { seedDemoAccount() }
    assertTrue(
      repository.login(AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = true).ok
    )

    val vm = viewModel(repository)

    assertEquals(UserProfile.blank(), vm.uiState.value.userProfile)
    assertNotEquals("Aditya Vardhan", vm.uiState.value.userProfile.fullName)
    assertEquals("", vm.uiState.value.userProfile.bloodGroup)
  }

  @Test
  fun `signing in loads that account, and signing out clears the identity`() {
    val storage = InMemoryAuthStorage()
    val repository = AuthRepository(storage)
    repository.register(
      RegistrationRequest(
        "meera@example.com",
        "longenough7",
        staySignedIn = true,
        profile = filledProfile("Meera Krishnan", "SARANA-AP-10231", "+91 98470 11223")
      )
    )
    repository.logout()
    val vm = viewModel(repository)
    // Signed out: nothing of the previous session is left on the Profile tab.
    assertEquals(UserProfile.blank(), vm.uiState.value.userProfile)

    repository.login("meera@example.com", "longenough7", staySignedIn = true)
    vm.syncSignedInAccountProfile()
    assertEquals("Meera Krishnan", vm.uiState.value.userProfile.fullName)

    repository.logout()
    vm.syncSignedInAccountProfile()
    assertEquals("", vm.uiState.value.userProfile.fullName)
  }

  @Test
  fun `switching accounts replaces the profile instead of merging it`() {
    val repository = AuthRepository(InMemoryAuthStorage())
    repository.register(
      RegistrationRequest(
        "meera@example.com",
        "longenough7",
        staySignedIn = true,
        profile = filledProfile("Meera Krishnan", "SARANA-AP-10231", "+91 98470 11223")
      )
    )
    val vm = viewModel(repository)
    assertEquals("Meera Krishnan", vm.uiState.value.userProfile.fullName)

    // Account B registers with its OWN profile values.
    repository.register(
      RegistrationRequest(
        "anand@example.com",
        "longenough7",
        staySignedIn = true,
        profile = UserProfile.blank().copy(
          fullName = "Anand Rao",
          citizenId = "SARANA-AP-55001",
          phone = "+91 90000 12345"
        )
      )
    )
    vm.syncSignedInAccountProfile()

    assertEquals("Anand Rao", vm.uiState.value.userProfile.fullName)
    assertEquals("SARANA-AP-55001", vm.uiState.value.userProfile.citizenId)
    assertEquals("+91 90000 12345", vm.uiState.value.userProfile.phone)
    // Account A's medical/vulnerable data must NOT be inherited.
    assertEquals("", vm.uiState.value.userProfile.bloodGroup)
    assertEquals("", vm.uiState.value.userProfile.medicalTag)
    assertEquals(emptySet<String>(), vm.uiState.value.userProfile.vulnerableCategoryIds)
  }

  @Test
  fun `a Profile edit is persisted for the signed-in account and read back later`() {
    val storage = InMemoryAuthStorage()
    val repository = AuthRepository(storage)
    repository.register(
      RegistrationRequest(
        "meera@example.com",
        "longenough7",
        staySignedIn = true,
        profile = UserProfile.blank().copy(fullName = "Meera Krishnan")
      )
    )
    val vm = viewModel(repository)

    // Fill the optional fields later from the Profile editor.
    vm.updateUserProfile(
      vm.uiState.value.userProfile.copy(
        bloodGroup = "B+",
        medicalNotes = "Needs mobility support",
        vulnerableCategoryIds = setOf("elderly", "children"),
        needsMedicalSupport = true
      )
    )

    assertEquals("B+", repository.loadProfile("meera@example.com")?.bloodGroup)
    assertEquals("Needs mobility support", repository.loadProfile("meera@example.com")?.medicalNotes)

    // Restart: a new ViewModel over the same storage reads the edit back.
    val restarted = viewModel(AuthRepository(storage))
    assertEquals("B+", restarted.uiState.value.userProfile.bloodGroup)
    assertEquals("Needs mobility support", restarted.uiState.value.userProfile.medicalNotes)
    assertEquals(
      setOf("elderly", "children"),
      restarted.uiState.value.userProfile.vulnerableCategoryIds
    )
    assertTrue(restarted.uiState.value.userProfile.needsMedicalSupport)
  }
}
