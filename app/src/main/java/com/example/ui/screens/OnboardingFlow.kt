package com.example.ui.screens

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.launch

/**
 * Completion-flag storage. The flag is written only when the user skips or
 * finishes the final page — never merely for changing pages. Uses the same
 * preferences file/key the app already used, so existing installs keep their
 * completed state.
 */
class OnboardingCompletion(private val prefs: SharedPreferences) {

  fun isCompleted(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

  fun setCompleted() {
    prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
  }

  companion object {
    const val PREFS_NAME = "vippatti_onboarding"
    private const val KEY_COMPLETED = "completed"
  }
}

/**
 * The five onboarding cards. Navigation, pagination and completion all run
 * off this single list — never off duplicated per-page logic.
 */
internal enum class OnboardingStep {
  WELCOME,
  MAP,
  NEWS,
  GUIDE,
  TOOLS
}

/**
 * Five-card swipeable onboarding carousel.
 *
 * A single [HorizontalPager] owns page state: cards snap horizontally,
 * Continue/Back advance or retreat exactly one page, Skip finishes
 * immediately, and the system Back button moves to the previous page (it is
 * consumed on the first card so it can neither close the app nor bypass
 * onboarding). The pager is never the only way to move — every action is a
 * real button.
 *
 * All copy comes from string resources (six supported languages); the
 * artwork is word-free so nothing needs translating inside images.
 * Finishing (skip or final action) delegates to [onFinish], which persists
 * the completion flag and hands the user to the existing auth flow.
 */
@Composable
fun OnboardingFlowScreen(
  onFinish: () -> Unit,
  modifier: Modifier = Modifier
) {
  val steps = OnboardingStep.entries
  val pagerState = rememberPagerState(pageCount = { steps.size })
  val scope = rememberCoroutineScope()
  val stepText = stringResource(
    R.string.onboarding_step_format,
    pagerState.currentPage + 1,
    steps.size
  )

  BackHandler(enabled = true) {
    if (pagerState.currentPage > 0) {
      scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }
    // On the first card Back is intentionally consumed: onboarding must be
    // finished via Skip or the primary action so the completion flag stays
    // honest.
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    OnboardingBackdrop(modifier = Modifier.fillMaxSize())
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
    ) {
      OnboardingTopBar(
        showBack = pagerState.currentPage > 0,
        stepText = stepText,
        onBack = {
          scope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
          }
        },
        onSkip = onFinish
      )
      HorizontalPager(
        state = pagerState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) { page ->
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
          verticalArrangement = Arrangement.Center
        ) {
          when (steps[page]) {
            OnboardingStep.WELCOME -> OnboardingWelcomePage()
            OnboardingStep.MAP -> MapPageBody()
            OnboardingStep.NEWS -> NewsPageBody()
            OnboardingStep.GUIDE -> GuidePageBody()
            OnboardingStep.TOOLS -> ToolsPageBody()
          }
        }
      }

      // Footer: progress dots + primary action (+ Log In on the final card),
      // pinned above the decorative hills so actions are always reachable.
      val last = pagerState.currentPage == steps.lastIndex
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        OnboardingPagerIndicator(
          pageCount = steps.size,
          currentPage = pagerState.currentPage,
          stepDescription = stepText,
          modifier = Modifier.padding(bottom = 16.dp)
        )
        OnboardingPrimaryButton(
          label = stringResource(
            when {
              pagerState.currentPage == 0 -> R.string.onboarding_get_started
              last -> R.string.onboarding_continue_sign_up
              else -> R.string.onboarding_continue
            }
          ),
          onClick = {
            if (last) {
              onFinish()
            } else {
              scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
              }
            }
          },
          modifier = Modifier.testTag(
            if (pagerState.currentPage == 0) "onboarding_get_started"
            else "onboarding_primary"
          )
        )
        if (last) {
          TextButton(
            onClick = onFinish,
            modifier = Modifier.testTag("onboarding_log_in")
          ) {
            Text(
              text = stringResource(R.string.onboarding_log_in),
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = OnboardingTeal
            )
          }
        } else {
          // Reserved space keeps the footer height stable so the primary
          // button never jumps between cards.
          Box(modifier = Modifier.height(48.dp))
        }
      }
      OnboardingHills(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
      )
    }
  }
}


