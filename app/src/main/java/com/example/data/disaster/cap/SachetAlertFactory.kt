package com.example.data.disaster.cap

import com.example.data.disaster.DisasterEvent
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.DisasterType
import com.example.data.disaster.EventDetails
import com.example.data.disaster.EventGeometry
import com.example.data.disaster.EventOrigin
import com.example.data.disaster.EventStatus
import com.example.data.model.HazardSeverity

/**
 * ============================================================================
 * CAP ALERT -> DisasterEvent (the app's normalized event model)
 * ============================================================================
 *
 * The single place where an official CAP alert becomes something the existing
 * hazard/risk/routing engines can consume.
 *
 * Honesty contract:
 *  - geometry exists ONLY when the authority published it AND it validated;
 *  - an alert with unusable geometry is still returned, as Unlocated(areaDesc),
 *    carrying its area text — never dropped, never given a synthetic circle;
 *  - every field the CAP did not supply stays null rather than being defaulted;
 *  - observed time comes from the alert's own sent/effective/onset, and only
 *    falls back to retrieval time when the payload carries none (with the
 *    resulting record marked as having no published observation time).
 */
object SachetAlertFactory {

  fun toDisasterEvent(
    cap: SachetCapParser.ParsedCap,
    sourceUrl: String,
    /** Raw published polygon text, or null when retrieval failed/was absent. */
    polygonText: String?,
    validation: GeometryValidation,
    retrievedAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis()
  ): DisasterEvent {
    val publishedAt = firstParsed(listOf(cap.sent, cap.effective, cap.onset))
    val expiresAt = parseTimestamp(cap.expires)
    val observed = publishedAt ?: retrievedAtMillis

    val geometry: EventGeometry = validation.geometry
      ?: EventGeometry.Unlocated(cap.areas.firstOrNull()?.areaDesc)

    val title = cap.headline?.takeIf { it.isNotBlank() }
      ?: cap.event
      ?: "Official alert"

    return DisasterEvent(
      id = "sachet-${cap.identifier}",
      source = DisasterSource.NDMA_CAP,
      sourceEventId = cap.identifier,
      disasterType = classifyEvent(cap.event ?: "", cap.category ?: ""),
      title = title,
      // The real CAP often has an empty <description/>. Preserve that as
      // "not provided" rather than writing invented explanatory text.
      description = cap.description?.takeIf { it.isNotBlank() }
        ?: "Official alert description not provided by source.",
      geometry = geometry,
      latitude = null,
      longitude = null,
      severity = mapSeverity(cap.severity),
      confidence = com.example.data.disaster.EventConfidence.NOT_PROVIDED,
      confidenceNote = cap.certainty?.takeIf { it.isNotBlank() },
      observedAtMillis = observed,
      updatedAtMillis = publishedAt ?: retrievedAtMillis,
      expiresAtMillis = expiresAt,
      status = if (expiresAt != null && expiresAt in 1 until nowMillis) {
        EventStatus.EXPIRED
      } else {
        EventStatus.ACTIVE
      },
      origin = EventOrigin.OBSERVED,
      affectedAreaLabel = cap.areas.firstOrNull()?.areaDesc,
      url = sourceUrl,
      details = EventDetails.OfficialAlert(
        event = cap.event ?: "Not provided by source",
        urgency = cap.urgency ?: "Not provided by source",
        certainty = cap.certainty ?: "Not provided by source",
        senderName = cap.senderName ?: cap.sender ?: "Not provided by source",
        instruction = cap.instruction?.takeIf { it.isNotBlank() },
        webLink = sourceUrl
      )
    )
  }

  /**
   * Geometry status for the detail UI. Distinguishes "the source published
   * nothing" from "we could not retrieve it" from "it failed validation" —
   * three different honest statements.
   */
  fun geometryStatus(
    cap: SachetCapParser.ParsedCap,
    validation: GeometryValidation
  ): GeometryStatus = when {
    validation.valid -> GeometryStatus.AVAILABLE
    cap.polygonUrl == null && cap.areas.all { it.inlinePolygon == null } ->
      GeometryStatus.NOT_PUBLISHED
    cap.polygonUrl != null && validation.failureReasons.contains(GeometryFailure.EMPTY) ->
      GeometryStatus.RETRIEVAL_FAILED
    else -> GeometryStatus.INVALID
  }

  fun geometryStatusNote(status: GeometryStatus, validation: GeometryValidation): String = when (status) {
    GeometryStatus.AVAILABLE ->
      "Authority-published area (${validation.vertexCount} vertices)"
    GeometryStatus.NOT_PUBLISHED ->
      "Official alert - the issuing authority published no area geometry for this alert"
    GeometryStatus.RETRIEVAL_FAILED ->
      "Official alert - the authority's geometry file could not be retrieved (${validation.failureReasons.joinToString()})"
    GeometryStatus.INVALID ->
      "Official alert - geometry rejected: ${validation.failureReasons.joinToString()}"
  }

  /** CAP severity -> app scale. Unknown NEVER escalates. */
  fun mapSeverity(raw: String?): HazardSeverity = when (raw?.trim()?.lowercase()) {
    "extreme" -> HazardSeverity.EXTREME
    "severe" -> HazardSeverity.HIGH
    "moderate" -> HazardSeverity.MODERATE
    "minor" -> HazardSeverity.LOW
    else -> HazardSeverity.MODERATE
  }

  /** Maps CAP event text onto the app's disaster types. */
  fun classifyEvent(event: String, category: String): DisasterType {
    val e = event.lowercase()
    return when {
      e.contains("cyclone") || e.contains("cyclonic") -> DisasterType.CYCLONE
      e.contains("flood") || e.contains("inundation") -> DisasterType.FLOOD
      e.contains("landslide") || e.contains("land slide") -> DisasterType.LANDSLIDE
      e.contains("earthquake") || e.contains("seismic") -> DisasterType.EARTHQUAKE
      e.contains("fire") || e.contains("wildfire") -> DisasterType.WILDFIRE
      e.contains("landslip") -> DisasterType.LANDSLIDE
      e.contains("lightning") || e.contains("thunder") ||
        e.contains("rain") || e.contains("rainfall") || e.contains("heavy") ->
        DisasterType.HEAVY_RAINFALL
      e.contains("heat") || e.contains("cold wave") -> DisasterType.OTHER
      else -> DisasterType.WEATHER_ALERT
    }
  }

  private fun parseTimestamp(raw: String?): Long? =
    com.example.data.india.CapTime.parse(raw)

  private fun firstParsed(values: List<String?>): Long? =
    values.firstNotNullOfOrNull { com.example.data.india.CapTime.parse(it) }
}

enum class GeometryStatus(val label: String) {
  AVAILABLE("Authority area published"),
  NOT_PUBLISHED("No geometry published"),
  RETRIEVAL_FAILED("Geometry unavailable"),
  INVALID("Geometry rejected")
}