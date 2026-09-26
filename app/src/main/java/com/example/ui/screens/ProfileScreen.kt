package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibleForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HolidayVillage
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ColorTheme
import com.example.ui.theme.OnSafeGreen
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WarningAmber
import com.example.data.news.NewsPresentation
import com.example.viewmodel.VippattiUiState
import java.util.Locale


/** Honest reference point for the tile-cache bar (64 MB) — no fabricated sizes. */
private const val OFFLINE_MAP_CACHE_TARGET_BYTES: Long = 64L * 1000L * 1000L

/** Human-readable form of [OFFLINE_MAP_CACHE_TARGET_BYTES] for the cache row. */
private fun offlineMapCacheTargetLabel(): String =
  "${OFFLINE_MAP_CACHE_TARGET_BYTES / (1000L * 1000L)} MB"

@Composable
fun ProfileScreen(
  uiState: VippattiUiState,
  accountEmail: String? = null,
  onSignOut: () -> Unit = {},
  onToggleTheme: () -> Unit,
  /**
   * Explicit appearance choice (System / Light / Dark) from the Profile
   * Appearance row. Defaults to a no-op so existing test call sites that pass
   * only [onToggleTheme] keep compiling.
   */
  onSetThemeMode: (ThemeMode) -> Unit = {},
  /**
   * Brand colour theme selection from the Profile "Color theme" row. Defaults
   * to a no-op so existing test call sites keep compiling.
   */
  onSetColorTheme: (ColorTheme) -> Unit = {},
  onSetSafety: (Boolean) -> Unit,
  onBroadcastSos: () -> Unit,
  onOpenAddContact: () -> Unit,
  onOpenEditProfile: () -> Unit,
  onOpenSituationReport: () -> Unit,
  /** Opens the AUTHORITY CONSOLE (field registry + relocation prioritization). */
  onOpenAuthorityConsole: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianSurface)
  ) {
    // ---- 1. COMPACT PROFILE HEADER -----------------------------------------
    ProfileHeaderCard(
      uiState = uiState,
      accountEmail = accountEmail,
      onOpenEditProfile = onOpenEditProfile
    )

    // ---- 2..10. SCANNABLE SETTINGS GROUPS ----------------------------------
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      item {
        // Compact screen title — the only heading on the page, kept small so
        // the identity card and safety controls stay above the fold.
        ProfileSectionHeader(
          title = stringResource(R.string.profile_header_title),
          modifier = Modifier.padding(top = 4.dp)
        )
      }
      item {
        SafetyStatusCard(
          userIsSafe = uiState.userIsSafe,
          onSetSafety = onSetSafety
        )
      }
      item {
        SosCenterCard(
          onBroadcastSos = onBroadcastSos,
          onOpenSituationReport = onOpenSituationReport
        )
      }
      item {
        EmergencyHelplinesSection()
      }
      item {
        ProfileSectionHeader(
          title = stringResource(R.string.profile_section_details),
          modifier = Modifier.padding(top = 4.dp)
        )
      }
      item {
        ProfileDetailsCard(uiState = uiState)
      }
      item {
        ProfileSectionHeader(title = stringResource(R.string.profile_section_contacts))
      }
      item {
        SectionCard(
          actionLabel = stringResource(R.string.profile_add_contact),
          onActionClick = onOpenAddContact
        ) {
          if (uiState.contactsList.isEmpty()) {
            Text(
              text = stringResource(R.string.profile_contacts_empty),
              fontSize = 12.sp,
              color = TacticalOnSurfaceVariant,
              lineHeight = 16.sp,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
          }
          uiState.contactsList.forEachIndexed { index, contact ->
            if (index > 0) {
              RowDivider()
            }
            ContactRow(
              uiState = uiState,
              name = contact.name,
              role = contact.role,
              phone = contact.phone,
              locationNote = contact.locationNote,
              initials = contact.initials,
              colorHex = contact.colorHex
            )
          }
        }
      }
      item {
        ProfileSectionHeader(
          title = stringResource(R.string.profile_section_authority)
        )
      }
      item {
        SectionCard(
          actionLabel = stringResource(R.string.profile_authority_open),
          onActionClick = onOpenAuthorityConsole
        ) {
          Text(
            text = stringResource(R.string.profile_authority_description),
            fontSize = 12.sp,
            color = TacticalOnSurfaceVariant,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
          )
        }
      }
      item {
        ProfileSectionHeader(title = stringResource(R.string.profile_section_app_data))
      }
      item {
        AppDataCard(uiState = uiState)
      }
      item {
        ProfileSectionHeader(title = stringResource(R.string.profile_section_preferences))
      }
      item {
        PreferencesCard(
          themeMode = uiState.themeMode,
          onSetThemeMode = onSetThemeMode,
          colorTheme = uiState.colorTheme,
          onSetColorTheme = onSetColorTheme
        )
      }
      item {
        ProfileSectionHeader(title = stringResource(R.string.profile_section_account))
      }
      item {
        AccountCard(
          accountEmail = accountEmail,
          onSignOut = onSignOut
        )
      }
      item {
        ProfileSectionHeader(title = stringResource(R.string.profile_section_about))
      }
      item {
        AboutCard()
      }
      item { Spacer(modifier = Modifier.height(20.dp)) }
    }
  }
}

