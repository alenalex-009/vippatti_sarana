package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SafeZone
import com.example.data.routing.OsrmRoutingService
import com.example.data.shelters.SafeZoneEvaluation
import com.example.data.shelters.SafeZoneEvaluator
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState
import kotlin.math.roundToInt

// ============================================================================
// HORIZONTAL SAFE-ZONE CAROUSEL — swipe; ~2 cards visible, next one peeks
// ============================================================================

/**
 * Horizontal card carousel of ALL India-network safe zones. Tapping a card SELECTS
 * that shelter as the evacuation destination — the ViewModel re-runs the
 * OSRM route to it and every downstream metric (distance, ETA, capacity,
 * route safety, hazard warnings, guidance) follows the selection.
 */
@Composable
internal fun SafeZoneCarousel(
  uiState: VippattiUiState,
  onSelectSafeZone: (SafeZone) -> Unit
) {
  val listState = rememberLazyListState()
  val selectedId = uiState.selectedSafeZone?.id

  // Keep the selected card visible in the carousel when selection changes
  // (map circle tap, "Best Zone" button, or initial recommendation).
  // NEARBY-FIRST (user rule #5): the rendered set is eval-driven and
  // sorted by LIVE distance from the user's current position - never the
  // raw network list, so another state's shelters can't appear at 0 m.
  val evalById = uiState.evaluatedShelters.associateBy { it.zone.id }
  val origin = uiState.userLocation
  val carouselZones: List<SafeZone> = run {
    // ONE source of truth: the LOCATION-SCOPED shelter set that the map
    // markers and the evaluator use. Registry records are REAL (always
    // allowed); demo shelters exist only while demo data is on. A card
    // here is always the exact object routing goes to (799m vs 14.8km bug).
    val source = uiState.scopedShelters
    source.sortedBy {
      evalById[it.id]?.distanceMeters
        ?: com.example.data.model.GeoMath.distanceMeters(origin, it.point)
    }.take(MAX_CAROUSEL_ZONES)
  }
  val hiddenCount = uiState.scopedShelters.size -
    carouselZones.size
  val selectedIndex = carouselZones.indexOfFirst { it.id == selectedId }
  LaunchedEffect(selectedId) {
    if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    // Shortest-distance read-out (radar requirement 3): nearest FEASIBLE safe
    // zone from the user's current position, recomputed on every evaluation.
    val nearest = carouselZones.mapNotNull { evalById[it.id] }
      .filter { it.isFeasible }.minByOrNull { it.distanceMeters }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "SAFE ZONES — TAP TO ROUTE",
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = TacticalOnSurfaceVariant,
        letterSpacing = 0.6.sp
      )
      Icon(
        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
        contentDescription = null,
        tint = TacticalOnSurfaceVariant,
        modifier = Modifier.size(12.dp)
      )
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = if (!uiState.isMockDataVisible) {
          "SIMULATED HIDDEN — switch SIMULATED DEMO on for the India demo network"
        } else if (nearest != null) {
          "NEAREST SAFE: ${nearest.zone.name} • " +
            "${OsrmRoutingService.formatDistance(nearest.distanceMeters)} away"
        } else if (carouselZones.isEmpty()) {
          "NO SHELTERS NEAR THIS PLACE — search a different area"
        } else {
          "NO FEASIBLE SHELTER — all in danger / full"
        },
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (nearest != null) NeonEmerald else WarningAmber,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
    }

    // Mock OFF = empty carousel (matches the empty map); no shelter cards.
    val visibleZones = carouselZones
    LazyRow(
      state = listState,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("safe_zone_carousel"),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(visibleZones, key = { it.id }) { zone ->
        val isSelected = zone.id == selectedId
        SafeZoneCard(
          zone = zone,
          // Full evaluation (feasible AND rejected) — rejected shelters show
          // their real rejection reason instead of a blank generic card.
          evaluation = evalById[zone.id],
          activeRouteHere = uiState.activeRoute?.takeIf { route ->
            route.destinationId == zone.id ||
            (route.destinationId.isBlank() && route.destinationName == zone.name)
          },
          userLocation = origin,
          isSelected = isSelected,
          isCalculatingRoute = uiState.isCalculatingRoute && isSelected,
          onSelect = { onSelectSafeZone(zone) },
          // Responsive carousel card: ~72% of the viewport width so the next
          // card always peeks, clamped to a sensible max on tablets.
          modifier = Modifier
            .fillParentMaxWidth(0.72f)
            .widthIn(max = 280.dp)
        )
      }
    }
    if (hiddenCount > 0) {
      Text(
        text = "$hiddenCount more shelters are farther away — nearest " +
          MAX_CAROUSEL_ZONES + " shown. Search another place to re-center them.",
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp)
      )
    }
  }
}

private const val MAX_CAROUSEL_ZONES = 8

/**
 * One safe-zone card: name, safety badge, distance/ETA, capacity bar with
 * available/total, occupancy %, facilities and a route action.
 */
