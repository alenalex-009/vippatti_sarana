package com.example

import org.junit.Assert.assertTrue
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the app's localization resource set for the completeness checks.
 *
 * English (`res/values/`) is the canonical key set; every static UI key must
 * exist in each supported locale. A new English string without translations is
 * expected to fail - the developer supplies the translations. Nothing is
 * auto-translated at runtime.
 */
internal object LocalizationResources {

  const val RES = "src/main/res"

  val LOCALES = listOf("hi", "te", "ta", "bn", "mr")

  /**
   * Proper nouns, brand names and acronyms that must stay identical in every
   * language. These are not translatable prose, so the "still English" and
   * "native script" checks must not flag them.
   */
  val ALLOWED_UNTRANSLATED = setOf(
    "app_name",        // Vippatti Sarana
    "login_title",     // VIPPATTI SARANA brand lockup
    "login_sos_badge"  // SOS, a universal emergency acronym
  )

  /** Files holding translatable UI strings, per locale folder. */
  val STRING_FILES = listOf("strings.xml", "strings-instructions.xml")

  /**
   * Codepoints that only appear as a by-product of a mojibake round-trip.
   *
   * The Latin-1 high range plus the cp1252 specials standing in for bytes
   * 0x80-0x9F. Legitimate punctuation that shares this neighbourhood
   * (em dash U+2014, bullet U+2022, ...) is deliberately NOT listed: only a
   * *run* of these characters indicates a mis-decoded byte stream, and that
   * run check is done separately.
   */
  val MOJIBAKE_CHARS: Set<Int> = buildSet {
    for (c in 0x00C0..0x00FF) add(c)
    addAll(
      listOf(
        0x20AC, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021, 0x02C6,
        0x2030, 0x0160, 0x2039, 0x0152, 0x017D, 0x2018, 0x2019, 0x201C,
        0x201D, 0x2022, 0x2013, 0x2014, 0x02DC, 0x2122, 0x0161, 0x203A,
        0x0153, 0x017E, 0x0178,
      )
    )
  }

  /**
   * A mojibake run is two or more consecutive candidate characters. Latin-1
   * text in a real translation (a stray accented letter, an em dash) never
   * clusters like this, so a cluster is a reliable corruption signal while
   * isolated characters are left alone.
   */
  fun mojibakeRuns(text: String): List<String> {
    val runs = mutableListOf<String>()
    val current = StringBuilder()
    for (c in text) {
      if (c.code in MOJIBAKE_CHARS) {
        current.append(c)
      } else {
        if (current.length >= 2) runs.add(current.toString())
        current.setLength(0)
      }
    }
    if (current.length >= 2) runs.add(current.toString())
    return runs
  }

  /**
   * True when a value carries translatable prose rather than being a pure
   * format/placeholder composition such as "%1$s (%2$s)". Those legitimately
   * contain no letters and so no native-script characters.
   */
  fun isFormatOnly(value: String): Boolean =
    value.replace(Regex("""%\d+\$[a-zA-Z]"""), "").none { it.isLetter() }

  /** Native script block per locale, used to prove the script survived. */
  val SCRIPT_RANGES: Map<String, IntRange> = mapOf(
    "hi" to (0x0900..0x097F),   // Devanagari
    "mr" to (0x0900..0x097F),   // Marathi shares Devanagari
    "te" to (0x0C00..0x0C7F),   // Telugu
    "ta" to (0x0B80..0x0BFF),   // Tamil
    "bn" to (0x0980..0x09FF)    // Bengali
  )

  fun read(file: File): String =
    String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)

  fun decodeStrict(file: File) {
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
    try {
      decoder.decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(file.toPath())))
    } catch (e: Exception) {
      throw AssertionError("${file.path} is not valid UTF-8: ${e.message}")
    }
  }

  private fun parse(file: File): Map<String, String> {
    assertTrue("missing resource file: ${file.path}", file.exists())
    val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
    val doc = dbf.newDocumentBuilder().parse(file)
    val out = LinkedHashMap<String, String>()
    val nodes = doc.getElementsByTagName("string")
    for (i in 0 until nodes.length) {
      val el = nodes.item(i)
      val name = el.attributes.getNamedItem("name")?.nodeValue
        ?: error("string without a name attribute in ${file.path}")
      assertTrue("duplicate resource '$name' in ${file.path}", !out.containsKey(name))
      out[name] = el.textContent
    }
    return out
  }

  /** All translatable strings for a locale folder ("values" or "values-hi"). */
  fun stringsFor(dirName: String): Map<String, String> {
    val dir = File(RES, dirName)
    val merged = LinkedHashMap<String, String>()
    for (name in STRING_FILES) {
      val parsed = parse(File(dir, name))
      for ((k, v) in parsed) {
        assertTrue("duplicate resource '$k' across $dirName files", !merged.containsKey(k))
        merged[k] = v
      }
    }
    return merged
  }

  fun baseStrings(): Map<String, String> = stringsFor("values")

  fun localeStrings(locale: String): Map<String, String> = stringsFor("values-$locale")

  fun filesToCheck(): List<File> =
    (listOf("values") + LOCALES.map { "values-$it" })
      .flatMap { dir -> STRING_FILES.map { File("$RES/$dir", it) } }
      .filter { it.exists() }
}
