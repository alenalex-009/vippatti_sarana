package com.example.data.disaster.providers

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterEvent
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.ProviderFailureKind
import com.example.data.disaster.ProviderResult
import com.example.data.disaster.cap.CapCacheStore
import com.example.data.disaster.cap.CacheEntry
import com.example.data.disaster.cap.ConditionalFetchPolicy
import com.example.data.disaster.cap.ConditionalResult
import com.example.data.disaster.cap.GeometryValidator
import com.example.data.disaster.cap.InMemoryCapCacheStore
import com.example.data.disaster.cap.SachetAlertFactory
import com.example.data.disaster.cap.SachetCapParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.TimeUnit

/**
 * ============================================================================
 * PRIMARY NATIONAL ALERT SOURCE — NDMA SACHET (the real site)
 * ============================================================================
 *
 * Implements the full three-step pipeline the app previously skipped:
 *
 *   RSS index  -> https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml
 *   CAP doc    -> https://sachet.ndma.gov.in/cap_public_website/FetchXMLFile?identifier=<id>
 *   Polygon    -> https://sachet.ndma.gov.in/cap_public_website/FetchPolygonXMLFile?identifier=<id>
 *
 * The RSS item is an INDEX, never a hazard: it carries a (often
 * regional-language) title, an empty description, and a link. Treating it as an
 * alert would show "no description, no location" to the user. So each item is
 * resolved into its real CAP document, and then into authority geometry when
 * the document advertises a Polygon URL.
 *
 * Verified server behaviour that this implementation accounts for:
 *  - the origin sends an ETag but IGNORES If-None-Match (answers 200 + full
 *    body), so "unchanged" is decided by comparing ETags, not by waiting for
 *    304 (see ConditionalFetchPolicy);
 *  - FetchPolygonXMLFile is rate-limited by the CDN and can answer 403. That
 *    is NOT a failure of the alert: the alert is kept, its geometry is reported
 *    unavailable, and the cached polygon (if any) is reused.
 *
 * The alert survives every geometry problem, because losing an official warning
 * is worse than showing it without a shape.
 */
