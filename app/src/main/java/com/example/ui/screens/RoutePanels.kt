package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.routing.OsrmRoutingService
import com.example.data.routing.RouteSafetyStatus
import com.example.viewmodel.RouteStatus
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.OnEmergencyRedContainer
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState

// ============================================================================
// ROUTE INTELLIGENCE — distance, ETA, mode, safety, warnings, capacity, alt.
// OSRM VALIDATION is shown ONLY when the displayed route actually came from
// the live OSRM service (RouteResult.isLiveOsrm == true).
// ============================================================================

@Composable
internal fun RouteIntelligencePanel(
  uiState: VippattiUiState,
  onSetTravelMode: (String) -> Unit,
  onLoadAlternativeRoutes: () -> Unit,
  onSelectBestSafeZone: () -> Unit,
  onRequestFallbackRoute: () -> Unit = {},
  /** Swap a listed alternative corridor in as the active route (chip tap). */
  onSelectAlternativeRoute: (String) -> Unit = {}
) {
  val route = uiState.activeRoute
  val evaluation = uiState.selectedEvaluation

  // BUG-2 FIX: the card used to render EVERY block (lifecycle message, retry
  // row, metrics, controls) unconditionally, so a DANGER route with a blocking
  // hazard warning grew ~616px tall and broke the sheet/screen layout. Secondary
  // detail now sits behind an explicit "Route details" action, which is what the
  // design brief asks for. The essential decision information — destination,
  // validation status, route safety, hazard warning, distance/ETA — stays visible.
  var showDetails by rememberSaveable { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(ObsidianContainerLow.copy(alpha = 0.96f))
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
      // BUG-2 FIX: the previous 12dp card padding + 8dp section spacing let the
      // card balloon once a DANGER status, a lifecycle message and the retry row
      // were all present — it grew past the bottom sheet and broke the layout.
      // Tighter but still readable; the test tag lets a layout test bound the height.
      .padding(10.dp)
      .testTag("route_intelligence_panel"),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    // Primary header row with the explicit "Route details" action in the same
    // line as the validation badge — controls the secondary blocks below.
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "ROUTE TO",
          fontSize = 11.sp,
          fontWeight = FontWeight.Black,
          color = TacticalOnSurfaceVariant,
          letterSpacing = 0.8.sp
        )
        Text(
          text = uiState.selectedSafeZone?.name ?: "No safe zone selected",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Box(
        modifier = Modifier
          .clip(CircleShape)
          .background(
            when (uiState.routeStatus) {
              RouteStatus.READY -> NeonEmerald.copy(alpha = 0.15f)
              RouteStatus.FALLBACK_UNVERIFIED -> WarningAmber.copy(alpha = 0.15f)
              RouteStatus.NETWORK_ERROR, RouteStatus.NO_ROUTE -> EmergencyRed.copy(alpha = 0.12f)
              else -> ObsidianContainerHigh
            }
          )
          .border(
            1.dp,
            when (uiState.routeStatus) {
              RouteStatus.READY -> NeonEmerald
              RouteStatus.FALLBACK_UNVERIFIED -> WarningAmber
              RouteStatus.NETWORK_ERROR, RouteStatus.NO_ROUTE -> EmergencyRedBright
              else -> TacticalOutlineVariant
            },
            CircleShape
          )
          .padding(horizontal = 8.dp, vertical = 3.dp)
          .testTag("osrm_validation_badge")
      ) {
        Text(
          text = when (uiState.routeStatus) {
            RouteStatus.IDLE -> "NO ROUTE"
            RouteStatus.REQUESTING -> "REQUESTING…"
            RouteStatus.RECEIVED -> "ROUTE RECEIVED"
            RouteStatus.VALIDATING_HAZARDS -> "CHECKING HAZARDS…"
            RouteStatus.READY -> "REAL ROADS VERIFIED"
            RouteStatus.NO_ROUTE -> "NO ROUTE FOUND"
            RouteStatus.NETWORK_ERROR -> "ROUTER UNREACHABLE"
            RouteStatus.FALLBACK_UNVERIFIED -> "UNVERIFIED ESTIMATE"
          },
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = when (uiState.routeStatus) {
            RouteStatus.READY -> NeonEmerald
            RouteStatus.FALLBACK_UNVERIFIED -> WarningAmber
            RouteStatus.NETWORK_ERROR, RouteStatus.NO_ROUTE -> EmergencyRedBright
            else -> TacticalOnSurfaceVariant
          }
        )
      }
    }

    // Explicit route lifecycle message — states exactly what happened so "no
    // route" can never be mistaken for a computed safe corridor. Shown only when
    // it adds information beyond the badge (in-flight / failure / fallback), and
    // clamped so it can never inflate the card.
    if (showDetails && (uiState.routeStatus == RouteStatus.REQUESTING ||
        uiState.routeStatus == RouteStatus.NETWORK_ERROR ||
        uiState.routeStatus == RouteStatus.NO_ROUTE ||
        uiState.routeStatus == RouteStatus.FALLBACK_UNVERIFIED)
    ) {
      uiState.routeStatusMessage?.let { message ->
        Text(
          text = message,
          fontSize = 12.sp,
          lineHeight = 16.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          color = when (uiState.routeStatus) {
            RouteStatus.FALLBACK_UNVERIFIED -> WarningAmber
            RouteStatus.NETWORK_ERROR, RouteStatus.NO_ROUTE -> EmergencyRedBright
            else -> TacticalOnSurfaceVariant
          },
          modifier = Modifier.testTag("route_status_message")
        )
      }
    }

    // Failure states offer a real Retry, and the offline estimate is available
    // ONLY as an explicit, clearly-labelled opt-in.
    if (showDetails &&
      (uiState.routeStatus == RouteStatus.NETWORK_ERROR ||
        uiState.routeStatus == RouteStatus.NO_ROUTE ||
        uiState.routeStatus == RouteStatus.FALLBACK_UNVERIFIED)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (uiState.routeStatus != RouteStatus.FALLBACK_UNVERIFIED) {
          TextButton(
            onClick = onLoadAlternativeRoutes,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier
              .heightIn(min = 32.dp)
              .testTag("route_retry_button")
          ) {
            Text("Retry road route", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalCyan)
          }
          TextButton(
            onClick = onRequestFallbackRoute,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier
              .heightIn(min = 32.dp)
              .testTag("route_use_estimate_button")
          ) {
            Text("Use unverified estimate", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarningAmber)
          }
        }
      }
    }

    // Metrics row: distance, ETA, mode, capacity.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      RouteMetric(
        label = "DISTANCE",
        value = route?.let { OsrmRoutingService.formatDistance(it.distanceMeters) } ?: "--",
        accent = TacticalCyan
      )
      RouteMetric(
        label = "ETA",
        value = route?.let { OsrmRoutingService.formatDuration(it.durationSeconds) } ?: "--",
        accent = NeonEmerald
      )
      RouteMetric(
        label = "MODE",
        value = if (uiState.travelMode == "driving") "VEHICLE" else "WALKING",
        accent = TacticalOnSurface
      )
      RouteMetric(
        label = "CAPACITY",
        value = evaluation?.let { "${it.capacityReport.availableCapacity} free" } ?: "--",
        accent = if ((evaluation?.capacityReport?.availableCapacity ?: 0) > 0) NeonEmerald else EmergencyRedBright
      )
    }

    // Route safety status + score bar.
    if (route != null) {
      Column(
        modifier = Modifier.fillMaxWidth().testTag("route_safety_block"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (uiState.routeStatus == RouteStatus.FALLBACK_UNVERIFIED) {
              "Route safety: NOT CHECKED"
            } else {
              "Route safety: ${route.routeSafetyStatus.label}"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = when (route.routeSafetyStatus) {
              RouteSafetyStatus.SAFE -> NeonEmerald
              RouteSafetyStatus.CAUTION -> WarningAmber
              RouteSafetyStatus.DANGER -> EmergencyRedBright
            },
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
          )
          Text(
            text = "${route.routeSafetyScore}/100",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurfaceVariant
          )
        }
        // Plain-language action line for the worst state (the raw label "Danger
        // — Route Enters Hazard Zone" left users guessing; the user asked for
        // wording people understand).
        if (route.routeSafetyStatus == RouteSafetyStatus.DANGER &&
          uiState.routeStatus != RouteStatus.FALLBACK_UNVERIFIED
        ) {
          Text(
            text = "This road passes through a danger area. Prefer another route if you can.",
            fontSize = 11.sp,
            color = EmergencyRedBright,
            lineHeight = 15.sp,
            modifier = Modifier.testTag("route_danger_advice")
          )
        }
      }
      LinearProgressIndicator(
        progress = { route.routeSafetyScore / 100f },
        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)),
        color = when (route.routeSafetyStatus) {
          RouteSafetyStatus.SAFE -> NeonEmerald
          RouteSafetyStatus.CAUTION -> WarningAmber
          RouteSafetyStatus.DANGER -> EmergencyRed
        },
        trackColor = ObsidianContainerHigh
      )

      // STAGE 5 — closure/traffic honesty: a READY road route is OSRM geometry
      // checked against known hazard geometry only. There is no road-closure
      // feed and no live-traffic feed, so the limitation is stated on the card
      // itself (the fallback estimate already carries its own disclaimer).
      if (route.isLiveOsrm && uiState.routeStatus == RouteStatus.READY) {
        Text(
          text = "Road closures and live traffic are not verified.",
          fontSize = 10.sp,
          color = TacticalOnSurfaceVariant,
          modifier = Modifier.testTag("route_closure_disclaimer")
        )
      }

      // Hazard warnings — surfaced when the route meets a hazard zone.
      route.hazardWarnings.forEach { warning ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
              if (warning.isBlocking) EmergencyRedContainer.copy(alpha = 0.35f)
              else ObsidianContainer.copy(alpha = 0.8f)
            )
            .border(
              1.dp,
              if (warning.isBlocking) EmergencyRed.copy(alpha = 0.6f) else WarningAmber.copy(alpha = 0.4f),
              RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.Top
        ) {
          Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = if (warning.isBlocking) EmergencyRedBright else WarningAmber,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = warning.message,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = if (warning.isBlocking) OnEmergencyRedContainer else TacticalOnSurface
          )
        }
      }
    }

    // Explicit action to inspect the secondary routing detail.
    TextButton(
      onClick = { showDetails = !showDetails },
      contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
      modifier = Modifier
        .heightIn(min = 30.dp)
        .testTag("route_details_toggle")
    ) {
      Icon(
        imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = null,
        tint = TacticalOnSurfaceVariant,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = if (showDetails) "Hide route details" else "Route details",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalOnSurfaceVariant
      )
    }

    // Controls: travel mode toggle + alternatives + best-zone.
    if (showDetails) {
      Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Walking / Driving toggle.
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(ObsidianContainer)
          .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(8.dp))
          .padding(2.dp)
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (uiState.travelMode == "foot") NeonEmerald else Color.Transparent)
            .clickable { onSetTravelMode("foot") }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("mode_walking_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
            contentDescription = "Walking",
            tint = if (uiState.travelMode == "foot") OnNeonEmerald else TacticalOnSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (uiState.travelMode == "driving") NeonEmerald else Color.Transparent)
            .clickable { onSetTravelMode("driving") }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("mode_driving_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = "Driving",
            tint = if (uiState.travelMode == "driving") OnNeonEmerald else TacticalOnSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Button(
        onClick = onLoadAlternativeRoutes,
        colors = ButtonDefaults.buttonColors(
          containerColor = ObsidianContainerHigh,
          contentColor = TacticalCyan
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp).testTag("alternative_routes_button")
      ) {
        Icon(Icons.Default.AltRoute, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Alternatives (${uiState.alternativeRoutes.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.weight(1f))

      Button(
        onClick = onSelectBestSafeZone,
        colors = ButtonDefaults.buttonColors(
          containerColor = NeonEmeraldContainer,
          contentColor = OnNeonEmerald
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp).testTag("select_best_safe_zone_button")
      ) {
        Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Best Zone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
    }
    } // end of showDetails (controls + alternatives)

    // Alternative corridor chips (when computed). TAPPING one swaps it in as
    // the active route (the chips used to be inert — the "Alternatives
    // doesn't work" report). Alternatives render as grey ghost lines on the
    // map; the active corridor stays green.
    if (showDetails && uiState.alternativeRoutes.size > 1) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(uiState.alternativeRoutes.size) { idx ->
          val alt = uiState.alternativeRoutes[idx]
          // Logical identity, not object identity: state copies create
          // equal-but-distinct RouteResult instances.
          val isPrimary = route != null && alt.routeId == route.routeId
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (isPrimary) NeonEmerald.copy(alpha = 0.15f) else ObsidianContainer)
              .border(
                1.dp,
                if (isPrimary) NeonEmerald else TacticalOutlineVariant,
                RoundedCornerShape(8.dp)
              )
              .clickable(enabled = !isPrimary) { onSelectAlternativeRoute(alt.routeId) }
              .padding(horizontal = 8.dp, vertical = 6.dp)
              .testTag("alternative_chip_$idx")
          ) {
            Text(
              text = (if (isPrimary) "" else "USE: ") + "${alt.summary} • ${OsrmRoutingService.formatDistance(alt.distanceMeters)} • Safety ${alt.routeSafetyScore}",
              fontSize = 11.sp,
              color = if (isPrimary) NeonEmerald else TacticalOnSurface,
              fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1
            )
          }
        }
      }
    }
  }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RouteMetric(label: String, value: String, accent: Color) {
  Column(
    modifier = Modifier
      .weight(1f)
      .clip(RoundedCornerShape(10.dp))
      .background(ObsidianContainer.copy(alpha = 0.7f))
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
      .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TacticalOnSurfaceVariant, maxLines = 1)
    Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
  }
}
