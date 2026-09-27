package com.example.data.auth

import android.content.Context
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Locale
import kotlin.random.Random

/** Why a login/registration attempt failed (shown verbatim on the login page). */
enum class AuthError {
  INVALID_EMAIL,
  WEAK_PASSWORD,
  EMAIL_TAKEN,
  ACCOUNT_NOT_FOUND,
  WRONG_CREDENTIALS
}

/** Outcome of a login/registration attempt; [ok] false means [error] is set. */
data class AuthResult(
  val ok: Boolean,
  val email: String = "",
  val error: AuthError? = null
) {
  companion object {
    fun success(email: String) = AuthResult(ok = true, email = email)
    fun failure(error: AuthError) = AuthResult(ok = false, error = error)
  }
}

/**
 * Backing store for local credentials and the session flag. Abstracted so the
 * repository is unit-testable with an in-memory store (JVM tests have no
 * Android SharedPreferences).
 */
interface AuthStorage {
  fun isLoggedIn(): Boolean
  fun setLoggedIn(value: Boolean)
  fun isStaySignedIn(): Boolean
  fun setStaySignedIn(value: Boolean)
  fun lastEmail(): String?
  fun setLastEmail(value: String?)
  /** Stored "salt:hash" for [email], or null when no account exists. */
  fun readCredential(email: String): String?
  fun writeCredential(email: String, stored: String)
}

/** SharedPreferences-backed [AuthStorage] used in production. */
class SharedPrefsAuthStorage(context: Context) : AuthStorage {

  private val prefs = context.getSharedPreferences("vippatti_sarana_auth", Context.MODE_PRIVATE)

  private fun credKey(email: String) = "cred_${email.trim().lowercase(Locale.ROOT)}"

  override fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_LOGGED_IN, false)
  override fun setLoggedIn(value: Boolean) { prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply() }

  override fun isStaySignedIn(): Boolean = prefs.getBoolean(KEY_STAY_SIGNED_IN, false)
  override fun setStaySignedIn(value: Boolean) { prefs.edit().putBoolean(KEY_STAY_SIGNED_IN, value).apply() }

  override fun lastEmail(): String? = prefs.getString(KEY_LAST_EMAIL, null)
  override fun setLastEmail(value: String?) {
    prefs.edit().putString(KEY_LAST_EMAIL, value).apply()
  }

  override fun readCredential(email: String): String? = prefs.getString(credKey(email), null)
  override fun writeCredential(email: String, stored: String) {
    prefs.edit().putString(credKey(email), stored).apply()
  }

  private companion object {
    const val KEY_LOGGED_IN = "logged_in"
    const val KEY_STAY_SIGNED_IN = "stay_signed_in"
    const val KEY_LAST_EMAIL = "last_email"
  }
}

/** In-memory [AuthStorage] for JVM unit tests. */
class InMemoryAuthStorage : AuthStorage {
  private val credentials = mutableMapOf<String, String>()
  private var loggedIn = false
  private var staySignedIn = true
  private var email: String? = null

  override fun isLoggedIn(): Boolean = loggedIn
  override fun setLoggedIn(value: Boolean) { loggedIn = value }

  override fun isStaySignedIn(): Boolean = staySignedIn
  override fun setStaySignedIn(value: Boolean) { staySignedIn = value }

  override fun lastEmail(): String? = email
  override fun setLastEmail(value: String?) { email = value }

  override fun readCredential(email: String): String? =
    credentials[email.trim().lowercase(Locale.ROOT)]

  override fun writeCredential(email: String, stored: String) {
    credentials[email.trim().lowercase(Locale.ROOT)] = stored
  }
}

/**
 * Local password hashing. AUDIT B12 HARDENING (2026-09-25):
 *
 * NEW credentials are stored as "pbkdf2$<iter>$<saltHex>$<hashHex>" - 120k
 * iterations of PBKDF2-HMAC-SHA256 with a fresh 16-byte salt (the previous
 * single-round SHA-256 over an 8-byte salt is fast to brute-force if the app
 * private data is ever extracted).
 *
 * EXISTING credentials keep the legacy "saltHex:sha256Hex" shape and are
 * VERIFIED against the legacy algorithm, then transparently upgraded on next
 * successful login (see AuthRepository.login). Nobody is locked out, and no
 * plaintext or recoverable secret is ever stored. Verification of both shapes
 * is constant-time.
 */
object PasswordHasher {

  private const val PBKDF2_ITERATIONS = 120_000
  private const val PBKDF2_KEY_BITS = 256
  private const val PREFIX = "pbkdf2"

  fun newSalt(): String = Random.Default.nextBytes(16).joinToString("") { "%02x".format(it) }

  /** Modern hash: the full versioned string, salt embedded. */
  fun hashNew(password: String): String {
    val salt = newSalt()
    val hash = pbkdf2(password, salt, PBKDF2_ITERATIONS)
    return PREFIX + "$" + PBKDF2_ITERATIONS + "$" + salt + "$" + hash
  }

