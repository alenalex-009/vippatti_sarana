package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Grading
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Landslide
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore

@Composable
fun HazardZoneDetailDialog(
  zone: com.example.data.model.HazardZone,
  detail: com.example.data.disaster.ZoneDetail,
  onDismiss: () -> Unit,
  /** Opens the safe-zone sheet for the nearest viable zone (exact record). */
  onOpenSafeZone: ((String) -> Unit)? = null
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = ObsidianSurface,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(24.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .fillMaxWidth()
          // Long hazard intelligence scrolls instead of clipping on short screens.
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        val accent = hazardAccent(zone)
        // ---- HEADER: icon chip / "Flood hazard" / record name / severity pill
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top
        ) {
          DetailIconChip(hazardTypeIcon(zone.type), accent,
            contentDescription = zone.type.label + " hazard")
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
              // Sentence-case per the reference; the record's own type words,
              // never "DEMO" here - global demo state lives outside the card.
              text = zone.type.label + " hazard",
              fontSize = 21.sp,
              fontWeight = FontWeight.Black,
              color = TacticalOnSurface,
              lineHeight = 25.sp,
              maxLines = 2,
              softWrap = true,
              overflow = TextOverflow.Visible
            )
            Text(
              text = zone.name,
              fontSize = 13.sp,
              color = TacticalOnSurfaceVariant,
              lineHeight = 17.sp,
              maxLines = 3,
              softWrap = true,
              overflow = TextOverflow.Visible
            )
            Spacer(Modifier.height(4.dp))
            StatusPill(
              icon = Icons.Default.PriorityHigh,
              label = zone.severity.label,
              accent = accent,
              tag = "hazard_severity_pill"
            )
            if (detail.userPositionLabel != null) {
              // USER POSITION RELATIVE TO HAZARD - the first step of the
              // evacuation chain (INSIDE / NEAR edge / OUTSIDE).
              Text(
                text = detail.userPositionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (detail.userPositionLabel.startsWith("You are INSIDE"))
                  EmergencyRedBright else TacticalOnSurfaceVariant,
                modifier = Modifier.testTag("hazard_user_position")
              )
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalOnSurfaceVariant)
          }
        }

        // ---- SUMMARY ROW: three compact cards, honest "Data unavailable"
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SummaryCard(
            label = "Affected radius",
            value = String.format(java.util.Locale.US, "%.1f km", zone.radiusMeters / 1000.0),
            icon = Icons.Default.Radar,
            modifier = Modifier.weight(1f),
            tag = "hazard_stat_radius"
          )
          SummaryCard(
            label = "Trend",
            value = zone.trend.label,
            icon = Icons.Default.TrendingFlat,
            valueAccent = if (zone.trend == com.example.data.model.HazardTrend.WORSENING)
              WarningAmber else TacticalCyan,
            modifier = Modifier.weight(1f),
            tag = "hazard_stat_trend"
          )
          val detected = detail.detectedAtMillis?.let { ms ->
            com.example.data.news.NewsPresentation.relativeAge(ms, System.currentTimeMillis())
          }
          SummaryCard(
            label = "Detected",
            value = detected ?: "Data unavailable",
            subValue = detail.detectedAtMillis?.let {
              java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(it))
            },
            icon = Icons.Default.Schedule,
            modifier = Modifier.weight(1f),
            tag = "hazard_stat_detected",
            unavailable = detected == null
          )
        }

        // ---- NEAREST SAFE ZONE: tappable, bound to the exact record id.
        // Only ever the zone the evaluator accepted for THIS hazard context.
        val nearest = detail.nearestSafeZone
        if (nearest != null && onOpenSafeZone != null) {
          NearestSafeZoneCard(
            name = nearest.name,
            detailLine = "${nearest.distanceText} \u2022 ${nearest.capacityText}",
            onClick = { onOpenSafeZone(nearest.id) }
          )
        } else if (nearest != null) {
          NearestSafeZoneCard(
            name = nearest.name,
            detailLine = "${nearest.distanceText} \u2022 ${nearest.capacityText}",
            onClick = onDismiss
          )
        } else {
          // Honest empty state: no viable zone, no navigation promise.
          Text(
            text = "No viable safe zone identified yet.",
            fontSize = 13.sp,
            color = TacticalOnSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.testTag("hazard_verdict_box")
          )
        }

        // ---- EXPANDABLE "{type} details": the existing mapped sections,
        // nothing deleted, only disclosed on demand.
        var detailsExpanded by remember(zone.id) { mutableStateOf(false) }
        TextButton(
          onClick = { detailsExpanded = !detailsExpanded },
          contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
          modifier = Modifier.heightIn(min = 44.dp).testTag("hazard_details_toggle")
        ) {
          Icon(
            imageVector = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = TacticalOnSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (detailsExpanded) "Hide ${zone.type.label.lowercase()} details"
              else "${zone.type.label} details",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TacticalOnSurface
          )
        }

        if (detailsExpanded) {
          detail.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
              SectionHeader(section.heading, Icons.Default.Description, TacticalCyan)
              Spacer(Modifier.height(4.dp))
              DetailPanel {
                section.fields.forEachIndexed { index, field ->
                  if (index > 0) RowDividerLine()
                  DetailRow(
                    label = field.label,
                    value = field.value ?: "Data unavailable",
                    unavailable = field.value == null
                  )
                }
              }
            }
          }
          // ---- ONE concise provenance block (moved out of the compact view)
          Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            SectionHeader("Source & details", Icons.Default.Info, TacticalCyan)
            Spacer(Modifier.height(4.dp))
            DetailPanel {
              DetailRow("Status", zone.sourceStatus)
              RowDividerLine()
              DetailRow(
                "Data",
                zone.provenance.classification.label,
                subNote = "Source: ${zone.provenance.source} \u00b7 ${zone.provenance.status}"
              )
              RowDividerLine()
              DetailRow(
                "Severity record",
                zone.severity.label,
                tag = "hazard_stat_severity"
              )
            }
          }
        }

        // ---- FOOTER: Area centre (the only geographic line kept compact).
        AreaCentreCard(
          latText = String.format(java.util.Locale.US, "%.4f N", zone.center.lat),
          lonText = String.format(java.util.Locale.US, "%.4f E", zone.center.lon)
        )
      }
    }
  }
}

