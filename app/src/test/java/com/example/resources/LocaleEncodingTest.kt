package com.example.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRODUCTION CONTRACT: the translated resources are readable UTF-8.
 *
 * Every non-English `strings.xml` here shipped double-encoded: UTF-8 bytes were
 * decoded as a single-byte Windows codepage and re-saved as UTF-8, so Hindi
 * rendered as Latin garbage instead of the intended Devanagari. Two decoder
 * flavours of the same bug were found: cp1252 (bytes 0x80-0x9F became
 * punctuation such as an em dash or a euro sign) and latin-1 (they stayed C1
 * controls). For a disaster-safety app that is not cosmetic: the translated
 * emergency guidance was unreadable, and it survived review because mojibake is
 * still perfectly valid XML.
 *
 * The only non-ASCII literals kept in this file are correct Devanagari/Indic
 * samples and ordinary typographic marks: a test that guards encoding must not
 * itself depend on the editor preserving mojibake.
 */
class LocaleEncodingTest {

  private val locales = StringResources.locales

  /** The script block each locale is expected to write in. */
  private val expectedRange: Map<String, IntRange> = mapOf(
    "values-hi" to 0x0900..0x097F, // Devanagari
    "values-mr" to 0x0900..0x097F,
    "values-bn" to 0x0980..0x09FF, // Bengali
    "values-ta" to 0x0B80..0x0BFF, // Tamil
    "values-te" to 0x0C00..0x0C7F, // Telugu
  )

  /**
   * Proof of a mis-decoded save: the whole Windows-1252 high range, which real
   * Indic text never needs. Kept OUT of the set because they are legitimate here:
   * U+2014 em dash and U+2022 bullet (both restored by the repair and used in
   * English copy), U+200C ZWNJ (19 occurrences in Telugu), U+200D ZWJ (Marathi and
   * Bengali conjuncts), and U+0964 danda - the full stop Bengali and Marathi use,
   * which lives in the Devanagari block and so looks like the wrong script.
   */
  private val mojibake: Set<Int> = buildSet {
    addAll(0x0080..0x00FF)
    addAll(listOf(0x0152, 0x0153, 0x0160, 0x0161, 0x0178, 0x017D, 0x017E, 0x0192, 0x02C6, 0x02DC))
    addAll(listOf(0x2013, 0x2018, 0x201A, 0x201C, 0x201E, 0x2020, 0x2021, 0x2026, 0x2030, 0x2039, 0x203A, 0x20AC, 0x2122))
  }

  private fun isMojibake(ch: Char) = ch.code in mojibake

  /** Delegated to StringResources so the parity guard parses identically. */
  private fun stringsOf(locale: String): Map<String, String> = StringResources.entries(locale)

  @Test
  fun `no string resource contains double-encoded mojibake`() {
    for (locale in listOf("values") + locales) {
      val offenders = stringsOf(locale).filterValues { v -> v.any { isMojibake(it) } }.keys
      assertTrue(
        "$locale has double-encoded strings (UTF-8 bytes read as Windows-1252): " +
          offenders.take(8) + " - re-save this file as UTF-8",
        offenders.isEmpty()
      )
    }
  }

  @Test
  fun `no locale leaks characters from a neighbouring script`() {
    // Real risk after a bulk encoding repair: a run decoded into the wrong Indic
    // block (Hindi glyphs landing in the Telugu file). Bengali, Hindi and Marathi
    // legitimately share the danda । and the joiners, so those are exempt - but
    // nothing else may cross a block boundary. A string with NO script at all
    // (e.g. "%1$s - %2$s") is fine: it simply has nothing to translate.
    for (locale in locales) {
      val range = expectedRange.getValue(locale)
      val shared = setOf(0x0964, 0x0965, 0x200C, 0x200D)
      val offenders = stringsOf(locale).filter { (_, value) ->
        value.any {
          it.code in 0x0900..0x0C7F && it.code !in range && it.code !in shared
        }
      }
      assertTrue(
        "$locale contains characters from a script that is not its own: " +
          offenders.keys.take(8),
        offenders.isEmpty()
      )
    }
  }

  @Test
  fun `every locale keeps every format placeholder of its English source`() {
    // A dropped %1$s crashes String.format at runtime, and a bulk encoding repair
    // is exactly the kind of edit that can silently damage one. Compared as a
    // MULTISET, not in order: "%2$s में से %1$s" reads better in Hindi than the
    // English order, and positional arguments exist precisely so it may be swapped.
    val fmt = Regex("%(\\d+\\$)?[sdf]")
    val base = stringsOf("values")
    for (locale in locales) {
      // Materialise to List before comparing: Sequence.sorted() hands back a
      // Sequence, and sequences do not implement equals, so a naive `!=` there
      // compares object identity and reports every single string as drift.
      val drift = stringsOf(locale).filter { (name, value) ->
        val en = base[name] ?: return@filter false
        fmt.findAll(en).map { it.value }.toList().sorted() !=
          fmt.findAll(value).map { it.value }.toList().sorted()
      }
      assertTrue(
        "$locale has placeholder drift against values/: " +
          drift.entries.take(4).joinToString("; ") { (k, v) ->
            val en = base[k] ?: ""
            "[$k] en=${fmt.findAll(en).map { it.value }.toList()} tr=${fmt.findAll(v).map { it.value }.toList()}" +
              " | enText=${en.take(28)} trText=${v.take(28)}"
          },
        drift.isEmpty()
      )
    }
  }

  @Test
  fun `no locale ships an empty or whitespace-only translation`() {
    for (locale in locales) {
      val blanks = stringsOf(locale).filterValues { it.isBlank() }.keys
      assertTrue("$locale has empty strings: $blanks", blanks.isEmpty())
    }
  }

  @Test
  fun `the characters Indic locales legitimately need are not flagged`() {
    // Guards the exclusion list, otherwise the first test would fail on correct
    // text: — bullet, ZWNJ (19 uses in Telugu), ZWJ (Marathi conjuncts) and the
    // danda । that Bengali and Marathi use as a full stop.
    for (cp in listOf(0x2014, 0x2022, 0x200C, 0x200D, 0x0964)) {
      assertFalse("U+%04X must not be treated as mojibake".format(cp), isMojibake(cp.toChar()))
    }
  }

  @Test
  fun `the detector catches the corruption it exists to catch`() {
    // A guard that cannot fail is decoration. Re-deriving the historical bytes
    // here proves the rule has teeth without storing mojibake in this source file.
    val hindi = "मैं सुरक्षित"
    assertFalse("correct Hindi must be clean", hindi.any { isMojibake(it) })
    val bytes = hindi.toByteArray(Charsets.UTF_8)
    // This is precisely what the broken editor did: read UTF-8 as a Latin-1-ish
    // single-byte codepage. The result must trip the detector.
    val misread = String(bytes, Charsets.ISO_8859_1)
    assertTrue("the double-encoded form must be detected", misread.any { isMojibake(it) })
    // And the repair must be lossless: reversing it returns the original text.
    val roundTrip = String(misread.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
    assertEquals(hindi, roundTrip)
  }
}