  /** Legacy shape kept ONLY so existing stored credentials stay verifiable. */
  fun legacyHash(password: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update((salt + ":" + password).toByteArray(Charsets.UTF_8))
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun pbkdf2(password: String, saltHex: String, iterations: Int): String {
    val saltBytes = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val spec = PBEKeySpec(password.toCharArray(), saltBytes, iterations, PBKDF2_KEY_BITS)
    val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      .generateSecret(spec).encoded
    return key.joinToString("") { "%02x".format(it) }
  }

  /** Verify a candidate password against a stored credential of EITHER shape. */
  fun verify(password: String, stored: String): Boolean {
    if (stored.startsWith("$PREFIX$")) {
      val parts = stored.split("$")
      if (parts.size != 4) return false
      val iterations = parts[1].toIntOrNull() ?: return false
      val computed = pbkdf2(password, parts[2], iterations)
      return MessageDigest.isEqual(
        computed.toByteArray(Charsets.UTF_8), parts[3].toByteArray(Charsets.UTF_8)
      )
    }
    val separator = stored.indexOf(':')
    if (separator <= 0) return false
    val salt = stored.substring(0, separator)
    val expected = stored.substring(separator + 1)
    return constantTimeEquals(legacyHash(password, salt), expected)
  }

  /** True when a stored credential is legacy and should be upgraded on login. */
  fun needsUpgrade(stored: String): Boolean = !stored.startsWith("$PREFIX$")

  private fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
    return diff == 0
  }
}

/**
 * Local email + password auth. Runs entirely offline: accounts are seeded and
 * kept in app-local storage, hashed with a per-account salt. Works without
 * any network, which is exactly what a disaster-relief app must do.
 */
class AuthRepository(private val storage: AuthStorage) {

  /** True across app restarts ONLY when the user asked to stay signed in. */
  fun isLoggedIn(): Boolean =
    storage.isLoggedIn() && (storage.isStaySignedIn() || processLoggedIn)

  val currentUserEmail: String?
    get() = if (isLoggedIn()) storage.lastEmail() else null

  /** Marks the in-process session (for the stay-signed-in = false restart case). */
  private var processLoggedIn: Boolean = false

  fun login(email: String, password: String, staySignedIn: Boolean): AuthResult {
    val clean = normalizeEmail(email) ?: return AuthResult.failure(AuthError.INVALID_EMAIL)
    val stored = storage.readCredential(clean)
      ?: return AuthResult.failure(AuthError.ACCOUNT_NOT_FOUND)
    if (!PasswordHasher.verify(password, stored)) {
      return AuthResult.failure(AuthError.WRONG_CREDENTIALS)
    }
    // Transparent hardening (audit B12): a legacy SHA-256 credential that
    // just authenticated correctly is rewritten as PBKDF2 on the spot. The
    // user notices nothing; extracted files get progressively stronger.
    if (PasswordHasher.needsUpgrade(stored)) {
      storage.writeCredential(clean, PasswordHasher.hashNew(password))
    }
    processLoggedIn = true
    storage.setLoggedIn(true)
    storage.setStaySignedIn(staySignedIn)
    storage.setLastEmail(clean)
    return AuthResult.success(clean)
  }

  fun register(email: String, password: String, staySignedIn: Boolean): AuthResult {
    val clean = normalizeEmail(email) ?: return AuthResult.failure(AuthError.INVALID_EMAIL)
    if (password.length < MIN_PASSWORD_LENGTH) {
      return AuthResult.failure(AuthError.WEAK_PASSWORD)
    }
    if (storage.readCredential(clean) != null) {
      return AuthResult.failure(AuthError.EMAIL_TAKEN)
    }
    storage.writeCredential(clean, PasswordHasher.hashNew(password))
    processLoggedIn = true
    storage.setLoggedIn(true)
    storage.setStaySignedIn(staySignedIn)
    storage.setLastEmail(clean)
    return AuthResult.success(clean)
  }

  fun logout() {
    processLoggedIn = false
    storage.setLoggedIn(false)
    // Keep lastEmail so the login page can prefill it next time.
  }

  /** Seeds the demo account on first run so the app is usable immediately. */
  fun seedDemoAccount() {
    if (storage.readCredential(DEMO_EMAIL) == null) {
      storage.writeCredential(DEMO_EMAIL, PasswordHasher.hashNew(DEMO_PASSWORD))
    }
  }

  private fun normalizeEmail(raw: String): String? {
    val email = raw.trim().lowercase(Locale.ROOT)
    return email.takeIf { EMAIL_REGEX.matches(it) }
  }

  companion object {
    const val DEMO_EMAIL = "demo@vippatti.in"
    const val DEMO_PASSWORD = "vippatti123"
    const val MIN_PASSWORD_LENGTH = 8
    private val EMAIL_REGEX =
      Regex("^[A-Za-z0-9.+_-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
  }
}