package com.example.data.disaster.providers

import android.util.Xml
import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterEvent
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.DisasterType
import com.example.data.disaster.EventDetails
import com.example.data.disaster.EventGeometry
import com.example.data.disaster.EventOrigin
import com.example.data.disaster.EventStatus
import com.example.data.disaster.IndiaGeo
import com.example.data.disaster.ProviderResult
import com.example.data.model.HazardSeverity
import com.example.data.routing.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ============================================================================
 * PRIORITY 3 PROVIDER — IMD OFFICIAL ALERTS (CAP 1.2 machine-readable feed).
 * ============================================================================
 *
 * Official feed (linked from the IMD homepage "Latest CAP Alert" section,
 * mausam.imd.gov.in):
 *
 *   RSS index : https://cap-sources.s3.amazonaws.com/in-imd-en/rss.xml
 *   CAP items : https://cap-sources.s3.amazonaws.com/in-imd-en/<timestamp>.xml
 *
 * This is the Common Alerting Protocol feed IMD publishes for the national
 * SACHET alerting system — no scraping, no invented endpoints. Each item is
 * an official signed CAP 1.2 alert carrying event, severity, urgency,
 * certainty, onset/expires times, senderName, description, instruction, web
 * link and an explicit geographic polygon where IMD supplies one.
 *
 * CYCLONE HONESTY NOTE: IMD publishes cyclone warnings through this CAP
 * channel when active, but there is no public machine-readable cyclone
 * TRACK/forecast-geometry feed. This provider therefore surfaces official
 * alerts (rainfall, cyclone warnings, etc.) with their real polygons — it
 * never fabricates track lines or forecast cones.
 */
open class ImdCapProvider(
  private val httpClient: OkHttpClient = defaultHttpClient(),
  /** Overridable so the NDMA/SACHET channel reuses this validated parser. */
  protected open val rssUrl: String = RSS_URL,
  private val feedName: String = "weather alert"
) : DisasterDataProvider {

  override val providerId: DisasterSource = DisasterSource.IMD_CAP

  override suspend fun fetchIndiaEvents(): ProviderResult = withContext(Dispatchers.IO) {
    try {
      val rssBody = fetchText(rssUrl) ?: return@withContext offlineFailure()
      val links = CapRssParser.parseItemLinks(rssBody).take(MAX_ALERTS)
      // Fetch the up-to-8 CAP alert documents in parallel so the provider
      // waits for ONE slow link instead of the sum off all timeouts.
      val events = coroutineScope {
        links.map { link -> async { link to fetchText(link) } }
          .awaitAll()
          .mapNotNull { (link, body) -> body?.let { CapAlertParser.parse(it, link) } }
      }
      ProviderResult.Success(events, System.currentTimeMillis())
    } catch (e: IOException) {
      offlineFailure()
    } catch (e: Exception) {
      ProviderResult.Failure("Official $feedName feed error (${e.javaClass.simpleName}).")
    }
  }

  private fun offlineFailure() = ProviderResult.Failure(
    "No connection to the official $feedName feed — cached alerts stay available."
  )

  private fun fetchText(url: String): String? = try {
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", "VippattiSarana-DisasterRelief/1.0 (Android; IMD CAP Feed)")
      .build()
    httpClient.newCall(request).execute().use { response ->
      if (response.isSuccessful) response.body?.string() else null
    }
  } catch (e: Exception) {
    null
  }

  companion object {
    const val RSS_URL = "https://cap-sources.s3.amazonaws.com/in-imd-en/rss.xml"
    const val MAX_ALERTS = 8

    fun defaultHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
  }
}

/** Pure parser for the CAP RSS item index (item/link elements). */
object CapRssParser {

  fun parseItemLinks(rssBody: String): List<String> {
    val parser = Xml.newPullParser()
    parser.setInput(java.io.StringReader(rssBody))
    val links = mutableListOf<String>()
    var inItem = false
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "item" -> inItem = true
            "link" -> if (inItem) {
              val link = parser.nextText().trim()
              if (link.startsWith("http")) links += link
            }
          }
        }
        XmlPullParser.END_TAG -> if (parser.name == "item") inItem = false
      }
      event = parser.next()
    }
    return links
  }
}

