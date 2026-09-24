package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.disaster.WeatherMetrics
import com.example.data.disaster.DisasterCachePolicy
import com.example.data.disaster.DisasterEvent
import com.example.data.disaster.DisasterLayer
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.IncidentCategory
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.data.routing.OsrmRoutingService
import com.example.data.routing.RouteSafetyStatus
import com.example.data.risk.RiskLevel
import com.example.data.shelters.SafeZoneEvaluation
import com.example.data.shelters.SafeZoneEvaluator
import com.example.ui.components.DisasterEventDetailDialog
import com.example.ui.components.OsmDroidRadarMapView
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.LocalVippattiColors
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnEmergencyRedContainer
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * RADAR MAP — mobile-first map decision screen.
 *
 * Info hierarchy (tactical identity preserved, responsive layout):
 *  1. FULL-BLEED MAP — large pulsing hazard/safe-zone circles, OSRM route
 *     polyline. The map stays fully draggable/zoomable under the sheet.
 *  2. Floating PERSONAL RISK strip (RED/ORANGE/YELLOW/GREEN + explanation)
 *     and the SOS broadcast button.
 *  3. COLLAPSIBLE BOTTOM SHEET:
 *       COLLAPSED -> drag handle + current destination one-liner (compact)
 *       EXPANDED  -> WHAT SHOULD I DO? -> horizontal safe-zone carousel ->
 *                   compact weather row -> route intelligence + navigation.
 *  4. Live turn-by-turn HUD pinned under the risk strip while guidance runs,
 *     always tied to the SELECTED destination.
 */
