package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terrain
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
import com.example.data.suitability.SuitabilityBand
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.TerrainSelfAssessment

/**
 * TERRAIN SELF-ASSESSMENT chip + result card — "is MY spot a red zone?".
 * The check runs ONLY on an explicit tap (it makes live service calls); the
 * result states its source, classification and disclaimer, and an
 * unavailable service never renders as a fake SAFE.
 */
@Composable
internal fun TerrainSelfAssessmentChip(
  assessment: TerrainSelfAssessment?,
  isAssessing: Boolean,
  onAssess: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier.fillMaxWidth()) {
    // Trigger chip (always available; shows progress while probing)
    Row(
      modifier = Modifier
        .padding(horizontal = 10.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(ObsidianContainerLowest.copy(alpha = 0.9f))
        .border(1.dp, TacticalCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        .clickable(enabled = !isAssessing, onClick = onAssess)
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      if (isAssessing) {
        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = TacticalCyan)
        Text("Checking terrain around you...", fontSize = 9.5.sp, color = TacticalOnSurface)
      } else {
        Icon(Icons.Default.Terrain, null, tint = TacticalCyan, modifier = Modifier.size(14.dp))
        Text(
          if (assessment == null) "CHECK MY TERRAIN" else "RE-CHECK MY TERRAIN",
          fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = TacticalCyan
        )
      }
    }

    AnimatedVisibility(visible = assessment != null && !isAssessing) {
      when (assessment) {
        is TerrainSelfAssessment.Result -> {
          val v = assessment.verdict
          val accent = when (v.band) {
            SuitabilityBand.SAFE -> NeonEmerald
            SuitabilityBand.CAUTION -> TacticalCyan
            SuitabilityBand.HIGH_RISK -> WarningAmber
            SuitabilityBand.RED_ZONE -> EmergencyRedBright
            null -> TacticalOnSurfaceVariant
          }
          Column(
            modifier = Modifier
              .padding(horizontal = 10.dp, vertical = 4.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(ObsidianContainerLowest.copy(alpha = 0.94f))
              .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
              .padding(10.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                v.band?.label ?: "NOT ASSESSED",
                fontSize = 12.sp, fontWeight = FontWeight.Black, color = accent,
                modifier = Modifier.weight(1f)
              )
              if (v.status == com.example.data.suitability.SuitabilityStatus.ASSESSED) {
                Text("score ${v.score}/100", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent)
              }
              Spacer(Modifier.width(8.dp))
              Text(
                "✕", fontSize = 12.sp, color = TacticalOnSurfaceVariant,
                modifier = Modifier.clickable(onClick = onDismiss)
              )
            }
            if (v.slopePercent != null) {
              Text(
                "Slope %.1f%% • rain(24h) %s • coast %s".format(
                  v.slopePercent,
                  v.rainfallMm24h?.let { "%.0f mm".format(it) } ?: "unknown",
                  if (assessment.coastKnown) "from land-data grid" else "unresolved"
                ),
                fontSize = 8.5.sp, color = TacticalOnSurfaceVariant
              )
            }
            Spacer(Modifier.height(4.dp))
            v.reasons.take(4).forEach { reason ->
              Text("• $reason", fontSize = 8.5.sp, color = TacticalOnSurface, lineHeight = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(v.source, fontSize = 7.5.sp, color = TacticalOnSurfaceVariant)
            Text(
              "DERIVED • " + v.disclaimer,
              fontSize = 7.5.sp, color = WarningAmber, lineHeight = 10.sp
            )
          }
        }
        is TerrainSelfAssessment.Unavailable -> {
          Column(
            modifier = Modifier
              .padding(horizontal = 10.dp, vertical = 4.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(ObsidianContainerLowest.copy(alpha = 0.94f))
              .border(1.dp, WarningAmber.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
              .padding(10.dp)
          ) {
            Text("TERRAIN NOT ASSESSED", fontSize = 11.sp, fontWeight = FontWeight.Black, color = WarningAmber)
            Text(
              "The elevation/rainfall service could not answer (${assessment.detail}). " +
                "Nothing was assumed — try again when connected.",
              fontSize = 8.5.sp, color = TacticalOnSurface, lineHeight = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
              "✕ dismiss", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurfaceVariant,
              modifier = Modifier.clickable(onClick = onDismiss)
            )
          }
        }
        null -> Unit
      }
    }
  }
}
