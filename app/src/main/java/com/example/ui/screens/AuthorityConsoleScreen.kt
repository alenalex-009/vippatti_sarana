package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.disaster.IndiaGeo
import com.example.data.habitations.Habitation
import com.example.data.habitations.HabitationPriority
import com.example.data.habitations.PopulationInput
import com.example.data.habitations.RelocationTier
import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState

/**
 * ============================================================================
 * AUTHORITY CONSOLE — FIELD REGISTRY + RELOCATION PRIORITIZATION (SIH 26191)
 * ============================================================================
 *
 * The decision-support surface the problem statement asks for: enter real
 * shelter/habitation survey records, then rank habitations for
 * IMMEDIATE / SHORT-TERM / MEDIUM-TERM relocation against the live hazard
 * picture, with every row's scoring reasons visible and every record's data
 * classification labelled. Demo rows keep their SIMULATED label at all times.
 *
 * Presentation rebuild (2nd refinement pass): a compact operations layout —
 * scrollable content-width tabs (no ellipsized labels), a column-stacked
 * priority card (the View-on-Map button can no longer collide with the
 * disclaimer), LazyColumn dashboard, content-driven heights, and one
 * consistent type scale (20/18/17/14/13/12/11). Data + logic untouched.
 */

@Composable
private fun tierColor(tier: RelocationTier): Color = when (tier) {
  RelocationTier.IMMEDIATE -> EmergencyRedBright
  RelocationTier.SHORT_TERM -> WarningAmber
  RelocationTier.MEDIUM_TERM -> TacticalCyan
  RelocationTier.LOW -> NeonEmerald
}

@Composable
fun AuthorityConsoleScreen(
  uiState: VippattiUiState,
  onBack: () -> Unit,
  onSaveShelter: (SafeZone) -> Unit,
  onDeleteShelter: (String) -> Unit,
  onSaveHabitation: (Habitation) -> Unit,
  onDeleteHabitation: (String) -> Unit,
  onRerank: (Boolean) -> Unit,
  onViewOnMap: (com.example.data.routing.GeoPoint) -> Unit = {}
) {
  var tab by remember { mutableStateOf(0) } // 0 = dashboard, 1 = shelters, 2 = habitations
  var showHabForm by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianSurface)
      // Console opens full-bleed (no Scaffold): respect the status bar above
      // and the Android navigation bar below via real window insets.
      .windowInsetsPadding(WindowInsets.statusBars)
      .windowInsetsPadding(WindowInsets.navigationBars)
      // Forms open soft keyboards: keep fields above the IME too.
      .imePadding()
  ) {
    // Compact app bar. Short subtitle: the long one ellipsized on the device.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(ObsidianContainerLow)
        .padding(start = 8.dp, end = 16.dp)
        .padding(top = 2.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(TacticalCyan.copy(alpha = 0.12f))
          .clickable(onClick = onBack),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.ArrowBack, "Back", tint = TacticalCyan, modifier = Modifier.size(20.dp))
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text("Authority Console", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
          color = TacticalOnSurface, maxLines = 1)
        Text("Field registry & relocation", fontSize = 12.sp, fontWeight = FontWeight.Normal,
          color = TacticalOnSurfaceVariant, maxLines = 1)
      }
    }

    // Tabs. STRUCTURAL FIX: content width inside a horizontally scrollable
    // row. The old weight(1f) + maxLines/ellipsis cut "PRIORITIZATI..." on a
    // 360dp phone; a tab label must never ellipsize.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 12.dp)
        .padding(top = 4.dp, bottom = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      listOf("PRIORITIZATION", "SHELTERS (${uiState.fieldShelters.size})", "HABITATIONS (${uiState.fieldHabitations.size})")
        .forEachIndexed { index, label ->
          val selected = tab == index
          Box(
            modifier = Modifier
              .heightIn(min = 44.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(if (selected) TacticalCyan.copy(alpha = 0.2f) else ObsidianContainerLow)
              .border(
                1.dp,
                if (selected) TacticalCyan.copy(alpha = 0.7f) else Color.Transparent,
                RoundedCornerShape(8.dp)
              )
              .clickable { tab = index }
              .padding(horizontal = 9.dp, vertical = 10.dp)
          ) {
            // 12sp + tight padding so all three complete labels fit the
            // 360dp phone without the third tab edge-clipping; the row
            // stays scrollable as the fallback on narrower devices.
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
              color = if (selected) TacticalCyan else TacticalOnSurface)
          }
        }
    }

    when (tab) {
      0 -> DashboardTab(uiState, onRerank, onViewOnMap)
      1 -> SheltersTab(uiState, onSaveShelter, onDeleteShelter, onViewOnMap)
      else -> HabitationsTab(uiState, showHabForm, onToggleForm = { showHabForm = it }, onSaveHabitation, onDeleteHabitation)
    }
  }
}