// ---------------------------------------------------------------------------
// SECTION 2 — SAFETY STATUS (existing I-am-safe / need-assistance switcher)
// ---------------------------------------------------------------------------

@Composable
internal fun SafetyStatusCard(
  userIsSafe: Boolean,
  onSetSafety: (Boolean) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 4.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(ObsidianContainerLow)
        .border(
          width = 1.dp,
          color = TacticalOutlineVariant.copy(alpha = 0.22f),
          shape = RoundedCornerShape(16.dp)
        )
        .padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      SafetyOption(
        selected = userIsSafe,
        label = stringResource(R.string.profile_safety_safe),
        icon = Icons.Default.CheckCircle,
        selectedContainer = SafeGreen,
        selectedContent = OnSafeGreen,
        onClick = { onSetSafety(true) },
        modifier = Modifier.weight(1f)
      )
      SafetyOption(
        selected = !userIsSafe,
        label = stringResource(R.string.profile_safety_needs_help),
        icon = Icons.Default.Warning,
        selectedContainer = EmergencyRed,
        selectedContent = Color.White,
        onClick = { onSetSafety(false) },
        modifier = Modifier.weight(1f)
      )
    }
    RowDescription(
      text = if (userIsSafe) {
        stringResource(R.string.profile_safety_safe_description)
      } else {
        stringResource(R.string.profile_safety_needs_help_description)
      },
      modifier = Modifier.padding(start = 4.dp)
    )
  }
}

@Composable
private fun SafetyOption(
  selected: Boolean,
  label: String,
  icon: ImageVector,
  selectedContainer: Color,
  selectedContent: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(if (selected) selectedContainer else Color.Transparent)
      .clickable(onClick = onClick)
      .heightIn(min = 48.dp)
      .padding(horizontal = 10.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (selected) selectedContent else TacticalOnSurfaceVariant,
      modifier = Modifier.size(17.dp)
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = label,
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      color = if (selected) selectedContent else TacticalOnSurfaceVariant,
      textAlign = TextAlign.Center
    )
  }
}

// ---------------------------------------------------------------------------
// SECTION 3 — SOS CENTER  |  SECTION 4 — EMERGENCY HELPLINES
// ---------------------------------------------------------------------------

@Composable
internal fun SosCenterCard(
  onBroadcastSos: () -> Unit,
  onOpenSituationReport: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 16.dp)
      .clip(RoundedCornerShape(18.dp))
      .background(
        Brush.linearGradient(
          colors = listOf(EmergencyRed, Color(0xFFBE123C))
        )
      )
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Emergency,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(22.dp)
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = stringResource(R.string.profile_sos_title),
          fontSize = 17.sp,
          fontWeight = FontWeight.Black,
          color = Color.White
        )
        Text(
          text = stringResource(R.string.profile_sos_subtitle),
          fontSize = 12.sp,
          lineHeight = 16.sp,
          color = Color.White.copy(alpha = 0.9f)
        )
      }
    }

    // Primary distress action (existing callback + existing test tag).
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(EMERGENCY_ACTION_HEIGHT)
        .clip(RoundedCornerShape(12.dp))
        .background(Color.White)
        .clickable(onClick = onBroadcastSos)
        .testTag("broadcast_sos_hero_button"),
      contentAlignment = Alignment.Center
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Emergency,
          contentDescription = null,
          tint = EmergencyRed,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.profile_sos_broadcast),
          fontSize = 14.sp,
          fontWeight = FontWeight.Black,
          color = EmergencyRed
        )
      }
    }

    // Secondary channel (existing callback + existing test tag).
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = EMERGENCY_ACTION_HEIGHT)
        .clip(RoundedCornerShape(12.dp))
        .border(
          width = 1.dp,
          color = Color.White.copy(alpha = 0.6f),
          shape = RoundedCornerShape(12.dp)
        )
        .clickable(onClick = onOpenSituationReport)
        .testTag("report_situation_hero_button")
        .padding(horizontal = 12.dp, vertical = 10.dp),
      contentAlignment = Alignment.Center
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.RecordVoiceOver,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.profile_sos_report_situation),
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }
    }
  }
}

// SECTION 4 — EMERGENCY HELPLINES (toll-free)
// ---------------------------------------------------------------------------

