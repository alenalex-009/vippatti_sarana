package com.example.data.disaster.providers

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterEvent
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.DisasterType
import com.example.data.disaster.EventConfidence
import com.example.data.disaster.EventDetails
import com.example.data.disaster.EventGeometry
import com.example.data.disaster.EventOrigin
import com.example.data.disaster.FireIntensityScale
import com.example.data.disaster.IndiaGeo
import com.example.data.disaster.ProviderFailureKind
import com.example.data.disaster.ProviderResult
import com.example.data.model.HazardSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ============================================================================
 * PRIORITY 2 PROVIDER — NASA FIRMS ACTIVE FIRE (official documented API).
 * ============================================================================
 *
 * Official Area CSV API (documented at
 * https://firms.modaps.eosdis.nasa.gov/api/area/):
 *
 *   https://firms.modaps.eosdis.nasa.gov/api/area/csv/
 *     [MAP_KEY]/VIIRS_SNPP_NRT/[west,south,east,north]/[DAY_RANGE]
 *
 * MAP_KEY is a free key requested from FIRMS ("Get MAP Key" on the API page)
 * and is supplied via app/.env (never hardcoded). FIRMS limits each MAP_KEY
 * to 5000 transactions / 10 minutes; we query at most once per refresh cycle.
 * If the key is missing, the provider reports an honest auth failure and the
 * app continues with the providers that work.
 */
class FirmsFireProvider(
  private val mapKeyProvider: () -> String,
  private val httpClient: OkHttpClient = defaultHttpClient()
) : DisasterDataProvider {

  override val providerId: DisasterSource = DisasterSource.NASA_FIRMS

  override suspend fun fetchIndiaEvents(): ProviderResult = withContext(Dispatchers.IO) {
    val mapKey = mapKeyProvider().trim()
    if (mapKey.isBlank() || mapKey == FIRMS_PLACEHOLDER_KEY) {
      // UNCONFIGURED, not failed: this optional source simply has no key in
      // this build. The UI must not show it as an error or as live data.
      return@withContext ProviderResult.Failure(
        reason = "NASA FIRMS fire layer unavailable — no MAP_KEY configured. " +
          "Request a free key and add FIRMS_MAP_KEY to app/.env.",
        kind = ProviderFailureKind.UNCONFIGURED
      )
    }
    val url = "https://firms.modaps.eosdis.nasa.gov/api/area/csv/" +
      "$mapKey/${SATELLITE_SOURCE}/" +
      "${IndiaGeo.MIN_LON},${IndiaGeo.MIN_LAT},${IndiaGeo.MAX_LON},${IndiaGeo.MAX_LAT}/$DAY_RANGE"
    try {
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", "VippattiSarana-DisasterRelief/1.0 (Android; NASA FIRMS)")
        .build()
      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()
        if (response.code == 403) {
          // A key IS configured and the provider refused it: authentication
          // failure, distinct from "not configured".
          return@withContext ProviderResult.Failure(
            reason = "NASA FIRMS rejected the MAP_KEY (HTTP 403) — verify FIRMS_MAP_KEY.",
            kind = ProviderFailureKind.AUTHENTICATION_FAILED
          )
        }
        if (response.code == 400) {
          // FIRMS answers a malformed or EXPIRED/REVOKED key with 400
          // "Invalid MAP_KEY." — that is also an authentication failure, not
          // a transient service problem. Surfacing the real cause means the
          // user regenerates the key instead of blaming connectivity.
          return@withContext ProviderResult.Failure(
            reason = "NASA FIRMS rejected the MAP_KEY (HTTP 400 Invalid MAP_KEY) — " +
              "the key is wrong or expired. Generate a new MAP_KEY and update FIRMS_MAP_KEY.",
            kind = ProviderFailureKind.AUTHENTICATION_FAILED
          )
        }
        if (!response.isSuccessful || body.isNullOrBlank()) {
          return@withContext ProviderResult.Failure(
            "NASA FIRMS fire service unavailable (HTTP ${response.code})."
          )
        }
        val events = FirmsCsvParser.parse(body)
        if (events.isEmpty()) {
          // Distinguish the three zero-event shapes honestly:
          //  - header-only CSV  -> a genuine empty query result (Success, 0
          //    observations — never fabricated);
          //  - HTML/error page  -> the service refused instead of answering;
          //  - any other text   -> malformed/unusable payload.
          // Both non-CSV shapes are provider failures, not "no fires".
          val firstLine = body.lineSequence().firstOrNull()?.trim().orEmpty().lowercase()
          val looksLikeCsv = firstLine.contains("latitude") && firstLine.contains("longitude")
          if (!looksLikeCsv) {
            val shape = if (firstLine.startsWith("<")) "an error page" else "an unreadable (malformed) response"
            return@withContext ProviderResult.Failure(
              "NASA FIRMS returned $shape instead of fire data."
            )
          }
        }
        ProviderResult.Success(events, System.currentTimeMillis())
      }
    } catch (e: IOException) {
      ProviderResult.Failure(
        "No connection to the fire detection service — cached data stays available."
      )
    } catch (e: Exception) {
      ProviderResult.Failure("Fire detection feed error (${e.javaClass.simpleName}).")
    }
  }

  companion object {
    const val FIRMS_PLACEHOLDER_KEY = "YOUR_FIRMS_MAP_KEY_HERE"
    const val SATELLITE_SOURCE = "VIIRS_SNPP_NRT"
    const val DAY_RANGE = 2

    fun defaultHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
  }
}

