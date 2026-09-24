package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.instructions.CommonModule
import com.example.data.instructions.DisasterCategory
import com.example.data.instructions.DisasterInstructions
import com.example.data.instructions.InstructionItem
import com.example.data.instructions.InstructionPhase
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.OnTacticalCyan
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalCyanContainer
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant

// ============================================================================
// DETAIL SCREENS â€” back header + full existing instruction content
// ============================================================================

/** Shared detail-screen header: WORKING back button, icon, title, context subtitle. */
@Composable
internal fun DetailHeader(
  icon: ImageVector,
  iconTint: Color,
  title: String,
  subtitle: String,
  onBack: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(
      onClick = onBack,
      modifier = Modifier
        .size(34.dp)
        .clip(CircleShape)
        .background(ObsidianContainer)
        .testTag("instructions_back_button")
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.instructions_back),
        tint = TacticalOnSurface,
        modifier = Modifier.size(18.dp)
      )
    }
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(iconTint.copy(alpha = 0.15f))
        .border(1.dp, iconTint.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontSize = 15.sp, fontWeight = FontWeight.Black, color = TacticalOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(subtitle, fontSize = 10.sp, color = TacticalOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/**
 * CATEGORY DETAIL â€” shows ALL items of the tapped instruction group with
 * the existing critical/safety visual language, nothing omitted.
 */
@Composable
internal fun GroupDetailScreen(
  route: String,
  category: DisasterCategory,
  phase: InstructionPhase,
  onBack: () -> Unit
) {
  // route = "group:<categoryId>:<phaseId>:<groupId>"
  val parts = route.split(":")
  val groupId = parts.getOrNull(3) ?: "immediate_safety"
  val group = buildInstructionGroups(category, phase).firstOrNull { it.id == groupId }
    ?: buildInstructionGroups(category, phase).first()

  Column(modifier = Modifier.fillMaxWidth()) {
    val (icon, accent) = groupVisual(group.id)
    val (groupTitle, groupSubtitle) = groupLabels(group.id, category.title)
    DetailHeader(
      icon = icon,
      iconTint = accent,
      title = groupTitle,
      subtitle = groupSubtitle,
      onBack = onBack
    )
    Text(
      text = stringResource(R.string.instructions_detail_intro),
      fontSize = 11.sp,
      color = TacticalOnSurfaceVariant,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
    )
    group.items.forEachIndexed { index, item ->
      InstructionDetailCard(
        item,
        stepLabel = stringResource(
          R.string.instructions_step_format,
          index + 1,
          group.items.size
        )
      )
    }
  }
}

/**
 * Common-module detail (Evacuation Essentials and any future module) â€”
 * renders the existing common-module items with the same visual language.
 */
@Composable
internal fun ModuleDetailScreen(module: CommonModule, onBack: () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth()) {
    DetailHeader(
      icon = Icons.Default.DirectionsWalk,
      iconTint = NeonEmerald,
      title = module.title,
      subtitle = module.subtitle,
      onBack = onBack
    )
    Text(
      text = "Your safety comes first. Follow these instructions to reduce risk.",
      fontSize = 11.sp,
      color = TacticalOnSurfaceVariant,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
    )
    module.items.forEach { item ->
      InstructionDetailCard(item)
    }
  }
}

/** Full instruction card â€” critical items escalate to the red emergency look. */
@Composable
private fun InstructionDetailCard(
  item: InstructionItem,
  stepLabel: String? = null
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(
        if (item.isCritical) EmergencyRedContainer.copy(alpha = 0.3f)
        else ObsidianContainer.copy(alpha = 0.7f)
      )
      .border(
        1.dp,
        if (item.isCritical) EmergencyRed.copy(alpha = 0.55f) else TacticalOutlineVariant.copy(alpha = 0.3f),
        RoundedCornerShape(12.dp)
      )
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(
        modifier = Modifier
          .size(22.dp)
          .clip(CircleShape)
          .background(if (item.isCritical) EmergencyRed else TacticalCyanContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (item.isCritical) Icons.Default.CrisisAlert else Icons.Default.CheckCircle,
          contentDescription = null,
          tint = if (item.isCritical) Color.White else OnTacticalCyan,
          modifier = Modifier.size(13.dp)
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.title,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurface
        )
        if (stepLabel != null) {
          Text(
            text = stepLabel,
            fontSize = 11.sp,
            color = TacticalOnSurfaceVariant,
            letterSpacing = 0.4.sp
          )
        }
      }
      if (item.isCritical) {
        Text(
          text = stringResource(R.string.instructions_critical_badge),
          fontSize = 10.sp,
          fontWeight = FontWeight.Black,
          color = EmergencyRedBright,
          letterSpacing = 0.5.sp
        )
      }
    }
    Text(
      text = item.detail,
      fontSize = 11.sp,
      color = TacticalOnSurfaceVariant,
      lineHeight = 15.sp
    )
    if (item.region != null) {
      Text(
        text = stringResource(R.string.instructions_region_format, item.region),
        fontSize = 11.sp,
        color = TacticalCyan
      )
    }
  }
}

