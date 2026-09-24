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
        text = uiState.selectedSafeZone?.name ?: "No safe zone selected — expand to choose",
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
          text = route?.let { OsrmRoutingService.formatDuration(it.durationSeconds) } ?: "no route",
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
  onSetTravelMode: (String) -> Unit,
  onStartEvacuation: () -> Unit,
  onStopEvacuation: () -> Unit,
  onLoadAlternativeRoutes: () -> Unit,
  /** Opens the incident-report form — deliberately only from this explicit action. */
  onOpenIncidentReport: () -> Unit = {},
  /** Explicit opt-in for the unverified offline straight-line estimate. */
  onRequestFallbackRoute: () -> Unit = {},
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
      onRequestFallbackRoute = onRequestFallbackRoute
    )

    // 5. Large one-hand evacuation CTA.
    EvacuationCta(
      uiState = uiState,
      onStartEvacuation = onStartEvacuation,
      onStopEvacuation = onStopEvacuation
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