@Composable
internal fun EmergencyHelplinesSection() {
  val context = LocalContext.current
  val helplines = listOf(
    HelplineRow(
      number = "112",
      title = stringResource(R.string.profile_helpline_ndrf),
      subtitle = stringResource(R.string.profile_helpline_ndrf_description),
      icon = Icons.Default.Shield,
      tint = EmergencyRedBright
    ),
    HelplineRow(
      number = "108",
      title = stringResource(R.string.profile_helpline_ambulance),
      subtitle = stringResource(R.string.profile_helpline_ambulance_description),
      icon = Icons.Default.LocalHospital,
      tint = TacticalCyan
    ),
    HelplineRow(
      number = "101",
      title = stringResource(R.string.profile_helpline_fire),
      subtitle = stringResource(R.string.profile_helpline_fire_description),
      icon = Icons.Default.LocalFireDepartment,
      tint = WarningAmber
    )
  )

  Column(modifier = Modifier.fillMaxWidth()) {
    ProfileSectionHeader(title = stringResource(R.string.profile_section_helplines))
    RowDescription(
      text = stringResource(R.string.profile_section_helplines_caption),
      modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
    SectionCard {
      helplines.forEachIndexed { index, helpline ->
        if (index > 0) {
          RowDivider()
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { dialNumber(context, helpline.number) })
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(
            imageVector = helpline.icon,
            contentDescription = null,
            tint = helpline.tint,
            modifier = Modifier.size(20.dp)
          )
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            RowTitle(text = helpline.title)
            RowDescription(text = helpline.subtitle)
          }
          RowValue(text = helpline.number, color = EmergencyRedBright)
          Icon(
            imageVector = Icons.Default.Call,
            contentDescription = null,
            tint = TacticalOnSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

/** The three existing toll-free lines; no new numbers were introduced. */
private data class HelplineRow(
  val number: String,
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val tint: Color
)

/** Existing ACTION_DIAL behaviour, unchanged — only the Toast text is localized. */
private fun dialNumber(context: Context, number: String) {
  try {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
    context.startActivity(intent)
  } catch (e: Exception) {
    Toast.makeText(
      context,
      context.getString(R.string.profile_dial_error, number, e.message ?: ""),
      Toast.LENGTH_SHORT
    ).show()
  }
}

// SECTION 5 — PROFILE DETAILS (household, medical, evacuation, relocation)
// ---------------------------------------------------------------------------

/** Household / medical / evacuation needs / relocation priority, as rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileDetailsCard(uiState: VippattiUiState) {
  val relocationPlan = uiState.relocationPlan
  val profile = uiState.userProfile

  SectionCard {
    // HOUSEHOLD — same dependents label + detail the card showed before.
    SettingsRow(
      icon = Icons.Default.Group,
      title = stringResource(R.string.profile_household_label),
      description = profile.dependentsDetail,
      value = profile.dependentsLabel,
      leadingTint = EmergencyRedBright
    )

    RowDivider()

    // MEDICAL TAG — medical tag + notes, exactly as before.
    SettingsRow(
      icon = Icons.Default.MedicalInformation,
      title = stringResource(R.string.profile_medical_label),
      description = profile.medicalNotes,
      value = profile.medicalTag,
      leadingTint = WarningAmber
    )

    RowDivider()

    // EVACUATION SHELTER NEEDS — existing self-declared requirement chips.
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Icon(
          imageVector = Icons.Default.HolidayVillage,
          contentDescription = null,
          tint = TacticalCyan,
          modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
          RowTitle(text = stringResource(R.string.profile_evacuation_needs_label))
        }
      }
      RowDescription(text = stringResource(R.string.profile_evacuation_needs_description))
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        EvacuationNeedChip(
          icon = Icons.Default.AccessibleForward,
          label = stringResource(R.string.profile_need_elderly_mobility),
          tint = NeonEmerald
        )
        EvacuationNeedChip(
          icon = Icons.Default.Pets,
          label = stringResource(R.string.profile_need_pet_friendly),
          tint = WarningAmber
        )
        EvacuationNeedChip(
          icon = Icons.Default.Vaccines,
          label = stringResource(R.string.profile_need_oxygen_meds),
          tint = TacticalCyan
        )
      }
    }

    // RELOCATION INTELLIGENCE — only when the engine produced a plan.
    if (relocationPlan != null) {
      RowDivider()
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(
            imageVector = Icons.Default.HolidayVillage,
            contentDescription = null,
            tint = EmergencyRedBright,
            modifier = Modifier.size(20.dp)
          )
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            RowTitle(text = stringResource(R.string.profile_relocation_label))
            RowDescription(text = stringResource(R.string.profile_relocation_description))
          }
          RowValue(text = relocationPlan.priorityBand, color = EmergencyRedBright)
        }
        Text(
          text = relocationPlan.bandExplanation,
          fontSize = 12.sp,
          lineHeight = 16.sp,
          color = TacticalOnSurface
        )
        relocationPlan.assignedShelter?.let { shelter ->
          Text(
            text = stringResource(
              R.string.profile_relocation_assigned_shelter,
              shelter.zone.name
            ) + " • " + stringResource(
              R.string.profile_relocation_shelter_capacity,
              shelter.capacityReport.availableCapacity
            ),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = NeonEmerald
          )
        }
        relocationPlan.overflowNote?.let { note ->
          Text(
            text = note,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = WarningAmber
          )
        }
        RowDescription(text = stringResource(R.string.profile_relocation_priority_note))
      }
    }
  }
}

/** Requirement chip: wraps to as many lines as the translation needs. */
@Composable
private fun EvacuationNeedChip(
  icon: ImageVector,
  label: String,
  tint: Color
) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(tint.copy(alpha = 0.14f))
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(15.dp)
    )
    Text(
      text = label,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = tint
    )
  }
}

// SECTION 6 — EMERGENCY CONTACTS (existing kin list + Add contact)
// ---------------------------------------------------------------------------

/** One kin contact row: identity, phone/location, call + SOS SMS actions. */
@Composable
internal fun ContactRow(
  uiState: VippattiUiState,
  name: String,
  role: String,
  phone: String,
  locationNote: String,
  initials: String,
  colorHex: Long
) {
  val context = LocalContext.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(Color(colorHex).copy(alpha = 0.25f)),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = initials,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        color = Color(colorHex)
      )
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      RowTitle(text = stringResource(R.string.profile_contact_role, name, localizedContactRole(role)))
      RowDescription(
        text = stringResource(R.string.profile_contact_details, phone, locationNote)
      )
      Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianContainer)
            .clickable { dialNumber(context, phone.replace(" ", "")) }
            .heightIn(min = 36.dp)
            .padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Call,
            contentDescription = stringResource(
              R.string.profile_contact_call_content_description,
              name
            ),
            tint = TacticalOnSurface,
            modifier = Modifier.size(15.dp)
          )
          Text(
            text = stringResource(R.string.action_call),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurface
          )
        }

        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(EmergencyRedContainer.copy(alpha = 0.3f))
            .clickable {
              sendSosSms(
                context = context,
                phone = phone,
                latitude = uiState.userLocation.lat,
                longitude = uiState.userLocation.lon,
                isFallbackLocation = uiState.isUserLocationFallback
              )
            }
            .heightIn(min = 36.dp)
            .padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Send,
            contentDescription = null,
            tint = EmergencyRedBright,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = stringResource(R.string.profile_contact_sos_sms),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = EmergencyRedBright
          )
        }
      }
    }
  }
}

