package com.example.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contracts of the REAL backend-backed [AuthRepository], driven through a
 * fake [AuthApiService] (JVM tests must not hit the network) and an
 * [InMemoryTokenStorage]. The fake simulates the FastAPI error contract
 * honestly (409 duplicate, 401 bad credentials, network failure) — the
 * repository logic under test is the real production class.
 */
class AuthRepositoryTest {

  private class FakeApi : AuthApiService {
    var accounts = mutableMapOf<String, Pair<String, String>>() // email -> (password, fullName)
    var pendingConfirmationMode = false
    var nextId = 1L
    var failNetwork = false
    var revokedRefreshTokens = mutableSetOf<String>()
    var issuedRefresh: String? = null

    private fun tokenPair(user: BackendUser): AuthApiResult.Success {
      val access = "access-${user.id}"
      val refresh = "refresh-${user.id}"
      issuedRefresh = refresh
      return AuthApiResult.Success(
        TokenPair(access, refresh, expiresInSeconds = 1800), user
      )
    }

    private fun user(email: String): BackendUser {
      val (password, fullName) = accounts.getValue(email)
      return BackendUser(nextId, fullName, email, "t", "t").also { nextId++ }
    }

    override suspend fun register(fullName: String, email: String, password: String): AuthApiResult {
      if (failNetwork) return AuthApiResult.Failure(ApiErrorKind.NETWORK, "offline")
      val clean = email.trim().lowercase()
      if (accounts.containsKey(clean)) {
        return AuthApiResult.Failure(ApiErrorKind.EMAIL_TAKEN, "An account with this email already exists.")
      }
      if (password.length < AuthValidator.MIN_PASSWORD_LENGTH) {
        return AuthApiResult.Failure(ApiErrorKind.INVALID_INPUT, "Password must be at least 8 characters.")
      }
      accounts[clean] = password to fullName
      if (pendingConfirmationMode) {
        // Confirmation required: user WITHOUT any session tokens.
        return AuthApiResult.PendingConfirmation(user(clean))
      }
      return tokenPair(user(clean)).also { storedEmail = clean }
    }

    override suspend fun login(email: String, password: String): AuthApiResult {
      if (failNetwork) return AuthApiResult.Failure(ApiErrorKind.NETWORK, "offline")
      val clean = email.trim().lowercase()
      val account = accounts[clean]
        ?: return AuthApiResult.Failure(ApiErrorKind.INVALID_CREDENTIALS, "Incorrect email or password.")
      return if (account.first == password) {
        tokenPair(BackendUser(nextId++, account.second, clean, "t", "t")).also { storedEmail = clean }
      } else {
        AuthApiResult.Failure(ApiErrorKind.INVALID_CREDENTIALS, "Incorrect email or password.")
      }
    }

    override suspend fun me(accessToken: String): AuthApiResult {
      if (failNetwork) return AuthApiResult.Failure(ApiErrorKind.NETWORK, "offline")
      // An "EXPIRED" access token is exactly what the repository stores when
      // it needs to exercise the refresh path — the server rejects it.
      if (accessToken == "access-EXPIRED" || !accessToken.startsWith("access-")) {
        return AuthApiResult.Failure(ApiErrorKind.UNAUTHORIZED, "Not authenticated")
      }
      val email = storedEmail ?: return AuthApiResult.Failure(ApiErrorKind.UNAUTHORIZED, "Not authenticated")
      val account = accounts[email] ?: return AuthApiResult.Failure(ApiErrorKind.UNAUTHORIZED, "Not authenticated")
      return AuthApiResult.Success(
        TokenPair(accessToken, "", 0), BackendUser(nextId++, account.second, email, "t", "t")
      )
    }

