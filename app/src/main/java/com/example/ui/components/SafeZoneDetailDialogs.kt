package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Woman
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HolidayVillage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Woman
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun SafeZoneDetailDialog(
  zone: com.example.data.model.SafeZone,
  evaluation: com.example.data.shelters.SafeZoneEvaluation?,
  onDismiss: () -> Unit,
  onSelectAndRoute: () -> Unit,
  /** Carrying-capacity verdict for this site; null = not assessed (says so). */
  capacityAssessment: com.example.data.capacity.CapacityAssessment? = null
) {
  val capacity = com.example.data.shelters.ShelterCapacityService.report(zone)
  val simulated = zone.provenance.classification ==
    com.example.data.model.DataClassification.SIMULATED
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
          // Full details scroll instead of clipping on short screens.
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // ---- HEADER: chip icon / eyebrow / name / distance (real data only)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top
        ) {
          DetailIconChip(Icons.Default.HolidayVillage, NeonEmerald,
            contentDescription = "Safe zone")
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Safe zone", fontSize = 12.sp, fontWeight = FontWeight.Bold,
              color = NeonEmerald, letterSpacing = 0.4.sp)
            Text(
              text = zone.name,
              fontSize = 21.sp,
              fontWeight = FontWeight.Black,
              color = TacticalOnSurface,
              lineHeight = 25.sp,
              maxLines = 2,
              softWrap = true,
              overflow = TextOverflow.Visible
            )
            val distanceText = evaluation?.let {
              com.example.data.model.GeoMath.formatKm(it.distanceMeters) + " from your location"
            }
            Row(verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Icon(Icons.Default.Place, contentDescription = null, tint = TacticalCyan,
                modifier = Modifier.size(14.dp))
              Text(
                text = distanceText ?: "Distance not computed",
                fontSize = 13.sp,
                color = if (distanceText != null) TacticalOnSurfaceVariant
                  else TacticalOnSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 2
              )
            }
            if (simulated) {
              // ONE concise provenance indicator in the compact view (spec 20);
              // the full classification lives in Data & methodology.
              StatusPill(Icons.Default.Science, "Demo data", WarningAmber,
                tag = "safe_zone_demo_pill")
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalOnSurfaceVariant)
          }
        }

        // ---- CAPACITY GAUGE: the record's own spaces (never a fake number)
        CapacityGauge(
          available = capacity.availableCapacity,
          total = capacity.totalCapacity
        )

        // ---- QUICK STATUS: distance / operating / confidence (actuals only),
        // width-adaptive like the hazard sheet (3 across / 2+1 / stacked).
        val open = zone.operatingStatus.equals("OPEN", ignoreCase = true)
        val limiter = capacityAssessment?.limitingResource?.label
        AdaptiveStatCards(
          first = { cardModifier ->
            SummaryCard(
              label = "Distance",
              value = evaluation?.let {
                com.example.data.model.GeoMath.formatKm(it.distanceMeters)
              } ?: "Data unavailable",
              icon = Icons.Default.Navigation,
              valueAccent = TacticalCyan,
              modifier = cardModifier,
              tag = "safe_zone_stat_distance",
              unavailable = evaluation == null
            )
          },
          second = { cardModifier ->
            SummaryCard(
              label = "Status",
              value = when {
                open -> "Open"
                zone.operatingStatus.isBlank() -> "Not provided"
                else -> zone.operatingStatus
              },
              icon = Icons.Default.CheckCircle,
              valueAccent = if (open) NeonEmerald else EmergencyRedBright,
              modifier = cardModifier,
              tag = "safe_zone_stat_status",
              unavailable = zone.operatingStatus.isBlank()
            )
          },
          third = { cardModifier ->
            SummaryCard(
              label = "Confidence",
              value = capacityAssessment?.confidence?.label ?: "Not assessed",
              icon = Icons.Default.Insights,
              valueAccent = WarningAmber,
              modifier = cardModifier,
              tag = "safe_zone_stat_confidence",
              unavailable = capacityAssessment == null
            )
          }
        )

        // ---- LIMITING FACTOR: tappable - opens the capacity methodology.
        var detailsExpanded by remember(zone.id) { mutableStateOf(false) }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianContainerHigh)
            .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable { detailsExpanded = true }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("limiting_factor_row"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(Icons.Default.Layers, contentDescription = null, tint = TacticalCyan,
            modifier = Modifier.size(18.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("Limiting factor", fontSize = 11.sp, color = TacticalOnSurfaceVariant)
            Text(
              text = limiter ?: "Not assessed",
              fontSize = 14.sp,
              fontWeight = if (limiter != null) FontWeight.Bold else FontWeight.Normal,
              color = if (limiter != null) TacticalOnSurface else TacticalOnSurfaceVariant,
              maxLines = 2,
              modifier = Modifier.then(
                if (limiter != null) Modifier.testTag("capacity_limiter_line") else Modifier
              )
            )
          }
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = TacticalOnSurfaceVariant, modifier = Modifier.size(18.dp))
        }

        // ---- PRIMARY CTA: exact selected record, existing routing intact.
        Button(
          onClick = {
            onSelectAndRoute()
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
          shape = RoundedCornerShape(999.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("safe_zone_route_button")
        ) {
          Icon(Icons.Default.Navigation, contentDescription = null,
            tint = OnNeonEmerald, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Route to this safe zone", fontWeight = FontWeight.Bold, color = OnNeonEmerald)
        }

        // ---- FULL DETAILS (progressive disclosure; nothing deleted) -------
        TextButton(
          onClick = { detailsExpanded = !detailsExpanded },
          contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
          modifier = Modifier.heightIn(min = 44.dp).testTag("safe_zone_details_toggle")
        ) {
          Icon(
            imageVector = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = TacticalOnSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (detailsExpanded) "Hide full details" else "Show full details",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TacticalOnSurface
          )
        }

        if (detailsExpanded) {
          // KEY INFORMATION
          Column {
            SectionHeader("Key information", Icons.Default.Description)
            Spacer(Modifier.height(4.dp))
            DetailPanel {
              DetailRow("Total capacity", "${capacity.totalCapacity} people")
              RowDividerLine()
              DetailRow("Available capacity", "${capacity.availableCapacity} people")
              RowDividerLine()
              DetailRow(
                "Effective capacity",
                capacityAssessment?.effectiveCapacity?.let { "$it people" }
                  ?: "Not assessed",
                unavailable = capacityAssessment?.effectiveCapacity == null,
                tag = "capacity_effective_value"
              )
              capacityAssessment?.takeIf { it.unavailableResources.isNotEmpty() }?.let { ca ->
                RowDividerLine()
                DetailRow(
                  "Not assessed",
                  ca.unavailableResources.joinToString(", ") { it.label.lowercase() },
                  unavailable = true,
                  tag = "capacity_not_assessed_line"
                )
              }
              RowDividerLine()
              DetailRow("Distance",
                evaluation?.let { com.example.data.model.GeoMath.formatKm(it.distanceMeters) }
                  ?: "Data unavailable",
                unavailable = evaluation == null)
              RowDividerLine()
              DetailRow("Status", if (open) "Open"
                else if (zone.operatingStatus.isBlank()) "Not provided" else zone.operatingStatus)
              RowDividerLine()
              DetailRow("Limiting factor", limiter ?: "Not assessed",
                unavailable = limiter == null)
              RowDividerLine()
              DetailRow("Capacity confidence",
                capacityAssessment?.confidence?.label ?: "Not assessed",
                unavailable = capacityAssessment == null)
            }
          }

          // FACILITY RESOURCES - honest three-way wording, quantitative only
          // when the record actually carries a figure.
          Column {
            SectionHeader("Facility resources", Icons.Default.Kitchen)
            Spacer(Modifier.height(4.dp))
            DetailPanel {
              ResourceHonestyRow(
                "Water supply", DetailIcons.water, zone.waterAvailable,
                zone.waterLitresPerDay?.let {
                  java.text.NumberFormat.getInstance(java.util.Locale.US)
                    .format(it.toInt()) + " L/day"
                }
              )
              RowDividerLine()
              ResourceHonestyRow("Food supply", DetailIcons.food, zone.foodAvailable, null)
              RowDividerLine()
              ResourceHonestyRow("Electricity", DetailIcons.power, zone.electricityAvailable, null)
              RowDividerLine()
              ResourceHonestyRow(
                "Sanitation", DetailIcons.sanitation, zone.sanitationAvailable,
                zone.toiletCount?.let { "$it toilets" }
              )
              RowDividerLine()
              ResourceHonestyRow("Medical support", DetailIcons.medical, zone.medicalSupport, null)
              RowDividerLine()
              ResourceHonestyRow("Women & children suitability", Icons.Default.Woman,
                zone.womenChildrenSuitability, null)
              RowDividerLine()
              DetailRow("Usable land / floor area",
                zone.landAreaSquareMeters?.let {
                  java.text.NumberFormat.getInstance(java.util.Locale.US)
                    .format(it.toInt()) + " m2"
                } ?: "Not provided",
                unavailable = zone.landAreaSquareMeters == null)
            }
          }

          // RELOCATION FEASIBILITY (engine data, organised)
          Column {
            SectionHeader("Relocation feasibility", Icons.Default.Share)
            Spacer(Modifier.height(4.dp))
            DetailPanel(accent = feasibilityColor(capacityAssessment)) {
              if (capacityAssessment == null) {
                DetailRow("Feasibility", "Not assessed for this site", unavailable = true)
              } else {
                DetailRow(
                  "Verdict",
                  capacityAssessment.status.label,
                  valueAccent = feasibilityColor(capacityAssessment),
                  tag = "capacity_feasibility_status"
                )
                RowDividerLine()
                DetailRow(
                  "Population requirement",
                  capacityAssessment.demand.people?.let { "$it people" } ?: "Not available",
                  subNote = capacityAssessment.demand.roleLabel,
                  unavailable = capacityAssessment.demand.people == null
                )
                RowDividerLine()
                DetailRow("Population scope", capacityAssessment.demand.scopeLabel)
                RowDividerLine()
                DetailRow(
                  "Population data",
                  (capacityAssessment.demand.classification?.label ?: "Not provided") +
                    " \u00b7 " + capacityAssessment.demand.source
                )
                RowDividerLine()
                DetailRow("Accessibility",
                  zone.accessibility.ifBlank { "Not provided" },
                  unavailable = zone.accessibility.isBlank())
                RowDividerLine()
                DetailRow("Elevation",
                  zone.elevationNote.ifBlank { "Not provided" },
                  unavailable = zone.elevationNote.isBlank())
                if (capacityAssessment.remainingCapacity != null ||
                  capacityAssessment.shortfall != null
                ) {
                  RowDividerLine()
                  DetailRow(
                    "Fits demand?",
                    if (capacityAssessment.shortfall == null)
                      "Yes - ${capacityAssessment.remainingCapacity} spare"
                    else "No - short by ${capacityAssessment.shortfall}"
                  )
                }
              }
            }
          }

          // WHY THIS SAFE ZONE (evaluation reasons, kept verbatim)
          if (evaluation != null && evaluation.isFeasible) {
            Column {
              SectionHeader("Why this safe zone", Icons.Default.CheckCircle, NeonEmerald)
              Spacer(Modifier.height(4.dp))
              DetailPanel {
                evaluation.reasons.forEachIndexed { index, reason ->
                  if (index > 0) RowDividerLine()
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                  ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = NeonEmerald,
                      modifier = Modifier.size(14.dp))
                    Text(reason.text, fontSize = 13.sp, color = TacticalOnSurface,
                      lineHeight = 17.sp, maxLines = 3)
                  }
                }
              }
            }
          } else if (evaluation != null) {
            Text(
              text = "Not recommended: ${evaluation.rejectionReason?.label ?: "Ineligible"}",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = EmergencyRedBright
            )
          }

          // AREA CENTRE
          AreaCentreCard(
            latText = String.format(java.util.Locale.US, "%.4f N", zone.lat),
            lonText = String.format(java.util.Locale.US, "%.4f E", zone.lon)
          )

          // DATA & METHODOLOGY (all technical detail, compact)
          Column {
            SectionHeader("Data & methodology", Icons.Default.Info)
            Spacer(Modifier.height(4.dp))
            DetailPanel {
              DetailRow("Data classification",
                zone.provenance.classification.label + " \u00b7 " + zone.provenance.status)
              RowDividerLine()
              DetailRow("Verification", zone.verificationStatus)
              RowDividerLine()
              DetailRow("Source", zone.provenance.source)
              capacityAssessment?.let { ca ->
                RowDividerLine()
                DetailRow("Capacity methodology",
                  ca.limitingResource?.let { "${it.label} bottleneck \u2014 minimum over " +
                    "assessed resources" } ?: "Minimum over assessed resources")
                ca.regime?.let { regime ->
                  if (regime == com.example.data.capacity.CarryingCapacityEngine.Regime.CYCLONE_SHELTER) {
                    RowDividerLine()
                    DetailRow(
                      "Planning regime",
                      "Cyclone-shelter figures (GoI guidance: 3 sq ft/person floor + " +
                        "terrace) - applied only while a cyclone alert is in view"
                    )
                  }
                }
                RowDividerLine()
                DetailRow(
                  "Assessed at",
                  java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(ca.assessedAtMillis)) +
                    " (" + com.example.data.news.NewsPresentation.relativeAge(
                      ca.assessedAtMillis, System.currentTimeMillis()) + ")"
                )
                val assumptions = ca.assumptions + ca.demand.notes
                if (assumptions.isNotEmpty()) {
                  RowDividerLine()
                  DetailRow("Assumptions & limitations", assumptions.distinct().joinToString(" "))
                }
                RowDividerLine()
                DetailRow("Reason", ca.explanation)
              }
            }
          }
        }
      }
    }
  }
}