/** Existing SOS-SMS intent; only the message body + Toasts are localized. */
private fun sendSosSms(
  context: Context,
  phone: String,
  latitude: Double,
  longitude: Double,
  isFallbackLocation: Boolean
) {
  try {
    val locationTag = if (isFallbackLocation) {
      context.getString(R.string.profile_contact_sos_location_india_fallback)
    } else {
      context.getString(R.string.profile_contact_sos_location_device_gps)
    }
    val body = context.getString(
      R.string.profile_contact_sos_sms_body,
      String.format(Locale.US, "%.4f", latitude),
      String.format(Locale.US, "%.4f", longitude),
      locationTag
    )
    val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.replace(" ", "")}")).apply {
      putExtra("sms_body", body)
    }
    context.startActivity(smsIntent)
  } catch (e: Exception) {
    Toast.makeText(
      context,
      context.getString(R.string.profile_sms_error, e.message ?: ""),
      Toast.LENGTH_SHORT
    ).show()
  }
}

/** Maps the stored (English) contact role onto the localized label. */
@Composable
internal fun localizedContactRole(role: String): String {
  val keys = stringArrayResource(R.array.profile_contact_role_keys)
  val labels = stringArrayResource(R.array.profile_contact_role_labels)
  val index = keys.indexOfFirst { it.equals(role, ignoreCase = true) }
  return if (index in labels.indices) labels[index] else role
}

// SECTION 7 — APP DATA / OFFLINE
// ---------------------------------------------------------------------------

