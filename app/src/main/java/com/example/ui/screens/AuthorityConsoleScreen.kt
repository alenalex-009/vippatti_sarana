package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.TacticalOutlineVariant
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.disaster.IndiaGeo
import com.example.data.habitations.DemoHabitations
import com.example.data.habitations.Habitation
import com.example.data.habitations.HabitationPriority
import com.example.data.habitations.PopulationInput
import com.example.data.habitations.RelocationTier
import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.viewmodel.VippattiUiState
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.WarningAmber

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
  onRerank: (Boolean) -> Unit
) {
  var tab by remember { mutableStateOf(0) } // 0 = dashboard, 1 = shelters, 2 = habitations
  var showHabForm by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianSurface)
  ) {
    // Header -----------------------------------------------------------------
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(ObsidianContainerLow)
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(TacticalCyan.copy(alpha = 0.15f))
          .clickable(onClick = onBack),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.ArrowBack, "Back", tint = TacticalCyan, modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text("AUTHORITY CONSOLE", fontSize = 14.sp, fontWeight = FontWeight.Black, color = TacticalOnSurface)
        Text(
          "Field registry + relocation prioritization — demo data stays labelled",
          fontSize = 11.sp, color = TacticalOnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
      }
    }

    // Tab selector -------------------------------------------------------------
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      listOf("PRIORITIZATION", "SHELTERS (${uiState.fieldShelters.size})", "HABITATIONS (${uiState.fieldHabitations.size})")
        .forEachIndexed { index, label ->
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (tab == index) TacticalCyan.copy(alpha = 0.2f) else ObsidianContainerLow)
              .border(
                1.dp,
                if (tab == index) TacticalCyan.copy(alpha = 0.7f) else Color.Transparent,
                RoundedCornerShape(8.dp)
              )
              .clickable { tab = index }
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          }
        }
    }

    when (tab) {
      0 -> DashboardTab(uiState, onRerank)
      1 -> SheltersTab(uiState, onSaveShelter, onDeleteShelter)
      else -> HabitationsTab(uiState, showHabForm, onToggleForm = { showHabForm = it }, onSaveHabitation, onDeleteHabitation)
    }
  }
}

@Composable
private fun DashboardTab(uiState: VippattiUiState, onRerank: (Boolean) -> Unit) {
  var liveScan by remember { mutableStateOf(false) }
  val priorities = uiState.relocationPriorities
  val counts = priorities.groupingBy { it.tier }.eachCount()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 12.dp)
  ) {
    // Tier summary chips
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      RelocationTier.entries.forEach { tier ->
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(tierColor(tier).copy(alpha = 0.15f))
            .border(1.dp, tierColor(tier).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${counts[tier] ?: 0}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = tierColor(tier))
            Text(tier.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurfaceVariant)
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(TacticalCyan.copy(alpha = 0.9f))
          .clickable { onRerank(liveScan) }
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Text(
          if (uiState.isRankingPriorities) "RANKING..." else "RANK HABITATIONS",
          fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
          checked = liveScan,
          onCheckedChange = { liveScan = it },
          modifier = Modifier.size(22.dp)
        )
        Text("live terrain scan\n(SRTM + rain per site)", fontSize = 11.sp, color = TacticalOnSurfaceVariant)
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      "Ranking is transparent multi-criteria: hazard exposure 35%, terrain habitability 30%, vulnerability 20%, EM-DAT history 15% (history escalates at most one band). Every reason is shown per row.",
      fontSize = 11.sp, color = TacticalOnSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))

    if (uiState.isRankingPriorities && priorities.isEmpty()) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = TacticalCyan)
        Text("Probing terrain for every habitation — this takes a moment...", fontSize = 10.sp, color = TacticalOnSurfaceVariant)
      }
    }

    Text(
      if (priorities.isEmpty()) "Nothing ranked yet"
      else "Relocation order — most urgent first",
      fontSize = 13.sp, fontWeight = FontWeight.Black, color = TacticalOnSurface,
      modifier = Modifier.padding(vertical = 6.dp)
    )
    priorities.forEach { p -> PriorityRow(p) }
    if (priorities.isEmpty() && !uiState.isRankingPriorities) {
      Text(
        "Add habitation records in the HABITATIONS tab, then tap RANK HABITATIONS — " +
          "each row will explain WHY it landed in its tier.",
        fontSize = 12.sp, color = TacticalOnSurfaceVariant, lineHeight = 16.sp,
        modifier = Modifier.padding(vertical = 16.dp)
      )
    }
    Spacer(Modifier.height(20.dp))
  }
}

