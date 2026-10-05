package com.example.data.disaster.cap

/**
 * ============================================================================
 * ETAG / CONDITIONAL-FETCH SUPPORT
 * ============================================================================
 *
 * Section 2 mandates conditional GET so unchanged CAP documents are not
 * re-downloaded. Implemented against the REAL endpoint behaviour measured on
 * 2026-10-04, which differs from the textbook:
 *
 *   RSS index      -> ETag: W/"80851-1791134070750"   (weak)
 *   FetchXMLFile   -> ETag: "bbAS5UgazsFkq/Y6AilQ6Y9ikxAVs2G1ozLZUWNfJFM="
 *
 *   Sending If-None-Match with a MATCHING ETag returned:
 *       HTTP 200 + the FULL body  (3239 bytes)
 *   NOT:
 *       HTTP 304 + empty body
 *
 * The origin therefore IGNORES conditional GET on the document endpoints. A
 * client that waits for 304 would re-download every document on every poll and
 * never save a byte.
 *
 * So "unchanged" is decided by COMPARING ETAGS after the request:
 *   - stored ETag == returned ETag -> keep the cached body (NotModified)
 *   - ETag differs / absent       -> the returned body is authoritative
 *
 * A 304 is still handled, because the S3-hosted IMD bucket DOES honour it.
 * Both paths are supported; only the comparison logic decides.
 */
data class CacheEntry(
  val body: String,
  val etag: String?,
  val storedAtMillis: Long
)

/** Outcome of one conditional fetch. */
sealed class ConditionalResult {
  /** Fresh body obtained; caller should replace its cache. */
  data class Updated(val body: String, val etag: String?, val fromNotModified304: Boolean) :
    ConditionalResult()

  /** Server confirmed the cached copy is current. Cached body must be retained. */
  data class NotModified(val etag: String?) : ConditionalResult()

  /** Transport/server failure. Cached body (if any) must be retained. */
  data class Failed(val reason: String, val httpCode: Int? = null) : ConditionalResult()
}

/**
 * Pure decision logic for one conditional-fetch exchange. Kept free of Android
 * and of OkHttp so it can be unit-tested directly (section 30).
 */
object ConditionalFetchPolicy {

  /** Normalizes an ETag header for storage/comparison. Blank -> null. */
  fun normalizeETag(raw: String?): String? =
    raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

  /**
   * Decides the outcome of a completed HTTP exchange.
   *
   * @param httpCode       response code (200, 304, 403, ...)
   * @param returnedETag   ETag header on the response, if any
   * @param storedETag     ETag we sent / already had
   * @param body           response body (may be empty on a 304 or a 403)
   */
  fun decide(
    httpCode: Int,
    returnedETag: String?,
    storedETag: String?,
    body: String?
  ): ConditionalResult {
    val newETag = normalizeETag(returnedETag)
    val oldETag = normalizeETag(storedETag)

    // Textbook path: a server that really does honour If-None-Match.
    if (httpCode == 304) return ConditionalResult.NotModified(newETag ?: oldETag)

    if (httpCode !in 200..299) {
      return ConditionalResult.Failed("HTTP $httpCode", httpCode)
    }

    // SACHET behaviour: 200 with the same ETag means unchanged content.
    // Retain the cached body instead of overwriting with an identical copy.
    if (newETag != null && oldETag != null && newETag == oldETag) {
      return ConditionalResult.NotModified(newETag)
    }

    val text = body
    if (text.isNullOrEmpty()) {
      // A 200 with no body and no matching ETag is not usable content.
      return ConditionalResult.Failed("Empty response body", httpCode)
    }

    return ConditionalResult.Updated(text, newETag, fromNotModified304 = false)
  }

  /**
   * A weak ETag ("W/\"x\"") and its strong counterpart ("\"x\"") describe the
   * same representation, so they must compare EQUAL. Verified needed because
   * the SACHET RSS serves weak ETags while FetchXMLFile serves strong ones.
   */
  fun etagsMatch(a: String?, b: String?): Boolean {
    val x = normalizeETag(a) ?: return false
    val y = normalizeETag(b) ?: return false
    return stripWeak(x) == stripWeak(y)
  }

  private fun stripWeak(tag: String): String =
    if (tag.startsWith("W/", ignoreCase = true)) tag.substring(2) else tag
}

/**
 * Small persisted ETag/body store. Backed by whatever the app already uses for
 * cache files; the interface keeps it testable and storage-agnostic.
 */
interface CapCacheStore {
  fun read(key: String): CacheEntry?
  fun write(key: String, entry: CacheEntry)
  fun remove(key: String)
}

/** In-memory store — used by tests and as a fallback when disk is unavailable. */
class InMemoryCapCacheStore : CapCacheStore {
  private val map = mutableMapOf<String, CacheEntry>()
  override fun read(key: String): CacheEntry? = map[key]
  override fun write(key: String, entry: CacheEntry) {
    map[key] = entry
  }

  override fun remove(key: String) {
    map.remove(key)
  }
}
