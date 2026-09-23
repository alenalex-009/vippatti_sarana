package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.shelters.EmergencyGuidance
import com.example.data.shelters.SafeHaven
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.WarningAmber

/**
 * EMERGENCY GUIDANCE CARD — "a disaster is happening; where do I go?"
 *
 * Renders the pure EmergencyGuidance decision from the ViewModel. Every
 * variant states exactly what is known and what is not:
 *  - SuggestShelter      -> nearest feasible registered zone + GO.
 *  - NoShelterEligible   -> the REAL rejection reasons per closer site.
 *  - NoShelterKnown      -> honest "no shelter records here" + 112 + the
 *                           terrain-safe last-resort search.
 *  - already routing     -> no card (the flow handles it).
 * The terrain haven result is always labelled DERIVED open terrain.
 */
@Composable
internal fun EmergencyGuidanceCard(
  guidance: EmergencyGuidance,
  haven: SafeHaven?,
  isSearchingHaven: Boolean,
  onGo: () -> Unit,
  onSearchHaven: () -> Unit,
  onRouteToHaven: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(visible = guidance != EmergencyGuidance.None || haven != null || isSearchingHaven) {
    val accent = when (guidance) {
      is EmergencyGuidance.SuggestShelter -> NeonEmerald
      is EmergencyGuidance.NoShelterEligible -> WarningAmber
      is EmergencyGuidance.NoShelterKnown -> EmergencyRedBright
      else -> TacticalCyan
    }
    Column(
      modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(ObsidianContainerLowest.copy(alpha = 0.94f))
        .border(1.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
        .padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      when (guidance) {
        is EmergencyGuidance.SuggestShelter -> GuidanceRow(
          headline = guidance.headline,
          detail = guidance.detail,
          accent = accent,
          actionLabel = "GO",
          onAction = onGo,
          onDismiss = onDismiss
        )
        is EmergencyGuidance.NoShelterEligible -> GuidanceRow(
          headline = guidance.headline,
          detail = guidance.rejections.joinToString(" • "),
          accent = accent,
          actionLabel = if (isSearchingHaven) null else "FIND SAFE TERRAIN",
          onAction = onSearchHaven,
          onDismiss = onDismiss
        )
        is EmergencyGuidance.NoShelterKnown -> GuidanceRow(
          headline = guidance.headline,
          detail = guidance.detail,
          accent = accent,
          actionLabel = if (isSearchingHaven) null else "FIND SAFE TERRAIN",
          onAction = onSearchHaven,
          onDismiss = onDismiss
        )
        is EmergencyGuidance.AlreadyRouting, EmergencyGuidance.None -> {
          // no card content; the haven block below may still render
        }
      }

      if (isSearchingHaven) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = TacticalCyan)
          Text(
            "Probing terrain around you (slope + rain + coast)...",
            fontSize = 10.sp, color = TacticalOnSurfaceVariant
          )
        }
      }

      haven?.let { h ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TacticalCyan.copy(alpha = 0.14f))
            .border(1.dp, TacticalCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(onClick = onRouteToHaven)
            .padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Default.Terrain, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(18.dp))
          Column(Modifier.weight(1f)) {
            Text(h.headline, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacticalCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              "DERIVED open terrain • not a registered shelter • ${h.verdict.reasons.firstOrNull().orEmpty()}",
              fontSize = 8.5.sp, color = TacticalOnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
          }
          Icon(Icons.Default.Navigation, contentDescription = "Route", tint = TacticalCyan, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
private fun GuidanceRow(
  headline: String,
  detail: String,
  accent: Color,
  actionLabel: String?,
  onAction: () -> Unit,
  onDismiss: () -> Unit
) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(Modifier.weight(1f)) {
      Text(
        headline,
        fontSize = 12.sp, fontWeight = FontWeight.Black, color = accent,
        maxLines = 2, overflow = TextOverflow.Ellipsis
      )
      Text(
        detail,
        fontSize = 9.5.sp, color = TacticalOnSurface,
        maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp
      )
    }
    if (actionLabel != null) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .background(accent.copy(alpha = 0.9f))
          .clickable(onClick = onAction)
          .padding(horizontal = 14.dp, vertical = 8.dp)
      ) {
        Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
      }
    }
    Box(
      modifier = Modifier
        .size(28.dp)
        .clip(RoundedCornerShape(8.dp))
        .clickable(onClick = onDismiss),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TacticalOnSurfaceVariant, modifier = Modifier.size(14.dp))
    }
  }
}
