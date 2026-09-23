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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Bottom navigation — five destinations in user-journey order:
 * HOME (quiet: am I safe / what do I do) -> MAP (see it) -> NEWS -> GUIDE
 * -> PROFILE. Each tab tells the user where they are (selected pill + label)
 * and every interactive node carries its label as a screen-reader name.
 */
@Composable
fun VippattiBottomNavBar(
  currentTab: ScreenTab,
  onTabSelected: (ScreenTab) -> Unit,
  modifier: Modifier = Modifier
) {
  val items = listOf(
    NavItemData(
      tab = ScreenTab.HOME,
      label = "Home",
      activeIcon = Icons.Filled.Home,
      inactiveIcon = Icons.Outlined.Home,
      testTag = "nav_home"
    ),
    NavItemData(
      tab = ScreenTab.RADAR_MAP,
      label = "Map",
      activeIcon = Icons.Filled.LocationOn,
      inactiveIcon = Icons.Outlined.LocationOn,
      testTag = "nav_radar_map"
    ),
    NavItemData(
      tab = ScreenTab.NEWS_DISPATCHES,
      label = "News",
      activeIcon = Icons.Filled.Newspaper,
      inactiveIcon = Icons.Outlined.Newspaper,
      testTag = "nav_news"
    ),
    NavItemData(
      tab = ScreenTab.INSTRUCTIONS,
      label = "Guide",
      activeIcon = Icons.AutoMirrored.Filled.MenuBook,
      inactiveIcon = Icons.AutoMirrored.Outlined.MenuBook,
      testTag = "nav_instructions"
    ),
    NavItemData(
      tab = ScreenTab.PROFILE,
      label = "Profile",
      activeIcon = Icons.Filled.Person,
      inactiveIcon = Icons.Outlined.Person,
      testTag = "nav_profile"
    )
  )

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
            .padding(vertical = 4.dp)
            .testTag(item.testTag)
            .semantics { contentDescription = item.label + if (selected) " (current tab)" else "" }
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

          Text(
            text = item.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) TacticalOnSurface else TacticalNavInactive,
            letterSpacing = 0.2.sp
          )
        }
      }
    }
  }
}
