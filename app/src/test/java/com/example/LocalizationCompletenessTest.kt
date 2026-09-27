package com.example

import com.example.LocalizationResources.ALLOWED_UNTRANSLATED
import com.example.LocalizationResources.LOCALES
import com.example.LocalizationResources.RES
import com.example.LocalizationResources.SCRIPT_RANGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the localization resource set.
 *
 * English (`res/values/`) is the canonical key set. Every static UI key must
 * exist in each supported locale, placeholders must line up, and no locale file
 * may contain mojibake - the failure mode where UTF-8 bytes were read back
 * through a legacy 8-bit codepage and Devanagari/Telugu/Tamil/Bengali text
 * rendered as Latin-1 letter soup instead of native script.
 */
class LocalizationCompletenessTest {

  private val base = LocalizationResources.baseStrings()

  @Test
  fun `every static UI key is translated in all supported locales`() {
    val report = StringBuilder()

    for (locale in LOCALES) {
      val strings = LocalizationResources.localeStrings(locale)
      val missing = base.keys
        .filter { it !in strings && it !in ALLOWED_UNTRANSLATED }
        .sorted()
      if (missing.isNotEmpty()) {
        report.append("Missing in $locale:\n")
        missing.forEach { report.append("  $it\n") }
      }
    }

    if (report.isNotEmpty()) {
      throw AssertionError(
        "Translation resources are incomplete. Add the missing translations.\n$report"
      )
    }
  }

  @Test
  fun `no locale defines keys absent from the English canonical set`() {
    val report = StringBuilder()

    for (locale in LOCALES) {
      val extra = LocalizationResources.localeStrings(locale)
        .keys.filter { it !in base }.sorted()
      if (extra.isNotEmpty()) {
        report.append("Unknown keys in $locale:\n")
        extra.forEach { report.append("  $it\n") }
      }
    }

    if (report.isNotEmpty()) {
      throw AssertionError(
        "Locale resources declare keys English does not define.\n$report"
      )
    }
  }

  @Test
  fun `static UI strings are not left as English copies`() {
    val report = StringBuilder()

    for (locale in LOCALES) {
      val strings = LocalizationResources.localeStrings(locale)
      val stillEnglish = strings.keys
        .filter { it in base && it !in ALLOWED_UNTRANSLATED }
        // A value that is pure formatting ("%1$s (%2$s)") is identical in
        // every language by design and carries nothing to translate.
        .filter { !LocalizationResources.isFormatOnly(base[it]!!) }
        .filter { strings[it]?.trim() == base[it]?.trim() }
        .sorted()
      if (stillEnglish.isNotEmpty()) {
        report.append("Still English in $locale:\n")
        stillEnglish.forEach { report.append("  $it = \"${base[it]}\"\n") }
      }
    }

    if (report.isNotEmpty()) {
      throw AssertionError("These static UI strings were never translated.\n$report")
    }
  }

  @Test
  fun `positional placeholders survive translation`() {
    val pattern = Regex("""%\d+\$[a-zA-Z]""")
    val report = StringBuilder()

    for (locale in LOCALES) {
      for ((key, value) in LocalizationResources.localeStrings(locale)) {
        val english = base[key] ?: continue
        val expected = pattern.findAll(english).map { it.value }.sorted().toList()
        val actual = pattern.findAll(value).map { it.value }.sorted().toList()
        if (expected != actual) {
          report.append("Placeholder mismatch in $locale for '$key':")
          report.append(" expected $expected, found $actual\n")
        }
      }
    }

    if (report.isNotEmpty()) {
      throw AssertionError("Format placeholders must survive translation.\n$report")
    }
  }

  @Test
  fun `no resource file contains mojibake`() {
    val report = StringBuilder()

    for (file in LocalizationResources.filesToCheck()) {
      for (run in LocalizationResources.mojibakeRuns(LocalizationResources.read(file))) {
        report.append("${file.path} contains mojibake run: \"$run\"\n")
      }
    }

    if (report.isNotEmpty()) {
      throw AssertionError(
        "Mojibake detected: UTF-8 bytes were read back through a legacy 8-bit " +
          "codepage, so native script renders as letter soup.\n$report"
      )
    }
  }

  @Test
  fun `every locale renders its text in its own native script`() {
    for (locale in LOCALES) {
      val range = SCRIPT_RANGES.getValue(locale)
      val strings = LocalizationResources.localeStrings(locale)
        .filterKeys { it !in ALLOWED_UNTRANSLATED }
      assertTrue("no translatable strings for $locale", strings.isNotEmpty())

      val broken = strings.filter { (_, v) ->
        v.isNotBlank() &&
          !LocalizationResources.isFormatOnly(v) &&
          v.none { c -> c.code in range }
      }
      assertTrue(
        "These $locale strings contain no $locale script characters:\n" +
          broken.entries.joinToString("\n") { "  ${it.key} = \"${it.value}\"" },
        broken.isEmpty()
      )
    }
  }

  @Test
  fun `every resource file is valid UTF-8`() {
    LocalizationResources.filesToCheck().forEach { LocalizationResources.decodeStrict(it) }
  }

  @Test
  fun `locales config advertises exactly the supported languages`() {
    val file = File(RES, "xml/locales_config.xml")
    assertTrue("missing locales_config.xml", file.exists())
    val declared = Regex("""android:name="([a-z]{2})"""")
      .findAll(LocalizationResources.read(file))
      .map { it.groupValues[1] }
      .toList()
    assertEquals(listOf("en") + LOCALES, declared)
  }
}