@Composable
private fun OnboardingTopBar(
  showBack: Boolean,
  stepText: String,
  onBack: () -> Unit,
  onSkip: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp, start = 8.dp, end = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (showBack) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.onboarding_back_cd),
        tint = OnboardingNavy,
        modifier = Modifier
          .testTag("onboarding_back")
          .clip(CircleShape)
          .clickable(onClick = onBack)
          .padding(12.dp)
          .size(24.dp)
      )
    } else {
      // Placeholder keeps the step pill centered on the first card.
      Box(modifier = Modifier.size(48.dp))
    }
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xFFF1F5F9))
        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
        .padding(horizontal = 14.dp, vertical = 4.dp)
        .testTag("onboarding_step_pill")
    ) {
      Text(
        text = stepText,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = OnboardingSlate
      )
    }
    Text(
      text = stringResource(R.string.onboarding_skip),
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold,
      color = OnboardingTeal,
      modifier = Modifier
        .testTag("onboarding_skip")
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onSkip)
        .padding(horizontal = 16.dp, vertical = 12.dp)
    )
  }
}

@Composable
private fun OnboardingPrimaryButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Button(
    onClick = onClick,
    colors = ButtonDefaults.buttonColors(
      containerColor = OnboardingTeal,
      contentColor = Color.White
    ),
    shape = RoundedCornerShape(28.dp),
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
  ) {
    Text(
      text = label,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center
    )
    Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowForward,
      contentDescription = null,
      modifier = Modifier
        .padding(start = 8.dp)
        .size(18.dp)
    )
  }
}

/**
 * Shared body scaffold for cards 2–5: one word-free illustration, one
 * heading that wraps naturally, one short body paragraph that grows
 * vertically, and optional page-specific content below. No fixed-height
 * text boxes, so longer translations never clip.
 */
@Composable
private fun OnboardingPageBody(
  titleRes: Int,
  bodyRes: Int,
  modifier: Modifier = Modifier,
  illustration: @Composable () -> Unit,
  below: (@Composable () -> Unit)? = null
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp)
  ) {
    illustration()
    Text(
      text = stringResource(titleRes),
      fontSize = 24.sp,
      fontWeight = FontWeight.ExtraBold,
      color = OnboardingNavy,
      lineHeight = 30.sp,
      modifier = Modifier.padding(top = 16.dp)
    )
    Text(
      text = stringResource(bodyRes),
      fontSize = 14.sp,
      color = OnboardingSlate,
      lineHeight = 21.sp,
      modifier = Modifier.padding(top = 8.dp)
    )
    below?.invoke()
  }
}


// ---------------------------------------------------------------------------
// Card 2 — Radar Map: see risk around you.
// ---------------------------------------------------------------------------

@Composable
private fun MapPageBody() {
  OnboardingPageBody(
    titleRes = R.string.onboarding_map_title,
    bodyRes = R.string.onboarding_map_body,
    illustration = {
      MapHero(
        modifier = Modifier
          .fillMaxWidth()
          .height(190.dp)
      )
    }
  )
}

/**
 * Stylized map card: contour curves, radar rings around the user's location,
 * one hazard marker (amber) and one safer-area marker (green). Purely
 * illustrative — it describes map capabilities, it does not claim prediction.
 */