/** Offline readiness + data-source honesty, collected into one group. */
@Composable
internal fun AppDataCard(uiState: VippattiUiState) {
  val context = LocalContext.current
  val tileCacheLabel = uiState.tileCacheSizeLabel
    ?: stringResource(R.string.profile_offline_maps_not_measured)
  val cacheValue = if (uiState.tileCacheSizeLabel != null) {
    stringResource(
      R.string.profile_offline_maps_value,
      tileCacheLabel,
      offlineMapCacheTargetLabel()
    )
  } else {
    tileCacheLabel
  }
  val syncValue = uiState.disasterLastSyncMillis?.let { millis ->
    NewsPresentation.relativeAge(millis, System.currentTimeMillis())
  } ?: stringResource(R.string.profile_disaster_sync_never)

  SectionCard {
    SettingsRow(
      icon = Icons.Default.CloudSync,
      title = stringResource(R.string.profile_offline_first_label),
      value = if (uiState.isOfflineFirstMode) {
        stringResource(R.string.profile_theme_on)
      } else {
        stringResource(R.string.profile_theme_off)
      },
      valueColor = if (uiState.isOfflineFirstMode) NeonEmerald else TacticalOnSurfaceVariant,
      leadingTint = TacticalCyan
    )

    RowDivider()

    SettingsRow(
      icon = Icons.Default.CloudSync,
      title = stringResource(R.string.profile_offline_maps_label),
      value = cacheValue,
      leadingTint = TacticalCyan
    )

    RowDivider()

    SettingsRow(
      icon = Icons.Default.MyLocation,
      title = stringResource(R.string.profile_disaster_sync_label),
      value = syncValue,
      leadingTint = TacticalCyan
    )

    RowDivider()

    SettingsRow(
      icon = Icons.Default.Verified,
      title = stringResource(R.string.profile_data_source_label),
      value = uiState.disasterDataStatusLabel,
      valueColor = NeonEmerald,
      leadingTint = NeonEmerald
    )
  }
}

// SECTION 8 — PREFERENCES (theme + device/app language)
// ---------------------------------------------------------------------------

@Composable
internal fun PreferencesCard(
  themeMode: ThemeMode,
  onSetThemeMode: (ThemeMode) -> Unit,
  colorTheme: ColorTheme,
  onSetColorTheme: (ColorTheme) -> Unit
) {
  val context = LocalContext.current
  val appLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
  val languageLabel = remember(appLocale) {
    appLocale.getDisplayLanguage(appLocale).replaceFirstChar { first ->
      if (first.isLowerCase()) first.titlecase(appLocale) else first.toString()
    }
  }
  // Dialog visibility is transient UI state; the SELECTED MODE itself lives in
  // the ViewModel and is persisted to SharedPreferences, so it survives
  // navigation and process death.
  var showThemeDialog by remember { mutableStateOf(false) }
  var showColorDialog by remember { mutableStateOf(false) }

  SectionCard {
    SettingsRow(
      icon = Icons.Default.DarkMode,
      title = stringResource(R.string.profile_theme_label),
      description = stringResource(R.string.profile_theme_appearance),
      value = stringResource(themeMode.labelRes()),
      valueColor = if (themeMode == ThemeMode.DARK) WarningAmber else TacticalOnSurfaceVariant,
      leadingTint = WarningAmber,
      onClick = { showThemeDialog = true },
      modifier = Modifier.testTag("profile_theme_toggle_button")
    )

    RowDivider()

    // COLOR THEME. Independent of the Light/Dark row above: that one picks the
    // appearance, this one picks the brand palette. Both are persisted.
    SettingsRow(
      icon = Icons.Default.Palette,
      title = stringResource(R.string.profile_color_theme_label),
      description = stringResource(R.string.profile_color_theme_description),
      value = stringResource(colorTheme.labelRes()),
      valueColor = colorTheme.lightPalette.neonEmerald,
      leadingTint = colorTheme.lightPalette.neonEmerald,
      onClick = { showColorDialog = true },
      modifier = Modifier.testTag("profile_color_theme_button")
    )

    RowDivider()

    // Language is resolved by Android itself (res/values-*/strings.xml plus
    // res/xml/locales_config.xml). This row reports the ACTIVE language and
    // opens the system picker — it does not replace the localization
    // architecture with a second, in-app one.
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { openAppLanguageSettings(context) }
        .heightIn(min = 56.dp)
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Language,
          contentDescription = null,
          tint = TacticalCyan,
          modifier = Modifier.size(20.dp)
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          RowTitle(text = stringResource(R.string.profile_language_label))
          RowDescription(text = stringResource(R.string.profile_language_description))
        }
        RowValue(
          text = languageLabel,
          color = NeonEmerald,
          modifier = Modifier.weight(1f, fill = false)
        )
        Text(
          text = "\u203A",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurfaceVariant
        )
      }
      RowDescription(
        text = stringResource(R.string.profile_language_open_settings),
        modifier = Modifier.padding(start = 32.dp)
      )
    }
  }

  if (showColorDialog) {
    ColorThemeDialog(
      selected = colorTheme,
      onSelect = {
        onSetColorTheme(it)
        showColorDialog = false
      },
      onDismiss = { showColorDialog = false }
    )
  }

  if (showThemeDialog) {
    ThemeModeDialog(
      selected = themeMode,
      onSelect = {
        onSetThemeMode(it)
        showThemeDialog = false
      },
      onDismiss = { showThemeDialog = false }
    )
  }
}

/** Localized label for an appearance option, in the user's current locale. */
private fun ThemeMode.labelRes(): Int = when (this) {
  ThemeMode.SYSTEM -> R.string.profile_theme_mode_system
  ThemeMode.LIGHT -> R.string.profile_theme_mode_light
  ThemeMode.DARK -> R.string.profile_theme_mode_dark
}

