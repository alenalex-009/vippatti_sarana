package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalNavBg
import com.example.ui.theme.TacticalNavBorder
import com.example.ui.theme.TacticalNavInactive
import com.example.ui.theme.TacticalOnSurface
import com.example.viewmodel.ScreenTab

private data class NavItemData(
  val tab: ScreenTab,
  val label: String,
  val activeIcon: ImageVector,
  val inactiveIcon: ImageVector,
  val testTag: String
)

/** The icon pair for a destination. Lives here because icons are a rendering concern. */
private fun iconsFor(tab: ScreenTab): Pair<ImageVector, ImageVector> = when (tab) {
  ScreenTab.HOME -> Icons.Filled.Home to Icons.Outlined.Home
  ScreenTab.RADAR_MAP -> Icons.Filled.LocationOn to Icons.Outlined.LocationOn
  ScreenTab.NEWS_DISPATCHES -> Icons.Filled.Newspaper to Icons.Outlined.Newspaper
  ScreenTab.INSTRUCTIONS -> Icons.AutoMirrored.Filled.MenuBook to Icons.AutoMirrored.Outlined.MenuBook
  ScreenTab.PROFILE -> Icons.Filled.Person to Icons.Outlined.Person
}

/**
 * Primary bottom navigation — a FIXED five-tab structure in user-journey order:
 * HOME (quiet: am I safe / what do I do) -> MAP (see it) -> NEWS -> GUIDE ->
 * PROFILE. The tab LIST is a constant application structure. It is never built
 * from Firebase, Remote Config, authentication, onboarding, SharedPreferences,
 * feature flags, async state, or ViewModel state.
 *
 * Map (ScreenTab.RADAR_MAP) and News (ScreenTab.NEWS_DISPATCHES) are CORE pages.
 * Remote Config may still control functionality or content INSIDE those screens,
 * but it must never remove those two tabs from the primary bottom navigation.
 *
 * Every interactive node carries its label as a screen-reader name, and every
 * tab is tagged with a stable instrumentation identifier so a UI regression test
 * can assert that all five are rendered simultaneously.
 */
@Composable
fun VippattiBottomNavBar(
  currentTab: ScreenTab,
  onTabSelected: (ScreenTab) -> Unit,
  modifier: Modifier = Modifier
) {
  // The primary bottom navigation is a constant. Not a filter, not a take(), not
  // a config-dependent visible() list, not anything async.
  val allTabs = listOf(
    ScreenTab.HOME,
    ScreenTab.RADAR_MAP,
    ScreenTab.NEWS_DISPATCHES,
    ScreenTab.INSTRUCTIONS,
    ScreenTab.PROFILE
  )
  val items = allTabs.map { tab ->
    val (active, inactive) = iconsFor(tab)
    NavItemData(
      tab = tab,
      label = stringResource(NavTabs.labelRes(tab)),
      activeIcon = active,
      inactiveIcon = inactive,
      testTag = NavTabs.testTag(tab)
    )
  }

  val currentSuffix = stringResource(R.string.nav_current_suffix)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(TacticalNavBg)
      .border(
        width = 1.dp,
        color = TacticalNavBorder,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
      )
      .navigationBarsPadding()
      .padding(horizontal = 8.dp, vertical = 6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      items.forEach { item ->
        val selected = currentTab == item.tab
        val interactionSource = remember { MutableInteractionSource() }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
              interactionSource = interactionSource,
              indication = ripple(bounded = true, color = NeonEmerald)
            ) {
              onTabSelected(item.tab)
            }
            .heightIn(min = 48.dp)
            .testTag(item.testTag)
            .semantics(mergeDescendants = true) {
              contentDescription = item.label + if (selected) currentSuffix else ""
            }
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(34.dp)
              .then(
                if (selected) Modifier
                  .background(NeonEmerald, CircleShape)
                else Modifier
              )
          ) {
            Icon(
              imageVector = if (selected) item.activeIcon else item.inactiveIcon,
              contentDescription = null, // the row semantics carry the label
              tint = if (selected) OnNeonEmerald else TacticalNavInactive,
              modifier = Modifier.size(20.dp)
            )
          }

          // Translated tab names are longer than the English ones, so the
          // label centres within the tab and ellipsises on the rare longest
          // case rather than bleeding into the neighbouring tab.
          Text(
            text = item.label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) TacticalOnSurface else TacticalNavInactive,
            letterSpacing = 0.2.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
          )
        }
      }
    }
  }
}
