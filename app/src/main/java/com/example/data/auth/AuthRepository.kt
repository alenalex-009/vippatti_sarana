package com.example.data.auth

import java.util.Locale

/**
 * Abstraction over the secure session store so the repository (and every
 * test) can run against either the real EncryptedSharedPreferences-backed
 * [TokenStorage] or an in-memory fake — the project's established seam
 * pattern (the old AuthStorage/InMemoryAuthStorage pair did the same for the
 * local auth this file replaces).
 */
interface TokenStore {
  fun saveSession(accessToken: String, refreshToken: String?, email: String, fullName: String)
  fun updateAccessToken(accessToken: String)
  fun accessToken(): String?
  fun refreshToken(): String?
  fun userEmail(): String?
  fun userFullName(): String?
  fun clear()
}

/** Why a login/registration attempt failed (mapped to UI copy verbatim). */
enum class AuthError {
  INVALID_EMAIL,
  WEAK_PASSWORD,
  EMAIL_TAKEN,
  INVALID_CREDENTIALS,
  RATE_LIMITED,
  NETWORK,
  SERVER
}

/** Outcome of a login/registration attempt as the UI sees it. */
data class AuthResult(
  val ok: Boolean,
  val email: String = "",
  val fullName: String = "",
  val error: AuthError? = null,
  val errorMessage: String? = null,
  /** True when the account was created but email confirmation is required:
   * NO session exists yet — the UI must send the user to their inbox, and
   * must NOT open the app. */
  val pendingConfirmation: Boolean = false
) {
  companion object {
    fun success(email: String, fullName: String) =
      AuthResult(ok = true, email = email, fullName = fullName)

    fun failure(error: AuthError, message: String? = null) =
      AuthResult(ok = false, error = error, errorMessage = message)

    fun pendingConfirmation(email: String, fullName: String) =
      AuthResult(
        ok = false,
        email = email,
        fullName = fullName,
        pendingConfirmation = true,
        errorMessage = "Almost there — we sent a confirmation link to " +
          "$email. Open it, then sign in."
      )
  }
}

/**
 * Client-side validation BEFORE any network call: cheap, instant, and the
 * same rules the backend enforces again (defence in depth, never a
 * replacement for it).
 */