/** Localized name of a brand colour theme. */
private fun ColorTheme.labelRes(): Int = when (this) {
  ColorTheme.VIPPATTI_BLUE -> R.string.profile_color_theme_blue
  ColorTheme.FOREST_GREEN -> R.string.profile_color_theme_forest
  ColorTheme.SUNSET_ORANGE -> R.string.profile_color_theme_sunset
  ColorTheme.ROYAL_PURPLE -> R.string.profile_color_theme_purple
  ColorTheme.OCEAN_CYAN -> R.string.profile_color_theme_cyan
  ColorTheme.SLATE -> R.string.profile_color_theme_slate
}

/** Stable test tag for one colour theme option row. */
private fun colorThemeTag(theme: ColorTheme): String = when (theme) {
  ColorTheme.VIPPATTI_BLUE -> "profile_color_theme_blue"
  ColorTheme.FOREST_GREEN -> "profile_color_theme_forest"
  ColorTheme.SUNSET_ORANGE -> "profile_color_theme_sunset"
  ColorTheme.ROYAL_PURPLE -> "profile_color_theme_purple"
  ColorTheme.OCEAN_CYAN -> "profile_color_theme_cyan"
  ColorTheme.SLATE -> "profile_color_theme_slate"
}

/**
 * Brand colour theme selector.
 *
 * A compact, scannable list - one full-width row per theme, each with a real
 * two-tone swatch of that theme's own primary/secondary. Deliberately NOT large
 * preview cards: the row is short enough to fit a small portrait phone without
 * scrolling being required for the common case, and the dialog scrolls if the
 * locale's names are long.
 *
 * Accessibility:
 *  - Selection is shown by a RadioButton, a check icon AND bold weight, so it
 *    is never communicated by colour alone (Phase 5 requirement).
 *  - The swatch has a content description, and each row is >= 48dp tall.
 *  - Material3 dialog, so it inherits the active palette in light and dark.
 *  - The applied theme is live, so the dialog itself restyles on selection.
 */