@Composable
private fun DashboardTab(
  uiState: VippattiUiState,
  onRerank: (Boolean) -> Unit,
  onViewOnMap: (com.example.data.routing.GeoPoint) -> Unit = {}
) {
  var liveScan by remember { mutableStateOf(false) }
  var tierFilter by remember { mutableStateOf<RelocationTier?>(null) }
  val priorities = uiState.relocationPriorities
  val counts = priorities.groupingBy { it.tier }.eachCount()
  val shown = tierFilter?.let { t -> priorities.filter { it.tier == t } } ?: priorities
  // Destination lookup by ID so each row answers "where do they go?" with the
  // SAME zone the engine ranked it against.
  val zonesById = (uiState.safeZones + uiState.fieldShelters).associateBy { it.id }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("console_dashboard_list"),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(
      start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
    ),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // --- Priority summary tiles (compact dashboard row) ---
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        RelocationTier.entries.forEach { tier ->
          val active = tierFilter == tier
          Box(
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 68.dp, max = 76.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(tierColor(tier).copy(alpha = if (active) 0.28f else 0.10f))
              .border(
                1.dp, tierColor(tier).copy(alpha = if (active) 0.9f else 0.4f),
                RoundedCornerShape(10.dp)
              )
              .clickable { tierFilter = if (active) null else tier },
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(vertical = 6.dp)) {
              Text("${counts[tier] ?: 0}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                color = tierColor(tier))
              Text(tier.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                color = TacticalOnSurfaceVariant)
            }
          }
        }
      }
    }

    // --- Current Priority Area: COLUMN stacked, button on its own row so it
    // can never collide with, or be pushed by, the DERIVED disclaimer. ---
    priorities.firstOrNull()?.let { top ->
      val hab = top.habitation
      val exposures = com.example.data.risk.HazardAnalysisService
        .affectingHazards(hab.point, uiState.hazardZones)
      val nearbyShelters = (uiState.safeZones + uiState.fieldShelters).count {
        com.example.data.model.GeoMath.distanceMeters(hab.point, it.point) < 10_000.0
      }
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianContainerLow)
            .border(1.dp, tierColor(top.tier).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("CURRENT PRIORITY AREA", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = TacticalCyan, letterSpacing = 0.6.sp, maxLines = 1)
          Text(hab.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            color = TacticalOnSurface, lineHeight = 23.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (exposures.isNotEmpty()) {
              val worst = exposures.maxByOrNull { it.hazard.severity.weight }!!.hazard.severity.label
              Text("LIVE HAZARD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = EmergencyRedBright, letterSpacing = 0.5.sp)
              Text(exposures.size.toString() + " active zone" +
                  (if (exposures.size > 1) "s" else "") + " \u00b7 " + worst + " severity",
                fontSize = 13.sp, color = TacticalOnSurface)
            } else {
              Text("No live hazard over this point", fontSize = 13.sp,
                color = TacticalOnSurfaceVariant)
            }
            Text(
              buildString {
                hab.population?.let { append("Population " + it.value + " \u00b7 ") }
                append(nearbyShelters.toString() + " shelter")
                if (nearbyShelters != 1) append("s")
                append(" within 10 km")
              },
              fontSize = 13.sp, color = TacticalOnSurfaceVariant, lineHeight = 17.sp
            )
            Text("Priority: " + top.tier.label, fontSize = 13.sp,
              fontWeight = FontWeight.Medium, color = TacticalOnSurface)
          }
          // Single-line, comfortable-width button (never View/on/Map again).
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(TacticalCyan.copy(alpha = 0.9f))
              .clickable { onViewOnMap(hab.point) }
              .heightIn(min = 48.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("View on Map", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
              color = Color.Black, maxLines = 1,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("console_view_on_map"))
          }
          // Disclaimer gets its own row - below the button, never behind it.
          Text("DERIVED \u2014 not an official government decision",
            fontSize = 11.sp, fontWeight = FontWeight.Medium, color = WarningAmber)
        }
      }
    }

    // --- Ranking controls: aligned action row + caption below. ---
    item {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(TacticalCyan.copy(alpha = 0.9f))
              .clickable { onRerank(liveScan) }
              .heightIn(min = 48.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              if (uiState.isRankingPriorities) "Ranking\u2026" else "Rank Habitations",
              fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black,
              maxLines = 1,
              modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
          }
          Row(
            modifier = Modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Checkbox(checked = liveScan, onCheckedChange = { liveScan = it })
            Text("Live terrain scan", fontSize = 14.sp, color = TacticalOnSurface, maxLines = 1)
          }
        }
        Text("SRTM + rain per site (adds ~30 s to ranking)", fontSize = 12.sp,
          color = TacticalOnSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
      }
    }

    if (uiState.isRankingPriorities && priorities.isEmpty()) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
            color = TacticalCyan)
          Text("Probing terrain for every habitation \u2014 this takes a moment\u2026",
            fontSize = 12.sp, color = TacticalOnSurfaceVariant)
        }
      }
    }

    // --- Ranking criteria: compact card, real weights (UI of the SAME
    // 35/30/20/15 numbers the engine uses). ---
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(ObsidianContainerLow)
          .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text("Ranking criteria", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
          color = TacticalOnSurface)
        listOf(
          "Hazard exposure" to "35%",
          "Terrain habitability" to "30%",
          "Vulnerability" to "20%",
          "EM-DAT history" to "15%"
        ).forEach { (name, pct) ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontSize = 14.sp, color = TacticalOnSurfaceVariant)
            Text(pct, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TacticalOnSurface)
          }
        }
        Text("History escalates at most one band \u00b7 reasons shown per row",
          fontSize = 12.sp, color = TacticalOnSurfaceVariant)
      }
    }

    // --- Relocation order ---
    item {
      Text(
        if (priorities.isEmpty()) "Nothing ranked yet"
        else if (shown.isEmpty()) "No records in this tier \u2014 tap the tier again to clear."
        else if (tierFilter == null) "Relocation order \u2014 most urgent first"
        else "Filtered: " + tierFilter?.label + " only \u2014 tap the tier again to clear",
        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TacticalOnSurface,
        modifier = Modifier.padding(top = 8.dp)
      )
    }

    items(shown) { p -> PriorityRow(p, zonesById[p.nearestSafeZoneId], uiState.hazardZones) }

    if (priorities.isEmpty() && !uiState.isRankingPriorities) {
      item {
        Text(
          "Add habitation records in the HABITATIONS tab, then tap Rank " +
            "Habitations \u2014 each row will explain WHY it landed in its tier.",
          fontSize = 13.sp, color = TacticalOnSurfaceVariant, lineHeight = 18.sp
        )
      }
    }
  }
}

