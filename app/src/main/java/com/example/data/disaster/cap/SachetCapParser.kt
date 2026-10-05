package com.example.data.disaster.cap

import android.util.Xml
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

/**
 * ============================================================================
 * SACHET CAP PARSER — the real two-step payload, not the RSS item
 * ============================================================================
 *
 * Measured against live documents on 2026-10-04:
 *
 *   RSS item  -> title (often regional-language), EMPTY <description/>, <link> to
 *                FetchXMLFile?identifier=<id>, <author> = issuing authority.
 *
 *   CAP doc   -> namespace urn:oasis:names:tc:emergency:cap:1.2, ONE <info>
 *                block PER LANGUAGE (en-IN, BN, hi, te ...). The <area> carries
 *                areaDesc + <geocode> LGD pairs but usually NO inline polygon.
 *
 *   Polygon   -> advertised as
 *                <parameter><valueName>Polygon URL</valueName>
 *                               <value>…/FetchPolygonXMLFile?identifier=<id></value>
 *
 * Honesty rules enforced here:
 *  - only the preferred language block is read, so a Bengali-first document does
 *    not leak untranslated text into an English UI;
 *  - a missing or unreadable field stays null. Nothing is defaulted;
 *  - a payload with no identifier or no event is DROPPED, not turned into a
 *    partial "official alert".
 *
 * Implementation note: XmlPullParser is a stream, and CAP nests <info> inside
 * <alert> which is the document root. The parser walks events linearly and
 * tracks WHICH <info> block is currently open by comparing language. A block
 * whose language is a better match replaces the one collected so far.
 */
object SachetCapParser {

  /** One <area> from a CAP document. */
  data class ParsedArea(
    val areaDesc: String?,
    val geocodes: List<Geocode>,
    val inlinePolygon: String?
  )

  data class Geocode(val name: String, val value: String)

  /** Everything needed from one CAP document, before geometry is resolved. */
  data class ParsedCap(
    val identifier: String,
    val sender: String?,
    val sent: String?,
    val status: String?,
    val msgType: String?,
    val scope: String?,
    val language: String?,
    val category: String?,
    val event: String,
    val urgency: String?,
    val severity: String?,
    val certainty: String?,
    val effective: String?,
    val onset: String?,
    val expires: String?,
    val senderName: String?,
    val headline: String?,
    val description: String?,
    val instruction: String?,
    val areas: List<ParsedArea>,
    val polygonUrl: String?
  )

