package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.UserProfile
import com.example.R
import com.example.data.risk.RelocationPlanner
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
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

/**
 * Editable citizen profile dialog - the identity card's EDIT PROFILE action.
 *
 * Everything the distress pipeline relays (name, blood group, medical tag,
 * dependents, vulnerable categories) is editable here. Saving re-runs the
 * shelter ranking/relocation-priority engines because vulnerable categories
 * and the medical-support flag are inputs to them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileDialog(
  profile: UserProfile,
  onDismiss: () -> Unit,
  onSave: (UserProfile) -> Unit
) {
  var fullName by remember { mutableStateOf(profile.fullName) }
  var citizenId by remember { mutableStateOf(profile.citizenId) }
  var bloodGroup by remember { mutableStateOf(profile.bloodGroupLabel) }
  var medicalTag by remember { mutableStateOf(profile.medicalTag) }
  var medicalNotes by remember { mutableStateOf(profile.medicalNotes) }
  var dependentsCount by remember { mutableStateOf(profile.dependentsCount) }
  var dependentsDetail by remember { mutableStateOf(profile.dependentsDetail) }
  var vulnerableIds by remember { mutableStateOf(profile.vulnerableCategoryIds) }
  var needsMedicalSupport by remember { mutableStateOf(profile.needsMedicalSupport) }

  val bloodGroups = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")

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
          // Keep Save Profile reachable while the keyboard is open.
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
              text = stringResource(R.string.edit_profile_title),
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
            Text(
              // STAGE 7 â€” local-only honesty: reports stay on this device.
              text = stringResource(R.string.edit_profile_local_note),
              fontSize = 11.sp,
              color = TacticalOnSurfaceVariant
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dialog_close_content_desc), tint = TacticalOnSurfaceVariant)
          }
        }

        OutlinedTextField(
          value = fullName,
          onValueChange = { fullName = it },
          label = { Text(stringResource(R.string.edit_profile_full_name), fontSize = 12.sp) },
          leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TacticalOnSurfaceVariant, modifier = Modifier.size(18.dp)) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_profile_name_input")
        )

        OutlinedTextField(
          value = citizenId,
          onValueChange = { citizenId = it },
          label = { Text(stringResource(R.string.edit_profile_citizen_id), fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_profile_id_input")
        )

        // Blood group chip selector
        // FlowRow wraps chips naturally â€” no overflow at 360dp or large font scales.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(stringResource(R.string.edit_profile_blood_group), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmergencyRedBright, letterSpacing = 0.5.sp)
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            bloodGroups.forEach { group ->
              BloodGroupChip(
                label = group,
                selected = bloodGroup == group,
                onClick = { bloodGroup = group }
              )
            }
          }
        }

        OutlinedTextField(
          value = medicalTag,
          onValueChange = { medicalTag = it },
          label = { Text(stringResource(R.string.edit_profile_medical_tag), fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_profile_medical_input")
        )

        OutlinedTextField(
          value = medicalNotes,
          onValueChange = { medicalNotes = it },
          label = { Text(stringResource(R.string.edit_profile_medical_notes), fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_profile_medical_notes_input")
        )

        // Dependents stepper
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(stringResource(R.string.edit_profile_dependents), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurfaceVariant, letterSpacing = 0.5.sp)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(ObsidianContainer)
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
              .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = { if (dependentsCount > 0) dependentsCount-- },
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .testTag("dependents_decrease_button")
            ) {
              Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.edit_profile_remove_dependent), tint = EmergencyRedBright, modifier = Modifier.size(18.dp))
            }
            Text(
              text = "$dependentsCount Dependents",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
            IconButton(
              onClick = { dependentsCount++ },
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .testTag("dependents_increase_button")
            ) {
              Icon(Icons.Default.Add, contentDescription = stringResource(R.string.edit_profile_add_dependent), tint = NeonEmerald, modifier = Modifier.size(18.dp))
            }
          }
        }

        OutlinedTextField(
          value = dependentsDetail,
          onValueChange = { dependentsDetail = it },
          label = { Text(stringResource(R.string.edit_profile_dependents_detail), fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_profile_dependents_detail_input")
        )

        // Vulnerable categories â€” feed shelter ranking & relocation priority
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            "VULNERABLE MEMBERS (drives shelter priority)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalCyan,
            letterSpacing = 0.5.sp
          )
          RelocationPlanner.VULNERABLE_CATEGORIES.forEach { category ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianContainerLow)
                .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .clickable {
                  vulnerableIds = if (category.id in vulnerableIds) {
                    vulnerableIds - category.id
                  } else {
                    vulnerableIds + category.id
                  }
                }
                .padding(horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = category.id in vulnerableIds,
                onCheckedChange = { checked ->
                  vulnerableIds = if (checked) vulnerableIds + category.id else vulnerableIds - category.id
                },
                colors = CheckboxDefaults.colors(
                  checkedColor = NeonEmerald,
                  uncheckedColor = TacticalOutlineVariant
                )
              )
              Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(category.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TacticalOnSurface)
                Text(category.supportNeeds, fontSize = 10.sp, color = TacticalOnSurfaceVariant)
              }
            }
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(ObsidianContainerLow)
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
              .clickable { needsMedicalSupport = !needsMedicalSupport }
              .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = needsMedicalSupport,
              onCheckedChange = { needsMedicalSupport = it },
              colors = CheckboxDefaults.colors(
                checkedColor = WarningAmber,
                uncheckedColor = TacticalOutlineVariant
              )
            )
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
              Text(stringResource(R.string.edit_profile_need_medical), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TacticalOnSurface)
              Text(stringResource(R.string.edit_profile_need_medical_sub), fontSize = 10.sp, color = TacticalOnSurfaceVariant)
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
          onClick = {
            onSave(
              UserProfile(
                fullName = fullName.trim().ifBlank { profile.fullName },
                citizenId = citizenId.trim().ifBlank { profile.citizenId },
                bloodGroup = bloodGroup,
                medicalTag = medicalTag.trim(),
                medicalNotes = medicalNotes.trim(),
                dependentsCount = dependentsCount,
                dependentsDetail = dependentsDetail.trim(),
                vulnerableCategoryIds = vulnerableIds,
                needsMedicalSupport = needsMedicalSupport
              )
            )
          },
          enabled = fullName.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("save_profile_button")
        ) {
          Text(stringResource(R.string.edit_profile_save_button), fontWeight = FontWeight.Bold, color = OnNeonEmerald)
        }
      }
    }
  }
}

@Composable
private fun BloodGroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(if (selected) EmergencyRedBright.copy(alpha = 0.25f) else ObsidianContainerHigh)
      .border(
        1.dp,
        if (selected) EmergencyRedBright else TacticalOutlineVariant.copy(alpha = 0.5f),
        RoundedCornerShape(8.dp)
      )
      .clickable { onClick() }
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Text(
      text = label,
      fontSize = 12.sp,
      fontWeight = FontWeight.Black,
      color = if (selected) EmergencyRedBright else TacticalOnSurface
    )
  }
}


