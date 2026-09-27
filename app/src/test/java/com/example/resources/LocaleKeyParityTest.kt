package com.example.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRODUCTION CONTRACT: every locale defines the same keys, or the difference is
 * on this file's explicit list with a reason.
 *
 * A key missing from `values-hi` does not crash; Android quietly falls back to
 * the English `values/` file. That is the failure mode worth guarding, because it
 * is invisible: a Hindi user gets one English phrase in the middle of an otherwise
 * translated screen, and no test or build step notices. `app_name` is a deliberate
 * non-translation (a brand name); the `instructions_*` block is real debt - the
 * Survival Manual renders English-only everywhere today - and listing it here is
 * what keeps that debt visible instead of inferred from a green build.
 */
class LocaleKeyParityTest {

  private val locales = StringResources.locales

  /**
   * Keys intentionally absent from every locale. Add to this list only with a
   * reason; the point of the exact-set assertion below is that it cannot grow by
   * accident.
   */
  private val notTranslatable: Set<String> = setOf(
    "app_name", // brand name, shown as-is in every locale
  )

  /**
   * Known untranslated Survival Manual strings, pending professional translation.
   * Emergency guidance is the worst place to ship a machine translation: a wrong
   * word about drowning or burns is a safety defect, so these stay English and
   * flagged until a reviewer signs them off.
   *
   * Listed one by one on purpose. Deriving it from the `instructions_` prefix would
   * make this exemption open-ended, so adding a new untranslated Guide string could
   * never fail the build - which is the whole point of the guard.
   */
  private val untranslatedGuideDebt: Set<String> = setOf(
    "instructions_back",
    "instructions_call_format",
    "instructions_critical_badge",
    "instructions_critical_empty",
    "instructions_detail_intro",
    "instructions_group_evacuation",
    "instructions_group_evacuation_sub",
    "instructions_group_immediate_safety",
    "instructions_group_immediate_safety_sub",
    "instructions_group_more",
    "instructions_group_more_sub",
    "instructions_group_recovery",
    "instructions_group_recovery_sub",
    "instructions_group_utilities",
    "instructions_group_utilities_sub",
    "instructions_group_vulnerable",
    "instructions_group_vulnerable_sub",
    "instructions_manual_subtitle",
    "instructions_manual_title",
    "instructions_offline_subtitle",
    "instructions_offline_title",
    "instructions_phase_after",
    "instructions_phase_before",
    "instructions_phase_during",
    "instructions_region_format",
    "instructions_resources_contacts",
    "instructions_resources_contacts_sub",
    "instructions_resources_evacuation",
    "instructions_resources_kit",
    "instructions_resources_kit_sub",
    "instructions_section_categories",
    "instructions_section_critical",
    "instructions_section_critical_subtitle",
    "instructions_section_resources",
    "instructions_step_format",
  )

  private val allowedAbsent = notTranslatable + untranslatedGuideDebt

  @Test
  fun `every locale is missing only the documented keys`() {
    val expected = allowedAbsent
    for (locale in locales) {
      val missing = StringResources.names(StringResources.DEFAULTS) - StringResources.names(locale)
      assertEquals(
        "$locale has undocumented missing keys (list them with a reason, or translate them): " +
          (missing - expected),
        expected,
        missing
      )
    }
  }

  @Test
  fun `no locale carries a key the default file does not have`() {
    // An orphan key is dead weight that survives lint: R.string resolves it, but
    // nothing can ever fall back to it, and it hides the real English source.
    for (locale in locales) {
      val orphans = StringResources.names(locale) - StringResources.names(StringResources.DEFAULTS)
      assertTrue("$locale has keys with no English source: $orphans", orphans.isEmpty())
    }
  }

  @Test
  fun `the documented debt is still exactly the Survival Manual and the brand name`() {
    // Stops `allowedAbsent` from quietly widening into "everything is exempt".
    assertTrue(
      "only app_name and instructions_* may be absent from a locale, found: " +
        allowedAbsent.filterNot { it == "app_name" || it.startsWith("instructions_") },
      allowedAbsent.all { it == "app_name" || it.startsWith("instructions_") }
    )
    assertTrue("the Guide debt list must not be empty while the manual is untranslated",
      untranslatedGuideDebt.isNotEmpty())
  }

  @Test
  fun `positioned string arrays have the same length in every locale`() {
    // These are read by index: profile_contact_role_keys[i] pairs with
    // profile_contact_role_labels[i]. A locale that drops an item shifts every
    // label after it, so a user picks "Emergency contact" and stores "Doctor".
    val defaults = StringResources.arraySizes(StringResources.DEFAULTS)
    assertTrue("expected string-arrays to be discovered at all", defaults.isNotEmpty())
    for (locale in locales) {
      val sizes = StringResources.arraySizes(locale)
      assertEquals("$locale is missing a string-array present in values/",
        defaults.keys, sizes.keys)
      for ((name, n) in defaults) {
        assertEquals("$locale/$name has a different item count than values/", n, sizes[name])
      }
    }
  }

  @Test
  fun `the nav labels reached every locale`() {
    // Regression guard for the extraction that moved hardcoded bar labels into
    // resources: a half-finished sweep is the exact mistake this test exists for.
    val navKeys = setOf("nav_home", "nav_map", "nav_news", "nav_guide", "nav_profile",
      "nav_current_suffix")
    for (locale in listOf(StringResources.DEFAULTS) + locales) {
      val have = StringResources.names(locale)
      assertTrue("$locale is missing nav labels: ${navKeys - have}", navKeys.all { it in have })
    }
  }
}
