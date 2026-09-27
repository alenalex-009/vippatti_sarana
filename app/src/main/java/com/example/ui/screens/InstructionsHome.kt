package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Landslide
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Tsunami
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.instructions.DisasterCategory
import com.example.data.instructions.DisasterInstructions
import com.example.data.instructions.InstructionItem
import com.example.data.instructions.InstructionPhase
import com.example.data.risk.RiskLevel
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState

// ============================================================================
// INSTRUCTIONS â€” TWO CLEAR LEVELS, NOTHING MORE
// ============================================================================
//   HOME     ->  header, offline switch, FOUR full-width disaster cards
//                (approved poster thumbnail + name + short description) and
//                the essential resources (contacts / evacuation / kit).
//   DISASTER ->  [Back] [title + short description] [approved poster]
//                [BEFORE | DURING | AFTER] [critical actions now]
//                [instruction categories -> existing detail screens].
//
// No horizontally scrolling selector, no duplicate disaster pickers, no
// decorative layers. Back always pops exactly one level. Every label is a
// localized string resource and every piece of existing guidance is kept â€”
// only the old generated pictograms were removed in favour of the approved
// InstructionImages artwork.
// ============================================================================

/**
 * INSTRUCTIONS HOME â€” the disaster chooser. Four obvious choices plus the
 * essential resources; nothing competes with them.
 */
@Composable
internal fun InstructionsHome(
  uiState: VippattiUiState,
  onToggleTheme: () -> Unit,
  onToggleOfflineAccess: (Boolean) -> Unit,
  onOpenInteractiveBag: () -> Unit,
  onSelectDisaster: (String) -> Unit,
  onNavigate: (String) -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    // 1. Compact header â€” SURVIVAL MANUAL + theme toggle.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f) // title yields space; toggle never pushed off
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(EmergencyRedContainer.copy(alpha = 0.3f))
            .border(1.dp, EmergencyRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CrisisAlert,
            contentDescription = null,
            tint = EmergencyRedBright,
            modifier = Modifier.size(22.dp)
          )
        }
        Column {
          Text(
            text = stringResource(R.string.instructions_manual_title).uppercase(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = TacticalOnSurface,
            letterSpacing = 0.5.sp
          )
          Text(
            text = stringResource(R.string.instructions_manual_subtitle),
            fontSize = 11.sp,
            color = TacticalOnSurfaceVariant
          )
        }
      }
      IconButton(
        onClick = onToggleTheme,
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(ObsidianContainer)
          .testTag("instructions_theme_toggle_button")
      ) {
        Icon(
          imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
          contentDescription = if (uiState.isDarkTheme) "Switch to light mode" else "Switch to dark mode",
          tint = TacticalOnSurface,
          modifier = Modifier.size(18.dp)
        )
      }
    }


    // 2. Offline-first mode â€” compact functional card (switch stays functional).
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(ObsidianContainerLow)
        .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f) // label wraps; switch never pushed off-screen
      ) {
        Icon(
          imageVector = Icons.Default.CloudOff,
          contentDescription = null,
          tint = TacticalCyan,
          modifier = Modifier.size(20.dp)
        )
        Column {
          Text(
            text = stringResource(R.string.instructions_offline_title),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurface
          )
          Text(
            text = stringResource(R.string.instructions_offline_subtitle),
            fontSize = 12.sp,
            color = TacticalOnSurfaceVariant
          )
        }
      }
      Switch(
        checked = uiState.isOfflineFirstMode,
        onCheckedChange = onToggleOfflineAccess,
        colors = SwitchDefaults.colors(checkedTrackColor = NeonEmerald),
        modifier = Modifier.testTag("instructions_offline_switch")
      )
    }

    // 3. Choose a disaster â€” four full-width cards. No horizontal scrolling,
    //    no chips to hunt for: the four choices are the whole screen.
    Text(
      text = stringResource(R.string.instructions_choose_disaster).uppercase(),
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurfaceVariant,
      letterSpacing = 0.6.sp,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
    )
    DisasterInstructions.categories.forEach { category ->
      DisasterCard(category = category, onClick = { onSelectDisaster(category.id) })
    }


    // 4. Essential resources â€” contacts, evacuation module, kit + interactive.
    Text(
      text = stringResource(R.string.instructions_section_resources).uppercase(),
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurfaceVariant,
      letterSpacing = 0.6.sp,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
    )
    CategoryCard(
      title = stringResource(R.string.instructions_resources_contacts),
      subtitle = stringResource(R.string.instructions_resources_contacts_sub),
      icon = Icons.Default.Call,
      accent = EmergencyRed,
      testTag = "category_card_contacts",
      onClick = { onNavigate(InstructionsRoutes.CONTACTS) }
    )
    // Existing "Evacuation Essentials" common module keeps its own detail view.
    DisasterInstructions.commonModules.firstOrNull { it.id == "evacuation" }?.let { evac ->
      val (evacTitle, evacSubtitle) = commonModuleLabels(evac.id, evac.title, evac.subtitle)
      CategoryCard(
        title = evacTitle,
        subtitle = evacSubtitle,
        icon = Icons.Default.Backpack,
        accent = NeonEmerald,
        testTag = "category_card_evacuation",
        onClick = { onNavigate(InstructionsRoutes.module(evac.id)) }
      )
    }
    CategoryCard(
      title = stringResource(R.string.instructions_resources_kit),
      subtitle = stringResource(R.string.instructions_kit_subtitle),
      icon = Icons.Default.Backpack,
      accent = NeonEmerald,
      testTag = "category_card_kit",
      onClick = { onNavigate(InstructionsRoutes.KIT) }
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(ObsidianContainerLow)
        .border(1.dp, NeonEmerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        .clickable { onOpenInteractiveBag() }
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f)
      ) {
        Icon(
          imageVector = Icons.Default.HealthAndSafety,
          contentDescription = null,
          tint = WarningAmber,
          modifier = Modifier.size(20.dp)
        )
        Column {
          Text(
            text = stringResource(R.string.instructions_resources_kit),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurface
          )
          Text(
            text = stringResource(R.string.instructions_resources_kit_sub),
            fontSize = 10.sp,
            color = TacticalOnSurfaceVariant
          )
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = EmergencyRedBright,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}

/**
 * One disaster choice: approved poster thumbnail + clear name + concise
 * description. Full-width row, generous touch target, no decoration.
 */
@Composable
private fun DisasterCard(category: DisasterCategory, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 5.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
      .clickable { onClick() }
      .padding(10.dp)
      .testTag("disaster_card_${category.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    DisasterPosterThumbnail(category.id)
    val (disasterName, disasterSummary) = disasterCategoryLabels(category.id, category.title)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = disasterName,
        fontSize = 15.sp,
        fontWeight = FontWeight.Black,
        color = TacticalOnSurface
      )
      Text(
        text = disasterSummary,
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = NeonEmerald,
      modifier = Modifier.size(20.dp)
    )
  }
}


