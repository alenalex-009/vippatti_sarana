package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.disaster.DispatchTagType
import com.example.data.disaster.FeedDispatch
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant

/**
 * Editorial news card (News redesign spec):
 *
 *   ● Source            12h ago
 *   Headline (16sp semibold, max 3 lines)
 *   Short summary (13sp secondary, max 2 lines)
 *   scope metadata                 Read story →
 *
 * Content-driven height, 16dp internal padding, restrained color: the only
 * accent is a small severity dot — never a red border or alert styling,
 * because every card here is a NEWS REPORT, not an official alert.
 */
@Composable
internal fun FeedDispatchCard(
  dispatch: FeedDispatch,
  onActionClick: () -> Unit
) {
  val severe = dispatch.tagType == DispatchTagType.HIGH_ALERT ||
    dispatch.tagType == DispatchTagType.ROAD_CLOSED
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 6.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(ObsidianContainer)
        .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // [Source + time]
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          // Flex so long source names ellipsize, never push the time out.
          modifier = Modifier.weight(1f)
        ) {
          // Severity dot: the ONLY color accent. Color-blind users read the
          // scope text; color alone is never the message.
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(if (severe) EmergencyRed else TacticalCyan)
          )
          Text(
            text = dispatch.agency,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TacticalOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Spacer(Modifier.width(8.dp))
        Text(
          // Real publication time from the article itself.
          text = dispatch.issuedTime.removePrefix("Published "),
          fontSize = 12.sp,
          color = TacticalOnSurfaceVariant
        )
      }

      // Headline
      Text(
        text = dispatch.title,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = TacticalOnSurface,
        lineHeight = 22.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
      )

      // Summary
      Text(
        text = dispatch.description,
        fontSize = 13.sp,
        color = TacticalOnSurfaceVariant,
        lineHeight = 20.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      // [metadata]  [Read story ->]
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = dispatch.location,
          fontSize = 12.sp,
          color = TacticalOnSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onActionClick() }
            // Generous padding keeps the touch target ~48dp tall.
            .padding(horizontal = 8.dp, vertical = 13.dp)
            .testTag("dispatch_action_${dispatch.id}")
        ) {
          Text(
            text = dispatch.actionLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TacticalCyan
          )
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = TacticalCyan,
            modifier = Modifier.size(15.dp)
          )
        }
      }
    }
  }
}