@Composable
private fun ColorThemeDialog(
  selected: ColorTheme,
  onSelect: (ColorTheme) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = ObsidianSurface,
    titleContentColor = TacticalOnSurface,
    textContentColor = TacticalOnSurfaceVariant,
    title = {
      Text(
        text = stringResource(R.string.profile_color_theme_dialog_title),
        style = MaterialTheme.typography.titleMedium
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
      ) {
        Text(
          text = stringResource(R.string.profile_color_theme_dialog_hint),
          style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        ColorTheme.entries.forEach { theme ->
          val isSelected = theme == selected
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .clip(RoundedCornerShape(10.dp))
              .clickable { onSelect(theme) }
              .padding(horizontal = 8.dp, vertical = 6.dp)
              .testTag(colorThemeTag(theme)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            RadioButton(selected = isSelected, onClick = null)
            ThemeSwatch(theme)
            Text(
              text = stringResource(theme.labelRes()),
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) TacticalOnSurface else TacticalOnSurfaceVariant,
              // weight(1f) + ellipsis: a long translated name wraps or ellipses
              // instead of overflowing on a narrow phone (Phase 10).
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            if (isSelected) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.profile_theme_selected),
                tint = TacticalOnSurface,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(
          text = stringResource(R.string.profile_theme_close),
          color = TacticalOnSurface
        )
      }
    }
  )
}

/**
 * Two-tone preview of a theme's own primary and secondary colours.
 *
 * Reads from [ColorTheme.previewColors] (the real palette values) rather than
 * hardcoded literals, so a swatch can never drift from what actually gets
 * installed.
 */
@Composable
private fun ThemeSwatch(theme: ColorTheme) {
  val (primary, secondary) = theme.previewColors
  val name = stringResource(theme.labelRes())
  Box(
    modifier = Modifier
      .size(width = 34.dp, height = 22.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(primary)
      .border(
        width = 1.dp,
        color = theme.lightPalette.tacticalOutline,
        shape = RoundedCornerShape(6.dp)
      )
      .semantics { contentDescription = name },
    contentAlignment = Alignment.CenterEnd
  ) {
    Box(
      modifier = Modifier
        .fillMaxHeight()
        .width(11.dp)
        .background(secondary)
    )
  }
}

/**
 * Appearance selector.
 *
 * Only the three standard light/dark choices are offered, all of which reuse the
 * app's EXISTING light and dark palettes - no new colours are introduced and no
 * disaster/hazard semantic colour is made user-configurable.
 *
 * Accessibility:
 *  - Selection is shown by a RadioButton AND a check icon AND the row's
 *    bold weight, never by colour alone.
 *  - The dialog is Material3, so it inherits the active VippattiTheme palette
 *    and is automatically readable in Light and Dark.
 *  - Every option is a full-width row of at least 48dp touch height.
 */
@Composable
private fun ThemeModeDialog(
  selected: ThemeMode,
  onSelect: (ThemeMode) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = ObsidianSurface,
    titleContentColor = TacticalOnSurface,
    textContentColor = TacticalOnSurfaceVariant,
    title = {
      Text(
        text = stringResource(R.string.profile_theme_dialog_title),
        style = MaterialTheme.typography.titleMedium
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.profile_theme_dialog_hint),
          style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        ThemeMode.entries.forEach { mode ->
          val isSelected = mode == selected
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .clip(RoundedCornerShape(10.dp))
              .clickable { onSelect(mode) }
              .padding(horizontal = 8.dp, vertical = 8.dp)
              .testTag(themeModeTag(mode)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            RadioButton(
              selected = isSelected,
              // A null callback: the whole row handles the click, and the
              // RadioButton is the non-colour selection indicator.
              onClick = null
            )
            Text(
              text = stringResource(mode.labelRes()),
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) TacticalOnSurface else TacticalOnSurfaceVariant,
              modifier = Modifier.weight(1f)
            )
            if (isSelected) {
              // Redundant, shape-based confirmation for users who cannot
              // distinguish the accent colour.
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.profile_theme_selected),
                tint = TacticalOnSurface,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(
          text = stringResource(R.string.profile_theme_close),
          color = TacticalOnSurface
        )
      }
    }
  )
}

/** Stable test tag for one appearance option row. */
private fun themeModeTag(mode: ThemeMode): String = when (mode) {
  ThemeMode.SYSTEM -> "profile_theme_option_system"
  ThemeMode.LIGHT -> "profile_theme_option_light"
  ThemeMode.DARK -> "profile_theme_option_dark"
}

/** Opens the system per-app language screen; silently no-ops when unavailable. */
private fun openAppLanguageSettings(context: Context) {
  try {
    val intent = Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS)
      .setData(Uri.fromParts("package", context.packageName, null))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  } catch (e: Exception) {
    try {
      context.startActivity(
        Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (ignored: Exception) {
      // No settings activity on this build — nothing to open, nothing to break.
    }
  }
}

// SECTION 9 — ACCOUNT  |  SECTION 10 — ABOUT
// ---------------------------------------------------------------------------

/** Account email + the existing sign-out action, kept in its own group. */
@Composable
internal fun AccountCard(
  accountEmail: String?,
  onSignOut: () -> Unit
) {
  SectionCard {
    if (!accountEmail.isNullOrBlank()) {
      SettingsRow(
        icon = Icons.Default.Person,
        title = stringResource(R.string.profile_account_email_label),
        value = accountEmail,
        leadingTint = TacticalCyan
      )
      RowDivider()
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onSignOut)
        .heightIn(min = 56.dp)
        .testTag("profile_sign_out_button")
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Logout,
        contentDescription = stringResource(R.string.profile_sign_out_content_description),
        tint = EmergencyRed,
        modifier = Modifier.size(20.dp)
      )
      RowTitle(text = stringResource(R.string.profile_sign_out))
    }
  }
}

/** Version information only — provider/debug detail stays out of Profile. */
@Composable
internal fun AboutCard() {
  SectionCard {
    SettingsRow(
      icon = Icons.Default.Verified,
      title = stringResource(R.string.profile_about_app_label),
      description = stringResource(R.string.profile_about_tagline),
      leadingTint = NeonEmerald
    )

    RowDivider()

    SettingsRow(
      icon = Icons.Default.Verified,
      title = stringResource(R.string.profile_about_version_label),
      value = stringResource(
        R.string.profile_about_version_value,
        BuildConfig.VERSION_NAME,
        BuildConfig.VERSION_CODE
      ),
      leadingTint = TacticalCyan
    )
  }
}

// ---------------------------------------------------------------------------
// SHARED PROFILE PRIMITIVES
//

/**
 * Row title.
 *
 * Translated labels are routinely longer than their English source (Tamil and
 * Telugu in particular), so the text wraps over as many lines as it needs
 * rather than being truncated. The row grows vertically instead of clipping.
 */
@Composable
private fun RowTitle(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    fontSize = 14.sp,
    fontWeight = FontWeight.SemiBold,
    color = TacticalOnSurface,
    lineHeight = 18.sp,
    modifier = modifier
  )
}

/** Row description: grows to as many lines as the translation needs. */
@Composable
private fun RowDescription(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    color = TacticalOnSurfaceVariant,
    modifier = modifier
  )
}

/** Trailing value: wraps instead of clipping on narrow/translated layouts. */
@Composable
private fun RowValue(
  text: String,
  color: Color = TacticalOnSurface,
  modifier: Modifier = Modifier
) {
  Text(
    text = text,
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
    color = color,
    textAlign = TextAlign.End,
    lineHeight = 17.sp,
    modifier = modifier
  )
}

