package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Page 1 (welcome) content of the onboarding carousel.
 *
 * Visual identity for Vippatti Sarana: the app emblem as hero, the brand
 * label, one concise heading and one short body paragraph. All copy is
 * localized through string resources so the six supported languages
 * (en/hi/te/ta/bn/mr) resolve automatically; the artwork carries no words.
 * Content only — paging, Skip/Back, dots and actions are owned by
 * [OnboardingFlowScreen] so every card behaves identically.
 */
@Composable
fun OnboardingWelcomePage(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Image(
      painter = painterResource(id = R.drawable.onboarding_hero_logo),
      contentDescription = stringResource(R.string.onboarding_emblem_cd),
      contentScale = ContentScale.Fit,
      modifier = Modifier
        .size(190.dp)
        .shadow(8.dp, CircleShape)
        .clip(CircleShape)
        .background(Color.White)
        .testTag("onboarding_hero_logo")
    )
    Text(
      text = stringResource(R.string.onboarding_brand_label),
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      color = OnboardingTeal,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 20.dp)
    )
    Text(
      text = stringResource(R.string.onboarding_welcome_title),
      fontSize = 26.sp,
      fontWeight = FontWeight.ExtraBold,
      color = OnboardingNavy,
      textAlign = TextAlign.Center,
      lineHeight = 32.sp,
      modifier = Modifier.padding(top = 8.dp)
    )
    Text(
      text = stringResource(R.string.onboarding_welcome_body),
      fontSize = 14.sp,
      fontWeight = FontWeight.Normal,
      color = OnboardingSlate,
      textAlign = TextAlign.Center,
      lineHeight = 21.sp,
      modifier = Modifier.padding(top = 10.dp)
    )
  }
}

/** Sky glow and pine silhouettes (all decorative, no text). */
@Composable
internal fun OnboardingBackdrop(modifier: Modifier = Modifier) {
  androidx.compose.foundation.Canvas(modifier = modifier) {
    // Soft mint atmosphere around the hero band.
    drawCircle(
      brush = Brush.radialGradient(
        0.0f to OnboardingGlow,
        0.55f to OnboardingGlowSoft,
        1.0f to Color.Transparent,
        center = center.copy(y = size.height * 0.22f),
        radius = size.width * 0.75f
      ),
      radius = size.width * 0.75f,
      center = center.copy(y = size.height * 0.22f)
    )
    // Pine silhouettes left and right of the hero.
    val pine = OnboardingPine
    val baseY = size.height * 0.30f
    val peakH = size.height * 0.09f
    val left = Path().apply {
      val w = size.width
      moveTo(0f, baseY)
      lineTo(w * 0.03f, baseY - peakH * 0.55f)
      lineTo(w * 0.055f, baseY - peakH * 0.2f)
      lineTo(w * 0.085f, baseY - peakH * 0.8f)
      lineTo(w * 0.11f, baseY - peakH * 0.35f)
      lineTo(w * 0.135f, baseY - peakH)
      lineTo(w * 0.165f, baseY)
      close()
    }
    drawPath(left, pine)
    val right = Path().apply {
      val w = size.width
      moveTo(w, baseY)
      lineTo(w * 0.97f, baseY - peakH * 0.55f)
      lineTo(w * 0.945f, baseY - peakH * 0.2f)
      lineTo(w * 0.915f, baseY - peakH * 0.8f)
      lineTo(w * 0.89f, baseY - peakH * 0.35f)
      lineTo(w * 0.865f, baseY - peakH)
      lineTo(w * 0.835f, baseY)
      close()
    }
    drawPath(right, pine)
  }
}

/** Rolling hills pinned to the bottom edge (drawn in-flow, below actions). */
@Composable
internal fun OnboardingHills(modifier: Modifier = Modifier) {
  androidx.compose.foundation.Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val rear = Path().apply {
      moveTo(0f, h * 0.75f)
      quadraticTo(w * 0.13f, h * 0.3f, w * 0.26f, h * 0.58f)
      quadraticTo(w * 0.39f, h * 0.82f, w * 0.53f, h * 0.37f)
      quadraticTo(w * 0.66f, h * 0.1f, w * 0.79f, h * 0.63f)
      quadraticTo(w * 0.9f, h * 0.85f, w, h * 0.47f)
      lineTo(w, h)
      lineTo(0f, h)
      close()
    }
    drawPath(rear, OnboardingHillRear)
    val front = Path().apply {
      moveTo(0f, h * 0.83f)
      cubicTo(w * 0.08f, h * 0.67f, w * 0.16f, h * 0.87f, w * 0.28f, h * 0.63f)
      cubicTo(w * 0.36f, h * 0.47f, w * 0.42f, h * 0.75f, w * 0.55f, h * 0.53f)
      cubicTo(w * 0.66f, h * 0.37f, w * 0.72f, h * 0.67f, w * 0.84f, h * 0.47f)
      cubicTo(w * 0.92f, h * 0.33f, w * 0.96f, h * 0.55f, w, h * 0.4f)
      lineTo(w, h)
      lineTo(0f, h)
      close()
    }
    drawPath(front, OnboardingHillFront)
  }
}


/**
 * Horizontal progress dots: ● ○ ○ ○ ○ with an elongated active pill.
 * The row itself exposes a localized "Step X of 5" content description so
 * screen readers announce progress; individual dots stay decorative.
 */
@Composable
internal fun OnboardingPagerIndicator(
  pageCount: Int,
  currentPage: Int,
  stepDescription: String,
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .testTag("onboarding_pager")
      .semantics { contentDescription = stepDescription }
  ) {
    repeat(pageCount) { index ->
      val active = index == currentPage
      Box(
        modifier = Modifier
          .then(
            if (active) {
              Modifier.size(width = 20.dp, height = 8.dp)
            } else {
              Modifier.size(8.dp)
            }
          )
          .clip(CircleShape)
          .background(if (active) OnboardingTeal else OnboardingDotInactive)
      )
    }
  }
}

// Fixed light palette for the onboarding artwork (calm civic teal/navy).
internal val OnboardingTeal = Color(0xFF0A5C53)
internal val OnboardingNavy = Color(0xFF0F2438)
internal val OnboardingSlate = Color(0xFF4B5563)
internal val OnboardingBodyGray = Color(0xFF6B7280)
internal val OnboardingDotInactive = Color(0xFFCBD5E1)
internal val OnboardingGlow = Color(0x140AB8A6)
internal val OnboardingGlowSoft = Color(0xCCF0FDF9)
internal val OnboardingPine = Color(0x33115E59)
internal val OnboardingHillRear = Color(0x590D5248)
internal val OnboardingHillFront = Color(0xB3083E37)

