package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DataStatus
import com.example.data.risk.RiskLevel
import com.example.data.shelters.EmergencyGuidance
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState

/**
 * ============================================================================
 * HOME — the answer to "what should I do right now?"
 * ============================================================================
 *
 * First screen after sign-in. Deliberately quiet: ONE question — is my area
 * dangerous — answered first, then at most a few next steps. Everything this
 * screen touches exists in full on another tab; Home only points at it.
 * (UI principle: user-centricity + simplicity + clear hierarchy — the dense
 * tactical map is a place you GO, not where you START.)
 */
@Composable
fun HomeScreen(
  uiState: VippattiUiState,
  onOpenRadar: () -> Unit,
  onOpenNews: () -> Unit,
  onOpenGuide: () -> Unit,
  onOpenProfile: () -> Unit,
  onAssessTerrain: () -> Unit,
  onGuidanceGo: () -> Unit,
  onSearchTerrainHaven: () -> Unit,
  onRouteToTerrainHaven: () -> Unit,
  onGuidanceDismiss: () -> Unit,
  /** "Look at another state/city" (place picker). */
  onOpenPlacePicker: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val risk = uiState.personalRisk
  val guidance = uiState.emergencyGuidance
  val dangerNow = guidance is EmergencyGuidance.SuggestShelter ||
    guidance is EmergencyGuidance.NoShelterKnown ||
    guidance is EmergencyGuidance.NoShelterEligible
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianSurface)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Spacer(Modifier.height(6.dp))

    // Search-style location bar (Google-Maps familiar): shows WHERE the app
    // is looking right now; tapping it opens the place picker. Replaces the
    // old tiny header text AND keeps Home free of provider-health detail.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(ObsidianContainerLow)
        .border(1.dp, TacticalOnSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
        .clickable(onClick = onOpenPlacePicker)
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Search,
        contentDescription = null,
        tint = TacticalOnSurfaceVariant,
        modifier = Modifier.size(18.dp)
      )
      Column(Modifier.weight(1f)) {
        Text(
          when {
            uiState.isViewingChosenPlace ->
              (uiState.viewedPlaceLabel?.substringBefore(',')
                ?: stringResource(R.string.home_chosen_place)) + "  •  VIEWING"
            uiState.isUserLocationFallback -> stringResource(R.string.home_all_india)
            else -> stringResource(R.string.home_your_area_gps)
          },
          fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface,
          maxLines = 1, overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("home_location_bar")
        )
        Text(
          stringResource(R.string.home_tap_to_choose_place),
          fontSize = 11.sp, color = TacticalOnSurfaceVariant
        )
      }
      Icon(
        imageVector = Icons.Default.ExpandMore,
        contentDescription = null,
        tint = TacticalOnSurfaceVariant,
        modifier = Modifier.size(18.dp)
      )
    }

    // 1. THE one question: "Am I safe right now?"
    RiskHero(
      risk = risk,
      onOpenRadar = onOpenRadar,
      modifier = Modifier.testTag("home_risk_hero")
    )

    // 2. If danger is detected, the guidance card is the loudest thing here.
    if (dangerNow) {
      EmergencyGuidanceCard(
        guidance = guidance,
        haven = uiState.terrainHaven,
        isSearchingHaven = uiState.isSearchingHaven,
        onGo = { onGuidanceGo(); onOpenRadar() },
        onSearchHaven = onSearchTerrainHaven,
        onRouteToHaven = { onRouteToTerrainHaven(); onOpenRadar() },
        onDismiss = onGuidanceDismiss,
        modifier = Modifier.testTag("home_guidance_card")
      )
    }

    // 2b. Terrain self-check: trigger + honest result live together here.
    TerrainSelfAssessmentChip(
      assessment = uiState.terrainSelfAssessment,
      isAssessing = uiState.isAssessingTerrain,
      onAssess = onAssessTerrain,
      onDismiss = {},
      modifier = Modifier.testTag("home_terrain_chip")
    )

    // 3. Next steps — a short list, not a wall.
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(ObsidianContainerLow)
        .border(1.dp, TacticalOnSurfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
    ) {
      HomeActionRow(
        icon = Icons.Default.Shield,
        tint = NeonEmerald,
        title = stringResource(R.string.home_card_safe_zones_title),
        subtitle = stringResource(R.string.home_card_safe_zones_subtitle),
        tag = "home_action_radar"
      ) { onOpenRadar() }
      HomeDivider()
      HomeActionRow(
        icon = Icons.Default.NotificationsActive,
        tint = WarningAmber,
        title = stringResource(R.string.home_card_guide_title),
        subtitle = stringResource(R.string.home_card_guide_subtitle),
        tag = "home_action_guide"
      ) { onOpenGuide() }
      HomeDivider()
      HomeActionRow(
        icon = Icons.Default.WaterDrop,
        tint = TacticalCyan,
        title = stringResource(R.string.home_card_news_title),
        // Falls back to the app's own label, never to a translated article
        // title: the article headline is external content and stays as-is.
        subtitle = uiState.newsHero?.title?.take(64)
          ?: stringResource(R.string.home_card_news_subtitle),
        tag = "home_action_news"
      ) { onOpenNews() }
    }

    // 4. DATA STATUS deliberately removed from Home: citizens do not read
    //    provider-health strips (user feedback). The same detail lives one tap
    //    deep in the map sheet + Profile.
    Spacer(Modifier.height(20.dp))
  }
}

