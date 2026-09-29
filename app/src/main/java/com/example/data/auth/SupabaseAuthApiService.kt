package com.example.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real OkHttp-backed client for Supabase GoTrue Authentication.
 *
 * Direct integration with Supabase Auth:
 *  - Sign up with full name stored in user_metadata
 *  - Password authentication (grant_type=password)
 *  - Session restoration via GET /auth/v1/user
 *  - Token refresh via grant_type=refresh_token
 *  - Server-side logout via POST /auth/v1/logout
 */
class SupabaseAuthApiService(
  private val supabaseUrl: String,
  private val apiKey: String,
  private val httpClient: OkHttpClient = defaultAuthHttpClient()
) : AuthApiService {

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  override suspend fun register(
    fullName: String,
    email: String,
    password: String
  ): AuthApiResult = withContext(Dispatchers.IO) {
    val body = JSONObject().apply {
      put("email", email)
      put("password", password)
      put("data", JSONObject().apply {
        put("full_name", fullName)
      })
    }
    val request = Request.Builder()
      .post(body.toString().toRequestBody(jsonMediaType))
      .url(authUrl("signup"))
      .header("apikey", apiKey)
      .header("Authorization", "Bearer $apiKey")
      .build()

    executeForTokensOrUser(request, defaultFullName = fullName)
  }

  override suspend fun login(email: String, password: String): AuthApiResult =
    withContext(Dispatchers.IO) {
      val body = JSONObject().apply {
        put("email", email)
        put("password", password)
      }
      val request = Request.Builder()
        .post(body.toString().toRequestBody(jsonMediaType))
        .url(authUrl("token?grant_type=password"))
        .header("apikey", apiKey)
        .header("Authorization", "Bearer $apiKey")
        .build()

      executeForTokensOrUser(request, defaultFullName = "")
    }

  override suspend fun me(accessToken: String): AuthApiResult = withContext(Dispatchers.IO) {
    // Defense for installs that stored the OLD fabricated token: treat it as
    // signed-out rather than sending "pending_confirmation" as a bearer JWT.
    if (accessToken == "pending_confirmation") {
      return@withContext AuthApiResult.Failure(
        ApiErrorKind.UNAUTHORIZED, "Session not active — confirm your email first."
      )
    }
    val request = Request.Builder()
      .get()
      .url(authUrl("user"))
      .header("apikey", apiKey)
      .header("Authorization", "Bearer $accessToken")
      .build()

    try {
      httpClient.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful && text.isNotBlank()) {
          val json = JSONObject(text)
          val user = parseSupabaseUser(json, defaultFullName = "")
          AuthApiResult.Success(
            tokens = TokenPair(accessToken = accessToken, refreshToken = "", expiresInSeconds = 0),
            user = user
          )
        } else {
          mapSupabaseError(response.code, text)
        }
      }
    } catch (e: IOException) {
      AuthApiResult.Failure(ApiErrorKind.NETWORK, offlineMessage())
    } catch (e: Exception) {
      AuthApiResult.Failure(ApiErrorKind.SERVER, "Authentication check failed (${e.javaClass.simpleName}).")
    }
  }

  override suspend fun refresh(refreshToken: String): AuthApiResult = withContext(Dispatchers.IO) {
    val body = JSONObject().apply { put("refresh_token", refreshToken) }
    val request = Request.Builder()
      .post(body.toString().toRequestBody(jsonMediaType))
      .url(authUrl("token?grant_type=refresh_token"))
      .header("apikey", apiKey)
      .header("Authorization", "Bearer $apiKey")
      .build()

    executeForTokensOrUser(request, defaultFullName = "")
  }

  /**
   * Completes the native-app Google PKCE flow: exchanges the one-time
   * `code` GoTrue redirected back to the app for a real session, proving
   * possession of the [verifier] generated before the browser opened.
   * POST /auth/v1/token?grant_type=pkce.
   */
  override suspend fun exchangeGoogleCode(
    authCode: String,
    verifier: String,
    redirectUrl: String
  ): AuthApiResult = withContext(Dispatchers.IO) {
    val body = org.json.JSONObject().apply {
      put("authenticator", "google")
      put("auth_code", authCode)
      put("code_verifier", verifier)
      put("redirect_to", redirectUrl)
    }
    val request = Request.Builder()
      .post(body.toString().toRequestBody(jsonMediaType))
      .url(authUrl("token?grant_type=pkce"))
      .header("apikey", apiKey)
      .header("Authorization", "Bearer $apiKey")
      .build()
    executeForTokensOrUser(request, defaultFullName = "")
  }

  override suspend fun logout(accessToken: String, refreshToken: String?): SimpleApiResult =
    withContext(Dispatchers.IO) {
      val request = Request.Builder()
        .post("{}".toRequestBody(jsonMediaType))
        .url(authUrl("logout"))
        .header("apikey", apiKey)
        .header("Authorization", "Bearer $accessToken")
        .build()

      try {
        httpClient.newCall(request).execute().use { response ->
          if (response.isSuccessful || response.code == 204 || response.code == 401) {
            SimpleApiResult.Success
          } else {
            val text = response.body?.string().orEmpty()
            val failure = mapSupabaseError(response.code, text)
            SimpleApiResult.Failure(failure.errorKind, failure.errorMessage)
          }
        }
      } catch (e: IOException) {
        SimpleApiResult.Failure(ApiErrorKind.NETWORK, offlineMessage())
      } catch (e: Exception) {
        SimpleApiResult.Failure(ApiErrorKind.SERVER, "Logout failed (${e.javaClass.simpleName}).")
      }
    }

  // ------------------------------------------------------------------ helpers

  private fun authUrl(path: String) = supabaseUrl.trimEnd('/') + "/auth/v1/" + path

  private fun executeForTokensOrUser(request: Request, defaultFullName: String): AuthApiResult {
    return try {
      httpClient.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful && text.isNotBlank()) {
          val json = JSONObject(text)
          if (json.has("access_token")) {
            val userJson = if (json.has("user")) json.getJSONObject("user") else json
            val user = parseSupabaseUser(userJson, defaultFullName)
            AuthApiResult.Success(
              tokens = TokenPair(
                accessToken = json.getString("access_token"),
                // null (not "") when the project has no rotating refresh
                // token, so hasStoredSession() cannot trust an empty string.
                refreshToken = json.optString("refresh_token", "")
                  .takeIf { it.isNotBlank() },
                expiresInSeconds = json.optLong("expires_in", 3600L)
              ),
              user = user
            )
          } else if (json.has("id") && json.has("email")) {
            // EMAIL CONFIRMATION REQUIRED (this project's setting): GoTrue
            // returned the new user WITHOUT any session tokens. There is no
            // honest session here yet — surface the pending state so the UI
            // sends the user to their inbox. Never mint a fake token.
            AuthApiResult.PendingConfirmation(parseSupabaseUser(json, defaultFullName))
          } else {
            mapSupabaseError(response.code, text)
          }
        } else {
          mapSupabaseError(response.code, text)
        }
      }
    } catch (e: IOException) {
      AuthApiResult.Failure(ApiErrorKind.NETWORK, offlineMessage())
    } catch (e: Exception) {
      AuthApiResult.Failure(ApiErrorKind.SERVER, "Authentication request failed (${e.javaClass.simpleName}).")
    }
  }

  private fun parseSupabaseUser(json: JSONObject, defaultFullName: String): BackendUser {
    val meta = json.optJSONObject("user_metadata")
    val fullName = meta?.optString("full_name")
      ?.takeIf { it.isNotBlank() }
      ?: meta?.optString("name")?.takeIf { it.isNotBlank() }
      ?: defaultFullName.takeIf { it.isNotBlank() }
      ?: json.optString("email").substringBefore('@').replace('.', ' ')
        .split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    return BackendUser(
      id = json.optString("id", ""),
      fullName = fullName,
      email = json.optString("email", ""),
      createdAt = json.optString("created_at", ""),
      updatedAt = json.optString("updated_at", "")
    )
  }

  private fun mapSupabaseError(code: Int, responseBody: String): AuthApiResult.Failure {
    val json = runCatching { JSONObject(responseBody) }.getOrNull()
    val errorCode = json?.optString("error_code")?.takeIf { it.isNotBlank() }
      ?: json?.optString("error")?.takeIf { it.isNotBlank() }
      ?: ""
    val msg = json?.optString("msg")?.takeIf { it.isNotBlank() }
      ?: json?.optString("message")?.takeIf { it.isNotBlank() }
      ?: json?.optString("error_description")?.takeIf { it.isNotBlank() }
      ?: ""

    val kind = when {
      code == 429 || errorCode.contains("rate_limit") || msg.contains("rate limit", ignoreCase = true) -> ApiErrorKind.RATE_LIMITED
      errorCode.contains("email_not_confirmed") || msg.contains("Email not confirmed", ignoreCase = true) -> ApiErrorKind.INVALID_CREDENTIALS
      errorCode.contains("user_already_exists") || msg.contains("already registered", ignoreCase = true) -> ApiErrorKind.EMAIL_TAKEN
      errorCode.contains("invalid_credentials") || errorCode.contains("invalid_grant") || msg.contains("Invalid login credentials", ignoreCase = true) -> ApiErrorKind.INVALID_CREDENTIALS
      errorCode.contains("email_address_invalid") || errorCode.contains("weak_password") -> ApiErrorKind.INVALID_INPUT
      code == 401 -> ApiErrorKind.UNAUTHORIZED
      code == 409 -> ApiErrorKind.EMAIL_TAKEN
      code == 422 -> ApiErrorKind.INVALID_INPUT
      code in 500..599 -> ApiErrorKind.SERVER
      else -> ApiErrorKind.SERVER
    }

    val friendlyMessage = when {
      code == 429 || errorCode.contains("rate_limit") || msg.contains("rate limit", ignoreCase = true) ->
        "Security rate limit reached. Please wait a minute before trying again."
      errorCode == "email_not_confirmed" || msg.contains("Email not confirmed", ignoreCase = true) ->
        "Please check your email and click the confirmation link before signing in."
      errorCode == "user_already_exists" || msg.contains("already registered", ignoreCase = true) ->
        "An account with this email already exists."
      errorCode == "invalid_credentials" || errorCode == "invalid_grant" || msg.contains("Invalid login credentials", ignoreCase = true) ->
        "Incorrect email or password."
      errorCode == "over_email_send_rate_limit" ->
        "Security rate limit reached. Please wait a minute before requesting another email."
      errorCode == "email_address_invalid" ->
        "Please enter a valid email address."
      msg.isNotBlank() -> msg
      code == 401 -> "Incorrect email or password."
      code == 409 -> "An account with this email already exists."
      code in 500..599 -> "The authentication service is temporarily unavailable (HTTP $code). Try again shortly."
      else -> "Sign-in failed (HTTP $code)."
    }

    return AuthApiResult.Failure(kind, friendlyMessage)
  }

  private fun offlineMessage() =
    "Cannot reach the sign-in service. Check your connection and try again."

  companion object {
    fun defaultAuthHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
  }
}
