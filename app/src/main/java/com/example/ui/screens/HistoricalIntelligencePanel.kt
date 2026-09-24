package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.historical.HistoricalContext
import com.example.data.historical.HistoricalContextService
import com.example.data.historical.HistoricalDisasterEvent
import com.example.data.historical.HistoricalFilters
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState
import java.text.NumberFormat
import java.util.Locale

/**
 * ============================================================================
 * HISTORICAL DISASTER INTELLIGENCE (EM-DAT) — UI
 * ============================================================================
 *
 * A clearly separated historical section: it is labelled HISTORICAL DATA, it
 * carries the dataset's own attribution and version, it states spatial
 * precision per record, and it renders "Not available" for every value EM-DAT
 * does not provide. It never borrows the live visual language: no LIVE badge,
 * no current-alert wording, and the map layer is off unless the operator turns
 * it on.
 */
@Composable
fun HistoricalIntelligencePanel(
  uiState: VippattiUiState,
  onToggleLayer: () -> Unit,
  onFiltersChange: (HistoricalFilters) -> Unit,
  onClearFilters: () -> Unit,
  onSelectEvent: (HistoricalDisasterEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }
  val catalog = uiState.historicalCatalog

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .background(ObsidianContainer)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded },
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "HISTORICAL DATA — NOT LIVE HAZARDS",
          fontSize = 11.sp,
          fontWeight = FontWeight.Black,
          color = TacticalCyan,
          letterSpacing = 0.6.sp
        )
        Text(
          text = uiState.historicalStatusLine,
          fontSize = 11.sp,
          color = TacticalOnSurfaceVariant,
          lineHeight = 15.sp
        )
      }
      Text(
        text = if (expanded) "HIDE" else "SHOW",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalOnSurface
      )
    }

    // The dataset's own attribution is always visible, never behind a tap:
    // EM-DAT requires credit, and the version matters for interpretation.
    catalog?.info?.let { info ->
      Text(
        text = info.attributionLine,
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        lineHeight = 12.sp
      )
      Text(
        text = listOfNotNull(
          info.accessLine.ifBlank { null },
          info.sourceUrl
        ).joinToString(" • "),
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        lineHeight = 12.sp
      )
    }
    uiState.historicalError?.let { error ->
      Text(text = error, fontSize = 10.sp, color = WarningAmber, lineHeight = 14.sp)
    }

    if (catalog == null) {
      // Honest empty state: an absent archive is not "no disasters happened".
      Text(
        text = "No historical dataset is loaded in this build, so no historical " +
          "record can be shown. Attach a prepared EM-DAT dataset to enable this panel.",
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        lineHeight = 15.sp
      )
      return@Column
    }

    if (!expanded) {
      Text(
        text = "${catalog.totalCount} archived records • " +
          "${catalog.mappableCount} with source coordinates • " +
          "${catalog.contextOnlyCount} context-only",
        fontSize = 10.sp,
        color = TacticalOnSurfaceVariant
      )
      return@Column
    }

    // ---- Evidence for the user's own resolved area -------------------------
    uiState.historicalContext?.let { context -> HistoricalEvidenceBlock(context) }

    // ---- Map layer opt-in --------------------------------------------------
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Historical map layer",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurface
        )
        Text(
          text = "Off by default. Only records with EM-DAT's own coordinates are " +
            "drawn, as past events — never as current hazard zones.",
          fontSize = 11.sp,
          color = TacticalOnSurfaceVariant,
          lineHeight = 12.sp
        )
      }
      Switch(
        checked = uiState.isHistoricalLayerOn,
        onCheckedChange = { onToggleLayer() }
      )
    }

    // ---- Filters -----------------------------------------------------------
    val active = uiState.historicalFilters
    Text(
      text = "FILTER (${uiState.historicalFilteredEvents.size} of ${catalog.totalCount})",
      fontSize = 10.sp,
      fontWeight = FontWeight.Black,
      color = TacticalOnSurfaceVariant
    )
    Text(
      text = active.description,
      fontSize = 11.sp,
      color = TacticalOnSurfaceVariant
    )

    val types = catalog.typeFacets()
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      FilterChip(
        label = "All types",
        selected = active.type == null,
        onClick = { onFiltersChange(active.copy(type = null)) }
      )
      types.forEach { (type, count) ->
        FilterChip(
          label = "$type ($count)",
          selected = active.type == type,
          onClick = {
            onFiltersChange(active.copy(type = if (active.type == type) null else type))
          }
        )
      }
    }

    // Year-range presets keep the filter usable on a small screen without a
    // slider; they are plain ranges, not intents.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      FilterChip(
        label = "All years",
        selected = active.yearFrom == null && active.yearTo == null,
        onClick = { onFiltersChange(active.copy(yearFrom = null, yearTo = null)) }
      )
      listOf(1900 to 1949, 1950 to 1999, 2000 to 2026).forEach { (from, to) ->
        FilterChip(
          label = "$from–$to",
          selected = active.yearFrom == from && active.yearTo == to,
          onClick = {
            val same = active.yearFrom == from && active.yearTo == to
            onFiltersChange(
              if (same) {
                active.copy(yearFrom = null, yearTo = null)
              } else {
                active.copy(yearFrom = from, yearTo = to)
              }
            )
          }
        )
      }
      FilterChip(
        label = "EM-DAT historic flag",
        selected = active.historicFlag == true,
        onClick = {
          onFiltersChange(
            active.copy(historicFlag = if (active.historicFlag == true) null else true)
          )
        }
      )
      if (active.isActive) {
        FilterChip(label = "Clear filters", selected = false, onClick = onClearFilters)
      }
    }

    HistoricalImpactSummaryBlock(uiState)
    HistoricalTrendBlock(uiState)

    // ---- Record list -------------------------------------------------------
    val events = uiState.historicalFilteredEvents
    if (events.isEmpty()) {
      Text(
        text = "No record in the loaded dataset matches these filters. Absence of a " +
          "record is not evidence that nothing happened.",
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        lineHeight = 15.sp
      )
    } else {
      // A bounded preview: the full list would swamp the screen, and the count
      // above already states the total.
      events.take(12).forEach { event ->
        HistoricalRecordRow(event = event, onClick = { onSelectEvent(event) })
      }
      if (events.size > 12) {
        Text(
          text = "Showing the 12 most recent of ${events.size} matching records.",
          fontSize = 11.sp,
          color = TacticalOnSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun HistoricalEvidenceBlock(context: HistoricalContext) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = "YOUR AREA — HISTORICAL EVIDENCE",
      fontSize = 10.sp,
      fontWeight = FontWeight.Black,
      color = TacticalOnSurfaceVariant
    )
    Text(
      text = context.summaryLine +
        (context.areaLabel?.let { " • $it" } ?: ""),
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurface
    )
    Text(text = context.matchMethod, fontSize = 11.sp, color = TacticalOnSurfaceVariant, lineHeight = 12.sp)
    context.mostRecent?.let { recent ->
      Text(
        text = "Most recent: ${recent.startYear} — ${recent.type}" +
          (recent.impacts.totalDeaths?.let { " • ${formatCount(it)} deaths reported" } ?: ""),
        fontSize = 10.sp,
        color = TacticalOnSurface
      )
    }
    if (context.hasEvidence) {
      Text(
        text = "Evidence based on ${context.eventCount} matched records; " +
          "counts are limited to the loaded dataset.",
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant
      )
    }
    // The disclaimer is mandatory and never truncated.
    Text(
      text = HistoricalContextService.DISCLAIMER,
      fontSize = 11.sp,
      color = WarningAmber,
      lineHeight = 12.sp
    )
  }
}

