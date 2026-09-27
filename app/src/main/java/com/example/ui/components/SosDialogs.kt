package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant

@Composable
fun SosBroadcastDialog(
  locationLabel: String,
  batteryLabel: String,
  medicalTagLabel: String,
  relaysLabel: String,
  onDismiss: () -> Unit,
  onCancelSos: () -> Unit,
  onCallEmergencyServices: () -> Unit = {}
) {
  val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "sos_scale"
  )

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = ObsidianSurface,
      modifier = Modifier
        .fillMaxWidth()
        .border(2.dp, EmergencyRed, RoundedCornerShape(24.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(24.dp)
          .fillMaxWidth()
          // Emergency dialog content must remain reachable on short screens,
          // landscape and with the keyboard open — scroll instead of clip.
          .verticalScroll(rememberScrollState())
          .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.size(72.dp)
        ) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .scale(pulseScale)
              .background(EmergencyRed.copy(alpha = 0.3f), CircleShape)
          )
          Box(
            modifier = Modifier
              .size(52.dp)
              .background(EmergencyRed, CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(30.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = stringResource(R.string.sos_active_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.Black,
          color = EmergencyRed,
          letterSpacing = 0.5.sp
        )

        Text(
          text = stringResource(R.string.sos_active_notice),
          fontSize = 12.sp,
          color = TacticalOnSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // The label takes the flexible half so a longer translated label
          // wraps under itself instead of shoving the value off the row.
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              stringResource(R.string.sos_label_gps),
              fontSize = 12.sp,
              color = TacticalOnSurfaceVariant,
              modifier = Modifier.weight(1f)
            )
            Text(locationLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              stringResource(R.string.sos_label_battery),
              fontSize = 12.sp,
              color = TacticalOnSurfaceVariant,
              modifier = Modifier.weight(1f)
            )
            Text(batteryLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              stringResource(R.string.sos_label_medical),
              fontSize = 12.sp,
              color = TacticalOnSurfaceVariant,
              modifier = Modifier.weight(1f)
            )
            Text(medicalTagLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalCyan)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              stringResource(R.string.sos_label_delivery),
              fontSize = 12.sp,
              color = TacticalOnSurfaceVariant,
              modifier = Modifier.weight(1f)
            )
            Text(relaysLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // The ONLY way this build can actually reach a human being: a real dial.
        OutlinedButton(
          onClick = onCallEmergencyServices,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("sos_call_112_button")
        ) {
          Icon(
            Icons.Default.Call,
            contentDescription = null,
            tint = EmergencyRed,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            stringResource(R.string.sos_call_112_now),
            fontWeight = FontWeight.Black,
            color = EmergencyRed
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
          onClick = onDismiss,
          colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("sos_keep_broadcasting_button")
        ) {
          Text(
            stringResource(R.string.sos_keep_active),
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
          onClick = onCancelSos,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("sos_cancel_broadcast_button")
        ) {
          Text(
            stringResource(R.string.sos_cancel_false_alarm),
            color = TacticalOnSurfaceVariant,
            fontSize = 13.sp
          )
        }
      }
    }
  }
}

@Composable
fun SosConfirmDialog(
  locationLabel: String,
  batteryLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  onCallEmergencyServices: () -> Unit = {}
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = ObsidianSurface,
      modifier = Modifier
        .fillMaxWidth()
        .border(2.dp, EmergencyRed, RoundedCornerShape(24.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(24.dp)
          .fillMaxWidth()
          // Keep the YES/CANCEL emergency actions reachable on short screens.
          .verticalScroll(rememberScrollState())
          .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = EmergencyRed,
          modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = stringResource(R.string.sos_confirm_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.Black,
          color = EmergencyRed,
          letterSpacing = 0.5.sp
        )

        Text(
          text = stringResource(R.string.sos_confirm_body),
          fontSize = 12.sp,
          color = TacticalOnSurfaceVariant,
          lineHeight = 16.sp,
          modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              stringResource(R.string.sos_label_gps),
              fontSize = 12.sp,
              color = TacticalOnSurfaceVariant,
              modifier = Modifier.weight(1f)
            )
            Text(locationLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              stringResource(R.string.sos_label_battery),
              fontSize = 12.sp,
              color = TacticalOnSurfaceVariant,
              modifier = Modifier.weight(1f)
            )
            Text(batteryLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
          onClick = onConfirm,
          colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("sos_confirm_button")
        ) {
          Text(
            stringResource(R.string.sos_save_local),
            fontWeight = FontWeight.Black,
            color = Color.White
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Real action, available right here: open the dialer on 112.
        OutlinedButton(
          onClick = onCallEmergencyServices,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("sos_confirm_call_112_button")
        ) {
          Icon(
            Icons.Default.Call,
            contentDescription = null,
            tint = EmergencyRed,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            stringResource(R.string.sos_call_112_now),
            fontWeight = FontWeight.Black,
            color = EmergencyRed
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("sos_confirm_cancel_button")
        ) {
          Text(
            stringResource(R.string.sos_cancel_i_am_safe),
            color = TacticalOnSurfaceVariant,
            fontSize = 13.sp
          )
        }
      }
    }
  }
}