/**
 * Relocation row. Collapsed = the scannable essentials (tier, score, name,
 * coordinates/pop/distance, RELOCATE TO, provenance). Expanded adds the
 * live-hazard line and every scoring reason + weights. Heights are entirely
 * content-driven; long names wrap naturally to two lines.
 */
@Composable
private fun PriorityRow(p: HabitationPriority, destination: SafeZone?, hazardZones: List<com.example.data.model.HazardZone>) {
  var expanded by remember(p.habitation.id, p.score) { mutableStateOf(false) }
  val color = tierColor(p.tier)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
      .clickable { expanded = !expanded }
  ) {
    // Tier header band (shape + text, not only color: color-blind safe).
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(color.copy(alpha = 0.12f))
        .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(color))
      Text(p.tier.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = color, letterSpacing = 0.4.sp, maxLines = 1)
      Spacer(Modifier.weight(1f))
      Text("SCORE ${p.score}", fontSize = 14.sp, fontWeight = FontWeight.Medium,
        color = TacticalOnSurfaceVariant, maxLines = 1)
    }
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Text(
        p.habitation.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        color = TacticalOnSurface, lineHeight = 22.sp, maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("priority_row_${p.habitation.id}")
      )
      Text(
        buildString {
          append("${p.habitation.point.lat.fmt()}, ${p.habitation.point.lon.fmt()}")
          p.habitation.population?.let { append(" \u00b7 pop ${it.value}") }
          p.nearestSafeZoneDistanceMeters?.let {
            append(" \u00b7 safe zone %.1f km".format(it / 1000))
          }
        },
        fontSize = 13.sp, color = TacticalOnSurfaceVariant, lineHeight = 18.sp
      )
      // WHERE THEY GO.
      p.nearestSafeZoneId?.let { zoneId ->
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NeonEmerald.copy(alpha = 0.08f))
            .border(1.dp, NeonEmerald.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Text("RELOCATE TO", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = NeonEmerald, letterSpacing = 0.4.sp)
          Text(
            (destination?.name ?: zoneId) +
              (destination?.let { " \u00b7 " + it.availableCapacity + " beds free" } ?: "") +
              (p.nearestSafeZoneDistanceMeters?.let { " \u00b7 %.1f km".format(it / 1000) } ?: ""),
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TacticalOnSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp
          )
        }
      }
      if (p.habitation.population?.classification == DataClassification.SIMULATED) {
        Text("Demo record", fontSize = 12.sp, fontWeight = FontWeight.Medium,
          color = WarningAmber)
      }
      if (expanded) {
        // Live hazard status - same engine data the reasons describe.
        val exposures = com.example.data.risk.HazardAnalysisService
          .affectingHazards(p.habitation.point, hazardZones)
        if (exposures.isNotEmpty()) {
          val worst = exposures.maxByOrNull { it.hazard.severity.weight }!!.hazard.severity.label
          Text("LIVE HAZARD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = EmergencyRedBright, letterSpacing = 0.5.sp)
          Text(exposures.size.toString() + " active zone" +
              (if (exposures.size > 1) "s" else "") + " \u00b7 " + worst + " severity",
            fontSize = 13.sp, color = TacticalOnSurface)
        }
        p.reasons.forEach { reason ->
          Text("\u00b7  " + reason, fontSize = 13.sp, color = TacticalOnSurface,
            lineHeight = 18.sp)
        }
        Text(p.tier.actionGuide, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
        Text(
          "Weights: hazard 35% \u00b7 terrain 30% \u00b7 vulnerability 20% \u00b7 history 15%",
          fontSize = 12.sp, color = TacticalOnSurfaceVariant
        )
      }
      Text(
        if (expanded) "Tap to collapse" else "Tap for all reasons",
        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TacticalCyan,
        modifier = Modifier.padding(top = 2.dp)
      )
    }
  }
}

