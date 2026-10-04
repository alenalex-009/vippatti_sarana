package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DataStatus
import com.example.data.model.Freshness
import com.example.data.model.RecordStamp
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.StatusErrorText
import com.example.ui.theme.StatusHistoricalText
import com.example.ui.theme.StatusStaleText
import com.example.ui.theme.StatusSuccessText
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant

/**
 * ============================================================================
 * PHASE 2 — REUSABLE DATA-STATUS + PROVENANCE UI
 * ============================================================================
 *
 * One visual vocabulary for every data surface: a status pill
 * ([StatusBadge]), a source + freshness + coverage line ([StampLine]), and the
 * banners the spec requires (simulated warning, stale warning, unavailable
 * panel with retry). Colours and labels come from the model, never invented.
 */
/**
 * The colour every status label is written in. These are the dedicated
 * `status*Text` tokens rather than the bright map accents, because the pill sets
 * 12sp bold text and AA needs >= 4.5:1 for that: in light mode [NeonEmerald]
 * alone measured 4.21:1, [EmergencyRedBright] 4.31:1, [WarningAmber] 2.84:1 and
 * [TacticalOutlineVariant] 1.19:1. ThemeContrastTest re-measures each pair.
 */
@Composable
internal fun dataStatusColor(status: DataStatus): Color = when (status) {
  DataStatus.LOADING -> TacticalOnSurfaceVariant
  DataStatus.SUCCESS -> StatusSuccessText
  DataStatus.VERIFIED -> StatusSuccessText
  DataStatus.STALE -> StatusStaleText
  DataStatus.EMPTY -> TacticalOnSurfaceVariant
  // Not configured is a neutral fact about this build, never an alarm.
  DataStatus.NOT_CONFIGURED -> TacticalOnSurfaceVariant
  DataStatus.UNAVAILABLE -> TacticalOnSurfaceVariant
  DataStatus.ERROR -> StatusErrorText
  DataStatus.SIMULATED -> TacticalCyan
  DataStatus.NOT_VERIFIED -> StatusStaleText
  // Archived data is neither live (green) nor an error: a cool, distinct slate.
  DataStatus.HISTORICAL -> StatusHistoricalText
}

/** Compact status pill: LIVE / SIMULATED / STALE / ERROR / … */
@Composable
fun StatusBadge(
  status: DataStatus,
  modifier: Modifier = Modifier
) {
  val color = dataStatusColor(status)
  // PHASE 23: normal UI never spells "SIMULATED" - demo data reads "DEMO".
  // Internally DataStatus.SIMULATED is preserved untouched (model/tests).
  val uiLabel = if (status == DataStatus.SIMULATED) "Demo" else status.label
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(999.dp))
      .background(ObsidianContainer.copy(alpha = 0.9f))
      .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
      .padding(horizontal = 10.dp, vertical = 4.dp)
  ) {
    Text(
      text = uiLabel.uppercase(),
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = color,
      maxLines = 1
    )
  }
}

/** Source attribution + fetched age + coverage, from a [RecordStamp]. */
@Composable
fun StampLine(
  stamp: RecordStamp,
  nowMillis: Long,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      StatusBadge(stamp.status)
      Text(
        text = "Source: ${stamp.sourceName}",
        fontSize = 12.sp,
        color = TacticalOnSurfaceVariant,
        maxLines = 1
      )
    }
    Text(
      text = stamp.retrievedAgeLabel(nowMillis) +
        (stamp.coverage?.let { " • $it" } ?: "") +
        (stamp.eventAtMillis?.let { " • ${stamp.eventAgeLabel(nowMillis)}" } ?: ""),
      fontSize = 12.sp,
      color = TacticalOnSurfaceVariant
    )
    stamp.errorMessage?.takeIf { it.isNotBlank() }?.let {
      Text(text = "Problem: $it", fontSize = 12.sp, color = EmergencyRedBright)
    }
  }
}

/** Warning shown whenever the underlying record is simulated/demo data. */
@Composable
fun SimulatedWarningBar() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(TacticalCyan.copy(alpha = 0.12f))
      .border(1.dp, TacticalCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(modifier = Modifier.size(8.dp).background(TacticalCyan, CircleShape))
    Text(
      text = "DEMO — demonstration scenario, not a real observation.",
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = TacticalCyan
    )
  }
}

/** Warning shown when a real record is older than its freshness window. */
@Composable
fun StaleWarningBar(
  retrievedAtMillis: Long,
  nowMillis: Long
) {
  val age = com.example.data.news.NewsPresentation.relativeAge(retrievedAtMillis, nowMillis)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(StatusStaleText.copy(alpha = 0.12f))
      .border(1.dp, StatusStaleText.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(modifier = Modifier.size(8.dp).background(StatusStaleText, CircleShape))
    Text(
      text = "STALE — last refreshed $age. Treat with caution.",
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = StatusStaleText
    )
  }
}

/**
 * Honest empty/unavailable/error panel with an optional Retry action.
 * Never invents data to fill the space it guards.
 */
@Composable
fun UnavailablePanel(
  status: DataStatus,
  what: String,
  errorMessage: String? = null,
  onRetry: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainer)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = when (status) {
        DataStatus.LOADING -> "$what is loading…"
        DataStatus.ERROR -> errorMessage?.takeIf { it.isNotBlank() } ?: "$what is unavailable right now."
        DataStatus.NOT_CONFIGURED -> "$what is not configured in this build."
        DataStatus.UNAVAILABLE -> "$what is unavailable right now."
        DataStatus.STALE -> "$what is stale."
        else -> "No $what yet."
      },
      fontSize = 12.sp,
      color = TacticalOnSurfaceVariant
    )
    if (onRetry != null && (status == DataStatus.ERROR || status == DataStatus.UNAVAILABLE || status == DataStatus.STALE)) {
      TextButton(
        onClick = onRetry,
        colors = ButtonDefaults.textButtonColors(contentColor = TacticalCyan)
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Retry",
          tint = TacticalCyan,
          modifier = Modifier.size(16.dp)
        )
        Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalCyan)
      }
    }
  }
}