@Composable
private fun SafeZoneCard(
  zone: SafeZone,
  evaluation: SafeZoneEvaluation?,
  isSelected: Boolean,
  isCalculatingRoute: Boolean,
  activeRouteHere: com.example.data.routing.RouteResult? = null,
  userLocation: com.example.data.routing.GeoPoint,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier
) {
  val capacity = evaluation?.capacityReport
  val occupancyRatio = if (zone.capacityTotal > 0) {
    (zone.capacityCurrent.toFloat() / zone.capacityTotal).coerceIn(0f, 1f)
  } else 1f
  val isFull = capacity?.acceptsNewOccupants == false || zone.availableCapacity <= 0
  // Live distance from the USER: the evaluation carries it when present;
  // otherwise straight-line from the current position (never 0 by default).
  val distanceKm = evaluation?.distanceMeters
    ?: com.example.data.model.GeoMath.distanceMeters(
      userLocation, com.example.data.routing.GeoPoint(zone.lat, zone.lon)
    )
  val etaMins = SafeZoneEvaluator.estimateTravelMinutes(distanceKm, 1.35)

  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(if (isSelected) ObsidianContainerHigh else ObsidianContainer)
      .border(
        1.5.dp,
        when {
          isSelected -> NeonEmerald
          isFull -> EmergencyRed.copy(alpha = 0.5f)
          else -> TacticalOutlineVariant.copy(alpha = 0.4f)
        },
        RoundedCornerShape(14.dp)
      )
      .clickable(onClick = onSelect)
      .padding(10.dp)
      .testTag("safe_zone_card_${zone.id}"),
    verticalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    // Card header: name + selected check.
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      Text(
        text = zone.name,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = TacticalOnSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 14.sp,
        modifier = Modifier.weight(1f, fill = false)
      )
      if (isSelected) {
        Icon(
          imageVector = if (isCalculatingRoute) Icons.Default.Navigation else Icons.Default.Check,
          contentDescription = null,
          tint = if (isCalculatingRoute) WarningAmber else NeonEmerald,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    // Location note + rank badge / rejection reason.
    Text(
      text = zone.locationNote,
      fontSize = 11.sp,
      color = TacticalOnSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    // Feasibility badge: ranked score or the rejection reason.
    if (evaluation != null) {
      if (evaluation.isFeasible) {
        Text(
          text = "MATCH ${evaluation.score}/100 • ${evaluation.capacityReport.statusLabel.uppercase()}",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = if (isFull) EmergencyRedBright else NeonEmerald,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = EmergencyRedBright,
            modifier = Modifier.size(11.dp)
          )
          Text(
            text = evaluation.rejectionReason?.label ?: "Not recommended",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = EmergencyRedBright,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    // Distance / walking ETA row.
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = OsrmRoutingService.formatDistance(distanceKm),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalCyan
      )
      // When a real road route to THIS shelter exists, show its measured
      // duration; the walk figure is always labelled as an estimate.
      Text(
        text = if (activeRouteHere != null && activeRouteHere.isLiveOsrm) {
          "road route ~${OsrmRoutingService.formatDuration(activeRouteHere.durationSeconds)}"
        } else {
          "~${etaMins} min walk (est.)"
        },
        fontSize = 10.sp,
        color = if (activeRouteHere?.isLiveOsrm == true) NeonEmerald else TacticalOnSurfaceVariant
      )
      Spacer(modifier = Modifier.weight(1f))
      if (evaluation?.hazardExposureCount ?: 0 > 0) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = WarningAmber,
          modifier = Modifier.size(12.dp)
        )
      }
    }

    // Capacity bar: available/total + occupancy %.
    LinearProgressIndicator(
      progress = { occupancyRatio },
      modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
        .clip(RoundedCornerShape(2.dp)),
      color = if (isFull) EmergencyRed else NeonEmerald,
      trackColor = ObsidianContainerHigh
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "${(occupancyRatio * 100).roundToInt()}% full • ${zone.availableCapacity}/${zone.capacityTotal} spots free",
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        maxLines = 1
      )
      Text(
        text = if (isFull) "FULL" else "OPEN",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (isFull) EmergencyRedBright else NeonEmerald
      )
    }

    // Facilities strip (compact chips).
    Text(
      text = listOfNotNull(
        "Water".takeIf { zone.waterAvailable },
        "Food".takeIf { zone.foodAvailable },
        "Power".takeIf { zone.electricityAvailable },
        "Medical".takeIf { zone.medicalSupport },
        "Sanitation".takeIf { zone.sanitationAvailable },
        "Women & children".takeIf { zone.womenChildrenSuitability }
      ).joinToString(" • ").ifEmpty { "No resource flags set" },
      fontSize = 11.sp,
      color = TacticalOnSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      lineHeight = 12.sp
    )

    // Why this zone (top ranking reason) — only for feasible cards.
    if (evaluation != null && evaluation.isFeasible) {
      Text(
        text = evaluation.rankExplanation,
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 11.sp
      )
    }

    // Route action.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(
          if (isSelected) NeonEmerald.copy(alpha = 0.18f)
          else if (isFull) ObsidianContainerHigh
          else NeonEmeraldContainer.copy(alpha = 0.25f)
        )
        .border(
          1.dp,
          if (isSelected) NeonEmerald else TacticalOutlineVariant.copy(alpha = 0.4f),
          RoundedCornerShape(8.dp)
        )
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = when {
          isCalculatingRoute -> "Calculating OSRM route..."
          isSelected -> "Routing to this zone"
          isFull -> "Full — pick another zone"
          else -> "Set as destination"
        },
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (isSelected) NeonEmerald else TacticalOnSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false)
      )
      Icon(
        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Navigation,
        contentDescription = null,
        tint = if (isSelected) NeonEmerald else TacticalOnSurfaceVariant,
        modifier = Modifier.size(13.dp)
      )
    }
  }
}