@Composable
private fun HistoricalImpactSummaryBlock(uiState: VippattiUiState) {
  val summary = uiState.historicalImpactSummary
  if (summary.isEmpty) return
  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(
      text = "IMPACT TOTALS IN SELECTION",
      fontSize = 10.sp,
      fontWeight = FontWeight.Black,
      color = TacticalOnSurfaceVariant
    )
    ImpactLine(
      label = "Deaths",
      value = summary.deathsTotal?.let { formatCount(it) },
      coverage = "${summary.deathsReportingRecords} of ${summary.eventCount} records state a figure"
    )
    ImpactLine(
      label = "Affected",
      value = summary.affectedTotal?.let { formatCount(it) },
      coverage = "${summary.affectedReportingRecords} of ${summary.eventCount} records state a figure"
    )
    ImpactLine(
      label = "Homeless",
      value = summary.homelessTotal?.let { formatCount(it) },
      coverage = "${summary.homelessReportingRecords} of ${summary.eventCount} records state a figure"
    )
    ImpactLine(
      label = "Damage ('000 US$)",
      value = summary.damageThousandUsdTotal?.let { formatCount(it) },
      coverage = "${summary.damageReportingRecords} of ${summary.eventCount} records state a figure"
    )
  }
}

@Composable
private fun ImpactLine(label: String, value: String?, coverage: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(text = label, fontSize = 10.sp, color = TacticalOnSurfaceVariant)
    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = value ?: "Not available",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (value == null) TacticalOnSurfaceVariant else TacticalOnSurface
      )
    }
  }
  Text(text = coverage, fontSize = 10.sp, color = TacticalOnSurfaceVariant)
}

