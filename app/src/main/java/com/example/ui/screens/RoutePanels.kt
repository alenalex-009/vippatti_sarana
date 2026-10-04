package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.example.ui.theme.ObsidianContainerLowest
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
  onSetTravelMode: (com.example.data.routing.TravelMode) -> Unit,
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
          text = "Route to",
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = TacticalOnSurfaceVariant,
          letterSpacing = 0.3.sp
        )
        Text(
          text = uiState.selectedSafeZone?.name ?: "No safe zone selected",
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          color = TacticalOnSurface,
          lineHeight = 18.sp,
          maxLines = 2,
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
            RouteStatus.IDLE -> "No route"
            RouteStatus.REQUESTING -> if (uiState.isCalculatingAlternatives)
              "Checking alternatives…" else "Finding route…"
            RouteStatus.RECEIVED -> "Checking hazard overlap…"
            RouteStatus.VALIDATING_HAZARDS -> "Checking hazard overlap…"
            RouteStatus.READY -> "Real roads verified"
            RouteStatus.NO_ROUTE -> "No route found"
            RouteStatus.NETWORK_ERROR -> "Router unreachable"
            RouteStatus.FALLBACK_UNVERIFIED -> "Unverified estimate"
          },
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
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
      // B11 honesty: an offline corridor's time comes from ASSUMED average
      // speeds (mode.estimateSpeedMps), not measured road data. It must never
      // read as a live ETA: label switches and the value carries a "~".
      // P1 ETA honesty: offline corridors use ASSUMED average speeds; even
      // live OSRM durations are MODELLED road speeds, never live traffic.
      // No time may read as a guarantee: "~" always + EST. label.
      RouteMetric(
        label = if (route != null && !route.isLiveOsrm) "TIME (EST.)" else "ETA (EST.)",
        value = route?.let {
          "~" + OsrmRoutingService.formatDuration(it.durationSeconds)
        } ?: "--",
        accent = if (route != null && !route.isLiveOsrm) WarningAmber else NeonEmerald
      )
      RouteMetric(
        label = "MODE",
        value = if (uiState.travelMode == com.example.data.routing.TravelMode.DRIVING) "VEHICLE" else "WALKING",
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
        // Ordering matters: a corridor that WAS checked against hazard
        // geometry (it carries computed warnings / a computed status) keeps
        // its verdict even if the current hazard list moved on; "not
        // assessed" applies only to corridors with nothing to report.
        val noHazardsToCheck = uiState.hazardZones.isEmpty() &&
          route.hazardWarnings.isEmpty() &&
          route.routeSafetyStatus == RouteSafetyStatus.SAFE &&
          uiState.routeStatus != RouteStatus.FALLBACK_UNVERIFIED
        Text(
          text = when {
            // DATA INSUFFICIENT is a first-class state: a "safe" verdict
            // requires hazard geometry to check the corridor AGAINST.
            noHazardsToCheck -> "Route safety: not assessed (no hazard data on the map)"
            uiState.routeStatus == RouteStatus.FALLBACK_UNVERIFIED ->
              "Route safety: unverified estimate"
            route.routeSafetyStatus == RouteSafetyStatus.SAFE &&
              route.hazardWarnings.isNotEmpty() ->
              "Route safety: ${route.routeSafetyStatus.label}"
            route.routeSafetyStatus == RouteSafetyStatus.SAFE ->
              "Route safety: no hazard intersection detected"
            else -> "Route safety: ${route.routeSafetyStatus.label}"
          },
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = when {
            noHazardsToCheck || uiState.routeStatus == RouteStatus.FALLBACK_UNVERIFIED ->
              TacticalOnSurfaceVariant
            else -> when (route.routeSafetyStatus) {
              RouteSafetyStatus.SAFE -> NeonEmerald
              RouteSafetyStatus.CAUTION -> WarningAmber
              RouteSafetyStatus.DANGER -> EmergencyRedBright
            }
          },
          lineHeight = 16.sp,
          modifier = Modifier.testTag("route_safety_state")
        )
        // PHASE 5: the danger statement is built from the ROUTE'S OWN
        // warnings (real geometry vs real circles) - what it crosses, how
        // much, and whether it re-enters. No generic static sentence.
        val crossingWarnings = route.hazardWarnings.filter { it.isBlocking }
        if (crossingWarnings.isNotEmpty()) {
          val crossings = com.example.data.routing.HazardRoutingPolicy
            .hazardCrossingRuns(route.pathPoints, uiState.hazardZones)
          val names = crossingWarnings.joinToString(", ") { it.hazardName }
          Text(
            text = if (crossings > 1)
              "This corridor crosses $crossings hazard zones on its way: $names. Choose another route or wait for official guidance."
            else
              "This corridor enters a hazard area ($names). Prefer a route that avoids it.",
            fontSize = 11.sp,
            color = EmergencyRedBright,
            lineHeight = 15.sp,
            modifier = Modifier.testTag("route_danger_advice")
          )
        }
      }

      // STAGE 5 — closure/traffic honesty: a READY road route is OSRM geometry
      // checked against known hazard geometry only. There is no road-closure
      // feed and no live-traffic feed, so the limitation is stated on the card
      // itself (the fallback estimate already carries its own disclaimer).
      if (uiState.routeStatus == RouteStatus.READY ||
        uiState.routeStatus == RouteStatus.FALLBACK_UNVERIFIED
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianContainer.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("route_closure_disclaimer"),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = "ROUTE STATUS", fontSize = 10.sp, fontWeight = FontWeight.Black,
            color = TacticalOnSurfaceVariant, letterSpacing = 0.5.sp
          )
          RouteStatusRow("Road closure data", "Unavailable")
          RouteStatusRow("Live traffic", "Unavailable")
          RouteStatusRow(
            "Route source",
            if (route.isLiveOsrm) "OSRM verified roads" else "Offline estimate (not roads)"
          )
          Text(
            text = "Road conditions may have changed. Verify locally before evacuation.",
            fontSize = 11.sp, color = WarningAmber, lineHeight = 14.sp
          )
        }
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
      // P1 layout fix: this controls row crushed on narrow phones
      // (the broken "Best" button). Horizontally scrollable now - every
      // control keeps its natural single-line size.
      Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
            .background(if (uiState.travelMode == com.example.data.routing.TravelMode.FOOT) NeonEmerald else Color.Transparent)
            .clickable { onSetTravelMode(com.example.data.routing.TravelMode.FOOT) }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("mode_walking_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
            contentDescription = "Walking",
            tint = if (uiState.travelMode == com.example.data.routing.TravelMode.FOOT) OnNeonEmerald else TacticalOnSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (uiState.travelMode == com.example.data.routing.TravelMode.DRIVING) NeonEmerald else Color.Transparent)
            .clickable { onSetTravelMode(com.example.data.routing.TravelMode.DRIVING) }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("mode_driving_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = "Driving",
            tint = if (uiState.travelMode == com.example.data.routing.TravelMode.DRIVING) OnNeonEmerald else TacticalOnSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      // P1 route UX: a clickable "Alternatives (0)" must never render -
      // loading says so, an empty completed check states it honestly, and
      // the count only appears when it is real.
      val extraAlts = uiState.alternativeRoutes.count { r ->
        route == null || r.routeId != route.routeId
      }
      when {
        uiState.isCalculatingAlternatives -> Box(
          modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianContainerHigh)
            .padding(horizontal = 12.dp, vertical = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          Text("Checking alternatives…", fontSize = 12.sp,
            fontWeight = FontWeight.Medium, color = TacticalOnSurfaceVariant)
        }
        uiState.alternativesChecked && extraAlts == 0 -> Box(
          modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianContainer)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("no_alternative_message"),
          contentAlignment = Alignment.Center
        ) {
          Text("No alternative route available", fontSize = 12.sp,
            fontWeight = FontWeight.Medium, color = TacticalOnSurfaceVariant)
        }
        else -> Button(
          onClick = onLoadAlternativeRoutes,
          colors = ButtonDefaults.buttonColors(
            containerColor = ObsidianContainerHigh,
            contentColor = TacticalCyan
          ),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.heightIn(min = 44.dp).testTag("alternative_routes_button")
        ) {
          Icon(Icons.Default.AltRoute, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(if (extraAlts > 0) "Alternatives (${extraAlts + 1})" else "Find alternatives",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
      }

    }
    } // end of showDetails (controls + alternatives)

    // Alternative corridor chips (when computed). TAPPING one swaps it in as
    // the active route (the chips used to be inert — the "Alternatives
    // doesn't work" report). Alternatives render as grey ghost lines on the
    // map; the active corridor stays green.
    if (showDetails && uiState.alternativeRoutes.size > 1) {
      // SHORTEST / SAFEST badges: the alternatives are OSRM road corridors
      // already safety-scored against the hazard picture; label each option
      // by what it optimises so the user chooses distance vs safety.
      val alts = uiState.alternativeRoutes
      val minDistance = alts.minOf { it.distanceMeters }
      val maxSafety = alts.maxOf { it.routeSafetyScore }
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(alts.size) { idx ->
          val alt = alts[idx]
          // Logical identity, not object identity: state copies create
          // equal-but-distinct RouteResult instances.
          val isPrimary = route != null && alt.routeId == route.routeId
          val shortest = alt.distanceMeters <= minDistance + 1.0
          val safest = alt.routeSafetyScore >= maxSafety
          val optimiser = when {
            shortest && safest -> "SHORTEST + SAFEST"
            shortest -> "SHORTEST"
            safest -> "SAFEST"
            else -> null
          }
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
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              optimiser?.let { label ->
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (label.startsWith("SAFEST")) WarningAmber else TacticalCyan)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                  Text(label, fontSize = 9.sp, fontWeight = FontWeight.Black,
                    color = ObsidianContainerLowest, maxLines = 1)
                }
              }
              Text(
                text = (if (isPrimary) "" else "USE: ") + "${alt.summary} • ${OsrmRoutingService.formatDistance(alt.distanceMeters)} • ${alt.routeSafetyStatus.label}",
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
}

@Composable
private fun RouteStatusRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, fontSize = 11.sp, color = TacticalOnSurfaceVariant)
    Text(
      value, fontSize = 11.sp, fontWeight = FontWeight.Bold,
      color = if (value == "Unavailable") TacticalOnSurfaceVariant.copy(alpha = 0.8f)
      else TacticalOnSurface,
      maxLines = 2, modifier = Modifier.padding(start = 12.dp)
    )
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