@Composable
private fun feasibilityColor(
  assessment: com.example.data.capacity.CapacityAssessment?
): androidx.compose.ui.graphics.Color = when (assessment?.status) {
  com.example.data.capacity.FeasibilityStatus.FEASIBLE -> NeonEmerald
  com.example.data.capacity.FeasibilityStatus.INFEASIBLE -> EmergencyRedBright
  com.example.data.capacity.FeasibilityStatus.SIMULATED -> TacticalCyan
  com.example.data.capacity.FeasibilityStatus.INSUFFICIENT_DATA -> WarningAmber
  null -> TacticalOnSurfaceVariant
}

@Composable
private fun ResourceRow(label: String, available: Boolean) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, fontSize = 12.sp, color = TacticalOnSurface)
    Text(
      text = if (available) "AVAILABLE" else "NOT AVAILABLE",
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = if (available) NeonEmerald else EmergencyRedBright
    )
  }
}


@Composable
private fun CapacityRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top
  ) {
    Text(label, fontSize = 12.sp, color = TacticalOnSurfaceVariant)
    Text(
      value, fontSize = 12.sp, fontWeight = FontWeight.Bold,
      color = TacticalOnSurface, maxLines = 2,
      modifier = Modifier.padding(start = 12.dp)
    )
  }
}

@Composable
private fun InfoLine(label: String, value: String) {
  Column {
    Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurfaceVariant, letterSpacing = 0.5.sp)
    Text(value, fontSize = 11.sp, color = TacticalOnSurface)
  }
}

// ============================================================================
// SOS CONFIRM GATE - the "Are you sure?" dialog shown before any distress
// broadcast is actually transmitted (radar SOS icon, hero broadcast button,
// NEED ASSISTANCE switch all route through here).
// ============================================================================