/** Hazard-type accent matching the map language (fire red, cyclone teal...). */
private fun hazardAccent(zone: com.example.data.model.HazardZone): Color =
  Color(com.example.data.disaster.DisasterTypeColors.argbFor(zone.type))

/** Per-type leading icon for the header chip. */
@androidx.compose.runtime.Composable
private fun hazardTypeIcon(type: com.example.data.model.HazardType): androidx.compose.ui.graphics.vector.ImageVector =
  when (type) {
    com.example.data.model.HazardType.FLOOD -> Icons.Default.WaterDrop
    com.example.data.model.HazardType.HEAVY_RAINFALL -> Icons.Default.Grading
    com.example.data.model.HazardType.FIRE -> Icons.Default.LocalFireDepartment
    com.example.data.model.HazardType.EARTHQUAKE -> Icons.Default.Emergency
    com.example.data.model.HazardType.CYCLONE -> Icons.Default.Air
    com.example.data.model.HazardType.LANDSLIDE -> Icons.Default.Landslide
    else -> Icons.Default.Warning
  }

/** The one-glance verdict block shared by the map detail dialogs. */
@Composable
private fun VerdictBox(accent: Color, title: String, body: String, tag: String) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(accent.copy(alpha = 0.12f))
      .border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Text(
      text = title,
      fontSize = 15.sp,
      fontWeight = FontWeight.Black,
      color = accent,
      lineHeight = 20.sp,
      modifier = Modifier.testTag(tag)
    )
    Text(
      text = body,
      fontSize = 13.sp,
      color = TacticalOnSurface,
      lineHeight = 17.sp
    )
  }
}