    override suspend fun refresh(refreshToken: String): AuthApiResult {
      if (failNetwork) return AuthApiResult.Failure(ApiErrorKind.NETWORK, "offline")
      if (refreshToken in revokedRefreshTokens || !refreshToken.startsWith("refresh-")) {
        return AuthApiResult.Failure(ApiErrorKind.UNAUTHORIZED, "Refresh token is invalid or has been revoked.")
      }
      val email = storedEmail ?: return AuthApiResult.Failure(ApiErrorKind.UNAUTHORIZED, "x")
      val account = accounts.getValue(email)
      return AuthApiResult.Success(
        TokenPair("access-new", "refresh-new", 1800),
        BackendUser(nextId++, account.second, email, "t", "t")
      ).also { revokedRefreshTokens.add(refreshToken); issuedRefresh = "refresh-new" }
    }

    override suspend fun logout(accessToken: String, refreshToken: String?): SimpleApiResult {
      refreshToken?.let { revokedRefreshTokens.add(it) }
      return SimpleApiResult.Success
    }

    var storedEmail: String? = null
  }

  private lateinit var api: FakeApi
  private lateinit var store: InMemoryTokenStorage
  private lateinit var repository: AuthRepository

  @Before
  fun setUp() {
    api = FakeApi()
    store = InMemoryTokenStorage()
    repository = AuthRepository(api, store)
  }

  // ------------------------------------------------------------ registration

  @Test
  fun `registration succeeds stores tokens and real user`() = runTest {
    val result = repository.register("Meera Krishnan", "meera@example.com", "longenough7")
    assertTrue("expected ok: ${result.errorMessage}", result.ok)
    assertEquals("meera@example.com", result.email)
    assertEquals("Meera Krishnan", result.fullName)
    assertEquals("meera@example.com", repository.currentUserEmail)
    assertNotNull(store.refreshToken())
    assertTrue(repository.hasStoredSession())
  }

  @Test
  fun `signup with email confirmation required stores NOTHING and is not ok`() = runTest {
    // Simulates Supabase projects with "Confirm email" ON: /signup returns a
    // user WITHOUT tokens. The repository must NOT invent a session.
    api.pendingConfirmationMode = true
    val result = repository.register("Ravi", "ravi.t@example.org", "longenough7")
    org.junit.Assert.assertFalse("pending signup is not a login", result.ok)
    org.junit.Assert.assertTrue(result.pendingConfirmation)
    org.junit.Assert.assertNull("no token may be stored pre-confirmation", store.accessToken())
    org.junit.Assert.assertNull(store.refreshToken())
    org.junit.Assert.assertFalse("no fake session may exist", repository.hasStoredSession())
    org.junit.Assert.assertNull(
      "no authenticated identity before confirmation",
      repository.currentUserEmail
    )
  }

  @Test
  fun `empty-string refresh token cannot masquerade as a session`() = runTest {
    // Historical bug: '' refresh tokens were stored truthy-ish; session checks
    // must hinge on a REAL token, not an empty string.
    store.saveSession("access-abc", null, "x@y.org", "X Y")
    org.junit.Assert.assertFalse(repository.hasStoredSession())
    store.saveSession("access-abc", "", "x@y.org", "X Y")
    org.junit.Assert.assertFalse(
      "blank refresh must never count as a session",
      repository.hasStoredSession()
    )
  }

  @Test
  fun `duplicate email is rejected`() = runTest {
    repository.register("Meera", "meera@example.com", "longenough7")
    val second = AuthRepository(api, InMemoryTokenStorage())
    val result = second.register("Other", "MEERA@example.com", "longenough7")
    assertFalse(result.ok)
    assertEquals(AuthError.EMAIL_TAKEN, result.error)
  }

  @Test
  fun `invalid registration data is rejected client-side`() = runTest {
    val badEmail = repository.register("X", "not-an-email", "longenough7")
    assertEquals(AuthError.INVALID_EMAIL, badEmail.error)

    val shortPassword = repository.register("X", "x@example.com", "short")
    assertEquals(AuthError.WEAK_PASSWORD, shortPassword.error)

    val blankName = repository.register("  ", "x@example.com", "longenough7")
    assertEquals(AuthError.INVALID_EMAIL, blankName.error)

    // Nothing hit the network.
    assertTrue(api.accounts.isEmpty())
  }