@Composable
private fun MapHero(modifier: Modifier = Modifier) {
  val cd = stringResource(R.string.onboarding_map_illustration_cd)
  Canvas(
    modifier = modifier
      .clip(RoundedCornerShape(20.dp))
      .semantics { contentDescription = cd }
  ) {
    drawRect(FlowHeroCardBg)
    val w = size.width
    val h = size.height
    // Contour curves.
    val contour = FlowContour
    drawPath(
      Path().apply {
        moveTo(-10f, h * 0.3f)
        quadraticTo(w * 0.3f, h * 0.05f, w * 0.55f, h * 0.35f)
        quadraticTo(w * 0.75f, h * 0.6f, w + 10f, h * 0.25f)
      },
      contour, style = Stroke(width = 3f)
    )
    drawPath(
      Path().apply {
        moveTo(-10f, h * 0.6f)
        quadraticTo(w * 0.35f, h * 0.5f, w * 0.6f, h * 0.75f)
        quadraticTo(w * 0.8f, h * 0.95f, w + 10f, h * 0.65f)
      },
      contour, style = Stroke(width = 3f)
    )
    drawPath(
      Path().apply {
        moveTo(-10f, h * 0.88f)
        quadraticTo(w * 0.4f, h * 0.78f, w * 0.7f, h * 0.98f)
        quadraticTo(w * 0.85f, h * 1.05f, w + 10f, h * 0.9f)
      },
      contour, style = Stroke(width = 3f)
    )
    // Hazard marker (amber) upper-left, safer-area marker (green) lower-right.
    val hazard = Offset(w * 0.26f, h * 0.34f)
    drawCircle(FlowHazardRing, radius = h * 0.13f, center = hazard)
    drawCircle(FlowHazard, radius = h * 0.06f, center = hazard)
    val safe = Offset(w * 0.76f, h * 0.68f)
    drawCircle(FlowSafeRing, radius = h * 0.13f, center = safe)
    drawCircle(FlowSafe, radius = h * 0.06f, center = safe)
    // User location: teal disc, white ring, teal core, with radar rings.
    drawCircle(FlowRadarOuter, radius = h * 0.30f, center = center)
    drawCircle(FlowRadarInner, radius = h * 0.19f, center = center)
    drawCircle(OnboardingTeal, radius = h * 0.10f, center = center)
    drawCircle(Color.White, radius = h * 0.055f, center = center)
    drawCircle(OnboardingTeal, radius = h * 0.022f, center = center)
  }
}

// ---------------------------------------------------------------------------
// Card 3 — News: stay informed.
// ---------------------------------------------------------------------------

@Composable
private fun NewsPageBody() {
  OnboardingPageBody(
    titleRes = R.string.onboarding_news_title,
    bodyRes = R.string.onboarding_news_body,
    illustration = {
      NewsHero(
        modifier = Modifier
          .fillMaxWidth()
          .height(190.dp)
      )
    }
  )
}

/**
 * Phone showing stacked update cards with an alert indicator. Text is drawn
 * as neutral bars (no real words), so the artwork needs no translation.
 */
@Composable
private fun NewsHero(modifier: Modifier = Modifier) {
  val cd = stringResource(R.string.onboarding_news_illustration_cd)
  Canvas(
    modifier = modifier
      .clip(RoundedCornerShape(20.dp))
      .semantics { contentDescription = cd }
  ) {
    drawRect(FlowPhoneCanvasBg)
    val w = size.width
    val h = size.height
    // Phone frame.
    val pw = w * 0.62f
    val ph = h * 0.86f
    val px = (w - pw) / 2f
    val py = (h - ph) / 2f
    drawRoundRect(
      color = Color.White,
      topLeft = Offset(px, py),
      size = Size(pw, ph),
      cornerRadius = CornerRadius(18f, 18f)
    )
    drawRoundRect(
      color = FlowPhoneBorder,
      topLeft = Offset(px, py),
      size = Size(pw, ph),
      cornerRadius = CornerRadius(18f, 18f),
      style = Stroke(width = 3f)
    )
    // Status bar inside the phone.
    drawRoundRect(
      color = OnboardingTeal,
      topLeft = Offset(px + pw * 0.10f, py + ph * 0.08f),
      size = Size(pw * 0.34f, ph * 0.045f),
      cornerRadius = CornerRadius(8f, 8f)
    )
    // Three update cards.
    val cardX = px + pw * 0.10f
    val cardW = pw * 0.80f
    val cardH = ph * 0.20f
    val iconTints = listOf(FlowHazard, FlowInfoCyan, FlowSafe)
    repeat(3) { i ->
      val cardY = py + ph * (0.18f + i * 0.26f)
      drawRoundRect(
        color = FlowCardBg,
        topLeft = Offset(cardX, cardY),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(10f, 10f)
      )
      // Icon square.
      drawRoundRect(
        color = iconTints[i],
        topLeft = Offset(cardX + cardW * 0.07f, cardY + cardH * 0.22f),
        size = Size(cardH * 0.56f, cardH * 0.56f),
        cornerRadius = CornerRadius(8f, 8f)
      )
      // Text bars.
      drawRoundRect(
        color = FlowBarDark,
        topLeft = Offset(cardX + cardW * 0.30f, cardY + cardH * 0.24f),
        size = Size(cardW * 0.52f, cardH * 0.16f),
        cornerRadius = CornerRadius(6f, 6f)
      )
      drawRoundRect(
        color = FlowBarLight,
        topLeft = Offset(cardX + cardW * 0.30f, cardY + cardH * 0.52f),
        size = Size(cardW * 0.62f, cardH * 0.13f),
        cornerRadius = CornerRadius(6f, 6f)
      )
      // Alert indicator on the first card.
      if (i == 0) {
        drawCircle(
          color = FlowHazard,
          radius = cardH * 0.10f,
          center = Offset(cardX + cardW * 0.90f, cardY + cardH * 0.30f)
        )
      }
    }
  }
}


