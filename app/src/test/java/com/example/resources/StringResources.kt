package com.example.resources

import java.io.File

/**
 * Shared access to the translated string resources for the JVM unit tests.
 *
 * Exists so the encoding guard and the key-parity guard parse resources exactly
 * the same way; two private copies of a resource loader is how one test starts
 * disagreeing with the other about what "missing" means.
 */
object StringResources {

  /** Locales the app actually ships, in the order used across the suite. */
  val locales = listOf("values-hi", "values-te", "values-ta", "values-bn", "values-mr")

  /** The English source of truth. */
  const val DEFAULTS = "values"

  /** Gradle may set user.dir to :app or to the repo root; accept both layouts. */
  fun file(locale: String): File {
    val root = File(System.getProperty("user.dir") ?: error("user.dir is unset"))
    val relative = "src/main/res/$locale/strings.xml"
    return listOf(File(root, relative), File(root, "app/$relative")).firstOrNull { it.exists() }
      ?: throw AssertionError("missing $relative (searched from $root)")
  }

  /**
   * name -> raw inner text, unescaped.
   *
   * Reads the file as a string rather than through an XML parser on purpose: a
   * `plurals` block or an entity an Android-less parser chokes on must not be able
   * to make the encoding guard silently skip a whole file.
   */
  fun entries(locale: String): Map<String, String> {
    val body = file(locale).readText(Charsets.UTF_8)
    return Regex("<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
      .findAll(body)
      .associate { it.groupValues[1] to it.groupValues[2] }
  }

  fun names(locale: String): Set<String> = entries(locale).keys

  /**
   * string-array name -> item count.
   *
   * Arrays are read positionally (`profile_contact_role_keys` is looked up by the
   * same index as `profile_contact_role_labels`), so a locale that ships one item
   * short produces an IndexOutOfBounds deep in the profile form. Counting items is
   * enough to catch it and keeps this from needing an Android resource parser.
   */
  fun arraySizes(locale: String): Map<String, Int> {
    val body = file(locale).readText(Charsets.UTF_8)
    return Regex("<string-array\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string-array>", RegexOption.DOT_MATCHES_ALL)
      .findAll(body)
      .associate { m ->
        m.groupValues[1] to Regex("<item\\b").findAll(m.groupValues[2]).count()
      }
  }
}
