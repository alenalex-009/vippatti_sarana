package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DomainAdd
import androidx.compose.material.icons.filled.Flood
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NightShelter
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WrongLocation
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.example.data.model.DataStatus
import com.example.data.news.NewsCategory
import com.example.data.news.NewsPresentation
import com.example.data.news.ringLabel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.disaster.DispatchIconType
import com.example.data.disaster.DispatchTagType
import com.example.data.disaster.FeedDispatch
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianBright
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerHighest
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnEmergencyRedContainer
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.OnNeonEmeraldContainer
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalCyanContainer
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.ScreenTab
import com.example.viewmodel.VippattiUiState

@Composable
fun DispatchesScreen(
  uiState: VippattiUiState,
  onSync: () -> Unit,
  onToggleAudio: () -> Unit,
  onSelectCategory: (String) -> Unit,
  onSelectSeverity: (Int) -> Unit = {},
  onNavigateToEvacRoute: () -> Unit,
  onNavigateTab: (ScreenTab) -> Unit,
  onToggleHistoricalLayer: () -> Unit,
  onHistoricalFiltersChange: (com.example.data.historical.HistoricalFilters) -> Unit,
  onClearHistoricalFilters: () -> Unit,
  onSelectHistoricalEvent: (com.example.data.historical.HistoricalDisasterEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  val syncRotation by animateFloatAsState(
    targetValue = if (uiState.isSyncing) 360f else 0f,
    animationSpec = tween(durationMillis = 800),
    label = "sync_rotation"
  )

  val infiniteTransition = rememberInfiniteTransition(label = "pulse_live")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.8f,
    targetValue = 1.3f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "live_ping"
  )

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianSurface)
      .padding(bottom = 8.dp)
  ) {
    // 1. Header App Bar
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(ObsidianSurface)
          .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f))
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          // Flex so the title block wraps/shrinks safely and the refresh
          // button stays fully on screen at any width.
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(ObsidianContainerHigh),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Outlined.Shield,
              contentDescription = "Intelligence Shield",
              tint = NeonEmerald,
              modifier = Modifier.size(22.dp)
            )
          }
          Column {
            Text(
              text = "Disaster & Weather Intelligence",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface,
              lineHeight = 18.sp
            )
            Text(
              text = "VIPPATTI SARANA • EMERGENCY OPS",
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = TacticalOnSurfaceVariant,
              letterSpacing = 0.8.sp
            )
          }
        }

        IconButton(
          onClick = onSync,
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianContainer)
            .testTag("refresh_feed_button")
        ) {
          Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = "Refresh Feed",
            tint = TacticalOnSurface,
            modifier = Modifier
              .size(20.dp)
              .rotate(syncRotation)
          )
        }
      }
    }

    // 2. Offline Cached Mode Notification Banner
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianContainerLow)
            .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // One honest indicator colour for the whole banner: green only when
          // the news record is live, amber for cached, red for a failed feed.
          val newsStatusColor = com.example.ui.components.dataStatusColor(uiState.newsStatus)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .scale(pulseScale)
                  .background(newsStatusColor.copy(alpha = 0.4f), CircleShape)
              )
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .background(newsStatusColor, CircleShape)
              )
            }

            Column {
              Text(
                text = uiState.newsConnectionStateLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                // Status comes from the news record itself (live / cached / error),
                // never from sniffing the label text.
                color = newsStatusColor,
                letterSpacing = 0.8.sp
              )
              Text(
                text = uiState.lastSyncTime,
                fontSize = 12.sp,
                color = TacticalOnSurfaceVariant,
                maxLines = 1
              )
              Text(
                text = uiState.newsScopeNote,
                fontSize = 10.sp,
                color = TacticalOnSurfaceVariant,
                maxLines = 2
              )
              Text(
                text = "News articles — not official alerts",
                fontSize = 10.sp,
                color = TacticalOnSurfaceVariant
              )
            }
          }

          Button(
            onClick = onSync,
            colors = ButtonDefaults.buttonColors(
              containerColor = NeonEmeraldContainer,
              contentColor = OnNeonEmeraldContainer
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier
              .height(30.dp)
              .testTag("sync_banner_button")
          ) {
            Text(
              text = if (uiState.isSyncing) "Syncing..." else "Sync",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }

    // 3. Urgent Audio Bulletin Service Pill
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianContainerHigh)
            .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TacticalCyanContainer.copy(alpha = 0.25f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Campaign,
                contentDescription = null,
                tint = TacticalCyan,
                modifier = Modifier.size(20.dp)
              )
            }

            Column {
              Text(
                text = "Audio Bulletin Service",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TacticalOnSurface
              )
              Text(
                text = "Low-bandwidth speech playback during blackouts",
                fontSize = 11.sp,
                color = TacticalOnSurfaceVariant
              )
            }
          }

          Button(
            onClick = onToggleAudio,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (uiState.isAudioPlaying) NeonEmerald else ObsidianBright,
              contentColor = if (uiState.isAudioPlaying) OnNeonEmerald else NeonEmerald
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier
              .height(32.dp)
              .testTag("audio_bulletin_button")
          ) {
            Icon(
              imageVector = if (uiState.isAudioPlaying) Icons.Default.PauseCircle else Icons.Default.VolumeUp,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (uiState.isAudioPlaying) "Playing ${uiState.audioPlaybackSeconds}s" else "Listen",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }

    // 4. Horizontal Filter Chips — only chips with matching articles show,
    // so every visible chip is productive (no dead "Weather Radar" buttons).
    item {
      val categories = NewsPresentation.filterChipLabels(uiState.newsArticles)
      val selectedCategory = if (uiState.selectedNewsCategory in categories) {
        uiState.selectedNewsCategory
      } else {
        "All"
      }
      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
      ) {
        items(categories) { cat ->
          val isSelected = selectedCategory == cat
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (isSelected) NeonEmerald else ObsidianContainer)
              .border(
                1.dp,
                if (isSelected) NeonEmerald else TacticalOutlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
              )
              .clickable { onSelectCategory(cat) }
              .padding(horizontal = 14.dp, vertical = 7.dp)
              .testTag("filter_chip_${cat.replace(" ", "_").lowercase()}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            if (cat == "Severe Alerts") {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .background(EmergencyRed, CircleShape)
              )
            }
            Text(
              text = cat,
              fontSize = 12.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) OnNeonEmerald else TacticalOnSurface
            )
          }
        }
      }
    }

    // 5. Severe-Alert Hero Card — the top REAL GNews article (never fabricated)
    item {
      val hero = uiState.newsHero?.takeIf { article ->
        // Hero obeys the same severity selection as the feed below it.
        NewsPresentation.matchesSeverity(article, uiState.newsSeverityThreshold) &&
        (uiState.selectedNewsCategory == "All" ||
          NewsPresentation.matchesCategory(
            article.category,
            if (uiState.selectedNewsCategory in NewsPresentation.filterChipLabels(uiState.newsArticles))
              uiState.selectedNewsCategory else "All"
          ))
      }
      val now = System.currentTimeMillis()
      if (hero != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(ObsidianContainerLow)
              .border(2.dp, EmergencyRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
          ) {
            // Banner Image with Overlays
            // Width-proportional hero height (≈0.53 of card width — the original
            // 176dp on a 360dp phone) so it scales down on small phones and
            // grows sensibly on large ones, instead of a fixed 176dp.
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 0.53f)
                .background(ObsidianContainerHighest)
            ) {
              if (hero.imageUrl != null) {
                AsyncImage(
                  model = hero.imageUrl,
                  contentDescription = "Disaster news article image",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              } else {
                // Honest placeholder: the article has no image — never fake one.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = TacticalCyan,
                    modifier = Modifier.size(42.dp)
                  )
                }
              }

              // Bottom gradient scrim
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(
                    Brush.verticalGradient(
                      colors = listOf(
                        Color.Transparent,
                        ObsidianContainerLow.copy(alpha = 0.5f),
                        ObsidianContainerLow
                      )
                    )
                  )
              )

              // Honest source badge — a news article is NOT an official alert.
              Row(
                modifier = Modifier
                  .align(Alignment.TopStart)
                  .padding(12.dp)
                  .clip(CircleShape)
                  .background(EmergencyRedContainer.copy(alpha = 0.9f))
                  .border(1.dp, EmergencyRed.copy(alpha = 0.4f), CircleShape)
                  .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Warning,
                  contentDescription = null,
                  tint = EmergencyRedBright,
                  modifier = Modifier.size(14.dp)
                )
                Text(
                  text = NewsPresentation.HERO_BADGE,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = OnEmergencyRedContainer,
                  letterSpacing = 0.6.sp
                )
              }

              // Real publication age
              Box(
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(12.dp)
                  .clip(CircleShape)
                  .background(ObsidianContainerLowest.copy(alpha = 0.8f))
                  .padding(horizontal = 8.dp, vertical = 3.dp)
              ) {
                Text(
                  text = NewsPresentation.relativeAge(hero.publishedAtMillis, now),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  color = TacticalOnSurfaceVariant
                )
              }
            }

            // Article Text Content
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(EmergencyRed)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = hero.category.displayTag.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
                Text(
                  // Ring label from the place resolved at runtime - never a constant.
                text = "${hero.scope.ringLabel(uiState.resolvedPlace)} • ${hero.sourceName}",
                  fontSize = 12.sp,
                  color = TacticalOnSurfaceVariant
                )
              }

              Text(
                text = hero.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TacticalOnSurface,
                lineHeight = 22.sp
              )

              Text(
                text = NewsPresentation.articleSummary(hero),
                fontSize = 13.sp,
                color = TacticalOnSurfaceVariant,
                lineHeight = 18.sp
              )

              // Action row — real publisher attribution (no fake verification badge)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 4.dp)
                  .border(
                    width = 1.dp,
                    color = TacticalOutlineVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(0.dp)
                  )
                  .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                  // Flex so long publisher names ellipsize instead of pushing
                  // the evacuation button off screen.
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = NeonEmerald,
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = "Reported by ${hero.sourceName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonEmerald,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                // USER RULE (news redesign): never show a route button on
                // generic articles - only hazard/road coverage has a meaningful
                // affected-area + safe-zone workflow to open.
                if (hero.category == NewsCategory.SEVERE_ALERTS ||
                  hero.category == NewsCategory.ROAD_IMPACT
                ) {
                Button(
                  onClick = onNavigateToEvacRoute,
                  colors = ButtonDefaults.buttonColors(
                    containerColor = NeonEmeraldContainer,
                    contentColor = OnNeonEmeraldContainer
                  ),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier
                    .height(34.dp)
                    .testTag("evacuation_routes_hero_button")
                ) {
                  Text(
                    text = "Evacuation Routes",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                }
                }
              }

              // Open the real publisher story in the browser
              Button(
                onClick = {
                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hero.url)))
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = ObsidianBright,
                  contentColor = NeonEmerald
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(34.dp)
                  .testTag("hero_read_full_story_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Read Full Story at ${hero.sourceName}",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1
                )
              }
            }
          }
        }
      } else {
        // Honest empty / loading / error hero — never a fabricated alert
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(ObsidianContainerLow)
              .border(2.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              imageVector = Icons.Default.Campaign,
              contentDescription = null,
              tint = TacticalCyan,
              modifier = Modifier.size(36.dp)
            )
            Text(
              text = when {
                uiState.isSyncing -> "Fetching live disaster news from GNews…"
                uiState.newsError != null -> uiState.newsError.userMessage
                // Scope text is the runtime-resolved one; no district is assumed.
                else -> "No severe disaster news loaded yet — tap Sync to pull live " +
                  "GNews articles. ${uiState.newsScopeNote}"
              },
              fontSize = 13.sp,
              color = TacticalOnSurfaceVariant,
              lineHeight = 18.sp,
              textAlign = TextAlign.Center
            )
            Button(
              onClick = onSync,
              enabled = !uiState.isSyncing,
              colors = ButtonDefaults.buttonColors(
                containerColor = NeonEmeraldContainer,
                contentColor = OnNeonEmeraldContainer
              ),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .height(34.dp)
                .testTag("hero_sync_now_button")
            ) {
              Text(
                text = if (uiState.isSyncing) "Syncing…" else "Sync Now",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }

    // 6. Feed Dispatches Section Title (status-gated, never unconditionally LIVE)
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
          .testTag("feed_dispatches_header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // STAGE 7 — the dot follows the feed's real status (green only for a
          // live fetch, amber for cache, red for failure), exactly like the
          // sync banner above: a cached or failed feed must never wear LIVE.
          Box(
            modifier = Modifier
              .size(8.dp)
              .background(
                com.example.ui.components.dataStatusColor(uiState.newsStatus),
                CircleShape
              )
          )
          Text(
            text = "FEED DISPATCHES",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurface,
            letterSpacing = 0.6.sp
          )
        }
        Text(
          // 11: plain status line. LIVE stays honestly LIVE, cached data
          // says cached + the real last-fetch time; provider names and plan
          // limits moved into the Data & sources strip below.
          text = if (uiState.isSyncing) {
            "Syncing\u2026"
          } else {
            val stamp = uiState.newsLastFetchedAtMillis?.let {
              java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                .format(java.util.Date(it))
            }
            when (uiState.newsStatus) {
              DataStatus.SUCCESS -> if (stamp != null) "Live news \u00b7 Updated " + stamp
                else "Live news"
              DataStatus.STALE -> if (stamp != null) "Cached news \u00b7 Updated " + stamp
                else "Cached news"
              DataStatus.ERROR -> "Feed unreachable \u2014 tap Sync"
              DataStatus.LOADING -> "Syncing\u2026"
              else -> "Not synced \u00b7 tap Sync"
            }
          },
          fontSize = 11.sp,
          color = TacticalOnSurfaceVariant
        )
      }
    }

    // 6b. DATA & SOURCES - collapsed technical detail (11). Providers and
    // plan limits are not the reader's problem at a glance; one tap away.
    item {
      DataAndSourcesRow(uiState = uiState)
    }

    // 7. Feed Cards — REAL GNews articles mapped to dispatch cards
    val activeCategory = if (uiState.selectedNewsCategory in NewsPresentation.filterChipLabels(uiState.newsArticles)) {
      uiState.selectedNewsCategory
    } else {
      "All"
    }
    val visibleArticles = uiState.newsArticles
      .filter { article ->
        (activeCategory == "All" ||
          NewsPresentation.matchesCategory(article.category, activeCategory)) &&
        NewsPresentation.matchesSeverity(article, uiState.newsSeverityThreshold)
      }
    val filteredDispatches = NewsPresentation.toFeedDispatches(
      visibleArticles,
      System.currentTimeMillis(),
      uiState.resolvedPlace
    )

    if (filteredDispatches.isEmpty()) {
      item {
        // Honest empty state — no fabricated filler dispatches
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(ObsidianContainer)
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = when {
                uiState.isSyncing -> "Fetching live disaster news…"
                activeCategory != "All" || uiState.newsSeverityThreshold > 0 ->
                  "No articles match these filters right now — widen severity or switch to All."
                uiState.newsError != null -> uiState.newsError.userMessage
                uiState.newsEverLoaded -> "Live feed returned no new articles — try again later."
                else -> "No disaster news loaded yet — tap Sync to fetch live GNews articles."
              },
              fontSize = 13.sp,
              color = TacticalOnSurfaceVariant,
              lineHeight = 18.sp,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }

    // 8. HISTORICAL DISASTER INTELLIGENCE (EM-DAT archive). Deliberately its
    // own section: archived events are evidence, not live dispatches, and they
    // never appear in the live feed above.
    item {
      HistoricalIntelligencePanel(
        uiState = uiState,
        onToggleLayer = onToggleHistoricalLayer,
        onFiltersChange = onHistoricalFiltersChange,
        onClearFilters = onClearHistoricalFilters,
        onSelectEvent = onSelectHistoricalEvent
      )
    }

    items(filteredDispatches) { dispatch ->
      FeedDispatchCard(
        dispatch = dispatch,
        onActionClick = {
          dispatch.url?.let { url ->
            try {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
              Toast.makeText(context, "Cannot open article: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          } ?: onNavigateTab(ScreenTab.INSTRUCTIONS)
        }
      )
    }
  }
}

/** Collapsible "Data & sources" strip: honest provenance, off the main view. */
@Composable
private fun DataAndSourcesRow(uiState: VippattiUiState) {
  var expanded by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
      .clickable { expanded = !expanded }
      .padding(10.dp)
      .testTag("data_and_sources_row")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Data & sources",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalOnSurfaceVariant
      )
      Text(
        text = if (expanded) "HIDE" else "SHOW",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalCyan
      )
    }
    if (expanded) {
      Spacer(modifier = Modifier.height(6.dp))
      if (uiState.providerStatuses.isEmpty()) {
        Text("No provider fetches this session.", fontSize = 10.sp,
          color = TacticalOnSurfaceVariant)
      }
      uiState.providerStatuses.forEach { (state, status) ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(state.source.name.lowercase().replace('_', ' '),
            fontSize = 10.sp, color = TacticalOnSurface)
          Text(status.name, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = when (status) {
              DataStatus.SUCCESS -> NeonEmerald
              DataStatus.ERROR -> EmergencyRed
              else -> WarningAmber
            })
        }
      }
      if (uiState.newsScopeNote.isNotBlank()) {
        Text(uiState.newsScopeNote, fontSize = 10.sp,
          color = TacticalOnSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
      }
      Text(
        text = "News articles · not official alerts",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = WarningAmber,
        modifier = Modifier.padding(top = 2.dp)
      )
    }
  }
}