// ---------------------------------------------------------------------------
// Card 4 — Instructions: Before / During / After guidance.
// ---------------------------------------------------------------------------

private enum class HazardKind { FLOOD, QUAKE, LANDSLIDE, FIRE }

@Composable
private fun GuidePageBody() {
  OnboardingPageBody(
    titleRes = R.string.onboarding_guide_title,
    bodyRes = R.string.onboarding_guide_body,
    illustration = {
      GuideHero(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp)
      )
    }
  )
}

/**
 * Four word-free hazard tiles (flood, earthquake, landslide, fire) with
 * localized labels beneath, plus the Before/During/After phase chips that
 * mirror the real Instructions tab structure.
 */
@Composable
private fun GuideHero(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      HazardTile(HazardKind.FLOOD, FlowKitBlue, R.string.onboarding_hazard_flood)
      HazardTile(HazardKind.QUAKE, FlowQuake, R.string.onboarding_hazard_quake)
      HazardTile(HazardKind.LANDSLIDE, FlowLandslide, R.string.onboarding_hazard_landslide)
      HazardTile(HazardKind.FIRE, FlowFire, R.string.onboarding_hazard_fire)
    }
    Row(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(top = 18.dp)
    ) {
      PhaseChip(R.string.onboarding_phase_before)
      PhaseChip(R.string.onboarding_phase_during)
      PhaseChip(R.string.onboarding_phase_after)
    }
  }
}

@Composable
private fun HazardTile(kind: HazardKind, tint: Color, labelRes: Int) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(60.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(tint.copy(alpha = 0.12f))
        .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
    ) {
      Canvas(modifier = Modifier.size(32.dp)) {
        val w = size.width
        val h = size.height
        when (kind) {
          HazardKind.FLOOD -> {
            repeat(2) { i ->
              val y = h * (0.38f + i * 0.26f)
              drawPath(
                Path().apply {
                  moveTo(w * 0.08f, y)
                  quadraticTo(w * 0.28f, y - h * 0.2f, w * 0.5f, y)
                  quadraticTo(w * 0.72f, y + h * 0.2f, w * 0.92f, y)
                },
                tint, style = Stroke(width = w * 0.09f)
              )
            }
          }
          HazardKind.QUAKE -> {
            drawPath(
              Path().apply {
                moveTo(w * 0.5f, h * 0.06f)
                lineTo(w * 0.38f, h * 0.34f)
                lineTo(w * 0.60f, h * 0.52f)
                lineTo(w * 0.40f, h * 0.72f)
                lineTo(w * 0.52f, h * 0.94f)
              },
              tint, style = Stroke(width = w * 0.09f)
            )
          }
          HazardKind.LANDSLIDE -> {
            drawPath(
              Path().apply {
                moveTo(w * 0.08f, h * 0.86f)
                lineTo(w * 0.62f, h * 0.14f)
                lineTo(w * 0.92f, h * 0.86f)
                close()
              },
              tint, style = Stroke(width = w * 0.08f)
            )
            drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.30f, h * 0.62f))
            drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.20f, h * 0.78f))
          }
          HazardKind.FIRE -> {
            drawPath(
              Path().apply {
                moveTo(w * 0.5f, h * 0.08f)
                cubicTo(w * 0.78f, h * 0.34f, w * 0.86f, h * 0.55f, w * 0.72f, h * 0.78f)
                cubicTo(w * 0.62f, h * 0.94f, w * 0.38f, h * 0.94f, w * 0.28f, h * 0.78f)
                cubicTo(w * 0.14f, h * 0.55f, w * 0.30f, h * 0.36f, w * 0.5f, h * 0.08f)
                close()
              },
              tint
            )
            drawCircle(
              Color.White.copy(alpha = 0.85f),
              radius = w * 0.10f,
              center = Offset(w * 0.5f, h * 0.72f)
            )
          }
        }
      }
    }
    Text(
      text = stringResource(labelRes),
      fontSize = 11.sp,
      fontWeight = FontWeight.Medium,
      color = OnboardingSlate,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 6.dp)
    )
  }
}

