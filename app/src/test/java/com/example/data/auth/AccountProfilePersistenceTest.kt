package com.example.data.auth

import com.example.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Account-profile persistence contracts for the Registration -> Profile flow.
 *
 * A local credential store alone is not enough: the values the user types on
 * the Registration form must become the PROFILE of that account, they must come
 * back on the next launch, and one account's identity must never appear for
 * another account. These tests drive the same repository + the same in-memory
 * store the production code uses (JVM tests cannot touch SharedPreferences).
 */
class AccountProfilePersistenceTest {

  /** A fully filled registration form, field for field. */
  private fun filledProfile() = UserProfile(
    fullName = "Meera Krishnan",
    citizenId = "SARANA-AP-10231",
    phone = "+91 98470 11223",
    bloodGroup = "B+",
    medicalTag = "Diabetes / Insulin",
    medicalNotes = "Needs cold storage for insulin",
    dependentsCount = 2,
    dependentsDetail = "1 Elder, 1 Child (6yo)",
    vulnerableCategoryIds = setOf("elderly"),
    needsMedicalSupport = true
  )

  /** The form's state when every optional field is left empty. */
  private fun minimalProfile(name: String) = UserProfile.blank().copy(fullName = name)

  @Test
  fun `registration stores the profile typed on the form, value for value`() {
    val repo = AuthRepository(InMemoryAuthStorage())
    val entered = filledProfile()

    val result = repo.register(
      RegistrationRequest("meera@example.com", "longenough7", staySignedIn = true, profile = entered)
    )

    assertTrue("register failed: ${result.error}", result.ok)
    assertEquals(entered, repo.loadProfile("meera@example.com"))
    assertEquals(true, repo.currentUserProfile()?.needsMedicalSupport)
  }

  @Test
  fun `optional fields left empty stay empty instead of becoming sample values`() {
    val repo = AuthRepository(InMemoryAuthStorage())
    val entered = minimalProfile("Ravi Kumar")

    repo.register(
      RegistrationRequest("ravi@example.com", "longenough7", staySignedIn = true, profile = entered)
    )

    val stored = repo.loadProfile("ravi@example.com")
    assertEquals(entered, stored)
    assertEquals("", stored?.citizenId)
    assertEquals("", stored?.phone)
    assertEquals("", stored?.bloodGroup)
    assertEquals("", stored?.medicalTag)
    assertEquals("", stored?.medicalNotes)
    assertEquals(0, stored?.dependentsCount)
    assertEquals("", stored?.dependentsDetail)
    assertEquals(emptySet<String>(), stored?.vulnerableCategoryIds)
  }

  @Test
  fun `a rejected registration leaves no profile behind`() {
    val repo = AuthRepository(InMemoryAuthStorage())

    val weak = repo.register(
      RegistrationRequest("meera@example.com", "short", staySignedIn = true, profile = filledProfile())
    )
    assertEquals(AuthError.WEAK_PASSWORD, weak.error)
    assertNull(repo.loadProfile("meera@example.com"))

    // Same rule for a duplicate email: the seeded demo account keeps whatever
    // profile it has, and the rejected attempt never writes over it.
    repo.seedDemoAccount()
    val duplicate = repo.register(
      RegistrationRequest(
        AuthRepository.DEMO_EMAIL,
        "longenough7",
        staySignedIn = true,
        profile = filledProfile()
      )
    )
    assertEquals(AuthError.EMAIL_TAKEN, duplicate.error)
    assertNull(repo.loadProfile(AuthRepository.DEMO_EMAIL))
  }

  @Test
  fun `each account keeps its own profile`() {
    val repo = AuthRepository(InMemoryAuthStorage())
    repo.register(
      RegistrationRequest("a@example.com", "password-a1", staySignedIn = false, profile = filledProfile())
    )
    repo.register(
      RegistrationRequest(
        "b@example.com",
        "password-b1",
        staySignedIn = false,
        profile = minimalProfile("Anand Rao").copy(citizenId = "SARANA-AP-55001")
      )
    )

    assertEquals("Meera Krishnan", repo.loadProfile("a@example.com")?.fullName)
    assertEquals("B+", repo.loadProfile("a@example.com")?.bloodGroup)
    assertEquals("Anand Rao", repo.loadProfile("b@example.com")?.fullName)
    assertEquals("SARANA-AP-55001", repo.loadProfile("b@example.com")?.citizenId)
    // Account B must not inherit anything from account A.
    assertEquals("", repo.loadProfile("b@example.com")?.bloodGroup)
    assertEquals("", repo.loadProfile("b@example.com")?.phone)
    assertEquals(emptySet<String>(), repo.loadProfile("b@example.com")?.vulnerableCategoryIds)
  }

