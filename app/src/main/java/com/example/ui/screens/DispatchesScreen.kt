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
  // Safe external-link opener: a malformed/unsupported URL or a device
  // without a browser must never crash the feed (ActivityNotFoundException
  // was previously unguarded on the hero button).
  val openArticle: (String) -> Unit = { target ->
    try {
      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
    } catch (e: Exception) {
      android.widget.Toast.makeText(
        context, "Cannot open article: ${e.message}", android.widget.Toast.LENGTH_SHORT
      ).show()
    }
  }

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
          .padding(horizontal = 16.dp)
          .padding(top = 4.dp, bottom = 10.dp),
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
          // Plain editorial title. The shield + "EMERGENCY OPS" chrome made
          // a news reader page look like an official alert console.
          Column {
            Text(
              text = "News",
              fontSize = 22.sp,
              fontWeight = FontWeight.SemiBold,
              color = TacticalOnSurface,
              lineHeight = 26.sp
            )
            Text(
              text = "Latest updates and public news reports",
              fontSize = 13.sp,
              fontWeight = FontWeight.Normal,
              color = TacticalOnSurfaceVariant,
              lineHeight = 17.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        IconButton(
          onClick = onSync,
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
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
          .padding(horizontal = 16.dp, vertical = 6.dp)
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
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
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
              // One quiet provenance line — distinguishes news reports from
              // government alerts without shouting it as a badge.
              Text(
                text = uiState.newsScopeNote,
                fontSize = 11.sp,
                color = TacticalOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
          .padding(horizontal = 16.dp, vertical = 4.dp)
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(ObsidianContainerLow)
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
          ) {
            // Banner Image with Overlays
            // Width-proportional hero height (≈0.53 of card width — the original
            // 176dp on a 360dp phone) so it scales down on small phones and
            // grows sensibly on large ones, instead of a fixed 176dp.
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 0.45f)
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
            }

            // Article body — editorial hierarchy:
            //   [Source]                [time]
            //   Headline (20sp/26)
            //   Summary (14sp/21, clamped)
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  // Real publisher, shown normally — never as an alert badge.
                  text = hero.sourceName,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Medium,
                  color = TacticalOnSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = NewsPresentation.relativeAge(hero.publishedAtMillis, now),
                  fontSize = 12.sp,
                  color = TacticalOnSurfaceVariant
                )
              }

              Text(
                text = hero.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TacticalOnSurface,
                lineHeight = 26.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
              )

              Text(
                text = NewsPresentation.articleSummary(hero, maxChars = 220),
                fontSize = 14.sp,
                color = TacticalOnSurfaceVariant,
                lineHeight = 21.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
              )

              // Footer: real metadata left, quiet text CTA right.
              // No "Evacuation Routes" action on news cards: routing belongs
              // on the Map, where a real selected safe zone exists — an
              // article has no verified evacuation route attached to it.
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  // Ring label from the place resolved at runtime - never a constant.
                  text = hero.scope.ringLabel(uiState.resolvedPlace),
                  fontSize = 12.sp,
                  color = TacticalOnSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Row(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { openArticle(hero.url) }
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .testTag("hero_read_full_story_button"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Text(
                    text = "Read Full Story →",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TacticalCyan
                  )
                }
              }
            }
          }
        }
      } else {
        // Honest empty / loading / error hero — never a fabricated alert
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(ObsidianContainerLow)
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
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
            text = "LATEST",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
          dispatch.url?.let { openArticle(it) } ?: onNavigateTab(ScreenTab.INSTRUCTIONS)
        }
      )
    }
  }
}
