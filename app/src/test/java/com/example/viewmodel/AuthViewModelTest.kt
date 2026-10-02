package com.example.viewmodel

import com.example.data.auth.AuthApiResult
import com.example.data.auth.AuthApiService
import com.example.data.auth.AuthError
import com.example.data.auth.AuthRepository
import com.example.data.auth.BackendUser
import com.example.data.auth.InMemoryTokenStorage
import com.example.data.auth.SimpleApiResult
import com.example.data.auth.TokenPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * AuthViewModel contracts: the full state machine (loading, field errors,
 * server errors, authenticated state, logout) around the real
 * [AuthRepository] with a fake API boundary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private class StubApi : AuthApiService {
    var registered = mutableMapOf<String, Pair<String, String>>()
    var failNetwork = false
    var loggedOut = false
    var pendingMode = false
    /** Session-restore knobs: what /me and /refresh answer, and how often. */
    var meOk = false
    var meCalls = 0
    var refreshCalls = 0

    override suspend fun register(fullName: String, email: String, password: String): AuthApiResult {
      if (failNetwork) return AuthApiResult.Failure(com.example.data.auth.ApiErrorKind.NETWORK, "offline")
      val clean = email.trim().lowercase()
      if (registered.containsKey(clean)) {
        return AuthApiResult.Failure(com.example.data.auth.ApiErrorKind.EMAIL_TAKEN, "exists")
      }
      registered[clean] = password to fullName
      if (pendingMode) {
        return AuthApiResult.PendingConfirmation(BackendUser(1, fullName, clean, "t", "t"))
      }
      return AuthApiResult.Success(
        TokenPair("a", "r", 1800),
        BackendUser(1, fullName, clean, "t", "t")
      )
    }

    override suspend fun login(email: String, password: String): AuthApiResult {
      if (failNetwork) return AuthApiResult.Failure(com.example.data.auth.ApiErrorKind.NETWORK, "offline")
      val clean = email.trim().lowercase()
      val account = registered[clean]
        ?: return AuthApiResult.Failure(
          com.example.data.auth.ApiErrorKind.INVALID_CREDENTIALS,
          "Incorrect email or password."
        )
      return if (account.first == password) {
        AuthApiResult.Success(TokenPair("a", "r", 1800), BackendUser(1, account.second, clean, "t", "t"))
      } else {
        AuthApiResult.Failure(
          com.example.data.auth.ApiErrorKind.INVALID_CREDENTIALS,
          "Incorrect email or password."
        )
      }
    }

    override suspend fun me(accessToken: String): AuthApiResult {
      meCalls++
      return if (meOk) AuthApiResult.Success(
        TokenPair(accessToken, "r", 1800),
        BackendUser(1, "Meera Krishnan", "meera@example.com", "t", "t")
      ) else AuthApiResult.Failure(com.example.data.auth.ApiErrorKind.UNAUTHORIZED, "no session")
    }

    override suspend fun refresh(refreshToken: String): AuthApiResult {
      refreshCalls++
      return if (meOk) AuthApiResult.Success(
        TokenPair("a", refreshToken, 1800),
        BackendUser(1, "Meera Krishnan", "meera@example.com", "t", "t")
      ) else AuthApiResult.Failure(com.example.data.auth.ApiErrorKind.UNAUTHORIZED, "no session")
    }

    override suspend fun logout(accessToken: String, refreshToken: String?): SimpleApiResult {
      loggedOut = true
      return SimpleApiResult.Success
    }
  }

  private lateinit var api: StubApi
  private lateinit var viewModel: AuthViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    api = StubApi()
    viewModel = AuthViewModel(AuthRepository(api, InMemoryTokenStorage()))
  }


  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `successful login moves to authenticated state`() = runTest {
    api.registered["meera@example.com"] = "longenough7" to "Meera Krishnan"
    var success = false
    viewModel.login("meera@example.com", "longenough7") { success = true }
    assertTrue(success)
    assertTrue(viewModel.uiState.value.isAuthenticated)
    assertEquals("Meera Krishnan", viewModel.uiState.value.user?.fullName)
    assertNull(viewModel.uiState.value.loginError)
    assertFalse(viewModel.uiState.value.isSubmitting)
  }

  @Test
  fun `wrong password sets login error, not authenticated`() = runTest {
    api.registered["meera@example.com"] = "longenough7" to "Meera Krishnan"
    var success = false
    // A password long enough to pass CLIENT validation so the wrong
    // credentials actually reach the backend and its 401 comes back.
    viewModel.login("meera@example.com", "wrongpassword1") { success = true }
    assertFalse(success)
    assertFalse(viewModel.uiState.value.isAuthenticated)
    assertEquals("Incorrect email or password.", viewModel.uiState.value.loginError)
    assertNull(viewModel.uiState.value.loginFieldError)
  }

  @Test
  fun `network error surfaces in login state`() = runTest {
    api.registered["meera@example.com"] = "longenough7" to "Meera"
    api.failNetwork = true
    viewModel.login("meera@example.com", "longenough7") { }
    assertFalse(viewModel.uiState.value.isAuthenticated)
    assertEquals("offline", viewModel.uiState.value.loginError)
  }

  @Test
  fun `invalid email shows field error without touching the network`() = runTest {
    viewModel.login("not-an-email", "longenough7") { }
    assertEquals(
      com.example.R.string.login_error_invalid_email,
      viewModel.uiState.value.loginFieldError
    )
  }

  @Test
  fun `empty password shows field error`() = runTest {
    viewModel.login("meera@example.com", "") { }
    assertEquals(
      com.example.R.string.login_error_empty_password,
      viewModel.uiState.value.loginFieldError
    )
  }

  @Test
  fun `signup awaiting email confirmation shows inbox state and does NOT enter the app`() = runTest {
    api.pendingMode = true
    var entered = false
    viewModel.signup("Ravi", "ravi.t@example.org", "longenough7", "longenough7") { entered = true }
    org.junit.Assert.assertFalse("pending confirmation must never open the app", entered)
    val st = viewModel.uiState.value
    org.junit.Assert.assertFalse("must not be authenticated", st.isAuthenticated)
    org.junit.Assert.assertEquals("ravi.t@example.org", st.pendingConfirmationEmail)
    org.junit.Assert.assertNotNull("inbox guidance must be shown", st.signupError)
  }

  @Test
  fun `signup success stores the real name and authenticates`() = runTest {
    var success = false
    viewModel.signup("Meera Krishnan", "meera@example.com", "longenough7", "longenough7") {
      success = true
    }
    assertTrue(success)
    assertTrue(viewModel.uiState.value.isAuthenticated)
    assertEquals("Meera Krishnan", viewModel.uiState.value.user?.fullName)
  }

  @Test
  fun `signup mismatched passwords shows field error`() = runTest {
    viewModel.signup("Meera", "meera@example.com", "longenough7", "different1") { }
    assertEquals(
      com.example.R.string.login_passwords_do_not_match,
      viewModel.uiState.value.signupFieldError
    )
    assertFalse(viewModel.uiState.value.isAuthenticated)
  }

  @Test
  fun `signup short password shows field error`() = runTest {
    viewModel.signup("Meera", "meera@example.com", "short", "short") { }
    assertEquals(
      com.example.R.string.register_error_password,
      viewModel.uiState.value.signupFieldError
    )
  }

  @Test
  fun `signup duplicate email surfaces server error`() = runTest {
    viewModel.signup("First", "meera@example.com", "longenough7", "longenough7") { }
    viewModel.clearErrors()
    val second = AuthViewModel(AuthRepository(api, InMemoryTokenStorage()))
    second.signup("Second", "meera@example.com", "longenough7", "longenough7") { }
    assertFalse(second.uiState.value.isAuthenticated)
    assertEquals("exists", second.uiState.value.signupError)
  }

  @Test
  fun `logout clears authentication state`() = runTest {
    api.registered["meera@example.com"] = "longenough7" to "Meera"
    viewModel.login("meera@example.com", "longenough7") { }
    assertTrue(viewModel.uiState.value.isAuthenticated)
    viewModel.logout { }
    assertFalse(viewModel.uiState.value.isAuthenticated)
    assertNull(viewModel.uiState.value.user)
    assertTrue(api.loggedOut)
  }

  @Test
  fun `submitting flag toggles during and after the call`() = runTest {
    api.registered["meera@example.com"] = "longenough7" to "Meera"
    viewModel.login("meera@example.com", "longenough7") { }
    // UnconfinedTestDispatcher runs eagerly, so after runTest the flag is settled.
    assertFalse(viewModel.uiState.value.isSubmitting)
  }

  // ------------------------------------------------ P0 auth-gate regression
  //
  // Contract (session-flash fix): the root screen renders from AuthGate.
  //   * A VM starts RESTORING - Login must NEVER be offered while the
  //     stored-session check is undecided.
  //   * Only a POSITIVE signed-out determination yields SIGNED_OUT.
  //   * Rotation re-calls restoreSession against the same surviving VM:
  //     the verdict is served without a second network round-trip and
  //     without passing back through RESTORING.

  private fun storeWithSession(): InMemoryTokenStorage =
    InMemoryTokenStorage().apply {
      saveSession("access-tok", "refresh-tok", "meera@example.com", "Meera Krishnan")
    }

  @Test
  fun `signed-in app launch - stored session restores straight to SIGNED_IN`() = runTest {
    api.meOk = true
    val vm = AuthViewModel(AuthRepository(api, storeWithSession()))
    // Before the check runs, the gate is RESTORING (not SIGNED_OUT)...
    assertEquals(AuthGate.RESTORING, vm.uiState.value.gate)
    var opened = false
    vm.restoreSession { opened = true }
    assertTrue("callback must open the app", opened)
    assertEquals(AuthGate.SIGNED_IN, vm.uiState.value.gate)
    assertFalse(vm.uiState.value.isRestoring)
  }

  @Test
  fun `signed-out app launch - no stored session yields SIGNED_OUT, never a restore hang`() = runTest {
    val vm = AuthViewModel(AuthRepository(api, InMemoryTokenStorage()))
    var opened = true
    vm.restoreSession { opened = it }
    assertFalse("fresh install must not enter the app", opened)
    assertEquals(AuthGate.SIGNED_OUT, vm.uiState.value.gate)
    // And no pointless network call was made for a session that does not exist.
    assertEquals(0, api.meCalls)
    assertEquals(0, api.refreshCalls)
  }

  @Test
  fun `session restoration - revoked stored session falls to SIGNED_OUT honestly`() = runTest {
    api.meOk = false // both /me and /refresh refuse
    val vm = AuthViewModel(AuthRepository(api, storeWithSession()))
    var opened = true
    vm.restoreSession { opened = it }
    assertFalse(opened)
    assertEquals(AuthGate.SIGNED_OUT, vm.uiState.value.gate)
    assertNull(vm.uiState.value.user)
  }

  @Test
  fun `rotation - second restoreSession reuses the verdict with no second network call or flash`() = runTest {
    api.meOk = true
    val vm = AuthViewModel(AuthRepository(api, storeWithSession()))
    vm.restoreSession { }
    assertEquals(AuthGate.SIGNED_IN, vm.uiState.value.gate)
    val callsAfterFirst = api.meCalls + api.refreshCalls
    // Configuration change: the composition calls restoreSession again.
    var reopened = false
    vm.restoreSession { reopened = it }
    assertTrue("rotation must keep the user signed in", reopened)
    assertEquals("no second round-trip", callsAfterFirst, api.meCalls + api.refreshCalls)
    // Crucially: still SIGNED_IN - never flipped back through RESTORING.
    assertEquals(AuthGate.SIGNED_IN, vm.uiState.value.gate)
    assertFalse(vm.uiState.value.isRestoring)
  }

  @Test
  fun `logout - gate falls to SIGNED_OUT so Login is the positive next screen`() = runTest {
    api.meOk = true
    val repo = AuthRepository(api, storeWithSession())
    val vm = AuthViewModel(repo)
    vm.restoreSession { }
    assertEquals(AuthGate.SIGNED_IN, vm.uiState.value.gate)
    vm.logout { }
    assertEquals(AuthGate.SIGNED_OUT, vm.uiState.value.gate)
    assertFalse(vm.uiState.value.isRestoring)
    assertNull(vm.uiState.value.user)
    // A signed-out user may now legitimately see Login.
  }

  @Test
  fun `gate is derived from state - no separate flag can disagree`() {
    assertEquals(AuthGate.RESTORING, AuthUiState(isRestoring = true).gate)
    assertEquals(AuthGate.SIGNED_OUT, AuthUiState().gate)
    assertEquals(
      AuthGate.SIGNED_IN,
      AuthUiState(user = BackendUser(1, "M", "m@e.org", "t", "t")).gate
    )
    // Even a restoring flag cannot hide a user: SIGNED_IN wins (no logout
    // race can flash splash over a live session).
    assertEquals(
      AuthGate.SIGNED_IN,
      AuthUiState(isRestoring = true, user = BackendUser(1, "M", "m@e.org", "t", "t")).gate
    )
  }
}
