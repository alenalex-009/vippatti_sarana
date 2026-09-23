package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Landslide
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tsunami
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.instructions.CommonModule
import com.example.data.instructions.DisasterCategory
import com.example.data.instructions.DisasterInstructions
import com.example.data.instructions.InstructionItem
import com.example.data.instructions.InstructionPhase
import com.example.data.risk.RiskLevel
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.OnTacticalCyan
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalCyanContainer
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState

/**
 * ============================================================================
 * INSTRUCTIONS MODULE — SURVIVAL MANUAL
 * ============================================================================
 * Two clear levels, no redundant layers:
 *
 *   HOME  ->  choose one of the FOUR disasters (Flood / Earthquake /
 *             Landslide / Fire) or open the essential resources.
 *   DISASTER  ->  [Back] [title] [approved poster] [BEFORE | DURING | AFTER]
 *                 [critical actions now] [instruction categories -> detail]
 *
 * All instruction content comes from the existing DisasterInstructions data
 * model — nothing is invented, nothing dropped. The EMERGENCY QUICK TRIGGER
 * (flashlight + SOS siren) is pinned OUTSIDE the scrolling list, permanently
 * visible above the bottom navigation, on every route of this module.
 *
 * Back pops exactly ONE level: category/module detail -> its disaster,
 * disaster detail -> home. The poster reader is the innermost layer, so
 * system back closes it first without losing the disaster screen.
 */

/** In-module navigation routes (detail views are internal to this module). */
internal object InstructionsRoutes {
  const val HOME = "home"
  const val CONTACTS = "contacts"
  const val KIT = "kit"
  fun disaster(categoryId: String) = "disaster:$categoryId"
  fun group(categoryId: String, phaseId: String, groupId: String) =
    "group:$categoryId:$phaseId:$groupId"
  fun module(moduleId: String) = "module:$moduleId"
}

@Composable
fun InstructionsScreen(
  uiState: VippattiUiState,
  onToggleTheme: () -> Unit,
  onToggleOfflineAccess: (Boolean) -> Unit,
  onOpenInteractiveBag: () -> Unit,
  onToggleFlashlight: () -> Unit,
  onToggleSiren: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Survive tab switches and process death — selected disaster, phase AND
  // active detail route are all restored; user context is never reset.
  var selectedCategoryId by rememberSaveable { mutableStateOf(DisasterInstructions.categories.first().id) }
  var selectedPhaseId by rememberSaveable { mutableStateOf("during") }
  var route by rememberSaveable { mutableStateOf(InstructionsRoutes.HOME) }
  // Full-screen safety-poster reader. "" = closed (empty string rather than a
  // nullable value so the saver stays trivially Bundle-compatible).
  var posterCategoryId by rememberSaveable { mutableStateOf("") }

  val category = DisasterInstructions.categories.firstOrNull { it.id == selectedCategoryId }
    ?: DisasterInstructions.categories.first()
  val phase: InstructionPhase = when (selectedPhaseId) {
    "before" -> category.before
    "after" -> category.after
    else -> category.during
  }
  val posterCategory = DisasterInstructions.categories.firstOrNull { it.id == posterCategoryId }

  // Choosing a disaster opens its detail screen; the choice survives tab
  // switches and process death like every other state in this module.
  val selectDisaster: (String) -> Unit = { id ->
    selectedCategoryId = id
    route = InstructionsRoutes.disaster(id)
  }

  // Back pops exactly ONE level. The poster reader is the INNERMOST layer, so
  // system back closes it first; then a category detail returns to its
  // disaster, and the disaster detail returns to the Instructions home.
  BackHandler(enabled = route != InstructionsRoutes.HOME || posterCategoryId.isNotEmpty()) {
    when {
      posterCategoryId.isNotEmpty() -> posterCategoryId = ""
      route.startsWith("group:") -> route = InstructionsRoutes.disaster(selectedCategoryId)
      else -> route = InstructionsRoutes.HOME
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianSurface)
  ) {
    Column(
      modifier = Modifier.fillMaxSize()
    ) {
      LazyColumn(
        modifier = Modifier
          .weight(1f) // scrolling content always ends ABOVE the emergency trigger
          .fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        item(key = route) {
          val goHome = { route = InstructionsRoutes.HOME }
          when {
            route == InstructionsRoutes.CONTACTS -> ContactsDetailScreen(onBack = goHome)
            route == InstructionsRoutes.KIT -> KitDetailScreen(onBack = goHome)
            route.startsWith("group:") -> GroupDetailScreen(
              route,
              category,
              phase,
              // Back returns to THAT disaster (not the whole Instructions home),
              // so the user keeps their place and never re-picks the disaster.
              onBack = { route = InstructionsRoutes.disaster(category.id) }
            )
            route.startsWith("module:") -> {
              val module = DisasterInstructions.commonModules.firstOrNull {
                it.id == route.removePrefix("module:")
              }
              if (module != null) {
                ModuleDetailScreen(module, onBack = goHome)
              } else {
                // Unknown module id (e.g. after a data change) — fall back to
                // the home chooser instead of an empty screen.
                InstructionsHome(
                  uiState = uiState,
                  onToggleTheme = onToggleTheme,
                  onToggleOfflineAccess = onToggleOfflineAccess,
                  onOpenInteractiveBag = onOpenInteractiveBag,
                  onSelectDisaster = selectDisaster,
                  onNavigate = { newRoute -> route = newRoute }
                )
              }
            }
            route == InstructionsRoutes.disaster(category.id) -> DisasterDetailHome(
              uiState = uiState,
              category = category,
              phase = phase,
              selectedPhaseId = selectedPhaseId,
              onBack = goHome,
              onNavigate = { newRoute -> route = newRoute },
              onSelectPhase = { pid -> selectedPhaseId = pid },
              onOpenPoster = { posterCategoryId = category.id }
            )
            else -> InstructionsHome(
              uiState = uiState,
              onToggleTheme = onToggleTheme,
              onToggleOfflineAccess = onToggleOfflineAccess,
              onOpenInteractiveBag = onOpenInteractiveBag,
              onSelectDisaster = selectDisaster,
              onNavigate = { newRoute -> route = newRoute }
            )
          }
        }
      }

      // PERMANENT EMERGENCY QUICK TRIGGER — pinned above the bottom navigation
      // on every route of the Instructions module (never scrolls away).
      EmergencyQuickTrigger(
        isFlashlightOn = uiState.isFlashlightOn,
        isSirenOn = uiState.isSirenOn,
        onToggleFlashlight = onToggleFlashlight,
        onToggleSiren = onToggleSiren
      )
    }

    // FULL-SCREEN SAFETY POSTER READER (safety infographic for the selected
    // disaster). It is the INNERMOST layer of this module — a sibling of the
    // content column, never a replacement for it — so opening it never
    // unmounts any instruction content, and the app's bottom navigation and
    // every other tab stay exactly where they are.
    if (posterCategory != null) {
      InstructionPosterZoomOverlay(
        categoryId = posterCategory.id,
        disasterTitle = posterCategory.title,
        onDismiss = { posterCategoryId = "" }
      )
    }
  }
}

