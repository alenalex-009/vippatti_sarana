package com.example.data.auth


/**
 * The signed-in account's REAL data as returned by GET /api/v1/auth/me.
 *
 * This is what the Profile screen renders: the actual backend account, not a
 * local or demo identity. [fullName] comes from the users table row created
 * at registration.
 */
data class BackendUser(
  val id: String,
  val fullName: String,
  val email: String,
  val createdAt: String,
  val updatedAt: String
) {
  constructor(numericId: Long, fullName: String, email: String, createdAt: String, updatedAt: String) :
    this(id = numericId.toString(), fullName = fullName, email = email, createdAt = createdAt, updatedAt = updatedAt)
}

/** The token pair returned by register/login/refresh. */
data class TokenPair(
  val accessToken: String,
  /** null when the backend issued NO rotating refresh token. Empty strings
   * are never used as a "no session" marker: session checks test for null. */
  val refreshToken: String?,
  val expiresInSeconds: Long
)

/** Why an auth call failed — drives the UI's error states. */
enum class ApiErrorKind { INVALID_INPUT, EMAIL_TAKEN, INVALID_CREDENTIALS, UNAUTHORIZED, RATE_LIMITED, NETWORK, SERVER }

/** One auth API outcome. [ok] false means [errorKind] + [errorMessage] are set. */
sealed class AuthApiResult {
  /** Account exists and the backend issued a REAL session. */
  data class Success(val tokens: TokenPair, val user: BackendUser) : AuthApiResult()

  /**
   * The account was created, but the project requires EMAIL CONFIRMATION, so
   * GoTrue returned the user WITHOUT a session. There is no honest way to be
   * "logged in" here: the user must confirm via the link in their inbox.
   * (The old code invented a fake "pending_confirmation" token, which gave a
   * session that evaporated on the next cold start and poisoned every
   * authenticated call after it.)
   */
  data class PendingConfirmation(val user: BackendUser) : AuthApiResult()

  data class Failure(val errorKind: ApiErrorKind, val errorMessage: String) : AuthApiResult()
}

/** Outcome of a token-only call (refresh/logout). */
sealed class SimpleApiResult {
  data object Success : SimpleApiResult()
  data class Failure(val errorKind: ApiErrorKind, val errorMessage: String) : SimpleApiResult()
}

/**
 * Network boundary for the Vippatti Sarana FastAPI auth backend.
 * Abstracted so ViewModels/repos are unit-testable with a fake.
 */
interface AuthApiService {
  suspend fun register(fullName: String, email: String, password: String): AuthApiResult
  suspend fun login(email: String, password: String): AuthApiResult
  suspend fun me(accessToken: String): AuthApiResult
  suspend fun refresh(refreshToken: String): AuthApiResult
  suspend fun logout(accessToken: String, refreshToken: String?): SimpleApiResult
}