class SachetCapProvider(
  private val httpClient: OkHttpClient = defaultHttpClient(),
  private val cacheStore: CapCacheStore = InMemoryCapCacheStore(),
  private val rssUrl: String = RSS_URL,
  private val maxAlerts: Int = MAX_ALERTS,
  private val clock: () -> Long = System::currentTimeMillis
) : DisasterDataProvider {

  override val providerId: DisasterSource = DisasterSource.NDMA_CAP

  override suspend fun fetchIndiaEvents(): ProviderResult = withContext(Dispatchers.IO) {
    try {
      val indexBody = fetchIndex() ?: return@withContext offline()
      val links = SachetRssParser.parseItemLinks(indexBody).take(maxAlerts)
      if (links.isEmpty()) {
        return@withContext ProviderResult.Failure(
          "SACHET feed returned no alert links.",
          ProviderFailureKind.FAILED
        )
      }

      // Resolve each index item into its real CAP document in parallel, so the
      // wait is the slowest document rather than the sum of every timeout.
      val now = clock()
      val events = coroutineScope {
        links.map { link -> async { resolveAlert(link, now) } }
          .awaitAll()
          .mapNotNull { it }
      }
      if (events.isEmpty()) {
        return@withContext ProviderResult.Failure(
          "No SACHET alert document could be resolved.",
          ProviderFailureKind.FAILED
        )
      }
      ProviderResult.Success(events, now)
    } catch (e: Exception) {
      ProviderResult.Failure(
        "SACHET national alert feed error (${e.javaClass.simpleName}).",
        ProviderFailureKind.FAILED
      )
    }
  }

  private fun offline() = ProviderResult.Failure(
    "No connection to the NDMA SACHET alert feed — cached alerts stay available.",
    ProviderFailureKind.FAILED
  )

  /** Fetches the RSS index with conditional GET. */
  private fun fetchIndex(): String? {
    val entry = cacheStore.read(KEY_INDEX)
    val result = conditionalGet(rssUrl, entry)
    return when (result) {
      is ConditionalResult.Updated -> {
        cacheStore.write(KEY_INDEX, CacheEntry(result.body, result.etag, clock()))
        result.body
      }

      is ConditionalResult.NotModified -> entry?.body
      is ConditionalResult.Failed -> entry?.body
    }
  }

  /**
   * Index link -> CAP document -> validated geometry -> DisasterEvent.
   * Returns null only when the CAP document itself cannot be read; a document
   * whose polygon is unavailable still produces an event.
   */
  private suspend fun resolveAlert(link: String, nowMillis: Long): DisasterEvent? {
    val capBody = conditionalGet(link, cacheStore.read(linkKey(link)))
    val body = when (capBody) {
      is ConditionalResult.Updated -> {
        cacheStore.write(linkKey(link), CacheEntry(capBody.body, capBody.etag, clock()))
        capBody.body
      }

      is ConditionalResult.NotModified -> cacheStore.read(linkKey(link))?.body
      is ConditionalResult.Failed -> cacheStore.read(linkKey(link))?.body
    } ?: return null

    val cap = SachetCapParser.parse(body, preferredLanguage = "en-IN") ?: return null

    // Geometry: only from an authority-published Polygon URL, and only when it
    // validates. A 403/timeout leaves the alert in place, geometry unavailable.
    val polygonText = resolvePolygon(cap.polygonUrl)
    val validation = GeometryValidator.validateRawPolygon(polygonText)

    val event = SachetAlertFactory.toDisasterEvent(
      cap = cap,
      sourceUrl = link,
      polygonText = polygonText,
      validation = validation,
      retrievedAtMillis = nowMillis,
      nowMillis = nowMillis
    )
    return event
  }

  /** Fetches the published polygon, reusing a cached copy when the CDN blocks us. */
  private suspend fun resolvePolygon(polygonUrl: String?): String? {
    if (polygonUrl.isNullOrBlank()) return null
    val key = linkKey(polygonUrl)
    val entry = cacheStore.read(key)
    return when (val result = conditionalGet(polygonUrl, entry)) {
      is ConditionalResult.Updated -> {
        cacheStore.write(key, CacheEntry(result.body, result.etag, clock()))
        result.body
      }

      is ConditionalResult.NotModified -> entry?.body
      // 403 (rate limit) / timeout: fall back to the cached polygon if we ever
      // retrieved one, otherwise the geometry is honestly unavailable.
      is ConditionalResult.Failed -> entry?.body
    }
  }

  /** One conditional GET, returning a decision rather than a string. */
  private fun conditionalGet(url: String, entry: CacheEntry?): ConditionalResult = try {
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", USER_AGENT)
      .header("Accept", "application/xml, text/xml, */*")
      .apply { entry?.etag?.let { header("If-None-Match", it) } }
      .build()
    httpClient.newCall(request).execute().use { response ->
      val etag = response.header("ETag")
      val body = if (response.code == 304) null else response.body?.string()
      ConditionalFetchPolicy.decide(response.code, etag, entry?.etag, body)
    }
  } catch (e: Exception) {
    ConditionalResult.Failed("network error: ${e.javaClass.simpleName}")
  }

  private fun linkKey(url: String): String = "cap:$url"

  companion object {
    const val RSS_URL = "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml"
    const val CAP_FEED_URL = "https://sachet.ndma.gov.in/CapFeed"
    const val MAX_ALERTS = 8
    const val USER_AGENT =
      "VippattiSarana-DisasterRelief/1.0 (Android; NDMA SACHET CAP Feed)"

    private const val KEY_INDEX = "sachet:rss:index"

    fun defaultHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
  }
}

/**
 * Pure parser for the SACHET RSS INDEX.
 *
 * The index is deliberately NOT treated as an alert: it contributes only the
 * per-item CAP links. Its <title> is frequently in a regional language and its
 * <description> is empty, so treating those as alert content would put
 * untranslated or empty text in front of a user.
 */
object SachetRssParser {

  fun parseItemLinks(rssBody: String): List<String> {
    if (rssBody.isBlank()) return emptyList()
    val parser = android.util.Xml.newPullParser()
    return try {
      parser.setInput(java.io.StringReader(rssBody))
      val links = mutableListOf<String>()
      var inItem = false
      var event = parser.eventType
      while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
          val name = parser.name?.substringAfterLast(':').orEmpty()
          when (name) {
            "item" -> inItem = true
            "link" -> if (inItem) {
              val link = try {
                parser.nextText().trim()
              } catch (e: Exception) {
                ""
              }
              if (link.startsWith("http")) links += link
            }
          }
        } else if (event == XmlPullParser.END_TAG) {
          if (parser.name?.substringAfterLast(':') == "item") inItem = false
        }
        event = parser.next()
      }
      links
    } catch (e: Exception) {
      emptyList()
    }
  }
}
