package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.WarningAmber
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import com.example.data.disaster.WeatherMetrics
import com.example.data.disaster.DisasterLayer
import com.example.ui.components.StatusBadge
import com.example.ui.components.dataStatusColor
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.ObsidianContainerLow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DataClassification
import com.example.data.model.DataStatus
import com.example.data.model.RecordStamp
import com.example.data.model.SafeZone
import com.example.data.routing.OsrmRoutingService
import com.example.ui.components.StampLine
import com.example.ui.components.UnavailablePanel
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.viewmodel.VippattiUiState

// ============================================================================
// SHEET PAYLOADS
// ============================================================================

/** COLLAPSED state: compact destination status line under the handle. */
@Composable
internal fun CollapsedSheetContent(uiState: VippattiUiState) {
  val route = uiState.activeRoute
  // PROGRESSIVE DISCLOSURE (map redesign rule): the Destination card appears
  // ONLY once a safe zone is actually selected; before that the peek shows
  // a quiet hint instead of a permanent 'No safe zone' message.
  if (uiState.selectedSafeZone == null) {
    Text(
      text = "Swipe up — safe zones, routing and data",
      fontSize = 11.sp,
      fontWeight = FontWeight.Medium,
      color = TacticalOnSurfaceVariant,
      maxLines = 1,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp)
        .testTag("collapsed_sheet_hint")
    )
    return
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Icon(
      imageVector = Icons.Default.Place,
      contentDescription = null,
      tint = NeonEmerald,
      modifier = Modifier.size(18.dp)
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "DESTINATION",
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = TacticalOnSurfaceVariant,
        letterSpacing = 0.8.sp
      )
      Text(
        text = uiState.selectedSafeZone?.name ?: "No viable safe zone found — expand to choose",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalOnSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    if (uiState.isCalculatingRoute) {
      CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 1.5.dp,
        color = NeonEmerald
      )
    } else {
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = route?.let { OsrmRoutingService.formatDistance(it.distanceMeters) } ?: "--",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = NeonEmerald,
          maxLines = 1
        )
        Text(
          text = route?.let {
            // B11: estimated offline times carry an explicit tilde.
            (if (it.isLiveOsrm) "" else "~") + OsrmRoutingService.formatDuration(it.durationSeconds)
          } ?: "no route",
          fontSize = 11.sp,
          color = TacticalOnSurfaceVariant,
          maxLines = 1
        )
      }
    }
  }
}