  // ------------------------------------------------------------------ login

  @Test
  fun `login with correct credentials stores the session`() = runTest {
    api.accounts["meera@example.com"] = "longenough7" to "Meera Krishnan"
    val result = repository.login("meera@example.com", "longenough7")
    assertTrue(result.ok)
    assertEquals("Meera Krishnan", result.fullName)
    assertTrue(repository.hasStoredSession())
  }

  @Test
  fun `wrong password is rejected as invalid credentials`() = runTest {
    api.accounts["meera@example.com"] = "longenough7" to "Meera Krishnan"
    val result = repository.login("meera@example.com", "wrongpassword")
    assertFalse(result.ok)
    assertEquals(AuthError.INVALID_CREDENTIALS, result.error)
    // And no session was stored.
    assertFalse(repository.hasStoredSession())
  }

  @Test
  fun `unknown email is rejected identically to wrong password`() = runTest {
    val result = repository.login("ghost@example.com", "whatever123")
    assertEquals(AuthError.INVALID_CREDENTIALS, result.error)
  }

  @Test
  fun `network failure is reported as NETWORK, never a fake login`() = runTest {
    api.accounts["meera@example.com"] = "longenough7" to "Meera Krishnan"
    api.failNetwork = true
    val result = repository.login("meera@example.com", "longenough7")
    assertFalse(result.ok)
    assertEquals(AuthError.NETWORK, result.error)
    assertFalse(repository.hasStoredSession())
  }

  // ---------------------------------------------------- session restoration

  @Test
  fun `session restoration returns the real user from me`() = runTest {
    repository.register("Meera Krishnan", "meera@example.com", "longenough7")
    api.storedEmail = repository.currentUserEmail
    val restored = repository.restoreSession()
    assertNotNull(restored)
    assertEquals("meera@example.com", restored?.email)
    assertEquals("Meera Krishnan", restored?.fullName)
  }

  @Test
  fun `expired access token is silently refreshed during restoration`() = runTest {
    repository.register("Meera Krishnan", "meera@example.com", "longenough7")
    api.storedEmail = repository.currentUserEmail
    // Simulate an access token the server rejects (expired).
    store.updateAccessToken("access-EXPIRED")
    val restored = repository.restoreSession()
    assertNotNull(restored)
    assertEquals("access-new", store.accessToken())
    assertEquals("refresh-new", store.refreshToken())
  }

  @Test
  fun `revoked refresh token ends the session`() = runTest {
    repository.register("Meera Krishnan", "meera@example.com", "longenough7")
    api.storedEmail = repository.currentUserEmail
    store.updateAccessToken("access-EXPIRED")
    api.revokedRefreshTokens.addAll(listOf(store.refreshToken()!!))
    assertNull(repository.restoreSession())
  }

  @Test
  fun `offline at startup keeps the stored session`() = runTest {
    repository.register("Meera Krishnan", "meera@example.com", "longenough7")
    api.failNetwork = true
    val restored = repository.restoreSession()
    assertNotNull("offline-first: cached session must survive", restored)
    assertTrue(repository.hasStoredSession())
  }

  // ----------------------------------------------------------------- logout

  @Test
  fun `logout clears the local session`() = runTest {
    repository.register("Meera Krishnan", "meera@example.com", "longenough7")
    assertTrue(repository.hasStoredSession())
    repository.logout()
    assertFalse(repository.hasStoredSession())
    assertNull(repository.currentUserEmail)
    assertNull(store.refreshToken())
  }

  @Test
  fun `logout revokes the refresh token server-side`() = runTest {
    repository.register("Meera Krishnan", "meera@example.com", "longenough7")
    val refresh = store.refreshToken()!!
    repository.logout()
    assertTrue(refresh in api.revokedRefreshTokens)
  }
}
