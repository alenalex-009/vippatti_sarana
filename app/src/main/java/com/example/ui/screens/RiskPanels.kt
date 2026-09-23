package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.risk.RiskLevel
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.LocalVippattiColors
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.WarningAmber

// ============================================================================
// PERSONAL RISK STRIP — RED/ORANGE/YELLOW/GREEN + understandable explanation
// ============================================================================

@Composable
private fun riskColor(level: RiskLevel?): Color {
  // Risk-yellow must stay readable on BOTH themes: bright yellow on dark
  // surfaces, deep amber text on light surfaces (yellow-on-white is invisible).
  val onLight = LocalVippattiColors.current.obsidianSurface.luminance() > 0.5f
  return when (level) {
    RiskLevel.RED -> EmergencyRedBright
    RiskLevel.ORANGE -> WarningAmber
    RiskLevel.YELLOW -> if (onLight) Color(0xFF7A5C00) else Color(0xFFFDD835)
    RiskLevel.GREEN -> NeonEmerald
    null -> TacticalOnSurfaceVariant
  }
}

@Composable
internal fun PersonalRiskStrip(
  risk: com.example.data.risk.PersonalRiskAssessment?,
  isFallbackLocation: Boolean,
  modifier: Modifier = Modifier
) {
  val color = riskColor(risk?.level)
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerLowest.copy(alpha = 0.94f))
      .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      modifier = Modifier
        .size(26.dp)
        .clip(CircleShape)
        .background(color.copy(alpha = 0.2f))
        .border(1.5.dp, color, CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = risk?.level?.label?.first()?.toString() ?: "-",
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        color = color
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "RISK ${risk?.level?.label ?: "..."}",
          fontSize = 10.sp,
          fontWeight = FontWeight.Black,
          color = color,
          letterSpacing = 0.5.sp,
          maxLines = 1
        )
        Text(
          text = if (isFallbackLocation) "• location approximate" else "• your GPS",
          fontSize = 8.sp,
          fontWeight = FontWeight.Bold,
          color = if (isFallbackLocation) TacticalCyan else NeonEmerald,
          maxLines = 1
        )
      }
      Text(
        text = risk?.explanation ?: "Checking hazards around your location…",
        fontSize = 9.sp,
        color = TacticalOnSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 11.sp
      )
    }
  }
}

// ============================================================================
// WHAT SHOULD I DO? — actionable recommendation with WHY
// ============================================================================

@Composable
internal fun RecommendedActionCard(
  action: com.example.data.risk.RecommendedAction?,
  selectedZoneName: String?,
  onWhyThisZone: () -> Unit,
  isCalculating: Boolean
) {
  if (action == null) return
  val isCritical = action.actionId in setOf(
    "EVACUATE_NOW", "MOVE_TO_HIGHER_GROUND", "MOVE_AWAY_FROM_RIVER"
  )
  val accent = if (isCritical) EmergencyRedBright else NeonEmerald
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(ObsidianContainerLowest.copy(alpha = 0.95f))
      .border(1.5.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(
        imageVector = if (isCritical) Icons.Default.Warning else Icons.Outlined.Shield,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(18.dp)
      )
      Text(
        text = "WHAT SHOULD I DO?",
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = TacticalOnSurfaceVariant,
        letterSpacing = 0.8.sp
      )
      if (isCalculating) {
        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = accent)
      }
    }
    Text(
      text = action.title,
      fontSize = 15.sp,
      fontWeight = FontWeight.Black,
      color = accent,
      letterSpacing = 0.4.sp
    )
    Text(
      text = action.explanation,
      fontSize = 10.sp,
      color = TacticalOnSurface,
      lineHeight = 13.sp
    )
    if (action.hasEvacuationTarget && selectedZoneName != null) {
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(ObsidianContainerHigh)
          .border(1.dp, NeonEmerald.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
          .clickable { onWhyThisZone() }
          .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(Icons.Default.NearMe, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(13.dp))
        Text(
          text = "Why this zone? Best pick — $selectedZoneName",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = NeonEmerald,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