// ---- shelters tab ------------------------------------------------------------

@Composable
private fun SheltersTab(
  uiState: VippattiUiState,
  onSave: (SafeZone) -> Unit,
  onDelete: (String) -> Unit,
  onViewOnMap: (com.example.data.routing.GeoPoint) -> Unit = {}
) {
  var editing by remember { mutableStateOf<SafeZone?>(null) }
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
    Text(
      "Field-entered shelters join the LIVE shelter network (real records — never hidden by the demo switch).",
      fontSize = 12.sp, color = TacticalOnSurfaceVariant, lineHeight = 16.sp,
      modifier = Modifier.padding(bottom = 8.dp)
    )
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(NeonEmerald.copy(alpha = 0.9f))
        .clickable { editing = blankShelter() }
        .heightIn(min = 44.dp),
      contentAlignment = Alignment.Center
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.Add, null, tint = Color.Black, modifier = Modifier.size(16.dp))
        Text("New Shelter Record", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
      }
    }
    Spacer(Modifier.height(8.dp))
    uiState.fieldShelters.forEach { zone ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 3.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(ObsidianContainerLow)
          .clickable { editing = zone }
          .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          Text(zone.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TacticalOnSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
          val latLonLine = "${zone.lat.fmt()}, ${zone.lon.fmt()}"
                    val totalCap = zone.capacityTotal
                    val currentCap = zone.capacityCurrent
                    val capacityLine = if (totalCap != null && currentCap != null) {
              "free ${(totalCap - currentCap).coerceAtLeast(0)}/$totalCap"
            } else {
              "capacity unknown"
            }
            Text(
                      "$latLonLine • $capacityLine • ${zone.operatingStatus}",
            fontSize = 12.sp, color = TacticalOnSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Box(
          modifier = Modifier.size(44.dp).clickable { onDelete(zone.id) },
          contentAlignment = Alignment.Center
        ) {
          Icon(
            Icons.Default.Delete, "Delete shelter record", tint = EmergencyRedBright,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
    // 12-12: REFERENCE DATA. The console used to read SHELTERS (0) - empty
    // screens sell nothing. These are demo/reference shelters (every record is
    // SIMULATED-labelled at the data level), shown READ-ONLY under an explicit
    // REFERENCE DATA heading, sorted by distance from the focus. They never
    // claim to be real live shelters, and they stay distinct from the editable
    // field records above.
    val referenceShelters = (uiState.safeZones + uiState.fieldShelters)
      .filter {
        it.id.startsWith("demo-sz-") || it.verificationStatus.contains("Simulated", true)
      }
      .distinctBy { it.id }
      .sortedBy {
        com.example.data.model.GeoMath.distanceMeters(uiState.userLocation, it.point)
      }
      .take(6)
    if (referenceShelters.isNotEmpty()) {
      Spacer(Modifier.height(10.dp))
      Text(
        text = "REFERENCE DATA — simulated demo shelters (not real)",
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = WarningAmber,
        letterSpacing = 0.4.sp
      )
      referenceShelters.forEach { zone ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianContainerLow)
            .border(1.dp, WarningAmber.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable { onViewOnMap(zone.point) }
            .padding(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(Modifier.weight(1f)) {
            Text(zone.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
              color = TacticalOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              buildString {
                append(zone.capacityTotal)
                append(" capacity · ")
                append(zone.capacityCurrent)
                append(" occupied · ")
                append(zone.availableCapacity)
                append(" free")
                val km = com.example.data.model.GeoMath.distanceMeters(
                  uiState.userLocation, zone.point) / 1000.0
                append(" · %.1f km".format(km))
              },
              fontSize = 11.sp, color = TacticalOnSurfaceVariant, maxLines = 1,
              overflow = TextOverflow.Ellipsis)
            val facilities = listOfNotNull(
              if (zone.waterAvailable) "Water" else null,
              if (zone.foodAvailable) "Food" else null,
              if (zone.electricityAvailable) "Power" else null,
              if (zone.sanitationAvailable) "Sanitation" else null,
              if (zone.medicalSupport) "Medical" else null
            ).joinToString(" · ")
            if (facilities.isNotBlank()) {
              Text(facilities, fontSize = 10.sp, color = TacticalOnSurfaceVariant, maxLines = 1)
            }
          }
          Text(zone.operatingStatus, fontSize = 10.sp, fontWeight = FontWeight.Black,
            color = if (zone.operatingStatus == "OPEN") NeonEmerald else WarningAmber)
        }
      }
    }

    editing?.let { target ->
      Spacer(Modifier.height(10.dp))
      ShelterForm(initial = target, onSave = { onSave(it); editing = null }, onCancel = { editing = null })
    }
    Spacer(Modifier.height(24.dp))
  }
}

private fun blankShelter(): SafeZone {
  val c = IndiaGeo.CENTER_LAT
  return SafeZone(
    id = "field-${System.currentTimeMillis()}",
    name = "", lat = c, lon = IndiaGeo.CENTER_LON, locationNote = "",
    capacityTotal = 100, capacityCurrent = 0,
    waterAvailable = false, foodAvailable = false, electricityAvailable = false,
    sanitationAvailable = false, medicalSupport = false, accessibility = "Road",
    womenChildrenSuitability = false, operatingStatus = "OPEN",
    verificationStatus = "FIELD RECORD", elevationNote = "",
    provenance = DataProvenance(
      source = "Field entry — operator device",
      classification = DataClassification.OBSERVED
    )
  )
}

@Composable
private fun ShelterForm(initial: SafeZone, onSave: (SafeZone) -> Unit, onCancel: () -> Unit) {
  var name by remember(initial.id) { mutableStateOf(initial.name) }
  var lat by remember(initial.id) { mutableStateOf(initial.lat.toString()) }
  var lon by remember(initial.id) { mutableStateOf(initial.lon.toString()) }
  var capacity by remember(initial.id) { mutableStateOf(initial.capacityTotal.toString()) }
  var occupied by remember(initial.id) { mutableStateOf(initial.capacityCurrent.toString()) }
  var land by remember(initial.id) { mutableStateOf(initial.landAreaSquareMeters?.toString() ?: "") }
  var waterL by remember(initial.id) { mutableStateOf(initial.waterLitresPerDay?.toString() ?: "") }
  var toilets by remember(initial.id) { mutableStateOf(initial.toiletCount?.toString() ?: "") }
  var water by remember(initial.id) { mutableStateOf(initial.waterAvailable) }
  var food by remember(initial.id) { mutableStateOf(initial.foodAvailable) }
  var power by remember(initial.id) { mutableStateOf(initial.electricityAvailable) }
  var sanitation by remember(initial.id) { mutableStateOf(initial.sanitationAvailable) }
  var medical by remember(initial.id) { mutableStateOf(initial.medicalSupport) }
  var error by remember(initial.id) { mutableStateOf<String?>(null) }

  FormCard(title = "SHELTER RECORD") {
    FormText("Name *", name) { name = it }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FormText("Latitude *", lat, Modifier.weight(1f), numeric = true) { lat = it }
      FormText("Longitude *", lon, Modifier.weight(1f), numeric = true) { lon = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FormText("Capacity", capacity, Modifier.weight(1f), numeric = true) { capacity = it }
      FormText("Occupied", occupied, Modifier.weight(1f), numeric = true) { occupied = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FormText("Land m² (optional)", land, Modifier.weight(1f), numeric = true) { land = it }
      FormText("Toilets (optional)", toilets, Modifier.weight(1f), numeric = true) { toilets = it }
    }
    FormText("Water L/day (optional)", waterL) { waterL = it }
    Text("Facilities", fontSize = 13.sp, fontWeight = FontWeight.Medium,
      color = TacticalOnSurfaceVariant)
    Row(modifier = Modifier.fillMaxWidth()) {
      Box(Modifier.weight(1f)) { FormCheck("Water", water) { water = it } }
      Box(Modifier.weight(1f)) { FormCheck("Food", food) { food = it } }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
      Box(Modifier.weight(1f)) { FormCheck("Power", power) { power = it } }
      Box(Modifier.weight(1f)) { FormCheck("Sanitation", sanitation) { sanitation = it } }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
      Box(Modifier.weight(1f)) { FormCheck("Medical", medical) { medical = it } }
      Box(Modifier.weight(1f)) {}
    }
    error?.let { Text(it, fontSize = 12.sp, color = EmergencyRedBright,
      fontWeight = FontWeight.Medium) }
    FormButtons(onSave = {
      val latV = lat.toDoubleOrNull()
      val lonV = lon.toDoubleOrNull()
      val point = latV?.let { l -> lonV?.let { GeoPoint(l, it) } }
      val trimmed = name.trim()
      when {
        trimmed.isBlank() -> error = "Name is required."
        point == null -> error = "Enter numeric coordinates."
        !IndiaGeo.contains(point) -> error = "Coordinates are outside India — record rejected."
        else -> onSave(
          initial.copy(
            name = trimmed,
            lat = point.lat, lon = point.lon,
            capacityTotal = capacity.toIntOrNull()?.coerceAtLeast(0) ?: initial.capacityTotal,
            capacityCurrent = occupied.toIntOrNull()?.coerceAtLeast(0) ?: initial.capacityCurrent,
            landAreaSquareMeters = land.toDoubleOrNull()?.takeIf { it > 0 },
            waterLitresPerDay = waterL.toDoubleOrNull()?.takeIf { it > 0 },
            toiletCount = toilets.toIntOrNull()?.takeIf { it > 0 },
            waterAvailable = water, foodAvailable = food, electricityAvailable = power,
            sanitationAvailable = sanitation, medicalSupport = medical
          )
        )
      }
    }, onCancel = onCancel)
  }
}

// ---- habitations tab -----------------------------------------------------------

@Composable
private fun HabitationsTab(
  uiState: VippattiUiState,
  showForm: Boolean,
  onToggleForm: (Boolean) -> Unit,
  onSave: (Habitation) -> Unit,
  onDelete: (String) -> Unit
) {
  var editing by remember { mutableStateOf<Habitation?>(null) }
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
    Text(
      "Surveyed habitations feed the relocation ranking with REAL population figures instead of demo data.",
      fontSize = 12.sp, color = TacticalOnSurfaceVariant, lineHeight = 16.sp,
      modifier = Modifier.padding(bottom = 8.dp)
    )
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(NeonEmerald.copy(alpha = 0.9f))
        .clickable { editing = blankHabitation(); onToggleForm(true) }
        .heightIn(min = 44.dp),
      contentAlignment = Alignment.Center
    ) {
      Text("New Habitation Record", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
    }
    Spacer(Modifier.height(8.dp))
    uiState.fieldHabitations.forEach { hab ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 3.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(ObsidianContainerLow)
          .clickable { editing = hab }
          .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          Text(hab.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TacticalOnSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
          Text(
            "${hab.point.lat.fmt()}, ${hab.point.lon.fmt()} • pop ${hab.population?.value ?: "not provided"} • ${hab.historicalEventCount} archive events",
            fontSize = 12.sp, color = TacticalOnSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Box(
          modifier = Modifier.size(44.dp).clickable { onDelete(hab.id) },
          contentAlignment = Alignment.Center
        ) {
          Icon(
            Icons.Default.Delete, "Delete habitation record", tint = EmergencyRedBright,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
    editing?.let { target ->
      Spacer(Modifier.height(10.dp))
      HabitationForm(
        initial = target,
        onSave = { onSave(it); editing = null; onToggleForm(false) },
        onCancel = { editing = null; onToggleForm(false) }
      )
    }
    Spacer(Modifier.height(24.dp))
  }
}

private fun blankHabitation(): Habitation = Habitation(
  id = "field-h-${System.currentTimeMillis()}",
  name = "",
  point = GeoPoint(IndiaGeo.CENTER_LAT, IndiaGeo.CENTER_LON)
)

@Composable
private fun HabitationForm(initial: Habitation, onSave: (Habitation) -> Unit, onCancel: () -> Unit) {
  var name by remember(initial.id) { mutableStateOf(initial.name) }
  var lat by remember(initial.id) { mutableStateOf(initial.point.lat.toString()) }
  var lon by remember(initial.id) { mutableStateOf(initial.point.lon.toString()) }
  var population by remember(initial.id) { mutableStateOf(initial.population?.value?.toString() ?: "") }
  var vulnerable by remember(initial.id) { mutableStateOf(initial.vulnerableShare?.times(100)?.toInt()?.toString() ?: "") }
  var history by remember(initial.id) { mutableStateOf(initial.historicalEventCount.toString()) }
  var error by remember(initial.id) { mutableStateOf<String?>(null) }

  FormCard(title = "HABITATION RECORD") {
    FormText("Habitation / village name *", name) { name = it }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FormText("Latitude *", lat, Modifier.weight(1f), numeric = true) { lat = it }
      FormText("Longitude *", lon, Modifier.weight(1f), numeric = true) { lon = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FormText("Population", population, Modifier.weight(1f), numeric = true) { population = it }
      FormText("Vulnerable %", vulnerable, Modifier.weight(1f), numeric = true) { vulnerable = it }
    }
    FormText("EM-DAT events", history, numeric = true) { history = it }
    error?.let { Text(it, fontSize = 12.sp, color = EmergencyRedBright,
      fontWeight = FontWeight.Medium) }
    FormButtons(onSave = {
      val trimmed = name.trim()
      val point = lat.toDoubleOrNull()?.let { l -> lon.toDoubleOrNull()?.let { GeoPoint(l, it) } }
      when {
        trimmed.isBlank() -> error = "Name is required."
        point == null -> error = "Enter numeric coordinates."
        !IndiaGeo.contains(point) -> error = "Coordinates are outside India — record rejected."
        else -> onSave(
          initial.copy(
            name = trimmed,
            point = point,
            population = population.toIntOrNull()?.takeIf { it > 0 }?.let {
              PopulationInput(
                value = it,
                classification = DataClassification.OBSERVED,
                source = "Field entry — operator device"
              )
            },
            vulnerableShare = vulnerable.toIntOrNull()?.div(100f)?.coerceIn(0f, 1f),
            historicalEventCount = history.toIntOrNull()?.coerceAtLeast(0) ?: 0
          )
        )
      }
    }, onCancel = onCancel)
  }
}

// ---- shared form atoms -----------------------------------------------------------

@Composable
private fun FormCard(title: String, content: @Composable () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, TacticalCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
      .padding(16.dp)
  ) {
    Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TacticalCyan,
      maxLines = 1)
    Spacer(Modifier.height(12.dp))
    androidx.compose.foundation.layout.Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxWidth()
    ) { content() }
  }
}


/** One labelled input; numeric=true shows a decimal keyboard. Label sits
 * clearly above a >=48dp field - nothing overlaps. */
@Composable
private fun FormText(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  numeric: Boolean = false,
  onValueChange: (String) -> Unit
) {
  Column(modifier) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
      color = TacticalOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
    TextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else KeyboardOptions.Default,
      textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TacticalOnSurface),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = ObsidianSurface,
        unfocusedContainerColor = ObsidianSurface,
        focusedIndicatorColor = TacticalCyan,
        unfocusedIndicatorColor = TacticalOnSurfaceVariant.copy(alpha = 0.3f)
      ),
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    )
  }
}

@Composable
private fun FormCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 44.dp)
      .clip(RoundedCornerShape(8.dp))
      .clickable { onCheckedChange(!checked) }
  ) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    Text(label, fontSize = 13.sp, color = TacticalOnSurface, maxLines = 1)
  }
}

@Composable
private fun FormButtons(onSave: () -> Unit, onCancel: () -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
    Box(
      modifier = Modifier
        .weight(1f)
        .clip(RoundedCornerShape(8.dp))
        .background(NeonEmerald.copy(alpha = 0.9f))
        .clickable(onClick = onSave)
        .heightIn(min = 48.dp),
      contentAlignment = Alignment.Center
    ) { Text("Save Record", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black) }
    Box(
      modifier = Modifier
        .weight(1f)
        .clip(RoundedCornerShape(8.dp))
        .background(TacticalOnSurfaceVariant.copy(alpha = 0.25f))
        .clickable(onClick = onCancel)
        .heightIn(min = 48.dp),
      contentAlignment = Alignment.Center
    ) { Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TacticalOnSurface) }
  }
}

private fun Double.fmt(): String = String.format(java.util.Locale.US, "%.4f", this)