@Composable
private fun PriorityRow(p: HabitationPriority) {
  var expanded by remember(p.habitation.id, p.score) { mutableStateOf(false) }
  val color = tierColor(p.tier)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
      .clickable { expanded = !expanded }
  ) {
    // Tier band: the urgency reads before any text does.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(color.copy(alpha = 0.16f))
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(color))
      Text(
        p.tier.label, fontSize = 11.sp, fontWeight = FontWeight.Black,
        color = color, letterSpacing = 0.6.sp
      )
      Spacer(Modifier.weight(1f))
      Text(
        "SCORE ${p.score}", fontSize = 11.sp, fontWeight = FontWeight.Black,
        color = TacticalOnSurfaceVariant
      )
    }
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Text(
        p.habitation.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        color = TacticalOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("priority_row_${p.habitation.id}")
      )
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(
          buildString {
            append("${p.habitation.point.lat.fmt()}, ${p.habitation.point.lon.fmt()}")
            p.habitation.population?.let { append(" · pop ${it.value}") }
            p.nearestSafeZoneDistanceMeters?.let {
              append(" · safe zone %.1f km".format(it / 1000))
            }
          },
          fontSize = 12.sp, color = TacticalOnSurfaceVariant, lineHeight = 16.sp,
          modifier = Modifier.weight(1f)
        )
      }
      if (p.habitation.population?.classification == DataClassification.SIMULATED) {
        Text(
          "SIMULATED demo record",
          fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WarningAmber
        )
      }
      // WHY, up front (was hidden behind expand — authorities read reasons first)
      p.reasons.firstOrNull()?.let {
        Text(
          it, fontSize = 12.sp, color = TacticalOnSurface, lineHeight = 16.sp,
          maxLines = if (expanded) 10 else 2,
          overflow = TextOverflow.Ellipsis
        )
      }
      if (expanded) {
        Box(
          Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TacticalOutlineVariant.copy(alpha = 0.3f))
        )
        Text(
          p.tier.actionGuide, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color
        )
        p.reasons.drop(1).forEach { reason ->
          Text(
            "·  $reason", fontSize = 12.sp, color = TacticalOnSurface, lineHeight = 16.sp
          )
        }
        Text(
          "Weights: hazard 35% · terrain 30% · vulnerability 20% · history 15%",
          fontSize = 11.sp, color = TacticalOnSurfaceVariant
        )
      }
      Text(
        if (expanded) "TAP TO COLLAPSE" else "TAP FOR ALL REASONS",
        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacticalCyan
      )
    }
  }
}

// ---- shelters tab ------------------------------------------------------------

