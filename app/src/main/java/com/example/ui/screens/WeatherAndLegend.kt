package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import com.example.data.disaster.WeatherMetrics
import com.example.data.disaster.DisasterLayer
import com.example.data.model.DataStatus
import com.example.ui.components.StatusBadge
import com.example.ui.components.dataStatusColor
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState

// ============================================================================
// COMPACT WEATHER ROW — LIVE temp / rainfall / wind / 3-hr trend in one line
// (Open-Meteo, keyless). The tag reads the shared data status: LIVE only for a
// reading fetched in this session, STALE when a refresh failed but a real
// earlier reading is still shown, NO FEED when there is nothing to show.
// ============================================================================

@Composable
internal fun CompactWeatherRow(weather: WeatherMetrics, status: DataStatus) {
  val isLive = status == DataStatus.SUCCESS
  val isStale = status == DataStatus.STALE
  val tagColor = dataStatusColor(status)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerLow.copy(alpha = 0.96f))
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Live-source tag: derived from the weather data status, never from whether
    // the row happens to have text in it.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = when {
          isLive -> "LIVE"
          isStale -> "STALE"
          else -> "NO"
        },
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = tagColor,
        letterSpacing = 0.5.sp
      )
      Text(
        text = if (isLive || isStale) "WX" else "FEED",
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = tagColor,
        letterSpacing = 0.5.sp
      )
    }
    WeatherCell(
      icon = Icons.Default.Thermostat,
      label = "TEMP",
      value = weather.currentTemp,
      tint = TacticalCyan,
      modifier = Modifier.weight(1f)
    )
    WeatherCell(
      icon = Icons.Default.Opacity,
      label = "RAIN",
      value = weather.rainfallIntensity,
      tint = WarningAmber,
      modifier = Modifier.weight(1f)
    )
    WeatherCell(
      icon = Icons.Default.Air,
      label = "WIND",
      value = weather.windGust,
      tint = TacticalCyan,
      modifier = Modifier.weight(1f)
    )
    // 3-HR TREND — live Open-Meteo delta (Warming / Cooling / Steady).
    val hasRealTrend = weather.trend3h.isNotBlank()
    Row(
      modifier = Modifier.weight(1.2f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      if (hasRealTrend) {
        val isCooling = weather.trend3h.contains("Cooling", ignoreCase = true)
        val isSteady = weather.trend3h.contains("Steady", ignoreCase = true)
        Icon(
          imageVector = when {
            isSteady -> Icons.Default.TrendingUp
            isCooling -> Icons.Default.TrendingDown
            else -> Icons.Default.TrendingUp
          },
          contentDescription = null,
          tint = when {
            isSteady -> TacticalOnSurfaceVariant
            isCooling -> TacticalCyan
            else -> WarningAmber
          },
          modifier = Modifier.size(15.dp)
        )
      }
      Column {
        Text(
          text = "3-HR TREND",
          fontSize = 10.sp,
          fontWeight = FontWeight.Black,
          color = TacticalOnSurfaceVariant,
          letterSpacing = 0.5.sp
        )
        Text(
          text = if (hasRealTrend) weather.trend3h else "No live trend data",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (hasRealTrend) TacticalOnSurface else TacticalOnSurfaceVariant,
          maxLines = 1
        )
      }
    }
  }
}