/** A single surface for a group of rows — replaces per-row bordered cards. */
@Composable
private fun SectionCard(
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onActionClick: (() -> Unit)? = null,
  content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(ObsidianContainerLow)
      .border(
        width = 1.dp,
        color = TacticalOutlineVariant.copy(alpha = 0.22f),
        shape = RoundedCornerShape(16.dp)
      )
      .padding(vertical = 4.dp)
  ) {
    if (actionLabel != null && onActionClick != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onActionClick)
          .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = null,
          tint = NeonEmerald,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = actionLabel,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = NeonEmerald
        )
      }
      RowDivider()
    }
    content()
  }
}

/** Hairline separator between rows inside one group. */
@Composable
private fun RowDivider(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(start = 48.dp)
      .height(1.dp)
      .background(TacticalOutlineVariant.copy(alpha = 0.18f))
  )
}

/** Tappable settings row: leading icon, title, description, value, chevron. */
@Composable
private fun SettingsRow(
  icon: ImageVector,
  title: String,
  description: String? = null,
  value: String? = null,
  valueColor: Color = TacticalOnSurface,
  showChevron: Boolean = false,
  leadingTint: Color = TacticalCyan,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .heightIn(min = 56.dp)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = leadingTint,
      modifier = Modifier.size(20.dp)
    )
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      RowTitle(text = title)
      if (!description.isNullOrBlank()) {
        RowDescription(text = description)
      }
    }
    if (!value.isNullOrBlank()) {
      // Give the value at most half the row so a long translated value wraps
      // beside the title instead of squeezing it into a clipped sliver.
      RowValue(
        text = value,
        color = valueColor,
        modifier = Modifier.weight(1f, fill = false)
      )
    }
    if (showChevron) {
      Text(
        text = "\u203A",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalOnSurfaceVariant
      )
    }
  }
}

/**
 * Small group label — spacing carries the hierarchy, not a bar.
 *
 * Latin section titles are uppercased for the tactical look, but that must not
 * touch the other five languages: uppercasing is a no-op for Devanagari,
 * Telugu, Tamil and Bengali, and forcing it can mangle conjuncts. The label
 * also wraps freely so a long translated title is never clipped.
 */
@Composable
private fun ProfileSectionHeader(title: String, modifier: Modifier = Modifier) {
  val isLatin = title.all { it.code < 0x0250 }
  Text(
    text = if (isLatin) title.uppercase(Locale.ROOT) else title,
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.6.sp,
    color = TacticalOnSurfaceVariant,
    modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
  )
}

/** Fixed target for the two emergency CTA buttons (>= 48 dp touch target). */
private val EMERGENCY_ACTION_HEIGHT: Dp = 48.dp

// ---------------------------------------------------------------------------
// SECTION 1 — PROFILE HEADER (compact identity card) + SECTION 2 — SAFETY
// ---------------------------------------------------------------------------

@Composable
internal fun ProfileHeaderCard(
  uiState: VippattiUiState,
  accountEmail: String?,
  onOpenEditProfile: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(ObsidianSurface)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Box(
      modifier = Modifier
        .size(52.dp)
        .clip(CircleShape)
        .background(EmergencyRedContainer.copy(alpha = 0.25f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.Person,
        contentDescription = stringResource(R.string.profile_avatar_content_description),
        tint = TacticalOnSurface,
        modifier = Modifier.size(28.dp)
      )
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = uiState.userProfile.fullName,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          lineHeight = 22.sp,
          color = TacticalOnSurface,
          modifier = Modifier.weight(1f, fill = false)
        )
        Icon(
          imageVector = Icons.Default.Verified,
          contentDescription = stringResource(R.string.profile_verified_content_description),
          tint = NeonEmerald,
          modifier = Modifier.size(16.dp)
        )
      }
      Text(
        text = stringResource(R.string.profile_id_label, uiState.userProfile.citizenId),
        fontSize = 12.sp,
        color = TacticalOnSurfaceVariant
      )
      Text(
        text = if (accountEmail.isNullOrBlank()) {
          stringResource(R.string.profile_account_no_email)
        } else {
          accountEmail
        },
        fontSize = 12.sp,
        color = TacticalOnSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box(
        modifier = Modifier
          .clip(CircleShape)
          .background(EmergencyRedContainer.copy(alpha = 0.35f))
          .padding(horizontal = 10.dp, vertical = 3.dp)
      ) {
        Text(
          text = uiState.userProfile.bloodGroupLabel,
          fontSize = 11.sp,
          fontWeight = FontWeight.Black,
          color = EmergencyRedBright
        )
      }
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(ObsidianContainer)
          .border(
            width = 1.dp,
            color = TacticalOutlineVariant.copy(alpha = 0.3f),
            shape = CircleShape
          )
          .clickable(onClick = onOpenEditProfile)
          .testTag("profile_edit_button"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Edit,
          contentDescription = stringResource(R.string.profile_edit_content_description),
          tint = TacticalCyan,
          modifier = Modifier.size(17.dp)
        )
      }
    }
  }
}

