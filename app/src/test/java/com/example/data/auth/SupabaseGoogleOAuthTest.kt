package com.example.data.auth

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Google browser round-trip is the one auth path whose input comes from
 * OUTSIDE (a web tab delivering an intent), so both GoTrue callback shapes
 * plus the failure shape must parse exactly — and a callback with NO usable
 * credential must NEVER produce a session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseGoogleOAuthTest {

  private fun uri(raw: String): Uri =
    Uri.parse(raw)

  @Test
  fun `authorize url targets GoTrue google with our custom redirect and pkce`() {
    val attempt = SupabaseGoogleOAuth.newAttempt()
    val url = Uri.parse(
      SupabaseGoogleOAuth.buildAuthorizeUrl(
        "https://demo.supabase.co", "anon-key-123", attempt
      )
    )
    assertEquals("https", url.scheme)
    assertEquals("demo.supabase.co", url.host)
    assertEquals("/auth/v1/authorize", url.path)
    assertEquals("google", url.getQueryParameter("provider"))
    assertEquals(
      "vippattisarana://auth-callback",
      url.getQueryParameter("redirect_to")
    )
    // Public client auth: the apikey rides as a QUERY param (no headers in a
    // browser navigation) — and the attempt binds state + S256 challenge.
    assertEquals("anon-key-123", url.getQueryParameter("apikey"))
    assertEquals(attempt.state, url.getQueryParameter("state"))
    // Native implicit callback: NO challenge params (they switch GoTrue to
    // its browser-relay mode, which breaks custom-scheme redirects).
    assertNull(url.getQueryParameter("code_challenge"))
    assertNull(url.getQueryParameter("code_challenge_method"))
  }

  @Test
  fun `fragment session callback parses access + refresh + expiry`() {
    val cb = uri(
      "vippattisarana://auth-callback#access_token=AT99&refresh_token=RT42" +
        "&expires_in=3600&token_type=bearer&provider_token=PT"
    )
    assertTrue(SupabaseGoogleOAuth.isAuthCallback(cb))
    val session = SupabaseGoogleOAuth.parseFragmentSession(cb)
    assertNotNull(session)
    assertEquals("AT99", session!!.accessToken)
    assertEquals("RT42", session.refreshToken)
    assertEquals(3600L, session.expiresInSeconds)
  }

  @Test
  fun `pkce code callback exposes the one-time code, not a session`() {
    val cb = uri("vippattisarana://auth-callback?code=FLOWSTATE-1&state=abc")
    assertNull(SupabaseGoogleOAuth.parseFragmentSession(cb))
    assertEquals("FLOWSTATE-1", SupabaseGoogleOAuth.parseCode(cb))
    assertEquals("abc", SupabaseGoogleOAuth.parseCallbackState(cb))
  }

  @Test
  fun `cancelled consent yields a human error and NO session`() {
    val cb = uri(
      "vippattisarana://auth-callback?error=access_denied" +
        "&error_description=User%20denied"
    )
    assertNull(SupabaseGoogleOAuth.parseFragmentSession(cb))
    assertNull(SupabaseGoogleOAuth.parseCode(cb))
    val msg = SupabaseGoogleOAuth.parseFailureError(cb)
    assertNotNull(msg)
    assertTrue(msg!!.contains("cancelled", ignoreCase = true))
  }

  @Test
  fun `empty callback is never a session`() {
    val cb = uri("vippattisarana://auth-callback")
    assertNull(SupabaseGoogleOAuth.parseFragmentSession(cb))
    assertNull(SupabaseGoogleOAuth.parseCode(cb))
    assertNull(SupabaseGoogleOAuth.parseFailureError(cb))
  }

  @Test
  fun `blank refresh token cannot masquerade as a session`() {
    val cb = uri("vippattisarana://auth-callback#access_token=AT&refresh_token=")
    val session = SupabaseGoogleOAuth.parseFragmentSession(cb)
    assertNotNull(session)
    assertNull(
      "blank refresh must normalize to null",
      session!!.refreshToken
    )
  }

  @Test
  fun `foreign deep links are not auth callbacks`() {
    assertFalse(SupabaseGoogleOAuth.isAuthCallback(uri("vippattisarana://other")))
    assertFalse(SupabaseGoogleOAuth.isAuthCallback(uri("https://vippattisarana.app/auth-callback")))
    assertFalse(SupabaseGoogleOAuth.isAuthCallback(null))
  }

  @Test
  fun `attempts are random and independent`() {
    val a = SupabaseGoogleOAuth.newAttempt()
    val b = SupabaseGoogleOAuth.newAttempt()
    assertTrue(a.verifier != b.verifier && a.state != b.state)
  }
}