/** Decade bars — the long-range trend of the current selection. */
@Composable
private fun HistoricalTrendBlock(uiState: VippattiUiState) {
  val trend = uiState.historicalDecadeTrend
  if (trend.isEmpty()) return
  val peak = trend.maxOf { it.eventCount }.coerceAtLeast(1)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = "RECORDED EVENTS BY DECADE",
      fontSize = 10.sp,
      fontWeight = FontWeight.Black,
      color = TacticalOnSurfaceVariant
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.Bottom
    ) {
      trend.forEach { point ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Bottom
        ) {
          Text(text = "${point.eventCount}", fontSize = 10.sp, color = TacticalOnSurfaceVariant)
          Box(
            modifier = Modifier
              .width(14.dp)
              .height((6 + (34 * point.eventCount / peak)).dp)
              .background(TacticalCyan.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
          )
          Text(text = "${point.year}s", fontSize = 10.sp, color = TacticalOnSurfaceVariant)
        }
      }
    }
    Text(
      text = "Counts come from the loaded dataset and its classification rules; " +
        "they are not a frequency estimate for any single location.",
      fontSize = 10.sp,
      color = TacticalOnSurfaceVariant,
      lineHeight = 11.sp
    )
  }
}

@Composable
private fun HistoricalRecordRow(event: HistoricalDisasterEvent, onClick: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(ObsidianContainerHigh, RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    Text(
      text = "${event.startDate.label} • ${event.type}",
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurface
    )
    Text(
      text = event.locationText ?: "Location not stated by the source",
      fontSize = 10.sp,
      color = TacticalOnSurfaceVariant
    )
    Text(
      text = listOfNotNull(
        event.impacts.totalDeaths?.let { "${formatCount(it)} deaths" },
        event.impacts.totalAffected?.let { "${formatCount(it)} affected" }
      ).joinToString(" • ").ifBlank { "Impact figures: Not available" } +
        " • ${event.spatialPrecision.label} • HISTORICAL",
      fontSize = 11.sp,
      color = TacticalOnSurfaceVariant
    )
  }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Text(
    text = label,
    fontSize = 10.sp,
    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    color = if (selected) ObsidianContainer else TacticalOnSurface,
    modifier = Modifier
      .background(
        if (selected) TacticalCyan else ObsidianContainerHigh,
        RoundedCornerShape(20.dp)
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp)
  )
}

/**
 * Full record sheet: every available EM-DAT field, the source, the version, the
 * spatial precision and the limitations. Unavailable values say "Not available".
 */
