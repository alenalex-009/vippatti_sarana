package com.example.data.auth

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SupabaseAuthApiServiceTest {

  private fun createService(
    statusCode: Int,
    responseBody: String,
    onInterceptRequest: (okhttp3.Request) -> Unit = {}
  ): SupabaseAuthApiService {
    val interceptor = Interceptor { chain ->
      val request = chain.request()
      onInterceptRequest(request)
      Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(statusCode)
        .message(if (statusCode in 200..299) "OK" else "Error")
        .body(responseBody.toResponseBody("application/json".toMediaType()))
        .build()
    }

    val client = OkHttpClient.Builder()
      .addInterceptor(interceptor)
      .connectTimeout(2, TimeUnit.SECONDS)
      .readTimeout(2, TimeUnit.SECONDS)
      .build()

    return SupabaseAuthApiService(
      supabaseUrl = "https://mock.supabase.co",
      apiKey = "mock-anon-key",
      httpClient = client
    )
  }

  @Test
  fun `register returns success with tokens and user`() = runTest {
    val json = """
      {
        "access_token": "sb-access-123",
        "token_type": "bearer",
        "expires_in": 3600,
        "refresh_token": "sb-refresh-123",
        "user": {
          "id": "usr-uuid-1",
          "email": "sarana.citizen@example.com",
          "user_metadata": {
            "full_name": "Citizen One"
          },
          "created_at": "2026-09-29T00:00:00Z",
          "updated_at": "2026-09-29T00:00:00Z"
        }
      }
    """.trimIndent()

    var checkedApiKey = false
    val service = createService(200, json) { req ->
      assertEquals("mock-anon-key", req.header("apikey"))
      assertEquals("Bearer mock-anon-key", req.header("Authorization"))
      assertTrue(req.url.toString().contains("/auth/v1/signup"))
      checkedApiKey = true
    }

    val result = service.register("Citizen One", "sarana.citizen@example.com", "SecretPass123!")
    assertTrue(checkedApiKey)
    assertTrue(result is AuthApiResult.Success)
    val success = result as AuthApiResult.Success
    assertEquals("sb-access-123", success.tokens.accessToken)
    assertEquals("sb-refresh-123", success.tokens.refreshToken)
    assertEquals("Citizen One", success.user.fullName)
    assertEquals("sarana.citizen@example.com", success.user.email)
    assertEquals("usr-uuid-1", success.user.id)
  }

  @Test
  fun `login returns success with user metadata`() = runTest {
    val json = """
      {
        "access_token": "sb-access-456",
        "token_type": "bearer",
        "expires_in": 3600,
        "refresh_token": "sb-refresh-456",
        "user": {
          "id": "usr-uuid-2",
          "email": "responder@example.com",
          "user_metadata": {
            "full_name": "Field Responder"
          },
          "created_at": "2026-09-29T00:00:00Z",
          "updated_at": "2026-09-29T00:00:00Z"
        }
      }
    """.trimIndent()

    val service = createService(200, json)
    val result = service.login("responder@example.com", "SecretPass123!")
    assertTrue(result is AuthApiResult.Success)
    val success = result as AuthApiResult.Success
    assertEquals("sb-access-456", success.tokens.accessToken)
    assertEquals("Field Responder", success.user.fullName)
  }

  @Test
  fun `login maps invalid credentials error cleanly`() = runTest {
    val json = """
      {
        "error": "invalid_grant",
        "error_description": "Invalid login credentials",
        "msg": "Invalid login credentials"
      }
    """.trimIndent()

    val service = createService(400, json)
    val result = service.login("wrong@example.com", "badpass")
    assertTrue(result is AuthApiResult.Failure)
    val failure = result as AuthApiResult.Failure
    assertEquals(ApiErrorKind.INVALID_CREDENTIALS, failure.errorKind)
    assertEquals("Incorrect email or password.", failure.errorMessage)
  }

  @Test
  fun `login maps unconfirmed email error with helpful advice`() = runTest {
    val json = """
      {
        "code": 400,
        "error_code": "email_not_confirmed",
        "msg": "Email not confirmed"
      }
    """.trimIndent()

    val service = createService(400, json)
    val result = service.login("unconfirmed@example.com", "secretpass")
    assertTrue(result is AuthApiResult.Failure)
    val failure = result as AuthApiResult.Failure
    assertEquals(ApiErrorKind.INVALID_CREDENTIALS, failure.errorKind)
    assertTrue(failure.errorMessage.contains("Please check your email"))
  }

  @Test
  fun `me returns user profile with access token`() = runTest {
    val json = """
      {
        "id": "usr-uuid-me",
        "email": "me@example.com",
        "user_metadata": {
          "full_name": "Meera Krishnan"
        },
        "created_at": "2026-09-29T00:00:00Z",
        "updated_at": "2026-09-29T00:00:00Z"
      }
    """.trimIndent()

    val service = createService(200, json)
    val result = service.me("valid-token")
    assertTrue(result is AuthApiResult.Success)
    val success = result as AuthApiResult.Success
    assertEquals("Meera Krishnan", success.user.fullName)
    assertEquals("me@example.com", success.user.email)
  }

  @Test
  fun `logout completes successfully`() = runTest {
    val service = createService(204, "")
    val result = service.logout("valid-token", "valid-refresh")
    assertEquals(SimpleApiResult.Success, result)
  }
}