@Composable
fun RadarMapScreen(
  uiState: VippattiUiState,
  onSelectBestSafeZone: () -> Unit,
  onSelectSafeZone: (SafeZone) -> Unit,
  onSetTravelMode: (String) -> Unit,
  onStartEvacuation: () -> Unit,
  onStopEvacuation: () -> Unit,
  onNextNavigationStep: () -> Unit,
  onLoadAlternativeRoutes: () -> Unit,
  onOpenSensorBroadcast: () -> Unit,
  onClearRoute: () -> Unit,
  onRealGpsFix: (latitude: Double, longitude: Double) -> Unit,
  onOpenHazardDetail: (com.example.data.model.HazardZone) -> Unit,
  onOpenSafeZoneDetail: (SafeZone) -> Unit,
  // --- REAL disaster-data integration (audit items 3/5/6/7) ---
  onToggleLayer: (DisasterLayer) -> Unit,
  onOpenIncidentReport: () -> Unit,
  onOpenDisasterEventDetail: (DisasterEvent) -> Unit,
  /** Opens the HISTORICAL (EM-DAT) record sheet for a tapped archive marker. */
  onOpenHistoricalEventDetail: (com.example.data.historical.HistoricalDisasterEvent) -> Unit = {},
  onToggleMockData: () -> Unit = {},
  /** Explicit opt-in for the unverified offline straight-line estimate. */
  onRequestFallbackRoute: () -> Unit = {},
  /** Retry the live Open-Meteo weather reading after a stale/failed attempt. */
  onRetryWeather: () -> Unit = {},
  // --- EMERGENCY GUIDANCE (nearest safe zone + terrain haven) ---
  onGuidanceGo: () -> Unit = {},
  onGuidanceDismiss: () -> Unit = {},
  onSearchTerrainHaven: () -> Unit = {},
  onRouteToTerrainHaven: () -> Unit = {},
  /** "Is MY spot a red zone?" — available on the map too (lives on Home as well). */
  onAssessTerrain: () -> Unit = {},
  onDismissTerrainAssessment: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  // The decision stack (risk -> safe zones -> weather -> route) opens
  // EXPANDED: production testing showed a collapsed sheet reads as "the
  // features are gone". Calm comes from the light map + plain chips, not
  // from hiding the tools.
  var isSheetExpanded by remember { mutableStateOf(true) }
  val sheetPeekHeight = 88.dp

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianSurface)
  ) {
    // Responsive sheet sizing — 68% of the available height on tall screens,
    // but never larger than the screen and never smaller than a usable peek.
    //
    // LANDSCAPE CRASH FIX: `coerceIn(min, max)` throws IllegalArgumentException
    // when min > max. In landscape (and with a large font scale) `maxHeight` can
    // be smaller than the 320.dp floor, so the floor is clamped to the ceiling
    // BEFORE coercing instead of assuming the phone is taller than 320.dp.
    val sheetCeiling = maxHeight * 0.92f
    val sheetFloor = minOf(320.dp, sheetCeiling)
    val sheetExpandedHeight = (maxHeight * 0.68f).coerceIn(sheetFloor, sheetCeiling)
    val collapsedSheetHeight = minOf(sheetPeekHeight, sheetCeiling)
    val animatedSheetHeight by animateDpAsState(
      targetValue = if (isSheetExpanded) sheetExpandedHeight else collapsedSheetHeight,
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
      ),
      label = "radar_sheet_height"
    )

    // 1. FULL-BLEED MAP — the single OSMDroid engine (draggable, zoomable).
    //    The floating overlay heights are MEASURED (not assumed) so the map's
    //    control stack and attribution banner reposition themselves cleanly
    //    on every screen size, density and font scale.
    val density = LocalDensity.current
    var topOverlayHeightPx by remember { mutableStateOf(0) }
    val topOverlayPadding = (topOverlayHeightPx / density.density).dp

    // Mock toggle OFF = FULLY EMPTY map: no hazard circles, no safe circles
    // and no live event markers reach the engine — only base tiles + GPS dot.
    OsmDroidRadarMapView(
      hazardZones = uiState.hazardZones,
      safeZones = uiState.fieldShelters +
        (if (uiState.isMockDataVisible) uiState.safeZones else emptyList()),
      selectedSafeZone = uiState.selectedSafeZone,
      activeRoute = uiState.activeRoute,
      travelMode = uiState.travelMode,
      onClearRoute = onClearRoute,
      onHazardZoneTapped = onOpenHazardDetail,
      onSafeZoneTapped = onOpenSafeZoneDetail,
      onRealGpsFix = onRealGpsFix,
      // LIVE provider events are always rendered: the simulated-demo switch only
      // controls the SIMULATED zone network, never real hazard data.
      disasterEvents = uiState.disasterEvents,
      enabledLayers = uiState.enabledLayers,
      onDisasterEventTapped = onOpenDisasterEventDetail,
      // NEARBY-FIRST: fold distant data while the camera is at city scale.
      focusPoint = if (uiState.isUserLocationFallback) null
      else GeoPoint(uiState.userLocation.lat, uiState.userLocation.lon),
      // HISTORICAL (EM-DAT): only when the operator enables the layer, and only
      // records with the dataset's own coordinates. Never a current hazard.
      historicalEvents = uiState.historicalMappableEvents,
      onHistoricalEventTapped = onOpenHistoricalEventDetail,
      modifier = Modifier.fillMaxSize(),
      topOverlayPadding = topOverlayPadding + 8.dp,
      bottomOverlayPadding = animatedSheetHeight
    )

    // 2. Floating top strip: personal risk + SOS broadcast.
    //    Structured as a Column flow (not absolute offsets) so the map's
    //    right-edge control stack always starts BELOW the strip — no overlap
    //    at any width/density, and the strip wraps instead of being clipped.
    Column(
      modifier = Modifier
        .align(Alignment.TopStart)
        .fillMaxWidth()
        .onSizeChanged { size -> topOverlayHeightPx = size.height }
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        PersonalRiskStrip(
          risk = uiState.personalRisk,
          isFallbackLocation = uiState.isUserLocationFallback,
          modifier = Modifier.weight(1f)
        )
        IconButton(
          onClick = onOpenSensorBroadcast,
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(EmergencyRed.copy(alpha = 0.9f))
            .testTag("mesh_sensor_broadcast_button")
        ) {
          Icon(
            imageVector = Icons.Default.Sensors,
            contentDescription = "Emergency SOS Broadcast",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      // 2b. DATA STATUS + LAYER TOGGLES + REPORT INCIDENT — one compact
      //      horizontally scrollable chip row; never covers the whole map.
      DisasterStatusLayerRow(
        uiState = uiState,
        onToggleLayer = onToggleLayer,
        onToggleMockData = onToggleMockData
      )

      // 2c. DISASTER-COLOR LEGEND — keys each zone color to its disaster
      //     type. Auto-hides with the empty map (no zones -> no legend).
      DisasterTypeLegend(types = uiState.hazardZones.map { it.type }.distinct())

      // 2d. EMERGENCY GUIDANCE — "disaster near you: where do I go?" card.
      //     Derived purely from risk + evaluated shelters; terrain haven
      //     results render inside the same card with a DERIVED label.
      EmergencyGuidanceCard(
        guidance = uiState.emergencyGuidance,
        haven = uiState.terrainHaven,
        isSearchingHaven = uiState.isSearchingHaven,
        onGo = onGuidanceGo,
        onSearchHaven = onSearchTerrainHaven,
        onRouteToHaven = onRouteToTerrainHaven,
        onDismiss = onGuidanceDismiss,
        modifier = Modifier.padding(top = 6.dp)
      )

      // 2e. TERRAIN SELF-ASSESSMENT — "is MY spot a red zone?" also lives on
      //     the map (it is on Home too): removing it read as "feature gone".
      TerrainSelfAssessmentChip(
        assessment = uiState.terrainSelfAssessment,
        isAssessing = uiState.isAssessingTerrain,
        onAssess = onAssessTerrain,
        onDismiss = onDismissTerrainAssessment,
        modifier = Modifier.padding(top = 4.dp)
      )


      // 3. Live turn-by-turn HUD — directly under the risk strip while
      //    guidance is active, always following the SELECTED destination's
      //    route. Flows below the strip (no hardcoded top offset).
      AnimatedVisibility(visible = uiState.isNavigatingLive) {
        LiveNavigationHud(
          uiState = uiState,
          onNextNavigationStep = onNextNavigationStep,
          onStopEvacuation = onStopEvacuation
        )
      }
    }

    // 4. COLLAPSIBLE BOTTOM SHEET — tap handle or flick to collapse/expand;
    //    the map underneath stays fully interactive. The drag gesture lives
    //    ONLY on the grabber strip so the scrollable expanded content keeps
    //    normal vertical scrolling.
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(animatedSheetHeight)
        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        .background(ObsidianContainerLowest.copy(alpha = 0.98f))
        .border(
          1.dp,
          TacticalOutlineVariant.copy(alpha = 0.5f),
          RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        )
    ) {
      // Grabber strip — tap toggles; flick up/down expands/collapses.
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(28.dp)
          .clickable { isSheetExpanded = !isSheetExpanded }
          .pointerInput(Unit) {
            detectVerticalDragGestures(
              onVerticalDrag = { _, dragAmount ->
                // Flick DOWN to collapse / UP to expand — dead-zone avoids
                // accidental state flips from micro-drags.
                if (abs(dragAmount) > 2f) isSheetExpanded = dragAmount < 0
              }
            )
          }
          .testTag("radar_sheet_handle"),
        contentAlignment = Alignment.Center
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .width(42.dp)
              .height(4.dp)
              .clip(CircleShape)
              .background(TacticalOutlineVariant.copy(alpha = 0.8f))
          )
          Icon(
            imageVector = if (isSheetExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (isSheetExpanded) "Collapse panel" else "Expand panel",
            tint = TacticalOnSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      // COLLAPSED payload: handle + current destination one-liner (compact).
      if (!isSheetExpanded) {
        CollapsedSheetContent(uiState = uiState)
      }

      // EXPANDED payload: What-should-I-do -> carousel -> weather -> route.
      if (isSheetExpanded) {
        ExpandedSheetContent(
          uiState = uiState,
          onSelectBestSafeZone = onSelectBestSafeZone,
          onSelectSafeZone = onSelectSafeZone,
          onSetTravelMode = onSetTravelMode,
          onStartEvacuation = onStartEvacuation,
          onStopEvacuation = onStopEvacuation,
          onLoadAlternativeRoutes = onLoadAlternativeRoutes,
          onOpenIncidentReport = onOpenIncidentReport,
          onRequestFallbackRoute = onRequestFallbackRoute,
          onRetryWeather = onRetryWeather,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}