/** Big-number fact tile: label 11sp, value 15sp black. */
@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier, tag: String = "") {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(ObsidianContainerHigh)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
      .padding(horizontal = 10.dp, vertical = 9.dp)
      .let { if (tag.isNotEmpty()) it.testTag(tag) else it }
  ) {
    Text(
      label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
      color = TacticalOnSurfaceVariant, letterSpacing = 0.4.sp
    )
    Spacer(Modifier.height(2.dp))
    Text(
      value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = accent,
      maxLines = 2, softWrap = true, overflow = TextOverflow.Visible
    )
  }
}

@Composable
private fun InfoPill(label: String, value: String, accent: Color) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(ObsidianContainerHigh)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurfaceVariant)
    Text(value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = accent)
  }
}

// ============================================================================
// DISASTER EVENT DETAIL - tapped USGS/FIRMS/IMD/user-report marker. Every
// field renders from the REAL event; missing data says "Not available" —
// nothing is invented.
// ============================================================================

@Composable
fun DisasterEventDetailDialog(
  event: com.example.data.disaster.DisasterEvent,
  onDismiss: () -> Unit
) {
  val freshness = com.example.data.disaster.DisasterCachePolicy.label(
    event.updatedAtMillis,
    System.currentTimeMillis()
  )
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = ObsidianSurface,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(20.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .fillMaxWidth()
          // Detail content scrolls instead of clipping on short screens.
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = event.disasterType.label.uppercase(),
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              color = EmergencyRedBright,
              letterSpacing = 0.8.sp
            )
            Text(
              text = event.title,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface,
              lineHeight = 19.sp
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalOnSurfaceVariant)
          }
        }

        // Bottom line first: what this event IS, in one sentence, then the
        // three facts that matter (severity / status / freshness) as tiles.
        VerdictBox(
          accent = when {
            event.origin == com.example.data.disaster.EventOrigin.REPORTED -> WarningAmber
            event.status.label == "Active" -> EmergencyRedBright
            else -> TacticalCyan
          },
          title = when (event.origin) {
            com.example.data.disaster.EventOrigin.REPORTED ->
              "Citizen report — not verified"
            else -> "${event.source.label} alert — ${event.status.label.lowercase()}"
          },
          body = event.description.takeIf { it.isNotBlank() }?.take(160)
            ?: "Detected at ${
              String.format(java.util.Locale.US, "%.2f, %.2f",
                (event.geometry as? com.example.data.disaster.EventGeometry.Point)?.lat
                  ?: (event.geometry as? com.example.data.disaster.EventGeometry.MultiPoint)?.points?.firstOrNull()?.lat ?: 0.0,
                (event.geometry as? com.example.data.disaster.EventGeometry.Point)?.lon
                  ?: (event.geometry as? com.example.data.disaster.EventGeometry.MultiPoint)?.points?.firstOrNull()?.lon ?: 0.0)
            }",
          tag = "event_verdict_box"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StatTile("SEVERITY", event.severity.label, EmergencyRedBright, Modifier.weight(1f),
            tag = "event_stat_severity")
          StatTile("STATUS", event.status.label, TacticalCyan, Modifier.weight(1f),
            tag = "event_stat_status")
          StatTile("DATA", freshness, TacticalOnSurface, Modifier.weight(1f),
            tag = "event_stat_freshness")
        }

        // Source / provider provenance.
        DetailLine("Source / provider", event.source.label)
        DetailLine(
          "Data state",
          when (event.origin) {
            com.example.data.disaster.EventOrigin.OBSERVED -> "Observed (provider measurement)"
            com.example.data.disaster.EventOrigin.REPORTED -> "Reported by a citizen - UNVERIFIED"
            com.example.data.disaster.EventOrigin.DERIVED -> "Derived from provider data"
            com.example.data.disaster.EventOrigin.SIMULATED -> "Demo field data"
          }
        )
        DetailLine(
          "Observed at",
          if (event.observedAtMillis > 0L) {
            java.text.SimpleDateFormat(
              "MMM d, yyyy HH:mm",
              java.util.Locale.getDefault()
            ).format(java.util.Date(event.observedAtMillis))
          } else {
            "Not available"
          }
        )
        DetailLine(
          "Location",
          when (val g = event.geometry) {
            is com.example.data.disaster.EventGeometry.Point ->
              String.format(java.util.Locale.US, "%.4f, %.4f", g.lat, g.lon)
            is com.example.data.disaster.EventGeometry.MultiPoint ->
              if (g.points.isEmpty()) "${g.points.size} points (no coordinates)"
              else "${g.points.size} points (first: " + String.format(
                java.util.Locale.US, "%.4f, %.4f", g.points.first().lat, g.points.first().lon
              ) + ")"
            is com.example.data.disaster.EventGeometry.Line ->
              "Track with ${g.points.size} points"
            is com.example.data.disaster.EventGeometry.Polygon ->
              "Alert area polygon (${g.ring.size} vertices)"
            is com.example.data.disaster.EventGeometry.RasterLayer ->
              "Raster layer: ${g.title}"
            // The source published no usable geometry: say so instead of showing
            // a coordinate the provider never gave.
            is com.example.data.disaster.EventGeometry.Unlocated ->
              g.areaLabel?.takeIf { it.isNotBlank() }?.let { "$it (no polygon from source)" }
                ?: "Not provided by source"
          }
        )
        when (val d = event.details) {
          is com.example.data.disaster.EventDetails.Quake -> {
            DetailLine("Magnitude", d.magnitude.toString())
            DetailLine("Depth", "${d.depthKm} km")
            DetailLine("Place", d.place.ifBlank { "Not available" })
          }
          is com.example.data.disaster.EventDetails.Fire -> {
            DetailLine("Satellite", d.satellite.ifBlank { "Not available" })
            DetailLine("Instrument", d.instrument.ifBlank { "Not available" })
            DetailLine(
              // The intensity word is derived from the provider's own FRP value
              // (documented thresholds); a detection without FRP says so.
              "Fire radiative power",
              d.frpMegawatts?.let { frp ->
                "$frp MW (${com.example.data.disaster.FireIntensityScale.of(frp).label} intensity)"
              } ?: "Not available"
            )
            DetailLine("Day/night", d.dayNight ?: "Not available")
          }
          is com.example.data.disaster.EventDetails.OfficialAlert -> {
            DetailLine("Alert event", d.event)
            DetailLine("Issued by", d.senderName)
            DetailLine("Urgency / certainty", "${d.urgency} / ${d.certainty}")
            DetailLine("Instruction", d.instruction ?: "Not available")
          }
          is com.example.data.disaster.EventDetails.UserIncident -> {
            DetailLine("Category", d.categoryLabel)
            DetailLine("Reporter note", d.reporterNote.ifBlank { "Not available" })
          }
          com.example.data.disaster.EventDetails.Generic -> Unit
        }
        DetailLine(
          "Confidence",
          when (event.confidence) {
            com.example.data.disaster.EventConfidence.NOT_PROVIDED ->
              "Not provided by source"
            else -> event.confidence.label + (event.confidenceNote?.let { " ($it)" } ?: "")
          }
        )
        event.affectedAreaLabel?.let { DetailLine("Affected area", it) }
        event.description.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            fontSize = 11.sp,
            color = TacticalOnSurfaceVariant,
            lineHeight = 15.sp
          )
        }
        if (event.url != null) {
          val context = androidx.compose.ui.platform.LocalContext.current
          Button(
            onClick = {
              context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(event.url))
              )
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OnNeonEmerald),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
          ) {
            Text("Open official source", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun DetailLine(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = label,
      fontSize = 12.sp,
      color = TacticalOnSurfaceVariant,
      lineHeight = 16.sp,
      modifier = Modifier.weight(0.42f)
    )
    Text(
      text = value,
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      color = TacticalOnSurface,
      lineHeight = 17.sp,
      modifier = Modifier.weight(0.58f)
    )
  }
}

