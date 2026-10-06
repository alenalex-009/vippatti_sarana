package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
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

  private val prefs: SharedPreferences = createEncryptedPrefs(context)

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

    /** Tink's shared keyset file backing [FILE_NAME] on this app. */
    private const val TINK_KEYSET_PREFS = "__androidx_security_crypto_encrypted_prefs__"

    /**
     * Deletes the encrypted session file AND the Tink keyset that encrypts it.
     * Only safe because TokenStorage is the sole EncryptedSharedPreferences
     * user in this app (verified), and the payload is only session tokens:
     * the worst case is the user signs in again - the server-side session is
     * untouched.
     */
    private fun wipeCorruptStorage(context: Context) {
      runCatching { context.deleteSharedPreferences(FILE_NAME) }
      runCatching {
        context.getSharedPreferences(TINK_KEYSET_PREFS, Context.MODE_PRIVATE)
          .edit().clear().apply()
      }
    }

    /**
     * FIRST-LAUNCH CRASH FIX: EncryptedSharedPreferences.create() used to run
     * unguarded here, and on a device whose stored Tink keyset can no longer be
     * decrypted (AEADBadTagException - stale Keystore key after reinstall,
     * backup restore, or vendor keystore reset) it threw straight out of the
     * first composition, crash-looping the app before ANY UI could appear.
     *
     * The creation is now self-healing: if the stored keyset cannot be
     * decrypted, both the keyset and the session file it encrypts are wiped and
     * a fresh keyset is created. The app starts normally; the only cost is one
     * re-login. A failure of the RETRY itself is genuinely unrecoverable and
     * propagates honestly.
     */
    private fun createEncryptedPrefs(context: Context): SharedPreferences {
      return try {
        openEncryptedPrefs(context)
      } catch (e: GeneralSecurityException) {
        android.util.Log.w(
          "TokenStorage",
          "Stored encrypted-session keyset unreadable (${e.javaClass.simpleName}); resetting it."
        )
        wipeCorruptStorage(context)
        openEncryptedPrefs(context)
      } catch (e: IOException) {
        android.util.Log.w(
          "TokenStorage",
          "Stored encrypted-session file unreadable (${e.javaClass.simpleName}); resetting it."
        )
        wipeCorruptStorage(context)
        openEncryptedPrefs(context)
      }
    }

    private fun openEncryptedPrefs(context: Context): SharedPreferences {
      val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
      return EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
      )
    }
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