@Composable
private fun SheltersTab(
  uiState: VippattiUiState,
  onSave: (SafeZone) -> Unit,
  onDelete: (String) -> Unit
) {
  var editing by remember { mutableStateOf<SafeZone?>(null) }
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
    Text(
      "Field-entered shelters join the LIVE shelter network (they are real records, so they are never hidden by the demo switch).",
      fontSize = 11.sp, color = TacticalOnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)
    )
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(NeonEmerald.copy(alpha = 0.9f))
        .clickable { editing = blankShelter() }
        .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Add, null, tint = Color.Black, modifier = Modifier.size(14.dp))
        Text(" NEW SHELTER RECORD", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
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
          Text(zone.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          Text(
            "${zone.lat.fmt()}, ${zone.lon.fmt()} • cap ${zone.capacityTotal - zone.capacityCurrent}/${zone.capacityTotal} open • ${zone.operatingStatus}",
            fontSize = 11.sp, color = TacticalOnSurfaceVariant
          )
        }
        Icon(
          Icons.Default.Delete, "Delete", tint = EmergencyRedBright,
          modifier = Modifier.size(16.dp).clickable { onDelete(zone.id) }
        )
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FormText("Latitude *", lat, Modifier.weight(1f)) { lat = it }
      FormText("Longitude *", lon, Modifier.weight(1f)) { lon = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FormText("Capacity", capacity, Modifier.weight(1f)) { capacity = it }
      FormText("Occupied", occupied, Modifier.weight(1f)) { occupied = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FormText("Land m² (optional)", land, Modifier.weight(1f)) { land = it }
      FormText("Water L/day (optional)", waterL, Modifier.weight(1f)) { waterL = it }
      FormText("Toilets (optional)", toilets, Modifier.weight(0.6f)) { toilets = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
      FormCheck("Water", water) { water = it }
      FormCheck("Food", food) { food = it }
      FormCheck("Power", power) { power = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
      FormCheck("Sanitation", sanitation) { sanitation = it }
      FormCheck("Medical", medical) { medical = it }
    }
    error?.let { Text(it, fontSize = 12.sp, color = EmergencyRedBright, modifier = Modifier.padding(top = 4.dp)) }
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
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
    Text(
      "Surveyed habitations feed the relocation ranking with REAL population figures instead of demo data.",
      fontSize = 11.sp, color = TacticalOnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)
    )
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(NeonEmerald.copy(alpha = 0.9f))
        .clickable { editing = blankHabitation(); onToggleForm(true) }
        .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
      Text(" NEW HABITATION RECORD", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
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
          Text(hab.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          Text(
            "${hab.point.lat.fmt()}, ${hab.point.lon.fmt()} • pop ${hab.population?.value ?: "not provided"} • ${hab.historicalEventCount} archive events",
            fontSize = 11.sp, color = TacticalOnSurfaceVariant
          )
        }
        Icon(
          Icons.Default.Delete, "Delete", tint = EmergencyRedBright,
          modifier = Modifier.size(16.dp).clickable { onDelete(hab.id) }
        )
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FormText("Latitude *", lat, Modifier.weight(1f)) { lat = it }
      FormText("Longitude *", lon, Modifier.weight(1f)) { lon = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FormText("Population", population, Modifier.weight(1f)) { population = it }
      FormText("Vulnerable %", vulnerable, Modifier.weight(1f)) { vulnerable = it }
      FormText("EM-DAT events", history, Modifier.weight(0.8f)) { history = it }
    }
    error?.let { Text(it, fontSize = 12.sp, color = EmergencyRedBright, modifier = Modifier.padding(top = 4.dp)) }
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
      .clip(RoundedCornerShape(10.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, TacticalCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
      .padding(10.dp)
  ) {
    Text(title, fontSize = 10.sp, fontWeight = FontWeight.Black, color = TacticalCyan)
    Spacer(Modifier.height(8.dp))
    androidx.compose.foundation.layout.Column(
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth()
    ) { content() }
  }
}


@Composable
private fun FormText(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
  Column(modifier) {
    Text(label, fontSize = 11.sp, color = TacticalOnSurfaceVariant)
    TextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = TacticalOnSurface),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = ObsidianSurface,
        unfocusedContainerColor = ObsidianSurface,
        focusedIndicatorColor = TacticalCyan,
        unfocusedIndicatorColor = TacticalOnSurfaceVariant.copy(alpha = 0.3f)
      ),
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
private fun FormCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onCheckedChange(!checked) }) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.size(18.dp))
    Text(label, fontSize = 11.sp, color = TacticalOnSurface)
  }
}

@Composable
private fun FormButtons(onSave: () -> Unit, onCancel: () -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(NeonEmerald.copy(alpha = 0.9f))
        .clickable(onClick = onSave)
        .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text("SAVE RECORD", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black) }
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(TacticalOnSurfaceVariant.copy(alpha = 0.25f))
        .clickable(onClick = onCancel)
        .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text("CANCEL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface) }
  }
}

private fun Double.fmt(): String = String.format(java.util.Locale.US, "%.4f", this)