  @Test
  fun `the stored profile survives a restart for the same account`() {
    val storage = InMemoryAuthStorage()
    AuthRepository(storage).register(
      RegistrationRequest("meera@example.com", "longenough7", staySignedIn = true, profile = filledProfile())
    )

    // Cold start: a NEW repository over the SAME device storage.
    val restarted = AuthRepository(storage)
    assertTrue(restarted.login("meera@example.com", "longenough7", staySignedIn = true).ok)
    assertEquals("Meera Krishnan", restarted.currentUserProfile()?.fullName)
    assertEquals("+91 98470 11223", restarted.currentUserProfile()?.phone)
    assertEquals(setOf("elderly"), restarted.currentUserProfile()?.vulnerableCategoryIds)
  }

  @Test
  fun `a later Profile edit is saved for that account only`() {
    val repo = AuthRepository(InMemoryAuthStorage())
    repo.register(
      RegistrationRequest("a@example.com", "password-a1", staySignedIn = false, profile = minimalProfile("A"))
    )
    repo.register(
      RegistrationRequest("b@example.com", "password-b1", staySignedIn = false, profile = minimalProfile("B"))
    )

    // The Profile editor's save path.
    repo.saveProfile(
      "a@example.com",
      minimalProfile("A").copy(bloodGroup = "AB-", medicalNotes = "Asthma / Inhaler")
    )

    assertEquals("AB-", repo.loadProfile("a@example.com")?.bloodGroup)
    assertEquals("Asthma / Inhaler", repo.loadProfile("a@example.com")?.medicalNotes)
    assertEquals("", repo.loadProfile("b@example.com")?.bloodGroup)
    assertEquals("", repo.loadProfile("b@example.com")?.medicalNotes)
  }

  @Test
  fun `an account that never saved a profile reads as none, not as a sample identity`() {
    val repo = AuthRepository(InMemoryAuthStorage()).apply { seedDemoAccount() }

    // The demo account has credentials but no stored profile: the caller must
    // show an EMPTY profile, never another user's or an invented identity.
    assertNull(repo.loadProfile(AuthRepository.DEMO_EMAIL))
    assertNull(repo.loadProfile(null))
    assertNull(repo.loadProfile(""))
    assertNull(repo.loadProfile("nobody@example.com"))
    assertNull(repo.currentUserProfile())
  }

  @Test
  fun `profile lookup is case-insensitive like the account itself`() {
    val repo = AuthRepository(InMemoryAuthStorage())
    repo.register(
      RegistrationRequest("Meera@Example.com", "longenough7", staySignedIn = false, profile = filledProfile())
    )

    assertEquals("Meera Krishnan", repo.loadProfile("meera@example.com")?.fullName)
    assertEquals("Meera Krishnan", repo.loadProfile("MEERA@EXAMPLE.COM")?.fullName)
    assertEquals("Meera Krishnan", repo.loadProfile("  Meera@Example.com  ")?.fullName)
  }

  @Test
  fun `the registered profile is the account's own, not the legacy household baseline`() {
    val repo = AuthRepository(InMemoryAuthStorage())
    repo.register(
      RegistrationRequest(
        "ravi@example.com",
        "longenough7",
        staySignedIn = true,
        profile = minimalProfile("Ravi Kumar")
      )
    )

    val stored = repo.loadProfile("ravi@example.com")!!
    assertTrue(
      "the model's legacy sample name leaked into a real account",
      stored.fullName != "Aditya Vardhan"
    )
    assertEquals(0, stored.dependentsCount)
    assertEquals(emptySet<String>(), stored.vulnerableCategoryIds)
  }
}