/**
 * DISASTER DETAIL â€” one disaster on one screen:
 * [Back] [disaster title + short description] [approved poster]
 * [BEFORE | DURING | AFTER] [critical actions now] [instruction categories].
 *
 * The approved poster already carries the whole Before/During/After journey,
 * so it is the visual summary and the written guidance below stays concise â€”
 * no heading is repeated for its own sake, and no existing guidance is lost
 * (every item still lives in its category detail screen).
 */
@Composable
internal fun DisasterDetailHome(
  uiState: VippattiUiState,
  category: DisasterCategory,
  phase: InstructionPhase,
  selectedPhaseId: String,
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
  onSelectPhase: (String) -> Unit,
  onOpenPoster: () -> Unit
) {
  val (disasterName, disasterSummary) = disasterCategoryLabels(category.id, category.title)
  Column(modifier = Modifier.fillMaxWidth()) {
    // 1. Predictable back â€” one tap returns to the Instructions home.
    DetailHeader(
      icon = categoryIcon(category.id),
      iconTint = NeonEmerald,
      title = disasterName,
      subtitle = disasterSummary,
      onBack = onBack
    )

    // 2. The approved disaster poster â€” full width, natural aspect ratio,
    //    nothing stretched or cropped away. Tap to zoom into the captions.
    DisasterInstructionPoster(
      categoryId = category.id,
      disasterTitle = disasterName,
      onOpen = onOpenPoster
    )

    // 3. BEFORE | DURING | AFTER â€” one compact localized segmented control.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(ObsidianContainerLowest)
        .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
        .padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      listOf(
        "before" to stringResource(R.string.instructions_phase_before),
        "during" to stringResource(R.string.instructions_phase_during),
        "after" to stringResource(R.string.instructions_phase_after)
      ).forEach { (id, label) ->
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selectedPhaseId == id) NeonEmerald else Color.Transparent)
            .clickable { onSelectPhase(id) }
            .padding(vertical = 10.dp)
            // Tag on the clickable container (not the inner Text) so the tag
            // survives semantics-merging and the tab stays test-clickable.
            .testTag("phase_${id}_tab"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selectedPhaseId == id) FontWeight.Black else FontWeight.Bold,
            color = if (selectedPhaseId == id) OnNeonEmerald else TacticalOnSurfaceVariant,
            maxLines = 1
          )
        }
      }
    }


    // 4. Critical actions now â€” top flagged items for THIS phase, plus the
    //    REAL personal risk level on the same scannable row.
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Emergency,
        contentDescription = null,
        tint = EmergencyRedBright,
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = stringResource(R.string.instructions_section_critical).uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = EmergencyRedBright,
        letterSpacing = 0.6.sp,
        modifier = Modifier.weight(1f)
      )
      DisasterRiskBadge(riskLevel = uiState.personalRisk?.level)
    }
    Text(
      text = stringResource(R.string.instructions_section_critical_subtitle),
      fontSize = 10.sp,
      color = TacticalOnSurfaceVariant,
      modifier = Modifier.padding(horizontal = 14.dp)
    )
    val criticalItems = phase.items.filter { it.isCritical }
    if (criticalItems.isEmpty()) {
      Text(
        text = stringResource(R.string.instructions_critical_empty),
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
      )
    } else {
      criticalItems.take(3).forEach { item -> CriticalActionRow(item) }
    }

    // 5. Instruction categories â€” the detailed existing guidance, one tap away.
    Text(
      text = stringResource(R.string.instructions_section_categories).uppercase(),
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurfaceVariant,
      letterSpacing = 0.6.sp,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
    )
    buildInstructionGroups(category, phase).forEach { group ->
      val (icon, accent) = groupVisual(group.id)
      val (title, subtitle) = groupLabels(group.id, category.title)
      CategoryCard(
        title = title,
        subtitle = subtitle,
        icon = icon,
        accent = accent,
        testTag = "category_card_${group.id}",
        onClick = { onNavigate(InstructionsRoutes.group(category.id, selectedPhaseId, group.id)) }
      )
    }
  }
}

