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

@Composable
fun HazardZoneDetailDialog(
  zone: com.example.data.model.HazardZone,
  detail: com.example.data.disaster.ZoneDetail,
  onDismiss: () -> Unit
) {
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
          // Long hazard intelligence scrolls instead of clipping on short screens.
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = zone.type.label.uppercase() + " HAZARD",
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              color = EmergencyRedBright,
              letterSpacing = 0.8.sp
            )
            Text(
              text = zone.name,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
            com.example.ui.components.StatusBadge(
              status = com.example.data.model.statusOf(
                provenance = zone.provenance,
                eventAtMillis = zone.lastUpdatedMillis.takeIf { it > 0L }
              ).status
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalOnSurfaceVariant)
          }
        }

        // --- BOTTOM LINE FIRST: what this means, in one glance ---
        // (Field report: details read as a wall of equal rows. The verdict +
        // the way out now come before any data rows.)
        val nearest = detail.nearestSafeZone
        VerdictBox(
          accent = if (zone.provenance.classification ==
              com.example.data.model.DataClassification.SIMULATED
          ) WarningAmber else EmergencyRedBright,
          title = when {
            zone.provenance.classification ==
              com.example.data.model.DataClassification.SIMULATED ->
              "DEMO danger zone — practice mode"
            zone.severity.label == "Extreme" -> "Very dangerous area — stay away"
            else -> "Danger area — keep your distance"
          },
          body = nearest?.let {
            "Nearest safe spot: ${it.name} — ${it.distanceText} away (${it.capacityText})."
          } ?: "No viable safe zone identified yet.",
          tag = "hazard_verdict_box"
        )

        // --- KEY FACTS as big readable tiles (not cramped pills) ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StatTile("SEVERITY", zone.severity.label, EmergencyRedBright, Modifier.weight(1f),
            tag = "hazard_stat_severity")
          StatTile("TREND", zone.trend.label, TacticalCyan, Modifier.weight(1f),
            tag = "hazard_stat_trend")
          StatTile(
            "AFFECTED RADIUS",
            String.format(java.util.Locale.US, "%.1f km", zone.radiusMeters / 1000.0),
            TacticalOnSurface, Modifier.weight(1f),
            tag = "hazard_stat_radius"
          )
        }

        // --- DATA SECTIONS: generous row spacing, 12sp labels / 13sp values ---
        detail.sections.forEach { section ->
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(ObsidianContainerHigh)
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = section.heading,
              fontSize = 12.sp,
              fontWeight = FontWeight.Black,
              color = TacticalCyan,
              letterSpacing = 0.5.sp
            )
            section.fields.forEachIndexed { index, field ->
              if (index > 0) {
                Box(
                  Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TacticalOutlineVariant.copy(alpha = 0.25f))
                )
              }
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
              ) {
                Text(
                  text = field.label,
                  fontSize = 12.sp,
                  color = TacticalOnSurfaceVariant,
                  lineHeight = 16.sp,
                  modifier = Modifier.weight(0.42f)
                )
                Text(
                  text = field.value ?: "Data unavailable",
                  fontSize = 13.sp,
                  fontWeight = if (field.value != null) FontWeight.SemiBold else FontWeight.Normal,
                  color = if (field.value != null) TacticalOnSurface else TacticalOnSurfaceVariant,
                  lineHeight = 17.sp,
                  modifier = Modifier.weight(0.58f)
                )
              }
            }
          }
        }

        // --- FOOTER: source + geometry demoted to quiet 11sp lines (they are
        // provenance for the curious, not the headline). Fixed the corrupted
        // "Source: X ? Status: Y" separators from an earlier text mangling. ---
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
          Text(
            text = zone.sourceStatus,
            fontSize = 11.sp,
            color = TacticalOnSurfaceVariant,
            lineHeight = 14.sp
          )
          Text(
            text = "Data from ${zone.provenance.source} · ${zone.provenance.status} · " +
              zone.provenance.classification.label,
            fontSize = 11.sp,
            color = TacticalCyan.copy(alpha = 0.9f),
            lineHeight = 14.sp
          )
          Text(
            text = String.format(
              java.util.Locale.US,
              "Area centre: %.4f N, %.4f E",
              zone.center.lat,
              zone.center.lon
            ),
            fontSize = 11.sp,
            color = TacticalOnSurfaceVariant,
            lineHeight = 14.sp
          )
        }
      }
    }
  }
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
      maxLines = 1, overflow = TextOverflow.Ellipsis
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
            com.example.data.disaster.EventOrigin.SIMULATED -> "Simulated field data"
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
// SAFE ZONE DETAIL ? full carrying-capacity & resource intelligence
// ============================================================================