/** EXPANDED state: full decision stack in the required priority order. */
@Composable
internal fun ExpandedSheetContent(
  uiState: VippattiUiState,
  onSelectBestSafeZone: () -> Unit,
  onSelectSafeZone: (SafeZone) -> Unit,
  onSetTravelMode: (com.example.data.routing.TravelMode) -> Unit,
  onStartEvacuation: () -> Unit,
  onStopEvacuation: () -> Unit,
  onLoadAlternativeRoutes: () -> Unit,
  /** Opens the incident-report form — deliberately only from this explicit action. */
  onOpenIncidentReport: () -> Unit = {},
  /** Explicit opt-in for the unverified offline straight-line estimate. */
  onRequestFallbackRoute: () -> Unit = {},
  onSelectAlternativeRoute: (String) -> Unit = {},
  /** Map-cleanup rule 1: layer switches + data status moved OFF the map
   *  into this secondary sheet surface (the infrastructure stays). */
  onToggleLayer: (com.example.data.disaster.DisasterLayer) -> Unit = {},
  /** Retries the live weather reading (used by the provenance panel). */
  onRetryWeather: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // 1. WHAT SHOULD I DO?
    RecommendedActionCard(
      action = uiState.recommendedAction,
      selectedZoneName = uiState.selectedSafeZone?.name,
      onWhyThisZone = onSelectBestSafeZone,
      isCalculating = uiState.isCalculatingRoute
    )

    // 2. HORIZONTAL SAFE-ZONE CAROUSEL (swipe, next card peeks).
    SafeZoneCarousel(
      uiState = uiState,
      onSelectSafeZone = onSelectSafeZone
    )

    // 3. COMPACT WEATHER ROW (live Open-Meteo feed, status-labelled) plus its
    //    provenance line: source, when the reading was actually retrieved, and
    //    a real error message when the refresh failed. "Time unknown" until a
    //    reading exists - no fabricated fetch time.
    CompactWeatherRow(weather = uiState.weather, status = uiState.weatherStatus)
    StampLine(
      stamp = RecordStamp(
        sourceName = "Open-Meteo (keyless)",
        retrievedAtMillis = uiState.weatherRetrievedAtMillis,
        // Provider observation time from the payload; absent -> "Time unknown".
        eventAtMillis = uiState.weather.observedAtMillis.takeIf { it > 0L },
        coverage = "current map location",
        status = uiState.weatherStatus,
        errorMessage = uiState.weatherErrorMessage,
        classification = DataClassification.OBSERVED
      ),
      nowMillis = System.currentTimeMillis(),
      modifier = Modifier.padding(horizontal = 14.dp)
    )
    // Honest stale/unavailable/error panel with a working retry. LOADING and
    // SUCCESS render nothing extra - there is nothing to explain.
    if (uiState.weatherStatus != DataStatus.SUCCESS && uiState.weatherStatus != DataStatus.LOADING) {
      UnavailablePanel(
        status = uiState.weatherStatus,
        what = "Weather for the current map location",
        errorMessage = uiState.weatherErrorMessage,
        onRetry = onRetryWeather,
        modifier = Modifier.padding(horizontal = 14.dp)
      )
    }

    // 4. ROUTE INTELLIGENCE + NAVIGATION CONTROLS.
    RouteIntelligencePanel(
      uiState = uiState,
      onSetTravelMode = onSetTravelMode,
      onLoadAlternativeRoutes = onLoadAlternativeRoutes,
      onSelectBestSafeZone = onSelectBestSafeZone,
      onRequestFallbackRoute = onRequestFallbackRoute,
      onSelectAlternativeRoute = onSelectAlternativeRoute
    )

    // (map-cleanup rule 1) The large START EVACUATION ROUTE bar is gone from
    // the sheet: routing starts from a safe-zone card tap, and the route
    // panel + destination card carry the state. Guidance start/stop is bound
    // to the card's GO action, so no duplicate tutorial-like bar exists.

    // 5b. DATA & LAYERS - the map's secondary controls, progressive
    // disclosure INSIDE the sheet (map-cleanup rule 1: no Layers button,
    // no cached-data badges on the map itself). All switching power lives
    // here; the map stays the workspace.
    DataAndLayersSection(
      uiState = uiState,
      onToggleLayer = onToggleLayer
    )

    // 6. Citizen incident reporting — reachable ONLY through this explicit
    //    action (never by tapping the map), so a map tap can never open a form.
    OutlinedButton(
      onClick = onOpenIncidentReport,
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp)
        .height(42.dp)
        .testTag("open_incident_report_button")
    ) {
      Text(
        text = "REPORT AN INCIDENT…",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

/**
 * Compact layer switches + honest data status for the bottom sheet.
 * Same DisasterLayer state the map engine consumes - nothing removed,
 * just relocated out of the map chrome.
 */
@Composable
private fun DataAndLayersSection(
  uiState: VippattiUiState,
  onToggleLayer: (com.example.data.disaster.DisasterLayer) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }
  val active = uiState.enabledLayers.size
  val totalLayers = com.example.data.disaster.DisasterLayer.entries.size
  val liveCount = uiState.providerStatuses.count { (_, st) ->
    st == com.example.data.model.DataStatus.SUCCESS ||
    st == com.example.data.model.DataStatus.STALE
  }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainer)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
      .clickable { expanded = !expanded }
      .padding(10.dp)
      .testTag("sheet_data_layers")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Map data & layers \u00b7 " + active + "/" + totalLayers,
        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface
      )
      Text(
        text = if (expanded) "HIDE" else "SHOW",
        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacticalCyan
      )
    }
    if (expanded) {
      Spacer(Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        com.example.data.disaster.DisasterLayer.entries.forEach { layer ->
          val on = layer in uiState.enabledLayers
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(999.dp))
              .background(
                if (on) NeonEmeraldContainer.copy(alpha = 0.3f) else ObsidianContainerHigh
              )
              .border(
                1.dp,
                if (on) NeonEmerald else TacticalOutlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(999.dp)
              )
              .clickable { onToggleLayer(layer) }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("sheet_layer_" + layer.name.lowercase())
          ) {
            Text(
              text = (if (on) "\u2713 " else "") + layer.label,
              fontSize = 10.sp,
              fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
              color = if (on) NeonEmerald else TacticalOnSurfaceVariant,
              maxLines = 1
            )
          }
        }
      }
      if (uiState.providerStatuses.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        uiState.providerStatuses.forEach { (state, status) ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              state.source.name.lowercase().replace('_', ' '),
              fontSize = 10.sp, color = TacticalOnSurfaceVariant)
            Text(status.name, fontSize = 10.sp, fontWeight = FontWeight.Bold,
              color = if (status == com.example.data.model.DataStatus.SUCCESS)
                NeonEmerald else WarningAmber)
          }
        }
        Text(
          "Sources responding: " + liveCount + " of " + uiState.providerStatuses.size,
          fontSize = 10.sp, color = TacticalOnSurfaceVariant,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
    }
  }
}