// ============================================================================
// EMERGENCY QUICK TRIGGER — permanently pinned above the bottom navigation
// on EVERY Instructions route. Light + SOS Siren stay fully functional.
// ============================================================================

@Composable
private fun EmergencyQuickTrigger(
  isFlashlightOn: Boolean,
  isSirenOn: Boolean,
  onToggleFlashlight: () -> Unit,
  onToggleSiren: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(EmergencyRed)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Emergency,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(18.dp)
      )
      Text(
        text = "EMERGENCY QUICK TRIGGER",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color.White,
        letterSpacing = 0.6.sp
      )
    }
    Text(
      text = "Get help instantly, anytime",
      fontSize = 10.sp,
      color = Color.White.copy(alpha = 0.85f)
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // LIGHT — large touch target, functional hardware torch toggle.
      Row(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(10.dp))
          .background(if (isFlashlightOn) Color.White else Color.White.copy(alpha = 0.18f))
          .clickable { onToggleFlashlight() }
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .testTag("emergency_flashlight_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.FlashlightOn,
          contentDescription = "Toggle flashlight",
          tint = if (isFlashlightOn) EmergencyRed else Color.White,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = if (isFlashlightOn) "LIGHT ON" else "LIGHT",
          fontSize = 12.sp,
          fontWeight = FontWeight.Black,
          color = if (isFlashlightOn) EmergencyRed else Color.White,
          letterSpacing = 0.4.sp
        )
      }
      // SOS SIREN — large touch target, functional siren toggle.
      Row(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(10.dp))
          .background(if (isSirenOn) Color.Yellow else Color.White.copy(alpha = 0.18f))
          .clickable { onToggleSiren() }
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .testTag("emergency_siren_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.VolumeUp,
          contentDescription = "Toggle SOS siren",
          tint = if (isSirenOn) Color.Black else Color.White,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = if (isSirenOn) "SIREN ON" else "SOS SIREN",
          fontSize = 12.sp,
          fontWeight = FontWeight.Black,
          color = if (isSirenOn) Color.Black else Color.White,
          letterSpacing = 0.4.sp
        )
      }
    }
  }
}
