package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale

/**
 * Secure local storage for the REAL backend session.
 *
 * WHAT IS STORED: the access JWT, the refresh JWT and the account identity —
 * inside EncryptedSharedPreferences (AES-256 keys held in the Android
 * Keystore). Plaintext passwords are NEVER stored anywhere: the server holds
 * the bcrypt hash, the device holds only the signed tokens the server issued.
 *
 * Session restoration on app start = a stored refresh token exists. The
 * access token may be expired; the repository then silently refreshes it.
 */
class TokenStorage(context: Context) : TokenStore {

  private val masterKey: MasterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

  private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
    context,
    FILE_NAME,
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
  )

  override fun saveSession(accessToken: String, refreshToken: String?, email: String, fullName: String) {
    prefs.edit()
      .putString(KEY_ACCESS, accessToken)
      .putString(KEY_REFRESH, refreshToken)
      .putString(KEY_EMAIL, email.trim().lowercase(Locale.ROOT))
      .putString(KEY_FULL_NAME, fullName)
      .apply()
  }

  override fun updateAccessToken(accessToken: String) {
    prefs.edit().putString(KEY_ACCESS, accessToken).apply()
  }

  override fun accessToken(): String? = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() }

  override fun refreshToken(): String? = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() }

  override fun userEmail(): String? = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }

  override fun userFullName(): String? = prefs.getString(KEY_FULL_NAME, null)?.takeIf { it.isNotBlank() }

  /** Clears the whole session (logout). Tokens are revoked server-side too. */
  override fun clear() {
    prefs.edit().clear().apply()
  }

  companion object {
    private const val FILE_NAME = "vippatti_sarana_secure_session"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EMAIL = "account_email"
    private const val KEY_FULL_NAME = "account_full_name"
  }
}

/**
 * A fake TokenStore for JVM unit tests (EncryptedSharedPreferences needs a
 * real Android Keystore, which plain JVM tests do not have).
 */
class InMemoryTokenStorage : TokenStore {
  private var access: String? = null
  private var refresh: String? = null
  private var email: String? = null
  private var fullName: String? = null

  override fun saveSession(accessToken: String, refreshToken: String?, email: String, fullName: String) {
    access = accessToken
    refresh = refreshToken
    this.email = email.trim().lowercase(Locale.ROOT)
    this.fullName = fullName
  }

  override fun updateAccessToken(accessToken: String) {
    access = accessToken
  }

  override fun accessToken(): String? = access

  override fun refreshToken(): String? = refresh

  override fun userEmail(): String? = email

  override fun userFullName(): String? = fullName

  override fun clear() {
    access = null
    refresh = null
    email = null
    fullName = null
  }
}
