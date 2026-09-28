package com.example.data.model

/**
 * Editable citizen identity shown on the Profile tab and attached to every
 * SOS broadcast / NDRF situation report.
 *
 * ONE schema for the whole account flow: the Registration form collects exactly
 * these fields, the signed-in account's values are persisted per account email
 * (see AuthRepository.saveProfile / loadProfile) and the Profile tab plus the
 * profile editor read the same object back. Nothing here is per-feature.
 *
 * The constructor defaults are the LEGACY baseline used by engine unit tests
 * (capacity/relocation maths asserts against the pilot household size). A real
 * signed-in account never renders them: the ViewModel loads that account's
 * stored profile, or [blank] when the account has no stored profile yet — see
 * VippattiViewModel.syncSignedInAccountProfile.
 *
 * The vulnerable-category set and medical-support flag feed the shelter
 * prioritization engines (SafeZoneEvaluator / RelocationPlanner).
 */
data class UserProfile(
  val fullName: String = "Aditya Vardhan",
  val citizenId: String = "SARANA-AP-89241",
  /** The account holder's own phone number (emergency callback). */
  val phone: String = "",
  val bloodGroup: String = "O+ POSITIVE",
  val medicalTag: String = "Asthma / Inhaler",
  val medicalNotes: String = "Requires Mobility Support",
  val dependentsCount: Int = 3,
  val dependentsDetail: String = "1 Elder, 1 Child (4yo), Spouse",
  /** IDs from RelocationPlanner.VULNERABLE_CATEGORIES (elderly/children/...). */
  val vulnerableCategoryIds: Set<String> = setOf("elderly", "children"),
  /** True when the household needs a shelter with on-site medical support. */
  val needsMedicalSupport: Boolean = false
) {
  val bloodGroupLabel: String get() = bloodGroup.trim().uppercase()
  val dependentsLabel: String get() = "$dependentsCount Dependents"

  companion object {
    /**
     * Empty profile for a fresh / unregistered account: every field belongs to
     * the user, so nothing is pre-filled with a sample identity. Optional
     * fields deliberately arrive blank — that is the honest "not provided yet"
     * state the Profile screen and editor render.
     */
    fun blank(): UserProfile = UserProfile(
      fullName = "",
      citizenId = "",
      phone = "",
      bloodGroup = "",
      medicalTag = "",
      medicalNotes = "",
      dependentsCount = 0,
      dependentsDetail = "",
      vulnerableCategoryIds = emptySet(),
      needsMedicalSupport = false
    )
  }
}