// ============================================================================
// EMERGENCY CONTACTS â€” dedicated detail screen with DIAL functionality
// ============================================================================

/**
 * Dedicated Emergency Contacts screen. Renders the EXISTING official lines
 * from the emergency_contacts common module; each phone-enabled entry gets a
 * real dial button (ACTION_DIAL) exactly like the Profile screen's quick-dial
 * tiles â€” nothing is transmitted without explicit user action.
 */
@Composable
internal fun ContactsDetailScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val contactsModule = DisasterInstructions.commonModules.firstOrNull { it.id == "emergency_contacts" }
    ?: return

  fun dial(number: String) {
    try {
      context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number.filter { it.isDigit() }}")))
    } catch (_: Exception) {
      // No dialer on this device â€” silently ignored, list stays informative.
    }
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    DetailHeader(
      icon = Icons.Default.Call,
      iconTint = EmergencyRed,
      title = "Emergency Contacts",
      subtitle = "Official Indian emergency lines",
      onBack = onBack
    )
    contactsModule.items.forEach { item ->
      val number = item.title.takeWhile { it.isDigit() }
      val hasNumber = number.isNotEmpty()
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(if (item.isCritical) EmergencyRedContainer.copy(alpha = 0.3f) else ObsidianContainerLow)
          .border(
            1.dp,
            if (item.isCritical) EmergencyRed.copy(alpha = 0.5f) else TacticalOutlineVariant.copy(alpha = 0.35f),
            RoundedCornerShape(12.dp)
          )
          .clickable(enabled = hasNumber) { if (hasNumber) dial(number) }
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .testTag("contact_card_${number.ifEmpty { "info" }}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (item.isCritical) EmergencyRed.copy(alpha = 0.15f) else TacticalCyan.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (hasNumber) Icons.Default.Call else Icons.Default.Emergency,
            contentDescription = null,
            tint = if (item.isCritical) EmergencyRedBright else TacticalCyan,
            modifier = Modifier.size(18.dp)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          Text(item.detail, fontSize = 10.sp, color = TacticalOnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (hasNumber) {
          Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Dial ${item.title}",
            tint = EmergencyRed,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

// ============================================================================
// EMERGENCY KIT â€” dedicated detail screen from the existing module data
// ============================================================================

/** Icon mapping for kit entries from the existing emergency_kit module. */
private fun kitIconFor(kitTitle: String): ImageVector = when {
  kitTitle.contains("Water", ignoreCase = true) -> Icons.Default.WaterDrop
  kitTitle.contains("food", ignoreCase = true) -> Icons.Default.Restaurant
  kitTitle.contains("First-aid", ignoreCase = true) -> Icons.Default.MedicalServices
  kitTitle.contains("Torch", ignoreCase = true) -> Icons.Default.FlashlightOn
  kitTitle.contains("Whistle", ignoreCase = true) -> Icons.Default.NotificationsActive
  kitTitle.contains("Documents", ignoreCase = true) -> Icons.Default.Description
  kitTitle.contains("Radio", ignoreCase = true) -> Icons.Default.NotificationsActive
  kitTitle.contains("Cash", ignoreCase = true) -> Icons.Default.AttachMoney
  else -> Icons.Default.Backpack
}

@Composable
internal fun KitDetailScreen(onBack: () -> Unit) {
  val kitModule = DisasterInstructions.commonModules.firstOrNull { it.id == "emergency_kit" }
    ?: return

  Column(modifier = Modifier.fillMaxWidth()) {
    DetailHeader(
      icon = Icons.Default.Backpack,
      iconTint = NeonEmerald,
      title = "Emergency Kit",
      subtitle = "72-hour self-reliance pack",
      onBack = onBack
    )
    kitModule.items.forEach { item ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(ObsidianContainerLow)
          .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NeonEmeraldContainer.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(kitIconFor(item.title), contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          Text(item.detail, fontSize = 10.sp, color = TacticalOnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
