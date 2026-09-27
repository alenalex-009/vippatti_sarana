package com.example.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber

/**
 * REPORT MY SITUATION - citizen-to-NDRF reporting channel.
 *
 * Three input modes:
 *  1. VOICE - device speech recognizer (RecognizerIntent) dictates the
 *     situation; no runtime permission needed for the intent flow.
 *  2. FORM - quick situation tags + free-form typed description.
 *  3. PHOTO - modern Photo Picker (PickVisualMedia), permissionless; the
 *     evidence preview is rendered with Coil and attached to the
 *     EmergencyReport relayed through EmergencyReportService to the NDRF
 *     ward dispatcher.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SituationReportDialog(
  reporterName: String,
  locationLabel: String,
  batteryLabel: String,
  isSubmitting: Boolean,
  onDismiss: () -> Unit,
  onSubmit: (message: String, photoUri: String?) -> Unit
) {
  var description by remember { mutableStateOf("") }
  var photoUri by remember { mutableStateOf<String?>(null) }
  var voiceNote by remember { mutableStateOf<String?>(null) }

  val quickTags = listOf(
    "Need drinking water", "Need food", "Medical help",
    "Trapped / cannot move", "Roof leaking", "Power lines down"
  )

  // VOICE input via the system speech recognizer.
  val voiceLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val spoken = result.data
        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull()
      if (!spoken.isNullOrBlank()) {
        description = if (description.isBlank()) spoken else "${description.trimEnd()} $spoken"
        voiceNote = null
      }
    }
  }

  // PHOTO evidence via the permissionless Photo Picker.
  val photoPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    photoUri = uri?.toString()
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = ObsidianSurface,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          // Keep the submit button reachable while the keyboard is open.
          .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = stringResource(R.string.report_title),
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
            Text(
              text = stringResource(R.string.report_subtitle),
              fontSize = 12.sp,
              color = TacticalOnSurfaceVariant
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dialog_close_content_desc), tint = TacticalOnSurfaceVariant)
          }
        }

        // --- Voice dictate button -------------------------------------------
        OutlinedButton(
          onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
              putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
              )
              putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your situation")
            }
            try {
              voiceLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
              voiceNote = "Speech input not available on this device - type your report below"
            }
          },
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = TacticalCyan),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("situation_voice_button")
        ) {
          Icon(Icons.Default.Mic, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.report_dictate), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        voiceNote?.let { note ->
          Text(note, fontSize = 10.sp, color = WarningAmber)
        }

        // --- Quick situation tags -------------------------------------------
        // FlowRow wraps tags naturally — no overflow at 360dp or large font scales.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(stringResource(R.string.report_quick_tags), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurfaceVariant, letterSpacing = 0.5.sp)
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            quickTags.forEach { tag ->
              QuickTagChip(label = tag) {
                description = if (description.isBlank()) tag else "${description.trimEnd()} | $tag"
              }
            }
          }
        }

        // --- Free-form description -------------------------------------------
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text(stringResource(R.string.report_description), fontSize = 12.sp) },
          minLines = 3,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("situation_description_input")
        )

        // --- Photo evidence ---------------------------------------------------
        if (photoUri == null) {
          OutlinedButton(
            onClick = {
              photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningAmber),
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
              .testTag("situation_photo_button")
          ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.report_attach_photo), fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianContainerLow)
                .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(12.dp))
            ) {
              AsyncImage(
                model = photoUri,
                contentDescription = "Report photo evidence",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(140.dp)
              )
              IconButton(
                onClick = { photoUri = null },
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(4.dp)
                  .size(28.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(ObsidianSurface.copy(alpha = 0.85f))
                  .testTag("situation_photo_remove_button")
              ) {
                Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = TacticalOnSurface, modifier = Modifier.size(16.dp))
              }
            }
            Text(stringResource(R.string.report_photo_saved), fontSize = 12.sp, color = NeonEmerald)
          }
        }

        // --- Local record preview (nothing leaves this device) ------------------
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianContainerLow)
            .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(12.dp))
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.report_reporting_as), fontSize = 11.sp, color = TacticalOnSurfaceVariant)
            Text(reporterName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.sos_label_gps), fontSize = 11.sp, color = TacticalOnSurfaceVariant)
            Text(locationLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.sos_label_battery), fontSize = 11.sp, color = TacticalOnSurfaceVariant)
            Text(batteryLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          }
        }

        Button(
          onClick = { onSubmit(description, photoUri) },
          enabled = !isSubmitting && description.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("situation_submit_button")
        ) {
          Icon(Icons.Default.Send, contentDescription = null, tint = OnNeonEmerald, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            if (isSubmitting) "Saving locally..." else "SAVE REPORT ON THIS DEVICE",
            fontWeight = FontWeight.Bold,
            color = OnNeonEmerald,
            fontSize = 12.sp
          )
        }
      }
    }
  }
}

@Composable
private fun QuickTagChip(label: String, onAppend: (String) -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(ObsidianContainerHigh)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
      .clickable { onAppend(label) }
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(label, fontSize = 11.sp, color = TacticalOnSurface, fontWeight = FontWeight.SemiBold)
  }
}