// ============================================================================
// DATA STATUS + LAYER TOGGLES + REPORT INCIDENT — compact single chip row.
// The SIMULATED DEMO chip switches only the labelled demo network; every
// other toggle changes real map visibility.
// ============================================================================

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun DisasterStatusLayerRow(
  uiState: VippattiUiState,
  onToggleLayer: (DisasterLayer) -> Unit,
  onToggleMockData: () -> Unit = {}
) {
  // PROGRESSIVE DISCLOSURE (user map-redesign rule): the strip shows only
  // two compact pills - Layers (with the active count) and data status.
  // Tapping a pill expands its detail in place; nothing is removed, and the
  // map stays visually dominant. Demo ON/OFF stays one tap away because it
  // is the scenario switch, not a data layer.
  val providerStatuses = uiState.providerStatuses
  val aggregateStatus = when {
    uiState.isDisasterDataLive -> DataStatus.SUCCESS
    providerStatuses.any { (_, status) -> status == DataStatus.ERROR } -> DataStatus.ERROR
    providerStatuses.any { (_, status) -> status == DataStatus.STALE } -> DataStatus.STALE
    else -> DataStatus.EMPTY
  }
  val statusColor = dataStatusColor(aggregateStatus)
  val liveCount = providerStatuses.count { (_, status) ->
    status == DataStatus.SUCCESS || status == DataStatus.STALE
  }
  val hazardLayers = listOf(
    DisasterLayer.EARTHQUAKES to "Quakes",
    DisasterLayer.ACTIVE_FIRES to "Fires",
    DisasterLayer.OFFICIAL_ALERTS to "Alerts",
    DisasterLayer.USER_REPORTS to "My Reports",
    DisasterLayer.SAFE_ZONES to "Shelters"
  )
  val activeLayerCount = hazardLayers.count { (layer, _) -> layer in uiState.enabledLayers }

  var layersExpanded by remember { mutableStateOf(false) }
  var statusExpanded by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Layers pill - shows the active count; expands to real toggles.
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(if (layersExpanded) NeonEmeraldContainer.copy(alpha = 0.3f) else ObsidianContainer)
          .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
          .clickable { layersExpanded = !layersExpanded }
          .padding(horizontal = 10.dp, vertical = 5.dp)
          .testTag("map_layers_pill")
      ) {
        Text(
          text = "Layers \u00b7 " + activeLayerCount,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurface
        )
      }
      // Data status pill - one honest word; tap expands per-source detail.
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(if (statusExpanded) ObsidianContainerHigh else ObsidianContainer)
          .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
          .clickable { statusExpanded = !statusExpanded }
          .padding(horizontal = 10.dp, vertical = 5.dp)
          .testTag("disaster_data_status_chip")
      ) {
        Text(
          text = when (aggregateStatus) {
            DataStatus.SUCCESS -> "Live " + liveCount + "/" + providerStatuses.size
            DataStatus.ERROR -> "Data issue"
            DataStatus.STALE -> "Cached data"
            else -> "No data yet"
          },
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = statusColor
        )
      }
      Spacer(Modifier.weight(1f))
      // Demo switch stays visible (scenario control, not a data layer).
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(if (uiState.isMockDataVisible) WarningAmber else ObsidianContainerHigh)
          .border(
            1.5.dp,
            if (uiState.isMockDataVisible) ObsidianContainerLowest else TacticalCyan,
            RoundedCornerShape(999.dp)
          )
          .clickable { onToggleMockData() }
          .padding(horizontal = 12.dp, vertical = 7.dp)
          .testTag("mock_data_toggle_chip")
      ) {
        Text(
          text = if (uiState.isMockDataVisible) "DEMO: ON" else "DEMO: OFF",
          fontSize = 11.sp,
          fontWeight = FontWeight.Black,
          color = if (uiState.isMockDataVisible) ObsidianContainerLowest else TacticalOnSurface
        )
      }
    }

    // EXPANDED LAYERS: the real toggles, only while the pill is open.
    AnimatedVisibility(visible = layersExpanded) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        hazardLayers.forEach { (layer, label) ->
          val enabled = layer in uiState.enabledLayers
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(999.dp))
              .background(
                if (enabled) NeonEmeraldContainer.copy(alpha = 0.25f) else ObsidianContainer
              )
              .border(
                1.dp,
                if (enabled) NeonEmerald.copy(alpha = 0.6f)
                else TacticalOutlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(999.dp)
              )
              .clickable { onToggleLayer(layer) }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("layer_toggle_" + layer.name.lowercase())
          ) {
            Text(
              text = label,
              fontSize = 10.sp,
              fontWeight = if (enabled) FontWeight.Bold else FontWeight.Medium,
              color = if (enabled) NeonEmerald else TacticalOnSurfaceVariant,
              maxLines = 1
            )
          }
        }
      }
    }

    // EXPANDED DATA STATUS: per-provider honesty, secondary by design.
    AnimatedVisibility(visible = statusExpanded) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        if (providerStatuses.isEmpty()) {
          Text("No provider fetches this session.", fontSize = 10.sp,
            color = TacticalOnSurfaceVariant)
        }
        providerStatuses.forEach { (state, status) ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(state.source.name.lowercase().replace('_', ' '),
              fontSize = 10.sp, color = TacticalOnSurface)
            Text(status.name, fontSize = 10.sp, fontWeight = FontWeight.Bold,
              color = statusColor)
          }
        }
      }
    }
  }
}

