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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.OnSafeGreen
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalOnSurface
import com.example.viewmodel.VippattiUiState

// ============================================================================
// EVACUATION CTA â€” large one-hand control, always bound to the SELECTED zone
// ============================================================================

@Composable
internal fun EvacuationCta(
  uiState: VippattiUiState,
  onStartEvacuation: () -> Unit,
  onStopEvacuation: () -> Unit
) {
  val route = uiState.activeRoute
  val distanceStr = route?.let { OsrmRoutingService.formatDistance(it.distanceMeters) } ?: "--"
  val durationStr = route?.let { OsrmRoutingService.formatDuration(it.durationSeconds) } ?: "--"

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(if (uiState.isNavigatingLive) Color(0xFF0284C7) else SafeGreen)
      .clickable {
        if (uiState.isNavigatingLive) onStopEvacuation() else onStartEvacuation()
      }
      .padding(horizontal = 14.dp, vertical = 12.dp)
      .testTag("start_evacuation_route_button"),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(if (uiState.isNavigatingLive) Color.White else OnSafeGreen),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (uiState.isNavigatingLive) Icons.Default.Check else Icons.Default.Navigation,
          contentDescription = null,
          tint = if (uiState.isNavigatingLive) Color(0xFF0284C7) else SafeGreen,
          modifier = Modifier.size(20.dp)
        )
      }
      Column {
        Text(
          text = if (uiState.isNavigatingLive) "ACTIVE GUIDANCE RUNNING" else "START EVACUATION ROUTE",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (uiState.isNavigatingLive) Color.White.copy(alpha = 0.85f) else OnSafeGreen.copy(alpha = 0.85f),
          letterSpacing = 0.8.sp
        )
        Text(
          text = "To ${uiState.selectedSafeZone?.name ?: "selected safe zone"}",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = if (uiState.isNavigatingLive) Color.White else OnSafeGreen,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
    Column(horizontalAlignment = Alignment.End) {
      Text(distanceStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (uiState.isNavigatingLive) Color.White else OnSafeGreen)
      Text(durationStr, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (uiState.isNavigatingLive) Color.White.copy(alpha = 0.8f) else OnSafeGreen.copy(alpha = 0.8f))
    }
  }
}

// ============================================================================
// LIVE TURN-BY-TURN HUD â€” guidance always follows the SELECTED destination
// ============================================================================

@Composable
internal fun LiveNavigationHud(
  uiState: VippattiUiState,
  onNextNavigationStep: () -> Unit,
  onStopEvacuation: () -> Unit
) {
  val route = uiState.activeRoute
  val currentStep = route?.steps?.getOrNull(uiState.currentNavigationStepIndex)
  val totalSteps = route?.steps?.size ?: 1

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 10.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerLowest.copy(alpha = 0.96f))
      .border(1.dp, NeonEmerald, RoundedCornerShape(12.dp))
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(NeonEmerald),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = null,
            tint = OnNeonEmerald,
            modifier = Modifier.size(18.dp)
          )
        }
        Column {
          Text(
            text = "STEP ${uiState.currentNavigationStepIndex + 1} OF $totalSteps",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = NeonEmerald,
            letterSpacing = 0.5.sp
          )
          Text(
            text = currentStep?.instruction ?: "Proceed along the safe corridor",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
          onClick = onNextNavigationStep,
          colors = ButtonDefaults.buttonColors(
            containerColor = NeonEmerald,
            contentColor = OnNeonEmerald
          ),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.height(30.dp).testTag("navigation_next_step_button")
        ) {
          Text(
            text = if (uiState.currentNavigationStepIndex + 1 >= totalSteps) "Arrive" else "Next",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          )
        }
        IconButton(
          onClick = onStopEvacuation,
          modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(EmergencyRed)
            .testTag("navigation_stop_button")
        ) {
          Icon(Icons.Default.Close, contentDescription = "Exit Navigation", tint = Color.White, modifier = Modifier.size(14.dp))
        }
      }
    }
  }
}