/**
 * Pure parser for the FIRMS Area CSV schema (official attribute table):
 * latitude, longitude, bright_ti4, scan, track, acq_date, acq_time,
 * satellite, instrument, confidence, version, bright_ti5, frp, daynight.
 */
object FirmsCsvParser {

  fun parse(body: String): List<DisasterEvent> {
    val lines = body.lineSequence()
      .map { it.trimEnd('\r', '\n') }
      .filter { it.isNotBlank() }
      .toList()
    if (lines.isEmpty()) return emptyList()
    val header = splitCsvLine(lines[0]).map { normalizeHeader(it) }
    val latIdx = header.indexOf("latitude")
    val lonIdx = header.indexOf("longitude")
    if (latIdx < 0 || lonIdx < 0) return emptyList()
    val dateIdx = header.indexOf("acq_date")
    val timeIdx = header.indexOf("acq_time")
    val satIdx = header.indexOf("satellite")
    val instrIdx = header.indexOf("instrument")
    val confIdx = header.indexOf("confidence")
    val frpIdx = header.indexOf("frp")
    val dayNightIdx = header.indexOf("daynight")

    val out = mutableListOf<DisasterEvent>()
    for (i in 1 until lines.size) {
      val cells = splitCsvLine(lines[i])
      if (cells.size <= maxOf(latIdx, lonIdx)) continue
      val lat = cells[latIdx].toDoubleOrNull() ?: continue
      val lon = cells[lonIdx].toDoubleOrNull() ?: continue
      if (!IndiaGeo.contains(lat, lon)) continue

      val acqDate = if (dateIdx >= 0) cells[dateIdx] else ""
      val acqTime = if (timeIdx >= 0) cells[timeIdx].padStart(4, '0') else ""
      val observedAt = parseAcquisitionMillis(acqDate, acqTime)
      val confidenceRaw = if (confIdx >= 0 && confIdx < cells.size) cells[confIdx] else ""

      // VOLUME/QUALITY POLICY (user report: "too many fire dots"): FIRMS
      // ships an explicit per-detection confidence flag - NASA recommends
      // discarding "low"-confidence detections for alerting use. Dropping
      // them is the provider's own quality rule, not invented filtering.
      if (mapConfidence(confidenceRaw) == EventConfidence.LOW) continue
      val id = "firms-${acqDate}T${acqTime}-${lat}-${lon}"
      out += DisasterEvent(
        id = id,
        source = DisasterSource.NASA_FIRMS,
        sourceEventId = id,
        disasterType = DisasterType.WILDFIRE,
        title = "Active Fire Detection",
        description = "Satellite fire/hotspot detection from NASA FIRMS.",
        geometry = EventGeometry.Point(lat, lon),
        latitude = lat,
        longitude = lon,
        // Severity comes from the provider's own FRP measurement and confidence
        // flag (documented thresholds in FireIntensityScale) instead of a fixed
        // MODERATE for every detection.
        severity = FireIntensityScale.severityFor(
          frpMegawatts = if (frpIdx >= 0 && frpIdx < cells.size) cells[frpIdx].toDoubleOrNull() else null,
          confidence = mapConfidence(confidenceRaw)
        ),
        confidence = mapConfidence(confidenceRaw),
        confidenceNote = if (confidenceRaw.isBlank()) null else "detection confidence $confidenceRaw",
        observedAtMillis = observedAt,
        updatedAtMillis = observedAt,
        origin = EventOrigin.OBSERVED,
        details = EventDetails.Fire(
          satellite = if (satIdx >= 0 && satIdx < cells.size) cells[satIdx] else "",
          instrument = if (instrIdx >= 0 && instrIdx < cells.size) cells[instrIdx] else "",
          frpMegawatts = if (frpIdx >= 0 && frpIdx < cells.size) cells[frpIdx].toDoubleOrNull() else null,
          dayNight = if (dayNightIdx >= 0 && dayNightIdx < cells.size) cells[dayNightIdx] else null
        )
      )
    }
    return out
  }

  /** FIRMS confidence: VIIRS uses l/n/h (low/nominal/high); MODIS uses 0-100. */
  fun mapConfidence(raw: String): EventConfidence = when (raw.trim().lowercase()) {
    "l", "low" -> EventConfidence.LOW
    "n", "nominal" -> EventConfidence.NOMINAL
    "h", "high" -> EventConfidence.HIGH
    else -> when (raw.toIntOrNull()) {
      null -> EventConfidence.NOT_PROVIDED
      in 0..34 -> EventConfidence.LOW
      in 35..79 -> EventConfidence.NOMINAL
      else -> EventConfidence.HIGH
    }
  }

  /** acq_date=YYYY-MM-DD, acq_time=HHMM (UTC) -> epoch millis (0 if unparsable). */
  fun parseAcquisitionMillis(date: String, time: String): Long = try {
    java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC)
      .plusMinutes(
        (time.take(2).toIntOrNull() ?: 0) * 60L + (time.drop(2).take(2).toIntOrNull() ?: 0)
      )
      .toInstant().toEpochMilli()
  } catch (e: Exception) {
    0L
  }

  private fun splitCsvLine(line: String): List<String> =
    line.split(',').map { it.trim().trim('"') }

  private fun normalizeHeader(name: String): String = name.trim().trim('"').lowercase()
}

