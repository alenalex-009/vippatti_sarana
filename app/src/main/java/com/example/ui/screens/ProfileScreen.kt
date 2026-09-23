package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibleForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HolidayVillage
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.data.news.NewsPresentation
import com.example.viewmodel.VippattiUiState
import java.util.Locale

/** Honest reference point for the tile-cache progress bar (64 MB). */

@Composable
internal fun DistressSignalCenter(
  onBroadcastSos: () -> Unit,
  onOpenSituationReport: () -> Unit,
  pulseScale: Float
) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
              Brush.linearGradient(
                colors = listOf(
                  EmergencyRed,
                  Color(0xFFBE123C)
                )
              )
            )
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
          ) {
            Column {
              Row(
                modifier = Modifier
                  .clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.2f))
                  .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .scale(pulseScale)
                    .background(NeonEmerald, CircleShape)
                )
                Text(
                  // STAGE 7 — local-only honesty: this build has no
                  // relief-network backend (see RELAY_CHANNEL), so the badge
                  // must never read as a live transmission relay.
                  text = "LOCAL SOS — NOT TRANSMITTED",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  letterSpacing = 0.5.sp
                )
              }

              Text(
                text = "Distress Signal Center",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp)
              )

              Text(
                text = "Records live GPS, battery & medical tags on this device only. " +
                  "Nothing is transmitted — dial 112 for response.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
              )
            }

            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.CrisisAlert,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
              )
            }
          }

          Button(
            onClick = onBroadcastSos,
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.White,
              contentColor = EmergencyRed
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(46.dp)
              .testTag("broadcast_sos_hero_button")
          ) {
            Icon(
              imageVector = Icons.Default.Emergency,
              contentDescription = null,
              tint = EmergencyRed,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "RECORD SOS WITH LIVE GPS",
              fontSize = 13.sp,
              fontWeight = FontWeight.Black,
              letterSpacing = 0.4.sp
            )
          }

          // REPORT MY SITUATION — voice/form/photo channel into the LOCAL
          // device record (no relief-network backend exists, so nothing
          // reaches any dispatcher). Deliberately separate from the SOS
          // record: no distress signal is armed, no confirmation gate required.
          OutlinedButton(
            onClick = onOpenSituationReport,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
              .testTag("report_situation_hero_button")
          ) {
            Icon(
              imageVector = Icons.Default.RecordVoiceOver,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "REPORT MY SITUATION",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.4.sp
            )
          }
        }
      }
}