// ============================================================================
// USER INCIDENT REPORT - "Add a Report" flow. Submissions are stored locally
// as USER_REPORT / REPORTED / unverified events with a TTL; they are never
// presented as verified disasters.
// ============================================================================


@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IncidentReportDialog(
  locationLabel: String,
  isGpsAvailable: Boolean,
  onDismiss: () -> Unit,
  onSubmit: (category: com.example.data.disaster.IncidentCategory, severityLabel: String, description: String) -> Unit
) {
  var selectedCategory by remember {
    mutableStateOf(com.example.data.disaster.IncidentCategory.ROAD_BLOCKED)
  }
  var selectedSeverity by remember { mutableStateOf("Moderate") }
  var description by remember { mutableStateOf("") }
  val severityOptions = listOf("Low", "Moderate", "High", "Extreme")

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = ObsidianSurface,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          // Keep Submit reachable while the keyboard is open.
          .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Add a Report",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
            Text(
              text = "Saved on this device as an UNVERIFIED report — visible only to you. " +
                "No relief-network backend exists, so no authority receives it.",
              fontSize = 12.sp,
              color = TacticalOnSurfaceVariant
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalOnSurfaceVariant)
          }
        }

        if (!isGpsAvailable) {
          Text(
            text = "Reporting needs a real device GPS fix — it is currently unavailable, " +
              "so a report cannot be attached to a trustworthy location. Enable location " +
              "services and try again.",
            fontSize = 10.sp,
            color = WarningAmber,
            lineHeight = 13.sp
          )
        }

        Text(
          "CATEGORY",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurfaceVariant,
          letterSpacing = 0.5.sp
        )
        // FlowRow wraps categories naturally at 360dp and large font scales.
        androidx.compose.foundation.layout.FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          com.example.data.disaster.IncidentCategory.entries.forEach { category ->
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                  if (category == selectedCategory) NeonEmerald.copy(alpha = 0.2f) else ObsidianContainerHigh
                )
                .border(
                  1.dp,
                  if (category == selectedCategory) NeonEmerald else TacticalOutlineVariant.copy(alpha = 0.5f),
                  RoundedCornerShape(8.dp)
                )
                .clickable { selectedCategory = category }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = category.label,
                fontSize = 11.sp,
                fontWeight = if (category == selectedCategory) FontWeight.Bold else FontWeight.Medium,
                color = if (category == selectedCategory) NeonEmerald else TacticalOnSurface
              )
            }
          }
        }

        Text(
          "SEVERITY",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurfaceVariant,
          letterSpacing = 0.5.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          severityOptions.forEach { option ->
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (option == selectedSeverity) EmergencyRedContainer.copy(alpha = 0.3f) else ObsidianContainerHigh)
                .border(
                  1.dp,
                  if (option == selectedSeverity) EmergencyRedBright else TacticalOutlineVariant.copy(alpha = 0.5f),
                  RoundedCornerShape(8.dp)
                )
                .clickable { selectedSeverity = option }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = option,
                fontSize = 11.sp,
                fontWeight = if (option == selectedSeverity) FontWeight.Bold else FontWeight.Medium,
                color = if (option == selectedSeverity) EmergencyRedBright else TacticalOnSurface
              )
            }
          }
        }

        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("What are you seeing? (optional)", fontSize = 12.sp) },
          minLines = 2,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier.fillMaxWidth()
        )

        DetailLine("Reporting location", locationLabel)

        Button(
          onClick = { onSubmit(selectedCategory, selectedSeverity, description) },
          colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OnNeonEmerald),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("submit_incident_button")
        ) {
          Text("Submit Report", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

// ============================================================================
// SAFE ZONE DETAI — ull carrying-capacity & resource intelligence
// ============================================================================
