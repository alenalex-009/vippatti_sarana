package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.data.disaster.GoBagItem
import com.example.R
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant

@Composable
fun InteractiveBagDialog(
  items: List<GoBagItem>,
  onToggleItem: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val checkedCount = items.count { it.isChecked }
  val progress = if (items.isNotEmpty()) checkedCount.toFloat() / items.size else 0f
  val progressPercent = (progress * 100).toInt()

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = ObsidianSurface,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(20.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .fillMaxWidth()
          // Adapts to short screens: the checklist scrolls INTERNALLY and the
          // bottom action button always stays visible â€” never covers items.
          .heightIn(max = 420.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.gobag_kit_title),
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
            Text(
              text = "$checkedCount of ${items.size} items packed",
              fontSize = 11.sp,
              color = TacticalOnSurfaceVariant
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dialog_close_content_desc), tint = TacticalOnSurfaceVariant)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
          color = NeonEmerald,
          trackColor = ObsidianContainerHigh,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "$progressPercent% packed",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (progressPercent == 100) NeonEmerald else TacticalOnSurfaceVariant,
          modifier = Modifier.align(Alignment.End)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Internally scrollable checklist â€” no item is ever clipped.
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(items) { item ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianContainerLow)
                .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable { onToggleItem(item.id) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = item.isChecked,
                onCheckedChange = { onToggleItem(item.id) },
                colors = CheckboxDefaults.colors(
                  checkedColor = NeonEmerald,
                  uncheckedColor = TacticalOutlineVariant
                )
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.name,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = if (item.isChecked) NeonEmerald else TacticalOnSurface
                )
                Text(
                  text = item.detail,
                  fontSize = 11.sp,
                  color = TacticalOnSurfaceVariant
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
          onClick = onDismiss,
          colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("interactive_bag_done_button")
        ) {
          Text(
            if (progressPercent == 100) "Kit Complete â€” Done" else "Mark as Complete",
            fontWeight = FontWeight.Bold,
            color = OnNeonEmerald
          )
        }
      }
    }
  }
}

@Composable
fun AddContactDialog(
  onDismiss: () -> Unit,
  onAddContact: (name: String, relation: String, phone: String, location: String) -> Unit
) {
  var name by remember { mutableStateOf("") }
  var relation by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var location by remember { mutableStateOf("") }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = ObsidianSurface,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(20.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .fillMaxWidth()
          // Form scrolls and stays clear of the keyboard on short screens.
          .verticalScroll(rememberScrollState())
          .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(R.string.gobag_kin_contact_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurface
          )
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dialog_close_content_desc), tint = TacticalOnSurfaceVariant)
          }
        }

        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.gobag_full_name), fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_name_input")
        )

        OutlinedTextField(
          value = relation,
          onValueChange = { relation = it },
          label = { Text(stringResource(R.string.gobag_relationship), fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_relation_input")
        )

        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text(stringResource(R.string.gobag_phone), fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_phone_input")
        )

        OutlinedTextField(
          value = location,
          onValueChange = { location = it },
          label = { Text(stringResource(R.string.gobag_proximity), fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_location_input")
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
          onClick = {
            if (name.isNotBlank() && phone.isNotBlank()) {
              onAddContact(name, relation.ifBlank { "Family" }, phone, location.ifBlank { "Nearby" })
            }
          },
          enabled = name.isNotBlank() && phone.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("save_contact_button")
        ) {
          Text(stringResource(R.string.gobag_save_button), fontWeight = FontWeight.Bold, color = OnNeonEmerald)
        }
      }
    }
  }
}

// ============================================================================
// HAZARD ZONE DETAIL ? opens from a map hazard-circle tap
// ============================================================================
