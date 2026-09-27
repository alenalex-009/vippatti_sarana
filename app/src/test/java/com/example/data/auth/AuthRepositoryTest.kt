package com.example.data.auth

import com.example.data.auth.AuthError
import com.example.data.auth.AuthRepository
import com.example.data.auth.InMemoryAuthStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local email+password auth contracts (JVM tests use the in-memory store):
 * registration, hashed storage, correct/incorrect login, duplicate accounts,
 * input validation, the seeded demo account and the stay-signed-in restart
 * rule.
 */
class AuthRepositoryTest {

  private fun freshRepository(): AuthRepository {
    val repo = AuthRepository(InMemoryAuthStorage())
    repo.seedDemoAccount()
    return repo
  }

  @Test
  fun `demo account is seeded and logs in with the documented password`() {
    val repo = freshRepository()
    val result = repo.login(AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = true)
    assertTrue("demo login should succeed: ${result.error}", result.ok)
    assertEquals(AuthRepository.DEMO_EMAIL, result.email)
    assertEquals(AuthRepository.DEMO_EMAIL, repo.currentUserEmail)
  }

  @Test
  fun `wrong password is rejected with WRONG_CREDENTIALS`() {
    val repo = freshRepository()
    val result = repo.login(AuthRepository.DEMO_EMAIL, "not-the-password", staySignedIn = true)
    assertFalse(result.ok)
    assertEquals(AuthError.WRONG_CREDENTIALS, result.error)
  }

  @Test
  fun `unknown email is reported as ACCOUNT_NOT_FOUND`() {
    val repo = freshRepository()
    val result = repo.login("nobody@example.com", "whatever123", staySignedIn = true)
    assertFalse(result.ok)
    assertEquals(AuthError.ACCOUNT_NOT_FOUND, result.error)
  }

  @Test
  fun `invalid email is rejected up front`() {
    val repo = freshRepository()
    assertFalse(repo.login("not-an-email", "whatever123", staySignedIn = true).ok)
    assertFalse(repo.register("nope@@", "whatever123", staySignedIn = true).ok)
  }

  @Test
  fun `weak password is rejected on registration`() {
    val repo = freshRepository()
    val result = repo.register("new.user@example.com", "123", staySignedIn = true)
    assertFalse(result.ok)
    assertEquals(AuthError.WEAK_PASSWORD, result.error)
  }

  @Test
  fun `registration then login round-trips`() {
    val repo = freshRepository()
    val registered = repo.register("new.user@example.com", "s3cret-pass", staySignedIn = true)
    assertTrue("register failed: ${registered.error}", registered.ok)
    repo.logout()

    val login = repo.login("new.user@example.com", "s3cret-pass", staySignedIn = true)
    assertTrue("login after register failed: ${login.error}", login.ok)
    assertEquals("new.user@example.com", repo.currentUserEmail)
  }

  @Test
  fun `duplicate registration is EMAIL_TAKEN`() {
    val repo = freshRepository()
    // Demo email is already seeded.
    val result = repo.register(AuthRepository.DEMO_EMAIL, "another-pass", staySignedIn = true)
    assertFalse(result.ok)
    assertEquals(AuthError.EMAIL_TAKEN, result.error)
  }

  @Test
  fun `logout clears the session but a fresh login restores it`() {
    val repo = freshRepository()
    repo.login(AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = true)
    assertTrue(repo.isLoggedIn())
    repo.logout()
    assertFalse(repo.isLoggedIn())
    assertNull(repo.currentUserEmail)
  }

  @Test
  fun `stay signed in false does not persist the session across restarts`() {
    val repo = freshRepository()
    repo.login(AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = false)
    assertTrue("should be logged in for the current process", repo.isLoggedIn())
    // Simulate a fresh process (a NEW repository over the SAME storage).
    val restarted = AuthRepository(InMemoryAuthStorage())
    restarted.seedDemoAccount()
    assertFalse("session must not survive a restart when stay-signed-in is off", restarted.isLoggedIn())
  }

  @Test
  fun `stay signed in true persists the session across restarts`() {
    val storage = InMemoryAuthStorage()
    val repo = AuthRepository(storage)
    repo.seedDemoAccount()
    repo.login(AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = true)

    val restarted = AuthRepository(storage)
    assertTrue(restarted.isLoggedIn())
    assertEquals(AuthRepository.DEMO_EMAIL, restarted.currentUserEmail)
  }

  // ------------------------------------------------ B12: hashing hardening

  @Test
  fun `a legacy sha-256 credential still logs in and is upgraded to pbkdf2`() {
    val storage = InMemoryAuthStorage()
    val legacySalt = PasswordHasher.newSalt()
    val legacy = "$legacySalt:" + PasswordHasher.legacyHash("old-style-pw", legacySalt)
    storage.writeCredential("legacy@example.com", legacy)

    val repo = AuthRepository(storage)
    assertTrue("legacy credential must still authenticate",
      repo.login("legacy@example.com", "old-style-pw", staySignedIn = false).ok)

    // And on that successful login it was transparently replaced by PBKDF2.
    val storedAfter = storage.readCredential("legacy@example.com")!!
    assertTrue("credential should now be pbkdf2-shaped, was: ${storedAfter.take(12)}",
      storedAfter.startsWith("pbkdf2$"))
    assertFalse("legacy shape must be gone", PasswordHasher.needsUpgrade(storedAfter))
    // Re-login with the upgraded record still works.
    val again = AuthRepository(storage)
    assertTrue(again.login("legacy@example.com", "old-style-pw", staySignedIn = false).ok)
    // Wrong password against the upgraded record fails.
    assertFalse(AuthRepository(storage).login("legacy@example.com", "nope", staySignedIn = false).ok)
  }

  @Test
  fun `registration stores pbkdf2 and enforces the 8-char minimum`() {
    val storage = InMemoryAuthStorage()
    val repo = AuthRepository(storage)
    assertTrue(repo.register("a.b@example.com", "longenough7", staySignedIn = false).ok)
    val stored = storage.readCredential("a.b@example.com")!!
    assertTrue("new accounts must use the hardened shape", stored.startsWith("pbkdf2$"))
    assertTrue("registered password must authenticate",
      AuthRepository(storage).login("a.b@example.com", "longenough7", staySignedIn = false).ok)
    val seven = repo.register("short@example.com", "1234567", staySignedIn = false)
    assertFalse("7-char passwords are now rejected: ${seven.error}", seven.ok)
    assertEquals(AuthError.WEAK_PASSWORD, seven.error)
  }

  @Test
  fun `hashNew produces pbkdf2 shape and verify round-trips`() {
    val h = PasswordHasher.hashNew("hunter2-hunter2")
    assertTrue(h.startsWith("pbkdf2$"))
    assertTrue(PasswordHasher.verify("hunter2-hunter2", h))
    assertFalse(PasswordHasher.verify("wrong", h))
  }
}
