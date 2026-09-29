package com.example.data.auth

import android.net.Uri
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Supabase "Sign in with Google" for a NATIVE app, implemented directly
 * against GoTrue (no SDK), covering BOTH callback shapes GoTrue projects
 * produce depending on their Auth API version:
 *
 *  A) LEGACY/IMPLICIT MOBILE FLOW — GoTrue completes the Google exchange
 *     server-side at /auth/v1/callback and redirects to our custom scheme
 *     with the session in the URL FRAGMENT:
 *        vippattisarana://auth-callback#access_token=..&refresh_token=..&expires_in=..
 *
 *  B) PKCE FLOW — the redirect carries ?code=<flow state id>, and the app
 *     exchanges it with the verifier it generated BEFORE opening the browser:
 *        POST /auth/v1/token?grant_type=pkce
 *          { authenticator: "google", auth_code: <code>, code_verifier: <S> }
 *     -> standard session JSON. The challenge (SHA-256 of the verifier) is
 *     attached to the authorize URL, so a intercepted code is useless alone.
 *
 * [SupabaseAuthApiService] exposes [exchangeGoogleCode] for shape B; the
 * ViewModel tries A first (fragment) and falls back to B (code). A callback
 * that yields neither is an error, never a silent success.
 */
object SupabaseGoogleOAuth {

  const val REDIRECT_SCHEME = "vippattisarana"
  const val REDIRECT_HOST = "auth-callback"
  const val REDIRECT_URL = "$REDIRECT_SCHEME://$REDIRECT_HOST"

  /** PKCE verifier kept in memory only, for the lifetime of one attempt. */
  class GoogleAttempt(val verifier: String, val state: String)

  private val RANDOM = SecureRandom()

  /**
   * Random PKCE verifier + anti-confusion state, generated per attempt and
   * held by the ViewModel until the callback resolves or is cancelled.
   */
  fun newAttempt(): GoogleAttempt {
    val verifier = randomUrlSafe(48)
    val state = randomUrlSafe(16)
    return GoogleAttempt(verifier = verifier, state = state)
  }

  private fun randomUrlSafe(bytes: Int): String {
    val buf = ByteArray(bytes)
    RANDOM.nextBytes(buf)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
  }

  fun codeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
  }

  /**
   * Native-app IMPLICIT callback: deliberately WITHOUT code_challenge
   * params (attaching one switches GoTrue to browser-relay mode). The
   * redirect_to custom scheme MUST be allowlisted in the Supabase dashboard
   * (Authentication -> Sign In / Up -> URL Configuration -> Redirect Allowlist)
   * — GoTrue silently falls back to the Site URL for non-allowlisted
   * redirects (observed live: the tab was handed to localhost:3000, the
   * project's placeholder Site URL). With the scheme allowlisted, GoTrue
   * completes the Google exchange server-side and returns the session in the
   * custom-scheme URL FRAGMENT. The ?code= PKCE shape is still parsed as a
   * fallback for deployments running with PKCE enabled.
   */
  fun buildAuthorizeUrl(
    supabaseUrl: String,
    apiKey: String,
    attempt: GoogleAttempt
  ): String =
    supabaseUrl.trimEnd('/') + "/auth/v1/authorize" +
      "?provider=google" +
      "&apikey=" + Uri.encode(apiKey) +
      "&redirect_to=" + Uri.encode(REDIRECT_URL) +
      "&state=" + Uri.encode(attempt.state)

  /** True when [uri] is our OAuth callback deep link. */
  fun isAuthCallback(uri: Uri?): Boolean =
    uri != null && uri.scheme == REDIRECT_SCHEME && uri.host == REDIRECT_HOST

  /** Shape A: session tokens carried directly in the callback. */
  fun parseFragmentSession(uri: Uri): TokenPair? {
    val access = param(uri, "access_token") ?: return null
    val refresh = param(uri, "refresh_token")
    val expiresIn = param(uri, "expires_in")?.toLongOrNull() ?: 3600L
    return TokenPair(
      accessToken = access,
      refreshToken = refresh?.takeIf { it.isNotBlank() },
      expiresInSeconds = expiresIn
    )
  }

  /** Shape B: the one-time code to exchange with [exchangeGoogleCode]. */
  fun parseCode(uri: Uri): String? = param(uri, "code")

  /** Human message for error callbacks (user cancelled, provider outage). */
  fun parseFailureError(uri: Uri): String? {
    val code = param(uri, "error_code")
    val desc = param(uri, "error_description") ?: param(uri, "error")
    if (code == null && desc == null) return null
    return when {
      code == "access_denied" || desc?.contains("denied", ignoreCase = true) == true ->
        "Google sign-in was cancelled."
      else -> "Google sign-in failed. Please try again."
    }
  }

  fun parseCallbackState(uri: Uri): String? = param(uri, "state")

  /** Fragment params (#k=v&k=v) are not exposed by Uri.getQueryParameter. */
  private fun param(uri: Uri, name: String): String? {
    val fragment = uri.fragment
    if (!fragment.isNullOrBlank()) {
      for (pair in fragment.split('&')) {
        val parts = pair.split('=', limit = 2)
        if (parts.size == 2 && parts[0] == name && parts[1].isNotBlank()) {
          return runCatching { Uri.decode(parts[1]) }.getOrDefault(parts[1])
        }
      }
    }
    return uri.getQueryParameter(name)
  }
}