/** Disaster icon by category id â€” preserved from the existing module. */
private fun categoryIcon(categoryId: String): ImageVector = when (categoryId) {
  "flood" -> Icons.Default.Tsunami
  "landslide" -> Icons.Default.Landslide
  "fire" -> Icons.Default.LocalFireDepartment
  "earthquake" -> Icons.Default.CrisisAlert
  else -> Icons.Default.MenuBook
}


// ============================================================================
// SHARED UI PIECES
// ============================================================================

/** Compact risk badge driven by the REAL personal risk engine. */
@Composable
private fun DisasterRiskBadge(riskLevel: RiskLevel?) {
  val label = when (riskLevel) {
    RiskLevel.RED -> "HIGH RISK"
    RiskLevel.ORANGE -> "ELEVATED RISK"
    RiskLevel.YELLOW -> "WATCH"
    RiskLevel.GREEN -> "LOW RISK"
    null -> "NO DATA"
  }
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(
        when (riskLevel) {
          RiskLevel.RED, RiskLevel.ORANGE -> EmergencyRed.copy(alpha = 0.85f)
          RiskLevel.YELLOW -> WarningAmber.copy(alpha = 0.85f)
          RiskLevel.GREEN -> SafeGreen.copy(alpha = 0.85f)
          null -> ObsidianContainer
        }
      )
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = FontWeight.Black,
      color = when (riskLevel) {
        RiskLevel.RED, RiskLevel.ORANGE -> Color.White
        // Near-black on the amber fill â€” readable in BOTH themes (the old
        // theme-aware value vanished on amber in dark mode and light mode).
        RiskLevel.YELLOW -> Color(0xFF201500)
        RiskLevel.GREEN -> OnNeonEmerald
        null -> TacticalOnSurfaceVariant
      },
      letterSpacing = 0.4.sp,
      maxLines = 1
    )
  }
}


/** Compact category card used for all section navigation. */
@Composable
private fun CategoryCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  accent: Color,
  testTag: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
      .clickable { onClick() }
      .padding(horizontal = 12.dp, vertical = 10.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(accent.copy(alpha = 0.18f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
      Text(subtitle, fontSize = 10.sp, color = TacticalOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = accent,
      modifier = Modifier.size(18.dp)
    )
  }
}

/** Scannable critical action row for the CRITICAL ACTIONS NOW strip. */
@Composable
private fun CriticalActionRow(item: InstructionItem) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 3.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(EmergencyRedContainer.copy(alpha = 0.3f))
      .border(1.dp, EmergencyRed.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Box(
      modifier = Modifier
        .size(22.dp)
        .clip(CircleShape)
        .background(EmergencyRed),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Default.Emergency, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
    }
    Text(
      text = item.title,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurface,
      modifier = Modifier.weight(1f),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = EmergencyRedBright,
      modifier = Modifier.size(16.dp)
    )
  }
}