/**
 * Pure parser for one CAP 1.2 alert XML (urn:oasis:names:tc:emergency:cap:1.2)
 * — live-validated against a real IMD alert (event, severity, urgency,
 * certainty, onset, expires, senderName, description, instruction, web link
 * and geographic polygon).
 *
 * STAGE 3 review notes (behaviour pinned by tests, not changed speculatively):
 *  - Timestamps: `sent` is the primary publication time; the alert's own
 *    `onset` / `effective` are the fallbacks (both real provider times).
 *    `expires` drives EXPIRED/ACTIVE only and is never used as the observed
 *    time. A payload with none of sent/onset/effective keeps the alert with
 *    the retrieval time rather than dropping an official record.
 *  - Severity: Extreme/Severe/Moderate/Minor map to the app scale; anything
 *    else (including Unknown/blank) keeps the pre-existing MODERATE default.
 *  - Geometry: a polygon-less alert stays Unlocated with the provider's area
 *    text — never pinned to a placeholder coordinate, never a hazard zone.
 *  - Attribution: senderName/urgency/certainty/web are preserved verbatim
 *    ("Not provided by source" when absent); the record URL is the alert's
 *    own `web` link, else the fetched CAP URL.
 *  - Errors: missing identifier/event returns null (malformed — dropped);
 *    network/timeout/malformed-RSS surface as provider Failure, never as
 *    fabricated alerts. Conditional (ETag/If-None-Match) requests are NOT
 *    implemented: the SACHET FetchXMLFile ETag guide describes a different
 *    endpoint (sachet.ndma.gov.in) that this provider does not consume, and
 *    its schema/behaviour is unverified from this environment.
 */
object CapAlertParser {