@Composable
fun HistoricalEventDetailDialog(
  event: HistoricalDisasterEvent,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("CLOSE") }
    },
    title = {
      Column {
        Text(
          text = "HISTORICAL DISASTER RECORD",
          fontSize = 13.sp,
          fontWeight = FontWeight.Black,
          color = TacticalCyan
        )
        Text(text = event.id, fontSize = 10.sp, color = TacticalOnSurfaceVariant)
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        DetailLine("Disaster group", event.group)
        DetailLine("Subgroup", event.subgroup)
        DetailLine("Type", event.type)
        DetailLine("Subtype", event.subtype)
        DetailLine("Event name", event.eventName)
        DetailLine("Country", event.country)
        DetailLine("Location (source text)", event.locationText)
        DetailLine(
          "Administrative units",
          event.adminUnitNames.takeIf { it.isNotEmpty() }?.joinToString(", ")
        )
        DetailLine("Start date", event.startDate.label)
        DetailLine("End date", event.endDate?.label)
        DetailLine("Year", event.startYear.toString())
        DetailLine("Total deaths", event.impacts.totalDeaths?.let { formatCount(it) })
        DetailLine("Injured", event.impacts.injured?.let { formatCount(it) })
        DetailLine("Affected", event.impacts.affected?.let { formatCount(it) })
        DetailLine("Homeless", event.impacts.homeless?.let { formatCount(it) })
        DetailLine("Total affected", event.impacts.totalAffected?.let { formatCount(it) })
        DetailLine(
          "Total damage ('000 US$)",
          event.impacts.damageThousandUsd?.let { formatCount(it) }
        )
        DetailLine(
          "Damage, adjusted ('000 US$)",
          event.impacts.damageAdjustedThousandUsd?.let { formatCount(it) }
        )
        DetailLine(
          "Magnitude",
          event.magnitude?.let { value ->
            value.toString() + (event.magnitudeScale?.let { " ($it)" } ?: "")
          }
        )
        DetailLine(
          "Coordinates (source)",
          event.latitude?.let { lat ->
            "$lat, ${event.longitude}"
          }
        )
        DetailLine("EM-DAT historic flag", event.historicFlag?.let { if (it) "Yes" else "No" })
        DetailLine("Source", event.source)
        DetailLine("Dataset version", event.datasetVersion)
        DetailLine("Dataset file created", event.accessedOn)
        DetailLine("Record entered", event.entryDate)
        DetailLine("Record last updated", event.lastUpdate)
        DetailLine("Classification", event.classification.label.uppercase(Locale.getDefault()))

        Text(
          text = "Spatial precision: ${event.spatialPrecision.label}",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurface
        )
        Text(
          text = event.spatialPrecision.explanation,
          fontSize = 11.sp,
          color = TacticalOnSurfaceVariant,
          lineHeight = 12.sp
        )
        if (!event.isMappable) {
          Text(
            text = "This record is not mapped: EM-DAT provides no coordinates for it. " +
              "Its location text is kept exactly as the source states it.",
            fontSize = 11.sp,
            color = WarningAmber,
            lineHeight = 12.sp
          )
        }
        event.notes.forEach { note ->
          Text(text = note, fontSize = 11.sp, color = TacticalOnSurfaceVariant)
        }
        Text(
          text = HistoricalContextService.DISCLAIMER,
          fontSize = 11.sp,
          color = WarningAmber,
          lineHeight = 12.sp
        )
      }
    }
  )
}

@Composable
private fun DetailLine(label: String, value: String?) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = label,
      fontSize = 10.sp,
      color = TacticalOnSurfaceVariant,
      modifier = Modifier.width(150.dp)
    )
    Text(
      text = value?.takeIf { it.isNotBlank() } ?: "Not available",
      fontSize = 10.sp,
      color = if (value.isNullOrBlank()) TacticalOnSurfaceVariant else TacticalOnSurface,
      textAlign = TextAlign.End,
      modifier = Modifier.weight(1f)
    )
  }
}

private fun formatCount(value: Long): String =
  NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)