@Composable
private fun PhaseChip(labelRes: Int) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .clip(RoundedCornerShape(14.dp))
      .background(Color(0xFFF1F5F9))
      .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
      .padding(horizontal = 14.dp, vertical = 6.dp)
  ) {
    Text(
      text = stringResource(labelRes),
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = OnboardingTeal
    )
  }
}


// ---------------------------------------------------------------------------
// Card 5 — Safety tools in one place.
// ---------------------------------------------------------------------------

@Composable
private fun ToolsPageBody() {
  OnboardingPageBody(
    titleRes = R.string.onboarding_tools_title,
    bodyRes = R.string.onboarding_tools_body,
    illustration = {
      ToolsHero(
        modifier = Modifier
          .fillMaxWidth()
          .height(190.dp)
      )
    },
    below = {
      Text(
        text = stringResource(R.string.onboarding_ready_status),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = OnboardingTeal,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 14.dp)
          .testTag("onboarding_ready_status")
      )
    }
  )
}

/**
 * Phone with a shield, orbited by word-free tool chips: an SOS life buoy, a
 * route between two points, and a report flag. Illustrative only — the app
 * supports emergency services, it does not replace them.
 */
@Composable
private fun ToolsHero(modifier: Modifier = Modifier) {
  val cd = stringResource(R.string.onboarding_tools_illustration_cd)
  Canvas(
    modifier = modifier
      .clip(RoundedCornerShape(20.dp))
      .semantics { contentDescription = cd }
  ) {
    drawRect(FlowPhoneCanvasBg)
    val w = size.width
    val h = size.height
    // Central phone.
    val pw = w * 0.34f
    val ph = h * 0.72f
    val px = (w - pw) / 2f
    val py = (h - ph) / 2f
    drawRoundRect(Color.White, Offset(px, py), Size(pw, ph), CornerRadius(18f, 18f))
    drawRoundRect(FlowPhoneBorder, Offset(px, py), Size(pw, ph),
      CornerRadius(18f, 18f), style = Stroke(width = 3f))
    // Shield glyph on the phone screen.
    val cx = w / 2f
    val cy = h / 2f
    val s = ph * 0.16f
    drawPath(
      Path().apply {
        moveTo(cx, cy - s)
        lineTo(cx + s * 0.85f, cy - s * 0.55f)
        lineTo(cx + s * 0.85f, cy + s * 0.2f)
        quadraticTo(cx + s * 0.85f, cy + s * 0.8f, cx, cy + s * 1.1f)
        quadraticTo(cx - s * 0.85f, cy + s * 0.8f, cx - s * 0.85f, cy + s * 0.2f)
        lineTo(cx - s * 0.85f, cy - s * 0.55f)
        close()
      },
      OnboardingTeal
    )
    val c = Offset(cx, cy + s * 0.1f)
    val r = s * 0.38f
    drawLine(Color.White, c.copy(x = c.x - r, y = c.y),
      c.copy(x = c.x - r * 0.25f, y = c.y + r * 0.7f), strokeWidth = s * 0.14f)
    drawLine(Color.White, c.copy(x = c.x - r * 0.25f, y = c.y + r * 0.7f),
      c.copy(x = c.x + r, y = c.y - r * 0.6f), strokeWidth = s * 0.14f)

    // Floating tool chips: SOS buoy (top-left), route (top-right), report
    // flag (bottom-left), map pin (bottom-right).
    val chip = h * 0.26f
    val chipR = CornerRadius(14f, 14f)
    fun chipFrame(at: Offset) {
      drawRoundRect(Color.White, Offset(at.x - chip / 2, at.y - chip / 2),
        Size(chip, chip), chipR)
      drawRoundRect(FlowPhoneBorder, Offset(at.x - chip / 2, at.y - chip / 2),
        Size(chip, chip), chipR, style = Stroke(width = 2.5f))
    }
    val sos = Offset(w * 0.16f, h * 0.20f)
    chipFrame(sos)
    drawCircle(FlowSos, radius = chip * 0.26f, center = sos,
      style = Stroke(width = chip * 0.14f))
    drawCircle(Color.White, radius = chip * 0.10f, center = sos)

    val route = Offset(w * 0.84f, h * 0.26f)
    chipFrame(route)
    drawPath(
      Path().apply {
        moveTo(route.x - chip * 0.24f, route.y + chip * 0.20f)
        quadraticTo(route.x + chip * 0.28f, route.y + chip * 0.10f,
          route.x + chip * 0.20f, route.y - chip * 0.20f)
      },
      FlowInfoCyan,
      style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    )
    drawCircle(OnboardingTeal, radius = chip * 0.07f,
      center = Offset(route.x - chip * 0.24f, route.y + chip * 0.20f))
    drawCircle(FlowHazard, radius = chip * 0.07f,
      center = Offset(route.x + chip * 0.20f, route.y - chip * 0.20f))

    val flag = Offset(w * 0.18f, h * 0.76f)
    chipFrame(flag)
    drawLine(OnboardingNavy, Offset(flag.x - chip * 0.12f, flag.y + chip * 0.24f),
      Offset(flag.x - chip * 0.12f, flag.y - chip * 0.24f), strokeWidth = 4f)
    drawPath(
      Path().apply {
        moveTo(flag.x - chip * 0.12f, flag.y - chip * 0.24f)
        lineTo(flag.x + chip * 0.22f, flag.y - chip * 0.14f)
        lineTo(flag.x - chip * 0.12f, flag.y - chip * 0.02f)
        close()
      },
      FlowHazard
    )

    val pin = Offset(w * 0.84f, h * 0.78f)
    chipFrame(pin)
    drawCircle(FlowRadarInner, radius = chip * 0.26f, center = pin)
    drawCircle(OnboardingTeal, radius = chip * 0.13f, center = pin)
    drawCircle(Color.White, radius = chip * 0.06f, center = pin)
  }
}

// Flow-local illustration tints (calm civic palette, disaster accents kept
// sparing and descriptive, never alarming).
private val FlowHeroCardBg = Color(0xFFEFFAF6)
private val FlowContour = Color(0xFFA7F3D0)
private val FlowRadarOuter = Color(0x40A7F3D0)
private val FlowRadarInner = Color(0x595CF3D0)
private val FlowHazard = Color(0xFFD97706)
private val FlowHazardRing = Color(0x33D97706)
private val FlowSafe = Color(0xFF059669)
private val FlowSafeRing = Color(0x33059669)
private val FlowInfoCyan = Color(0xFF0E7490)
private val FlowKitBlue = Color(0xFF0284C7)
private val FlowQuake = Color(0xFF7C3AED)
private val FlowLandslide = Color(0xFFB45309)
private val FlowFire = Color(0xFFDC2626)
private val FlowSos = Color(0xFFDC2626)
private val FlowPhoneCanvasBg = Color(0xFFF4F9F8)
private val FlowPhoneBorder = Color(0xFFE2E8F0)
private val FlowCardBg = Color(0xFFF8FAFC)
private val FlowBarDark = Color(0xFFCBD5E1)
private val FlowBarLight = Color(0xFFE2E8F0)