@Composable
internal fun DispatchPriorityLines() {
  val context = LocalContext.current
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "DISASTER DISPATCH (TOLL-FREE)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurfaceVariant,
            letterSpacing = 0.6.sp
          )
          Text(
            text = "Priority Lines",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = EmergencyRedBright
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // NDRF 112
          Column(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(ObsidianContainerLow)
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
              .clickable {
                try {
                  val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                  context.startActivity(intent)
                } catch (e: Exception) {
                  Toast.makeText(context, "Cannot dial 112: ${e.message}", Toast.LENGTH_SHORT).show()
                }
              }
              .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(EmergencyRedContainer.copy(alpha = 0.3f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Shield, contentDescription = null, tint = EmergencyRedBright, modifier = Modifier.size(20.dp))
            }
            Text("NDRF 112", fontSize = 14.sp, fontWeight = FontWeight.Black, color = TacticalOnSurface, modifier = Modifier.padding(top = 4.dp))
            Text("Disaster Force", fontSize = 10.sp, color = TacticalOnSurfaceVariant)
          }

          // Ambulance 108
          Column(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(ObsidianContainerLow)
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
              .clickable {
                try {
                  val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:108"))
                  context.startActivity(intent)
                } catch (e: Exception) {
                  Toast.makeText(context, "Cannot dial 108: ${e.message}", Toast.LENGTH_SHORT).show()
                }
              }
              .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.LocalHospital, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(20.dp))
            }
            Text("Ambulance", fontSize = 14.sp, fontWeight = FontWeight.Black, color = TacticalOnSurface, modifier = Modifier.padding(top = 4.dp))
            Text("Dial 108", fontSize = 10.sp, color = TacticalOnSurfaceVariant)
          }

          // Fire 101
          Column(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(ObsidianContainerLow)
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
              .clickable {
                try {
                  val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:101"))
                  context.startActivity(intent)
                } catch (e: Exception) {
                  Toast.makeText(context, "Cannot dial 101: ${e.message}", Toast.LENGTH_SHORT).show()
                }
              }
              .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(WarningAmber.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
            }
            Text("Fire 101", fontSize = 14.sp, fontWeight = FontWeight.Black, color = TacticalOnSurface, modifier = Modifier.padding(top = 4.dp))
            Text("Rescue Squad", fontSize = 10.sp, color = TacticalOnSurfaceVariant)
          }
        }
      }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EvacuationShelterNeeds() {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianContainerLow)
            .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(Icons.Default.HolidayVillage, contentDescription = null, tint = EmergencyRedBright, modifier = Modifier.size(18.dp))
              Text(
                text = "EVACUATION SHELTER NEEDS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TacticalOnSurface,
                letterSpacing = 0.5.sp
              )
            }
            Text("Self-Declared", fontSize = 11.sp, color = TacticalOnSurfaceVariant)
          }

          Text(
            text = "Assigned disaster camps match these requirements automatically during emergency evacuations:",
            fontSize = 12.sp,
            color = TacticalOnSurfaceVariant,
            lineHeight = 16.sp
          )

          // FlowRow so the requirement chips wrap to a new line on narrow
          // screens instead of overflowing the card edge.
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            // Elder
            Row(
              modifier = Modifier
                .clip(CircleShape)
                .background(NeonEmeraldContainer.copy(alpha = 0.15f))
                .border(1.dp, NeonEmerald.copy(alpha = 0.3f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Default.AccessibleForward, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(14.dp))
              Text("Elderly / Mobility", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = NeonEmerald)
            }

            // Pet
            Row(
              modifier = Modifier
                .clip(CircleShape)
                .background(WarningAmber.copy(alpha = 0.15f))
                .border(1.dp, WarningAmber.copy(alpha = 0.3f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Default.Pets, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
              Text("Pet-Friendly (1 Dog)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WarningAmber)
            }

            // Oxygen
            Row(
              modifier = Modifier
                .clip(CircleShape)
                .background(TacticalCyan.copy(alpha = 0.15f))
                .border(1.dp, TacticalCyan.copy(alpha = 0.3f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Default.Vaccines, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(14.dp))
              Text("Oxygen / Meds", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TacticalCyan)
            }
          }
        }
      }
}

@Composable
internal fun OfflineResiliencePack(
  uiState: VippattiUiState
) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianContainerLow)
            .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(Icons.Default.CloudSync, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(18.dp))
              Text(
                text = "OFFLINE RESILIENCE PACK",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TacticalOnSurface,
                letterSpacing = 0.5.sp
              )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Box(modifier = Modifier.size(6.dp).background(if (uiState.isOfflineFirstMode) NeonEmerald else TacticalOnSurfaceVariant, CircleShape))
              Text(
                text = if (uiState.isOfflineFirstMode) "Offline-first ON" else "Offline-first OFF",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (uiState.isOfflineFirstMode) NeonEmerald else TacticalOnSurfaceVariant
              )
            }
          }

          // REAL offline map-cache size — measured from the osmdroid tile
          // directory on disk. No fabricated "48 MB / 64 MB" numbers: until a
          // measurement exists the row says so honestly.
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Offline Relief Map Cache", fontSize = 11.sp, color = TacticalOnSurfaceVariant)
            Text(
              text = uiState.tileCacheSizeLabel ?: "Not measured yet",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
          }

          LinearProgressIndicator(
            progress = {
              uiState.tileCacheBytes?.let { bytes ->
                (bytes.toFloat() / OFFLINE_MAP_CACHE_TARGET_BYTES).coerceIn(0f, 1f)
              } ?: 0f
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp)),
            color = TacticalCyan,
            trackColor = ObsidianContainerHigh
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.2f))
              .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Icon(Icons.Default.MyLocation, contentDescription = null, tint = TacticalOnSurfaceVariant, modifier = Modifier.size(14.dp))
              Text(
                text = uiState.disasterLastSyncMillis?.let { millis ->
                  "Disaster sync: " + NewsPresentation.relativeAge(millis, System.currentTimeMillis())
                } ?: "Disaster sync: not yet run",
                fontSize = 11.sp,
                color = TacticalOnSurfaceVariant
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Icon(Icons.Default.Verified, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(14.dp))
              Text(
                text = "Data: ${uiState.disasterDataStatusLabel}",
                fontSize = 11.sp,
                color = NeonEmerald
              )
            }
          }
        }
      }
}