  fun parse(capXml: String, sourceUrl: String): DisasterEvent? {
    val parser = Xml.newPullParser()
    parser.setInput(java.io.StringReader(capXml))
    val fields = mutableMapOf<String, String>()
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG && !isInsideSignature(localName(parser.name))) {
        collectSimpleField(parser, fields)
      }
      event = parser.next()
    }

    val identifier = fields["identifier"] ?: return null
    val info = fields["event"] ?: return null
    val areaDesc = fields["areadesc"].orDefaultIfBlank("Area description not provided by source")

    // Observation-time chain (all values are the provider's own timestamps,
    // never invented): `sent` first, then the alert's own `onset` /
    // `effective` times. Only when the alert carries NONE of these does the
    // parser fall back to the retrieval time — such a payload carries no
    // publication timestamp at all, and dropping it would silently lose an
    // official alert, so it is kept and its times read as "retrieved now".
    // (Downstream, Stage-2 status rules still require a real observed time
    // for a LIVE reading; retrieval recency alone never makes it live.)
    val sentAt = parseCapTimestamp(fields["sent"])
    val onsetAt = parseCapTimestamp(fields["onset"])
    val effectiveAt = parseCapTimestamp(fields["effective"])
    val observedAt = when {
      sentAt > 0L -> sentAt
      onsetAt > 0L -> onsetAt
      effectiveAt > 0L -> effectiveAt
      else -> 0L
    }
    val expiresAt = parseCapTimestamp(fields["expires"])
    val now = System.currentTimeMillis()
    val observedOrRetrievedAt = if (observedAt > 0L) observedAt else now

    val polygon = parsePolygon(fields["polygon"])
    val disasterType = classifyEvent(info, fields["category"] ?: "")

    return DisasterEvent(
      id = "imd-cap-$identifier",
      source = DisasterSource.IMD_CAP,
      sourceEventId = identifier,
      disasterType = disasterType,
      title = fields["headline"]?.takeIf { it.isNotBlank() } ?: info,
      description = fields["description"]?.takeIf { it.isNotBlank() }
        ?: "Official alert description not provided by source.",
      // HONESTY: an alert without a CAP polygon has NO known location. It is
      // kept as an official alert with its stated area text and recorded as
      // UNLOCATED - never pinned to India's centre as if that were the area.
      geometry = polygon ?: EventGeometry.Unlocated(areaDesc),
      latitude = null,
      longitude = null,
      severity = mapSeverity(fields["severity"]),
      observedAtMillis = observedOrRetrievedAt,
      updatedAtMillis = observedOrRetrievedAt,
      expiresAtMillis = expiresAt.takeIf { it > 0 },
      status = if (expiresAt in 1 until now) EventStatus.EXPIRED else EventStatus.ACTIVE,
      origin = EventOrigin.OBSERVED,
      affectedAreaLabel = areaDesc,
      url = fields["web"]?.takeIf { it.isNotBlank() } ?: sourceUrl,
      details = EventDetails.OfficialAlert(
        event = info,
        urgency = fields["urgency"].orDefaultIfBlank("Not provided by source"),
        certainty = fields["certainty"].orDefaultIfBlank("Not provided by source"),
        senderName = fields["sendername"].orDefaultIfBlank("Not provided by source"),
        instruction = fields["instruction"]?.takeIf { it.isNotBlank() },
        webLink = fields["web"]?.takeIf { it.isNotBlank() } ?: sourceUrl
      )
    )
  }

  /** Reads one simple element's text into [fields] keyed by lowercase name. */
  private fun collectSimpleField(parser: XmlPullParser, fields: MutableMap<String, String>) {
    val name = localName(parser.name).lowercase()
    if (name in FIELD_WHITELIST) {
      val text = parser.safeNextText().trim()
      if (text.isNotBlank() && !fields.containsKey(name)) fields[name] = text
    }
  }

  /**
   * CAP XML uses namespace prefixes (cap:identifier); the XML signature block
   * uses ds:Signature — the parser key must be the local name so both forms
   * ("identifier" and "cap:identifier") map to one key.
   */
  fun localName(rawName: String): String =
    rawName.substringAfterLast(':').ifBlank { rawName }

  private fun isInsideSignature(name: String): Boolean = name == "Signature"


  private fun XmlPullParser.safeNextText(): String = try {
    nextText() ?: ""
  } catch (e: Exception) {
    ""
  }

  private fun parsePolygon(raw: String?): EventGeometry? {
    if (raw.isNullOrBlank()) return null
    val ring = mutableListOf<GeoPoint>()
    raw.trim().split(" ").forEach { pair ->
      val parts = pair.split(",")
      if (parts.size == 2) {
        val lat = parts[0].toDoubleOrNull()
        val lon = parts[1].toDoubleOrNull()
        if (lat != null && lon != null) ring += GeoPoint(lat, lon)
      }
    }
    return if (ring.size >= 3) EventGeometry.Polygon(ring) else null
  }

  fun parseCapTimestamp(raw: String?): Long = try {
    if (raw.isNullOrBlank()) 0L
    else java.time.OffsetDateTime.parse(raw.trim()).toInstant().toEpochMilli()
  } catch (e: Exception) {
    0L
  }

  /** CAP severity (Extreme/Severe/Moderate/Minor/Unknown) -> app scale. */
  fun mapSeverity(raw: String?): HazardSeverity = when (raw?.trim()?.lowercase()) {
    "extreme" -> HazardSeverity.EXTREME
    "severe" -> HazardSeverity.HIGH
    "moderate" -> HazardSeverity.MODERATE
    "minor" -> HazardSeverity.LOW
    else -> HazardSeverity.MODERATE
  }

  /**
   * Classifies the official event into the app's disaster types using the CAP
   * event text (e.g. "Extremely heavy rainfall") — official terminology is
   * preserved verbatim in the title/description.
   */
  fun classifyEvent(event: String, category: String): DisasterType {
    val e = event.lowercase()
    return when {
      e.contains("cyclone") || e.contains("cyclonic") -> DisasterType.CYCLONE
      e.contains("rainfall") || e.contains("rain") || e.contains("thunder") ||
        e.contains("heavy") -> DisasterType.HEAVY_RAINFALL
      e.contains("flood") -> DisasterType.FLOOD
      e.contains("landslide") -> DisasterType.LANDSLIDE
      else -> DisasterType.WEATHER_ALERT
    }
  }

  private fun String?.orDefaultIfBlank(fallback: String): String =
    this?.takeIf { it.isNotBlank() } ?: fallback

  private val FIELD_WHITELIST = setOf(
    "identifier", "sender", "sent", "status", "msgtype", "scope", "language",
    "category", "event", "responsetype", "urgency", "severity", "certainty",
    "onset", "effective", "expires", "sendername", "headline", "description",
    "instruction", "web", "areadesc", "polygon"
  )
}

/**
 * NDMA / SACHET national alert channel (PHASE 14 research, 2026-10-03):
 * the same WMO CAP S3 bucket publishes India's national disaster alerts
 * under /in-ndma-en/ - verified live from this environment (RSS index
 * reachable, 6 items at probe time). Reuses the validated CAP parser.
 */
class NdmaCapProvider(
  httpClient: OkHttpClient = defaultHttpClient()
) : ImdCapProvider(httpClient, RSS_NDMA_URL, "NDMA national alert") {
  override val providerId: DisasterSource = DisasterSource.NDMA_CAP

  companion object {
    const val RSS_NDMA_URL = "https://cap-sources.s3.amazonaws.com/in-ndma-en/rss.xml"
  }
}