@Composable
internal fun DisasterTypeLegend(
  types: List<com.example.data.model.HazardType>,
  selectedType: com.example.data.model.HazardType? = null,
  onSelectType: (com.example.data.model.HazardType) -> Unit = {},
  onToggleMockData: () -> Unit = {},
  isMockVisible: Boolean = false
) {
  // The row always renders: the fixed hazard FILTER chips plus the Demo
  // toggle stay reachable even when the current scenario is calm (the
  // Demo pill is the only map-side way back into the scenario).

  LazyRow(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    contentPadding = PaddingValues(horizontal = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    // RULE 3: the five named hazard filters are ALWAYS visible and tappable
    // (dimmer when the current scenario has none of that type - a judge can
    // still see the filter vocabulary and clear it). Tap = only this type;
    // tap again = clear.
    val chipTypes = listOf(
      com.example.data.model.HazardType.FLOOD,
      com.example.data.model.HazardType.FIRE,
      com.example.data.model.HazardType.EARTHQUAKE,
      com.example.data.model.HazardType.CYCLONE,
      com.example.data.model.HazardType.LANDSLIDE
    )
    items(chipTypes, key = { it.name }) { type ->
      val swatch = Color(com.example.data.disaster.DisasterTypeColors.argbFor(type))
      val isSel = selectedType == type
      val present = type in types
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(
            if (isSel) swatch.copy(alpha = 0.92f)
            else ObsidianContainer.copy(alpha = 0.94f)
          )
          .border(
            if (isSel) 2.dp else 1.dp,
            if (isSel) Color.White.copy(alpha = 0.85f)
            else swatch.copy(alpha = if (present) 0.7f else 0.35f),
            RoundedCornerShape(999.dp)
          )
          .clickable { onSelectType(type) }
          .padding(horizontal = 12.dp, vertical = 8.dp)
          .testTag("legend_" + type.name.lowercase()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (isSel) Color.White else swatch)
        )
        Text(
          text = type.label,
          fontSize = 11.sp,
          fontWeight = if (isSel) FontWeight.Black else FontWeight.Bold,
          color = when {
            isSel -> Color(0xFF081016)
            present -> TacticalOnSurface
            else -> TacticalOnSurfaceVariant
          },
          maxLines = 1
        )
        if (isSel) {
          Text(
            text = "\u2715",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF081016)
          )
        }
      }
    }
    // Rule 5: the demo scenario switch lives on this chip row (compact,
    // clearly separate from data layers).
    item {
      // User contrast fix: NO alpha wash. ON = solid amber, dark text.
      // OFF = solid dark container, bright text, strong outline. Either
      // state is legible against tiles at any zoom/brightness.
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(if (isMockVisible) WarningAmber else ObsidianContainerHigh)
          .border(
            1.5.dp,
            if (isMockVisible) ObsidianContainerLowest else TacticalCyan,
            RoundedCornerShape(999.dp)
          )
          .clickable { onToggleMockData() }
          .padding(horizontal = 12.dp, vertical = 8.dp)
          .testTag("mock_data_toggle_chip")
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(if (isMockVisible) ObsidianContainerLowest else TacticalCyan)
          )
          Text(
            text = if (isMockVisible) "DEMO: ON" else "DEMO: OFF",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = if (isMockVisible) ObsidianContainerLowest else TacticalOnSurface
          )
        }
      }
    }
  }
}

@Composable
private fun StatusChip(text: String, color: Color, testTag: String, onClick: (() -> Unit)? = null) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(999.dp))
      .background(ObsidianContainer.copy(alpha = 0.9f))
      .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 10.dp, vertical = 5.dp)
      .testTag(testTag)
  ) {
    Text(
      text = text,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      color = color,
      maxLines = 1
    )
  }
}

@Composable
private fun WeatherCell(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  value: String,
  tint: Color,
  modifier: Modifier = Modifier
) {
  // Blank value = no live feed for this metric. Show the em-dash placeholder
  // and mute the tint rather than rendering an empty gap that reads as a bug.
  val hasValue = value.isNotBlank()
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (hasValue) tint else TacticalOnSurfaceVariant,
      modifier = Modifier.size(15.dp)
    )
    Column {
      Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = TacticalOnSurfaceVariant,
        letterSpacing = 0.5.sp
      )
      Text(
        text = if (hasValue) value else "—",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (hasValue) TacticalOnSurface else TacticalOnSurfaceVariant,
        maxLines = 1
      )
    }
  }
}