@Composable
private fun RiskHero(
  risk: com.example.data.risk.PersonalRiskAssessment?,
  onOpenRadar: () -> Unit,
  modifier: Modifier = Modifier
) {
  val color = when (risk?.level) {
    RiskLevel.RED -> EmergencyRedBright
    RiskLevel.ORANGE -> WarningAmber
    RiskLevel.YELLOW -> WarningAmber
    RiskLevel.GREEN -> NeonEmerald
    null -> TacticalOnSurfaceVariant
  }
  val headline = when (risk?.level) {
    RiskLevel.RED -> stringResource(R.string.home_badge_danger_nearby)
    RiskLevel.ORANGE -> stringResource(R.string.home_badge_elevated_risk)
    RiskLevel.YELLOW -> stringResource(R.string.home_badge_stay_alert)
    RiskLevel.GREEN -> stringResource(R.string.home_badge_calm)
    null -> stringResource(R.string.home_badge_assessing)
  }
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(color.copy(alpha = 0.12f))
      .border(1.5.dp, color.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
      .padding(16.dp)
  ) {
    Text(
      stringResource(R.string.home_area_safe_question),
      fontSize = 10.sp, fontWeight = FontWeight.Black,
      color = TacticalOnSurfaceVariant, letterSpacing = 1.sp
    )
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(14.dp)
          .clip(CircleShape)
          .background(color)
      )
      Spacer(Modifier.width(8.dp))
      Text(headline, fontSize = 22.sp, fontWeight = FontWeight.Black, color = color)
    }
    Spacer(Modifier.height(6.dp))
    Text(
      risk?.explanation ?: stringResource(R.string.home_assessing_body),
      fontSize = 12.sp, color = TacticalOnSurface, lineHeight = 17.sp
    )
    risk?.let {
      Text(
        it.trendSummary + (if (it.confidenceNote.isNotBlank()) " • ${it.confidenceNote}" else ""),
        fontSize = 10.sp, color = TacticalOnSurfaceVariant, lineHeight = 14.sp,
        modifier = Modifier.padding(top = 4.dp)
      )
    }
    Spacer(Modifier.height(12.dp))
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(color.copy(alpha = 0.9f))
        .clickable(onClick = onOpenRadar)
        .padding(vertical = 13.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        if (risk != null && risk.level != RiskLevel.GREEN) {
          stringResource(R.string.home_action_show_where_to_go)
        } else {
          stringResource(R.string.home_action_open_hazard_map)
        },
        fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp)
      )
    }
  }
}

@Composable
private fun HomeActionRow(
  icon: ImageVector,
  tint: Color,
  title: String,
  subtitle: String,
  tag: String,
  busy: Boolean = false,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp)
      .testTag(tag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(34.dp)
        .clip(CircleShape)
        .background(tint.copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center
    ) {
      if (busy) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = tint)
      } else {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
      Text(
        subtitle, fontSize = 11.sp, color = TacticalOnSurfaceVariant,
        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp
      )
    }
    Icon(
      Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = TacticalOnSurfaceVariant,
      modifier = Modifier.size(20.dp)
    )
  }
}

@Composable
private fun HomeDivider() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(TacticalOnSurfaceVariant.copy(alpha = 0.15f))
  )
}

/** Honest, low-prominence data health: what worked, what did not, today. */