  /**
   * Parses one CAP document.
   *
   * Returns null — never a partially-filled object — when the payload has no
   * identifier or no event, or when the XML is malformed. A document we cannot
   * read is dropped, not guessed at.
   */
  fun parse(
    xml: String,
    preferredLanguage: String = "en-IN"
  ): ParsedCap? {
    val parser = try {
      Xml.newPullParser()
    } catch (e: Exception) {
      return null
    }
    try {
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    } catch (e: Exception) {
      // Older/limited pull parsers may not expose this; the parser handles
      // prefixes manually via localName(), so it is not required.
    }
    try {
      parser.setInput(StringReader(xml))
    } catch (e: Exception) {
      return null
    }

    var identifier: String? = null
    var sender: String? = null
    var sent: String? = null
    var status: String? = null
    var msgType: String? = null
    var scope: String? = null

    // ---- <info> block selection -------------------------------------------
    // A document may carry several <info> blocks in different languages, in
    // document order. The language is NOT known when <info> opens: the real
    // SACHET documents carry no xml:lang attribute and put the code in a
    // <language> CHILD element. So each block accumulates into scratch state
    // and is committed only at its closing tag, and only if it ranks better
    // than the block kept so far. Deciding at open time would let a later,
    // worse block (a Bengali block after English) overwrite a better one.
    var insideInfo = false
    var scratchLanguage: String? = null
    var scratchFields: MutableMap<String, String>? = null
    var scratchAreas: MutableList<ParsedArea>? = null
    var scratchPolygonUrl: String? = null

    var bestLanguage: String? = null
    var fields: MutableMap<String, String>? = null
    var areas: MutableList<ParsedArea>? = null
    var polygonUrl: String? = null

    // Current <area> being built.
    var areaDesc: String? = null
    var areaGeocodes = mutableListOf<Geocode>()
    var areaPolygon: String? = null
    var insideArea = false

    // Current <parameter> pair.
    var paramName: String? = null

    // <geocode><valueName/><value/></geocode>
    var insideGeocode = false
    var geoName: String? = null
    var geoValue: String? = null

    var type = parser.eventType
    try {
      while (type != XmlPullParser.END_DOCUMENT) {
        if (type == XmlPullParser.START_TAG) {
          when (localName(parser.name).lowercase()) {
            "info" -> {
              insideInfo = true
              scratchLanguage = null
              scratchFields = mutableMapOf()
              scratchAreas = mutableListOf()
              scratchPolygonUrl = null
              // xml:lang, when present, is authoritative for this block.
              val langAttr = parser.getAttributeValue(null, "lang").orEmpty()
              if (langAttr.isNotBlank()) scratchLanguage = langAttr
            }

            "language" -> if (insideInfo) {
              val text = safeText(parser)
              if (text.isNotBlank()) scratchLanguage = text
            }

            "area" -> if (insideInfo) {
              insideArea = true
              areaDesc = null
              areaGeocodes = mutableListOf()
              areaPolygon = null
            }

            "geocode" -> if (insideArea) insideGeocode = true

            "valuename" -> {
              val text = safeText(parser)
              if (insideGeocode) geoName = text else paramName = text
            }

            "value" -> {
              val text = safeText(parser)
              if (insideGeocode) {
                geoValue = text
              } else if (paramName.equals(POLYGON_PARAM, ignoreCase = true) && text.isNotBlank()) {
                scratchPolygonUrl = text
              }
            }

            "parameter" -> paramName = null

            else -> {
              val name = localName(parser.name).lowercase()
              if (insideArea) {
                when (name) {
                  "areadesc" -> areaDesc = safeText(parser)
                  "polygon" -> areaPolygon = safeText(parser)
                  // A <cap:circle> is a centre+radius we cannot draw honestly as
                  // an area, so it is deliberately ignored, not approximated.
                  "circle" -> Unit
                }
              } else if (insideInfo && name in INFO_FIELDS) {
                val text = safeText(parser)
                if (text.isNotBlank()) scratchFields?.putIfAbsent(name, text)
              } else if (!insideInfo && name in ENVELOPE_FIELDS) {
                val text = safeText(parser)
                if (text.isNotBlank()) {
                  when (name) {
                    "identifier" -> if (identifier == null) identifier = text
                    "sender" -> if (sender == null) sender = text
                    "sent" -> if (sent == null) sent = text
                    "status" -> if (status == null) status = text
                    "msgtype" -> if (msgType == null) msgType = text
                    "scope" -> if (scope == null) scope = text
                  }
                }
              }
            }
          }
        } else if (type == XmlPullParser.END_TAG) {
          when (localName(parser.name).lowercase()) {
            // Commit or discard this block now that its language is known.
            "info" -> if (insideInfo) {
              insideInfo = false
              val lang = scratchLanguage.orEmpty()
              if (bestLanguage == null || isBetterMatch(lang, bestLanguage!!, preferredLanguage)) {
                bestLanguage = lang
                fields = scratchFields
                areas = scratchAreas
                polygonUrl = scratchPolygonUrl
              }
              scratchFields = null
              scratchAreas = null
              scratchPolygonUrl = null
              scratchLanguage = null
            }

            "geocode" -> {
              if (insideGeocode && geoName != null && geoValue != null) {
                areaGeocodes.add(Geocode(geoName!!, geoValue!!))
              }
              insideGeocode = false
              geoName = null
              geoValue = null
            }

            "parameter" -> paramName = null

            // Commit the area when its closing tag is reached.
            "area" -> if (insideArea) {
              scratchAreas?.add(ParsedArea(areaDesc, areaGeocodes.toList(), areaPolygon))
              insideArea = false
              areaDesc = null
              areaGeocodes = mutableListOf()
              areaPolygon = null
            }
          }
        }
        type = parser.next()
      }
    } catch (e: Exception) {
      // Malformed XML: a document we cannot read is dropped, never half-parsed.
      return null
    }

    val id = identifier?.takeIf { it.isNotBlank() } ?: return null
    val ev = fields?.get("event")?.takeIf { it.isNotBlank() } ?: return null

    return ParsedCap(
      identifier = id,
      sender = sender,
      sent = sent,
      status = status,
      msgType = msgType,
      scope = scope,
      language = bestLanguage,
      category = fields["category"],
      event = ev,
      urgency = fields["urgency"],
      severity = fields["severity"],
      certainty = fields["certainty"],
      effective = fields["effective"],
      onset = fields["onset"],
      expires = fields["expires"],
      senderName = fields["sendername"],
      headline = fields["headline"],
      description = fields["description"],
      instruction = fields["instruction"],
      areas = areas?.toList().orEmpty(),
      polygonUrl = polygonUrl
    )
  }

  /**
   * Is [candidate] a better language than [current]?
   * An exact match (en-IN) wins over a base-language match (en), which wins
   * over anything else.
   */
  private fun isBetterMatch(candidate: String, current: String, preferred: String): Boolean {
    fun rank(lang: String): Int = when {
      lang.equals(preferred, ignoreCase = true) -> 3
      lang.substringBefore('-').equals(preferred.substringBefore('-'), ignoreCase = true) -> 2
      lang.startsWith("en", ignoreCase = true) -> 1
      else -> 0
    }
    return rank(candidate) > rank(current)
  }

  fun localName(raw: String): String =
    raw.substringAfterLast(':').ifBlank { raw }

  /**
   * Reads an element's text safely, leaving the parser on the element's END_TAG.
   *
   * The real CAP payloads are full of self-closing empties — `<cap:note/>`,
   * `<cap:restriction/>`, `<cap:description/>`. Two wrong approaches:
   *
   *   - plain nextText() throws on those, and the exception escapes the loop,
   *     silently dropping every field after the first empty element;
   *   - calling parser.next() first to peek (a tempting "is it empty?" check)
   *     overshoots: it already consumed the event, so the following nextText()
   *     reads the NEXT element's text and the whole stream desynchronises.
   *
   * [XmlPullParser.isEmptyElementTag] answers the question without moving, so
   * an empty element returns "" and a populated one returns its text, both
   * leaving the cursor exactly where the main loop expects it.
   */
  private fun safeText(parser: XmlPullParser): String = try {
    if (parser.isEmptyElementTag) {
      ""
    } else {
      parser.nextText().orEmpty().trim()
    }
  } catch (e: Exception) {
    ""
  }

  const val POLYGON_PARAM = "Polygon URL"

  private val INFO_FIELDS = setOf(
    "category", "event", "urgency", "severity", "certainty", "effective",
    "onset", "expires", "headline", "description", "instruction", "sendername", "web"
  )

  private val ENVELOPE_FIELDS = setOf(
    "identifier", "sender", "sent", "status", "msgtype", "scope"
  )
}
