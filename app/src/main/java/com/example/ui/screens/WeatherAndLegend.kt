package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
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
  val providerStatuses = uiState.providerStatuses
  // Aggregate status for the demo chip colour: LIVE wins, then a real provider
  // failure, then cached/stale data.
  val aggregateStatus = when {
    uiState.isDisasterDataLive -> DataStatus.SUCCESS
    providerStatuses.any { (_, status) -> status == DataStatus.ERROR } -> DataStatus.ERROR
    providerStatuses.any { (_, status) -> status == DataStatus.STALE } -> DataStatus.STALE
    else -> DataStatus.EMPTY
  }
  val statusColor = dataStatusColor(aggregateStatus)
  // Plain-language data summary instead of per-provider jargon chips: normal
  // people read "Live: 2 of 3 sources" — the full per-source provenance stays
  // available where it belongs (event detail sheets, Home DATA STATUS footer).
  val liveCount = providerStatuses.count { (_, status) ->
    status == DataStatus.SUCCESS || status == DataStatus.STALE
  }
  LazyRow(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    contentPadding = PaddingValues(horizontal = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    item {
      StatusChip(
        text = if (providerStatuses.isEmpty()) "No data yet"
        else "Live: $liveCount of ${providerStatuses.size} sources",
        color = statusColor,
        testTag = "disaster_data_status_chip"
      )
    }
    // Simulated-demo toggle, plainly worded.
    item {
      StatusChip(
        text = if (uiState.isMockDataVisible) "Demo data: ON" else "Demo data: OFF",
        color = if (uiState.isMockDataVisible) TacticalCyan else TacticalOnSurfaceVariant,
        testTag = "mock_data_toggle_chip",
        onClick = onToggleMockData
      )
    }
    // Layer toggles — only layers the providers/data model actually support.
    listOf(
      DisasterLayer.EARTHQUAKES to "Quakes",
      DisasterLayer.ACTIVE_FIRES to "Fires",
      DisasterLayer.OFFICIAL_ALERTS to "Alerts",
      DisasterLayer.USER_REPORTS to "My Reports",
      DisasterLayer.SAFE_ZONES to "Shelters"
    ).forEach { (layer, label) ->
      item {
        val enabled = layer in uiState.enabledLayers
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) NeonEmeraldContainer.copy(alpha = 0.25f) else ObsidianContainer)
            .border(
              1.dp,
              if (enabled) NeonEmerald.copy(alpha = 0.6f) else TacticalOutlineVariant.copy(alpha = 0.4f),
              RoundedCornerShape(999.dp)
            )
            .clickable { onToggleLayer(layer) }
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("layer_toggle_${layer.name.lowercase()}")
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
    // NOTE: the "+ Report Incident" chip used to live here, floating over the map
    // directly under the risk strip. A stray tap while panning/zooming could open
    // the report form (the app's only text-input popup), which is what made map
    // taps feel like an unwanted comment box. Reporting now lives behind the
    // explicit "REPORT AN INCIDENT..." action in the bottom sheet.
  }
}

@Composable
internal fun DisasterTypeLegend(types: List<com.example.data.model.HazardType>) {
  if (types.isEmpty()) return
  LazyRow(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    contentPadding = PaddingValues(horizontal = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    items(types, key = { it.name }) { type ->
      val swatch = Color(com.example.data.disaster.DisasterTypeColors.argbFor(type))
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(ObsidianContainer.copy(alpha = 0.9f))
          .border(1.dp, swatch.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .testTag("legend_${type.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(swatch)
        )
        Text(
          text = type.label,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurface,
          maxLines = 1
        )
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