private const val OFFLINE_MAP_CACHE_TARGET_BYTES: Long = 64L * 1000L * 1000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
  uiState: VippattiUiState,
  accountEmail: String? = null,
  onSignOut: () -> Unit = {},
  onToggleTheme: () -> Unit,
  onSetSafety: (Boolean) -> Unit,
  onBroadcastSos: () -> Unit,
  onOpenAddContact: () -> Unit,
  onOpenEditProfile: () -> Unit,
  onOpenSituationReport: () -> Unit,
  /** Opens the AUTHORITY CONSOLE (field registry + relocation prioritization). */
  onOpenAuthorityConsole: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val infiniteTransition = rememberInfiniteTransition(label = "profile_anim")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.8f,
    targetValue = 1.3f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "profile_live_pulse"
  )

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianSurface)
      .padding(bottom = 8.dp)
  ) {
    // 1. Header Bar
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(ObsidianSurface)
          .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f))
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(EmergencyRedContainer.copy(alpha = 0.3f))
              .border(1.dp, EmergencyRed.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Outlined.Shield,
              contentDescription = null,
              tint = EmergencyRedBright,
              modifier = Modifier.size(20.dp)
            )
          }
          Column {
            Text(
              text = "VIPPATTI SARANA",
              fontSize = 11.sp,
              fontWeight = FontWeight.Black,
              color = EmergencyRedBright,
              letterSpacing = 0.8.sp
            )
            Text(
              text = "Profile & Emergency Net",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
            if (!accountEmail.isNullOrBlank()) {
              Text(
                text = String.format(Locale.US, "%s \u2022 SARANA", accountEmail),
                fontSize = 9.sp,
                color = TacticalOnSurfaceVariant,
                maxLines = 1
              )
            }
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (!accountEmail.isNullOrBlank()) {
            IconButton(
              onClick = onSignOut,
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(ObsidianContainer)
                .testTag("profile_sign_out_button")
            ) {
              Icon(
                imageVector = Icons.Default.Logout,
                contentDescription = "Sign Out",
                tint = EmergencyRed,
                modifier = Modifier.size(17.dp)
              )
            }
          }
          IconButton(
            onClick = onToggleTheme,
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(ObsidianContainer)
              .testTag("profile_theme_toggle_button")
          ) {
            Icon(
              imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
              contentDescription = "Theme",
              tint = if (uiState.isDarkTheme) WarningAmber else TacticalOnSurface,
              modifier = Modifier.size(17.dp)
            )
          }

          IconButton(
            onClick = { /* Open settings */ },
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(ObsidianContainer)
              .testTag("profile_settings_button")
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Settings",
              tint = TacticalOnSurface,
              modifier = Modifier.size(17.dp)
            )
          }
        }
      }
    }

    // 2. Distress Signal Center (Red Gradient Hero Card)
    item {
      DistressSignalCenter(
        onBroadcastSos = onBroadcastSos,
        onOpenSituationReport = onOpenSituationReport,
        pulseScale = pulseScale
      )
    }
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianContainerLow)
            .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // User Details Header Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              // Flex so long citizen names wrap and the blood-group chip +
              // edit button stay fully visible on any width.
              modifier = Modifier.weight(1f)
            ) {
              Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianContainerHigh)
                    .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(14.dp)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = TacticalOnSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                  )
                }
                Box(
                  modifier = Modifier
                    .size(12.dp)
                    .background(NeonEmerald, CircleShape)
                    .border(2.dp, ObsidianContainerLow, CircleShape)
                )
              }

              Column {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Text(
                    text = uiState.userProfile.fullName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TacticalOnSurface
                  )
                  Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = "Verified ID",
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(16.dp)
                  )
                }
                Text(
                  text = "ID: ${uiState.userProfile.citizenId}",
                  fontSize = 12.sp,
                  color = TacticalOnSurfaceVariant
                )
              }
            }

            Box(
              modifier = Modifier
                .clip(CircleShape)
                .background(EmergencyRedContainer.copy(alpha = 0.4f))
                .border(1.dp, EmergencyRed.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
              Text(
                text = uiState.userProfile.bloodGroupLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = EmergencyRedBright
              )
            }

            // Edit Profile — opens the editable citizen identity dialog
            IconButton(
              onClick = onOpenEditProfile,
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(ObsidianContainer)
                .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), CircleShape)
                .testTag("profile_edit_button")
            ) {
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Profile",
                tint = TacticalCyan,
                modifier = Modifier.size(15.dp)
              )
            }
          }

          // Safety Status Switcher
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(ObsidianContainer)
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
              .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            // I AM SAFE
            Row(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (uiState.userIsSafe) NeonEmerald else Color.Transparent)
                .clickable { onSetSafety(true) }
                .padding(vertical = 8.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (uiState.userIsSafe) OnNeonEmerald else TacticalOnSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "I AM SAFE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (uiState.userIsSafe) OnNeonEmerald else TacticalOnSurfaceVariant
              )
            }

            // NEED ASSISTANCE
            Row(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (!uiState.userIsSafe) EmergencyRed else Color.Transparent)
                .clickable { onSetSafety(false) }
                .padding(vertical = 8.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = if (!uiState.userIsSafe) Color.White else TacticalOnSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "NEED ASSISTANCE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (!uiState.userIsSafe) Color.White else TacticalOnSurfaceVariant
              )
            }
          }

          // Two Info Cards (Dependents & Medical)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Dependents
            Column(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianContainer)
                .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text("Family Dependents", fontSize = 11.sp, color = TacticalOnSurfaceVariant)
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp)
              ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = EmergencyRedBright, modifier = Modifier.size(16.dp))
                Text(uiState.userProfile.dependentsLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
              }
              Text(uiState.userProfile.dependentsDetail, fontSize = 10.sp, color = TacticalOnSurfaceVariant)
            }

            // Medical
            Column(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianContainer)
                .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text("Medical Attention Tag", fontSize = 11.sp, color = TacticalOnSurfaceVariant)
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp)
              ) {
                Icon(Icons.Default.MedicalInformation, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                Text(uiState.userProfile.medicalTag, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
              }
              Text(uiState.userProfile.medicalNotes, fontSize = 10.sp, color = TacticalOnSurfaceVariant)
            }
          }
        }
      }
    }
    // 4. Disaster Dispatch (Toll-Free) Priority Lines
    item { DispatchPriorityLines() }
    // 5. Family & Neighborhood Kin
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "FAMILY & NEIGHBORHOOD KIN",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurfaceVariant,
            letterSpacing = 0.6.sp
          )
          Row(
            modifier = Modifier
              .clickable { onOpenAddContact() }
              .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = EmergencyRedBright, modifier = Modifier.size(14.dp))
            Text("+ Add Contact", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmergencyRedBright)
          }
        }
      }
    }

    if (uiState.contactsList.isEmpty()) {
      item {
        Text(
          "No emergency contacts yet — add family or neighbours so your local SOS\n" +
            "record and go-bag checklists can reference them. (Contacts are stored\n" +
            "on this device only and nothing is transmitted from this build.)",
          fontSize = 9.sp, color = TacticalOnSurfaceVariant, lineHeight = 12.sp,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )
      }
    }

    items(uiState.contactsList) { contact ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianContainerLow)
            .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // Flex so long names wrap safely and the call/SOS buttons stay on screen.
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(contact.colorHex).copy(alpha = 0.25f)),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = contact.initials,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = Color(contact.colorHex)
              )
            }

            Column {
              Text(
                text = "${contact.name} (${contact.role})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TacticalOnSurface
              )
              Text(
                text = "${contact.phone} • ${contact.locationNote}",
                fontSize = 11.sp,
                color = TacticalOnSurfaceVariant
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Call button
            IconButton(
              onClick = {
                try {
                  val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone.replace(" ", "")}"))
                  context.startActivity(intent)
                } catch (e: Exception) {
                  Toast.makeText(context, "Cannot dial ${contact.name}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianContainer)
            ) {
              Icon(Icons.Default.Call, contentDescription = "Call", tint = TacticalOnSurface, modifier = Modifier.size(16.dp))
            }

            // SOS SMS button
            Button(
              onClick = {
                try {
                  val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.phone.replace(" ", "")}")).apply {
                    // Fallback India-centre coordinates until a REAL GPS fix is applied.
                    val locationTag = if (uiState.isUserLocationFallback) " (INDIA FALLBACK)" else " (DEVICE GPS)"
                    putExtra(
                      "sms_body",
                      "EMERGENCY SOS: I need assistance! GPS: " +
                        String.format("%.4f", uiState.userLocation.lat) + " N, " +
                        String.format("%.4f", uiState.userLocation.lon) + " E" +
                        locationTag + ". Sent via VIPPATTI SARANA."
                    )
                  }
                  context.startActivity(smsIntent)
                } catch (e: Exception) {
                  Toast.makeText(context, "Cannot send SMS: ${e.message}", Toast.LENGTH_SHORT).show()
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = EmergencyRedContainer.copy(alpha = 0.3f),
                contentColor = EmergencyRedBright
              ),
              shape = RoundedCornerShape(8.dp),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
              modifier = Modifier.height(32.dp)
            ) {
              Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(13.dp))
              Spacer(modifier = Modifier.width(3.dp))
              Text("SOS SMS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // 6. Evacuation Shelter Needs
    item { EvacuationShelterNeeds() }
    // 7. Relocation Intelligence (household evacuation priority)
    item {
      val plan = uiState.relocationPlan
      if (plan != null) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianContainerLow)
            .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Default.HolidayVillage, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(18.dp))
              Text(
                text = "RELOCATION INTELLIGENCE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TacticalOnSurface,
                letterSpacing = 0.5.sp
              )
            }
            Text(
              text = plan.priorityBand,
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              color = EmergencyRedBright
            )
          }

          Text(
            text = plan.bandExplanation,
            fontSize = 11.sp,
            color = TacticalOnSurface,
            lineHeight = 15.sp
          )

          if (plan.assignedShelter != null) {
            Text(
              text = "Assigned shelter: ${plan.assignedShelter.zone.name} (${plan.assignedShelter.capacityReport.availableCapacity} spots available)",
              fontSize = 11.sp,
              color = NeonEmerald,
              fontWeight = FontWeight.Bold
            )
          }
          // Population & demand: baseline, affected and the figure actually used
          // for capacity assessment — each labelled for what it is.
          Text(
            text = "POPULATION & DEMAND",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = TacticalCyan,
            letterSpacing = 0.6.sp
          )
          Text(
            text = "Baseline population: ${uiState.baselinePopulationLabel}",
            fontSize = 11.sp,
            color = TacticalOnSurface
          )
          Text(
            text = "Affected population: ${uiState.affectedPopulationLabel}",
            fontSize = 11.sp,
            color = TacticalOnSurface
          )
          Text(
            text = "Relocation demand: ${uiState.relocationDemandLabel}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = when (uiState.populationResolution?.demand?.state) {
              com.example.data.capacity.ResourceDataState.SIMULATED -> TacticalCyan
              com.example.data.capacity.ResourceDataState.USER_DECLARED -> WarningAmber
              com.example.data.capacity.ResourceDataState.NOT_PROVIDED -> TacticalOnSurfaceVariant
              else -> NeonEmerald
            }
          )
          uiState.populationResolution?.let { resolution ->
            resolution.demand.referenceMillis?.let { reference ->
              Text(
                text = "Demand reference: " +
                  com.example.data.news.NewsPresentation.relativeAge(reference, System.currentTimeMillis()) +
                  " (${resolution.demand.source})",
                fontSize = 9.sp,
                color = TacticalOnSurfaceVariant
              )
            } ?: Text(
              text = "Demand reference time: not stated by the source (${resolution.demand.source})",
              fontSize = 9.sp,
              color = TacticalOnSurfaceVariant
            )
            Text(
              text = "Population source status: ${resolution.statusLabel}",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurfaceVariant
            )
          }
          // A failed fetch is stated plainly; the previous figures stay in use.
          uiState.populationSourceError?.let { error ->
            Text(
              text = error,
              fontSize = 9.sp,
              color = WarningAmber,
              lineHeight = 12.sp
            )
          }

          // Carrying-capacity verdict for the assigned destination: required vs
          // effective capacity, limiting resource and the honest reason.
          plan.capacityAssessment?.let { assessment ->
            Text(
              text = "Capacity check: ${assessment.status.label.uppercase()} — " +
                "required ${assessment.demand.people?.toString() ?: "not available"}, " +
                "effective ${assessment.effectiveCapacity?.toString() ?: "not available"}, " +
                "limiting ${assessment.limitingResource?.label ?: "not identified"}",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = when (assessment.status) {
                com.example.data.capacity.FeasibilityStatus.FEASIBLE -> NeonEmerald
                com.example.data.capacity.FeasibilityStatus.INFEASIBLE -> EmergencyRedBright
                com.example.data.capacity.FeasibilityStatus.SIMULATED -> TacticalCyan
                com.example.data.capacity.FeasibilityStatus.INSUFFICIENT_DATA -> WarningAmber
              }
            )
          }
          if (plan.feasibilityNote != null) {
            Text(
              text = plan.feasibilityNote,
              fontSize = 10.sp,
              color = TacticalOnSurfaceVariant,
              lineHeight = 14.sp
            )
          }
          // Ranked sites the capacity check skipped, with their own shortfall.
          if (plan.skippedSites.isNotEmpty()) {
            Text(
              text = "Capacity-checked sites not assigned:",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = WarningAmber
            )
            plan.skippedSites.forEach { skipped ->
              Text(
                text = "• ${skipped.siteName} — ${skipped.reason}" +
                  "${skipped.status?.let { status -> " (${status.status.label}, limiting " +
                    "${status.limitingResource?.label?.lowercase() ?: "not identified"})" } ?: ""}",
                fontSize = 10.sp,
                color = TacticalOnSurfaceVariant,
                lineHeight = 14.sp
              )
            }
          }
          if (plan.overflowNote != null) {
            Text(
              text = plan.overflowNote,
              fontSize = 10.sp,
              color = WarningAmber
            )
          }
          Text(
            text = "Vulnerable priority categories: Elderly • Children • Persons with disabilities • Pregnant women • Medical dependency — household data is entered or connected later; no population statistics are fabricated.",
            fontSize = 9.sp,
            color = TacticalOnSurfaceVariant,
            lineHeight = 12.sp
          )
        }
      }
    }
    // 7. Offline Resilience Pack
    item { OfflineResiliencePack(uiState = uiState) }

    // 8. AUTHORITY CONSOLE (SIH 26191) — field registry + relocation
    //    prioritization for survey operators / district officials.
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(ObsidianContainerLow)
          .border(1.dp, TacticalCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
          .clickable(onClick = onOpenAuthorityConsole)
          .padding(14.dp)
      ) {
        Text(
          "AUTHORITY CONSOLE",
          fontSize = 12.sp, fontWeight = FontWeight.Black, color = TacticalCyan,
          letterSpacing = 0.6.sp
        )
        Text(
          "Enter field shelter & habitation records and rank habitations for " +
            "IMMEDIATE / SHORT-TERM / MEDIUM-TERM relocation against the live " +
            "hazard picture. Records stay on this device; demo rows stay labelled SIMULATED.",
          fontSize = 9.sp,
          color = TacticalOnSurfaceVariant,
          lineHeight = 12.sp,
          modifier = Modifier.padding(top = 4.dp)
        )
      }
    }
  }
}
