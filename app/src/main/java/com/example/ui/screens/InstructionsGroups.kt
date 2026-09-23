package com.example.ui.screens

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.instructions.DisasterCategory
import com.example.data.instructions.InstructionItem
import com.example.data.instructions.InstructionPhase
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.WarningAmber

// ============================================================================
// CATEGORY GROUPING â€” classifies EXISTING instruction items into meaningful
// categories. No content is invented; every item of the selected phase lands
// in exactly one group (fallback: "More Safety Steps"). "After" phases get a
// single Recovery group (recovery wording dominates the data).
// ============================================================================

internal data class InstructionGroup(
  val id: String,
  val title: String,
  val subtitle: String,
  val items: List<InstructionItem>
)

/** Theme-aware visual (icon + accent) for a group id â€” resolved in composition. */
@Composable
internal fun groupVisual(groupId: String): Pair<ImageVector, Color> = when (groupId) {
  "immediate_safety" -> Icons.Default.Shield to EmergencyRed
  "evacuation" -> Icons.Default.DirectionsWalk to NeonEmerald
  "utilities" -> Icons.Default.Bolt to WarningAmber
  "vulnerable" -> Icons.Default.Groups to TacticalCyan
  "recovery" -> Icons.Default.HealthAndSafety to TacticalCyan
  else -> Icons.Default.MenuBook to TacticalCyan
}

/**
 * Localized title/subtitle for an instruction group. The classifier below
 * keeps its internal English keys; the LABELS come from the existing string
 * resources so every supported language is shown correctly.
 */
@Composable
internal fun groupLabels(groupId: String, disasterTitle: String): Pair<String, String> {
  val title = when (groupId) {
    "immediate_safety" -> stringResource(R.string.instructions_group_immediate_safety)
    "evacuation" -> stringResource(R.string.instructions_group_evacuation)
    "utilities" -> stringResource(R.string.instructions_group_utilities)
    "vulnerable" -> stringResource(R.string.instructions_group_vulnerable)
    "recovery" -> stringResource(R.string.instructions_group_recovery, disasterTitle)
    else -> stringResource(R.string.instructions_group_more)
  }
  val subtitle = when (groupId) {
    "immediate_safety" -> stringResource(R.string.instructions_group_immediate_safety_sub)
    "evacuation" -> stringResource(R.string.instructions_group_evacuation_sub)
    "utilities" -> stringResource(R.string.instructions_group_utilities_sub)
    "vulnerable" -> stringResource(R.string.instructions_group_vulnerable_sub)
    "recovery" -> stringResource(R.string.instructions_group_recovery_sub)
    else -> stringResource(R.string.instructions_group_more_sub)
  }
  return title to subtitle
}

internal fun buildInstructionGroups(
  category: DisasterCategory,
  phase: InstructionPhase
): List<InstructionGroup> {
  data class RawGroup(val id: String, val title: String, val subtitle: String, val keywords: List<String>)

  val definitions = listOf(
    RawGroup("immediate_safety", "Immediate Safety", "Stay safe right now", listOf(
      "critical", "immediately", "never", "do not", "drop", "cover", "hold",
      "get out", "stay out", "moving water", "rumbling", "walking", "walk",
      "re-enter", "open ground", "windows", "lifts", "knock", "sweep", "stay away"
    )),
    RawGroup("evacuation", "Evacuation Guide", "Where to go and what to do", listOf(
      "evacuat", "higher ground", "route", "safe zone", "assembly", "leave", "leaving",
      "shelter", "register", "go-bag", "belongings", "move away", "sideways", "bag"
    )),
    RawGroup("utilities", "Electricity & Utilities", "Power, gas and water safety", listOf(
      "power", "electric", "gas", "water", "switch", "mains", "cylinder", "fuel", "wiring", "wet", "live wires"
    )),
    RawGroup("vulnerable", "Vulnerable People", "Children, elderly, disabled", listOf(
      "children", "elderly", "disabled", "neighbors", "neighbours", "family", "practice", "drill", "trapped", "carry"
    ))
  )

  val assigned = mutableMapOf<String, MutableList<InstructionItem>>()
  val unassigned = mutableListOf<InstructionItem>()
  phase.items.forEach { item ->
    val text = (item.title + " " + item.detail).lowercase()
    val hit = definitions.firstOrNull { def -> def.keywords.any { text.contains(it) } }
    if (hit != null) {
      assigned.getOrPut(hit.id) { mutableListOf() }.add(item)
    } else {
      unassigned.add(item)
    }
  }

  // "After" phases: recovery terminology dominates â€” one clean Recovery group
  // holding ALL of that phase's items (nothing dropped).
  if (phase.title.equals("After", ignoreCase = true)) {
    return listOf(
      InstructionGroup(
        id = "recovery",
        title = "After the ${category.title}",
        subtitle = "Recovery and health precautions",
        items = phase.items
      )
    )
  }

  val groups = mutableListOf<InstructionGroup>()
  definitions.forEach { def ->
    val items = assigned[def.id]
    if (!items.isNullOrEmpty()) {
      groups.add(
        InstructionGroup(id = def.id, title = def.title, subtitle = def.subtitle, items = items)
      )
    }
  }
  if (unassigned.isNotEmpty()) {
    groups.add(
      InstructionGroup(
        id = "more_safety",
        title = "More Safety Steps",
        subtitle = "Additional guidance for this phase",
        items = unassigned
      )
    )
  }
  return groups
}

/** Disaster icon by category id â€” preserved from the existing module. */