object AuthValidator {
  const val MIN_PASSWORD_LENGTH = 8
  private val EMAIL_REGEX = Regex("^[A-Za-z0-9.+_-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

  fun normalizeEmail(raw: String): String? =
    raw.trim().lowercase(Locale.ROOT).takeIf { EMAIL_REGEX.matches(it) }

  fun passwordValid(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH
}

/**
 * The ONE authentication system of the app: real token-based authentication
 * against the Vippatti Sarana FastAPI backend (which owns the PostgreSQL
 * users table).
 *
 * Responsibilities:
 *  - client-side validation, then call [AuthApiService]
 *  - store/restore the token session securely via [TokenStore]
 *  - silently refresh an expired access token with the refresh token
 *  - expose the authenticated user's real name/email (from /me) for Profile
 *  - logout: revoke server-side (best effort) AND clear local tokens
 *
 * No fake success path exists: when the backend cannot be reached the result
 * is a NETWORK failure, never a pretend login.
 */
class AuthRepository(
  private val api: AuthApiService,
  private val store: TokenStore
) {

  /** True when a stored token session exists (restored on every cold start).
   * Blank counts as absent: an empty string was never a real session. */
  fun hasStoredSession(): Boolean =
    store.refreshToken()?.takeIf { it.isNotBlank() } != null

  val currentUserEmail: String?
    get() = if (hasStoredSession()) store.userEmail() else null

  val currentUserFullName: String?
    get() = if (hasStoredSession()) store.userFullName() else null

  suspend fun login(email: String, password: String): AuthResult {
    val clean = AuthValidator.normalizeEmail(email)
      ?: return AuthResult.failure(AuthError.INVALID_EMAIL)
    if (!AuthValidator.passwordValid(password)) {
      return AuthResult.failure(AuthError.WEAK_PASSWORD)
    }
    return when (val result = api.login(clean, password)) {
      is AuthApiResult.Success -> {
        store.saveSession(
          accessToken = result.tokens.accessToken,
          refreshToken = result.tokens.refreshToken,
          email = result.user.email,
          fullName = result.user.fullName
        )
        AuthResult.success(result.user.email, result.user.fullName)
      }
      // Login never grants a session before confirmation.
      is AuthApiResult.PendingConfirmation -> AuthResult.failure(
        AuthError.INVALID_CREDENTIALS,
        "Please confirm your email first — the link is in your inbox."
      )
      is AuthApiResult.Failure -> result.toAuthResult()
    }
  }

  suspend fun register(fullName: String, email: String, password: String): AuthResult {
    val name = fullName.trim()
    if (name.isEmpty()) return AuthResult.failure(AuthError.INVALID_EMAIL)
    val clean = AuthValidator.normalizeEmail(email)
      ?: return AuthResult.failure(AuthError.INVALID_EMAIL)
    if (!AuthValidator.passwordValid(password)) {
      return AuthResult.failure(AuthError.WEAK_PASSWORD)
    }
    return when (val result = api.register(name, clean, password)) {
      is AuthApiResult.Success -> {
        store.saveSession(
          accessToken = result.tokens.accessToken,
          refreshToken = result.tokens.refreshToken,
          email = result.user.email,
          fullName = result.user.fullName
        )
        AuthResult.success(result.user.email, result.user.fullName)
      }
      // Confirmation required: NO token is stored, NO session starts.
      is AuthApiResult.PendingConfirmation ->
        AuthResult.pendingConfirmation(result.user.email, result.user.fullName)
      is AuthApiResult.Failure -> result.toAuthResult()
    }
  }

  /**
   * Session restoration + Profile data source: validates the stored session
   * against the backend and returns the account's REAL information from
   * /me. Silently refreshes when the access token has expired. Returns null
   * when the session is gone (revoked, expired beyond refresh). While
   * OFFLINE the cached identity is kept — an offline-first disaster app must
   * not lock its user out at startup just because the network is down.
   */
  suspend fun restoreSession(): BackendUser? {
    val refresh = store.refreshToken() ?: return null
    val access = store.accessToken()
    if (access != null) {
      when (val me = api.me(access)) {
        is AuthApiResult.Success -> return me.user
        is AuthApiResult.PendingConfirmation -> return null
        is AuthApiResult.Failure ->
          if (me.errorKind == ApiErrorKind.NETWORK) return cachedUser()
      }
    }
    return when (val refreshed = api.refresh(refresh)) {
      is AuthApiResult.PendingConfirmation -> null
      is AuthApiResult.Success -> {
        store.saveSession(
          accessToken = refreshed.tokens.accessToken,
          refreshToken = refreshed.tokens.refreshToken,
          email = refreshed.user.email,
          fullName = refreshed.user.fullName
        )
        refreshed.user
      }
      is AuthApiResult.Failure ->
        if (refreshed.errorKind == ApiErrorKind.NETWORK) cachedUser() else null
    }
  }

  /**
   * Logout: revoke the refresh token server-side (best effort — a network
   * failure must never strand the user inside the app) and ALWAYS clear the
   * local session.
   */
  suspend fun logout() {
    val access = store.accessToken()
    val refresh = store.refreshToken()
    if (access != null) {
      api.logout(access, refresh) // result ignored on purpose: local clear wins
    }
    store.clear()
  }

  /**
   * ADOPTS a session delivered by the Google OAuth browser flow: confirms the
   * fragment tokens against /auth/v1/user BEFORE storing them (a callback the
   * app cannot verify is never trusted). Offline at that moment is the same
   * honest case as startup: the freshly delivered session is stored, identity
   * fields fall back to empty, and Profile/refresh resolve it on next sync.
   */
  suspend fun adoptGoogleTokens(tokens: TokenPair): AuthResult =
    when (val me = api.me(tokens.accessToken)) {
      is AuthApiResult.Success -> {
        store.saveSession(
          accessToken = me.tokens.accessToken.takeIf { it.isNotBlank() } ?: tokens.accessToken,
          refreshToken = tokens.refreshToken,
          email = me.user.email,
          fullName = me.user.fullName
        )
        AuthResult.success(me.user.email, me.user.fullName)
      }
      is AuthApiResult.PendingConfirmation -> AuthResult.failure(
        AuthError.INVALID_CREDENTIALS,
        "Google account is not ready yet: " + me.user.email
      )
      is AuthApiResult.Failure -> {
        // A FRESH callback token has never been verified by this app, and any
        // app on the device can fire the deep link — so unlike the startup
        // path (which trusts a previously-VERIFIED stored session), an
        // offline /me here is NOT grounds to adopt. Fail honestly; the user
        // retries when the network returns.
        AuthResult.failure(
          if (me.errorKind == ApiErrorKind.NETWORK) AuthError.NETWORK
          else AuthError.INVALID_CREDENTIALS,
          me.errorMessage
        )
      }
    }

  /**
   * Completes the PKCE callback shape (code -> token exchange -> adopt).
   */
  suspend fun completeGoogleSignIn(authCode: String, verifier: String): AuthResult =
    when (val exchanged = api.exchangeGoogleCode(authCode, verifier, com.example.data.auth.SupabaseGoogleOAuth.REDIRECT_URL)) {
      is AuthApiResult.Success -> adoptGoogleTokens(exchanged.tokens)
      is AuthApiResult.PendingConfirmation -> AuthResult.failure(
        AuthError.INVALID_CREDENTIALS, "Google account needs email confirmation."
      )
      is AuthApiResult.Failure -> exchanged.toAuthResult()
    }

  /**
   * One authenticated GET /me with the stored access token. Returns the
   * failure (so callers can distinguish NETWORK from auth problems) or the
   * real user. Never rotates tokens — that is restoreSession's job.
   */
  suspend fun meOrNull(): AuthApiResult {
    val access = store.accessToken() ?: return AuthApiResult.Failure(
      ApiErrorKind.UNAUTHORIZED, "No access token stored."
    )
    return api.me(access)
  }

  private fun cachedUser(): BackendUser? {
    val email = store.userEmail() ?: return null
    return BackendUser(
      id = "",
      fullName = store.userFullName().orEmpty(),
      email = email,
      createdAt = "",
      updatedAt = ""
    )
  }

  private fun AuthApiResult.Failure.toAuthResult(): AuthResult = when (errorKind) {
    ApiErrorKind.EMAIL_TAKEN -> AuthResult.failure(AuthError.EMAIL_TAKEN, errorMessage)
    ApiErrorKind.INVALID_CREDENTIALS, ApiErrorKind.UNAUTHORIZED ->
      AuthResult.failure(AuthError.INVALID_CREDENTIALS, errorMessage)
    ApiErrorKind.NETWORK -> AuthResult.failure(AuthError.NETWORK, errorMessage)
    ApiErrorKind.RATE_LIMITED -> AuthResult.failure(AuthError.RATE_LIMITED, errorMessage)
    ApiErrorKind.INVALID_INPUT -> AuthResult.failure(AuthError.WEAK_PASSWORD, errorMessage)
    ApiErrorKind.SERVER -> AuthResult.failure(AuthError.SERVER, errorMessage)
  }
}
