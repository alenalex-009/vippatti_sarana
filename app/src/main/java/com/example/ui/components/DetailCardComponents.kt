package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.HolidayVillage
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber

/**
 * ============================================================================
 * SHARED DETAIL-CARD PRIMITIVES (approved reference redesign, 2026-10-03)
 * ============================================================================
 * One set of building blocks reused by the hazard and safe-zone detail
 * sheets so all five disaster types render through the same system.
 * Components NEVER invent values: null data renders as "Unavailable" /
 * "Not assessed" per the honesty rules, and the compact view carries at
 * most ONE provenance indicator.
 */

/** Circular leading icon chip used in both sheet headers. */
@Composable
fun DetailIconChip(
  icon: ImageVector,
  accent: Color,
  modifier: Modifier = Modifier,
  contentDescription: String? = null
) {
  Box(
    modifier = modifier
      .size(44.dp)
      .clip(CircleShape)
      .background(accent.copy(alpha = 0.14f))
      .border(1.5.dp, accent.copy(alpha = 0.55f), CircleShape),
    contentAlignment = Alignment.Center
  ) {
    Icon(icon, contentDescription = contentDescription, tint = accent,
      modifier = Modifier.size(22.dp))
  }
}

/** Severity/verification pill: icon + one word, colour-coded. */
@Composable
fun StatusPill(
  icon: ImageVector?,
  label: String,
  accent: Color,
  modifier: Modifier = Modifier,
  tag: String = ""
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(999.dp))
      .background(accent.copy(alpha = 0.13f))
      .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
      .padding(horizontal = 10.dp, vertical = 4.dp)
      .let { if (tag.isNotEmpty()) it.testTag(tag) else it },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
    }
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent,
      maxLines = 1)
  }
}

/** One compact summary card: grey label over bold value (+ optional sub). */
@Composable
fun SummaryCard(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  valueAccent: Color = TacticalOnSurface,
  subValue: String? = null,
  icon: ImageVector? = null,
  tag: String = "",
  unavailable: Boolean = false
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerHigh)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
      .padding(horizontal = 12.dp, vertical = 10.dp)
      .let { if (tag.isNotEmpty()) it.testTag(tag) else it },
    verticalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    Text(
      text = label, fontSize = 11.sp, color = TacticalOnSurfaceVariant,
      maxLines = 2, lineHeight = 14.sp
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      if (icon != null) {
        Icon(icon, contentDescription = null,
          tint = if (unavailable) TacticalOnSurfaceVariant.copy(alpha = 0.6f) else valueAccent,
          modifier = Modifier.size(14.dp))
      }
      Text(
        text = value,
        fontSize = 14.sp,
        fontWeight = if (unavailable) FontWeight.Medium else FontWeight.Bold,
        color = if (unavailable) TacticalOnSurfaceVariant else valueAccent,
        maxLines = 2, softWrap = true, overflow = TextOverflow.Visible
      )
    }
    if (subValue != null) {
      Text(subValue, fontSize = 10.sp, color = TacticalOnSurfaceVariant, maxLines = 2)
    }
  }
}

/**
 * WIDTH-ADAPTIVE SUMMARY CARD ROW (responsive detail-card fix).
 *
 * The rigid three-across row cramped "Affected radius | Trend | Detected" on
 * small phones: labels wrapped, timestamps forced tall cards. This container
 * measures the AVAILABLE WIDTH (never a device model) and arranges the three
 * cards so each keeps a readable minimum width:
 *
 *   wide   (>= 3 cards + 2 gaps)  -> [ Card ] [ Card ] [ Card ]
 *   normal (>= 2 cards + 1 gap)   -> [ Card ] [ Card ]  then full-width third
 *   narrow (< 2 cards + 1 gap)    -> each card full width, stacked
 *
 * Each slot receives the Modifier it must apply (weight in a row, full width
 * when stacked) so a short value can still stretch its card. Pure layout —
 * no content knowledge, no font-size changes.
 */
@Composable
fun AdaptiveStatCards(
  modifier: Modifier = Modifier,
  first: @Composable (Modifier) -> Unit,
  second: @Composable (Modifier) -> Unit,
  third: @Composable (Modifier) -> Unit
) {
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val gap = 8.dp
    // Minimum width one summary card needs to stay readable (a two-line
    // label, a bold value and a wrapped timestamp sub-line). Chosen so a
    // ~320dp phone stacks the cards, a ~360dp phone gets 2+1, and a tablet
    // / wide window shows three across.
    val minCardWidth = 144.dp
    when {
      maxWidth >= minCardWidth * 3 + gap * 2 -> {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
          first(Modifier.weight(1f))
          second(Modifier.weight(1f))
          third(Modifier.weight(1f))
        }
      }
      maxWidth >= minCardWidth * 2 + gap -> {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
          Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
          }
          third(Modifier.fillMaxWidth())
        }
      }
      else -> {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
          first(Modifier.fillMaxWidth())
          second(Modifier.fillMaxWidth())
          third(Modifier.fillMaxWidth())
        }
      }
    }
  }
}

/** Teal-tinted tappable "nearest safe zone" navigation card. */
@Composable
fun NearestSafeZoneCard(
  name: String,
  detailLine: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  unavailableLine: String? = null,
  tag: String = "nearest_safe_zone_card"
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(NeonEmerald.copy(alpha = 0.08f))
      .border(1.5.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp)
      .testTag(tag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    DetailIconChip(Icons.Default.HolidayVillage, NeonEmerald,
      contentDescription = "Nearest safe zone")
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("Nearest safe zone", fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = NeonEmerald, letterSpacing = 0.3.sp)
      Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface,
        maxLines = 2)
      Text(unavailableLine ?: detailLine, fontSize = 12.sp,
        color = if (unavailableLine != null) TacticalOnSurfaceVariant else TacticalOnSurface.copy(alpha = 0.85f),
        maxLines = 2)
    }
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(18.dp))
  }
}

/** Label-left / value-right row inside a bordered panel. */
@Composable
fun DetailRow(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  valueAccent: Color = TacticalOnSurface,
  subNote: String? = null,
  icon: ImageVector? = null,
  tag: String = "",
  unavailable: Boolean = false
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .let { if (tag.isNotEmpty()) it.testTag(tag) else it }
      .padding(vertical = 7.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null,
        tint = if (unavailable) TacticalOnSurfaceVariant.copy(alpha = 0.55f) else valueAccent,
        modifier = Modifier.size(16.dp))
    }
    Text(label, fontSize = 13.sp, color = TacticalOnSurfaceVariant,
      lineHeight = 17.sp, modifier = Modifier.weight(0.45f))
    Column(
      modifier = Modifier.weight(0.55f),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
      Text(
        text = value,
        fontSize = 13.sp,
        fontWeight = if (unavailable) FontWeight.Normal else FontWeight.Bold,
        color = if (unavailable) TacticalOnSurfaceVariant else valueAccent,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
        maxLines = 3, softWrap = true, overflow = TextOverflow.Visible
      )
      if (subNote != null) {
        Text(subNote, fontSize = 10.sp, color = TacticalOnSurfaceVariant, maxLines = 2)
      }
    }
  }
}

/** Section title with leading accent icon (KEY INFORMATION etc.). */
@Composable
fun SectionHeader(title: String, icon: ImageVector, accent: Color = TacticalCyan) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
    Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black,
      color = accent, letterSpacing = 0.7.sp)
  }
}

/** Bordered panel that stacks rows separated by hairlines. */
@Composable
fun DetailPanel(
  modifier: Modifier = Modifier,
  accent: Color = TacticalOutlineVariant,
  content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerHigh)
      .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
      .padding(horizontal = 14.dp, vertical = 4.dp)
  ) {
    content()
  }
}

/** Hairline separator for DetailPanel rows. */
@Composable
fun RowDividerLine() {
  Box(
    Modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(TacticalOutlineVariant.copy(alpha = 0.22f))
  )
}

/** Footer "Area centre" card: coordinates + copy button (real geometry only). */
@Composable
fun AreaCentreCard(
  latText: String,
  lonText: String,
  modifier: Modifier = Modifier,
  address: String? = null
) {
  val clipboard = LocalClipboardManager.current
  val coords = "$latText, $lonText"
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
      .padding(horizontal = 14.dp, vertical = 12.dp)
      .testTag("area_centre_card"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Icon(Icons.Default.Place, contentDescription = null, tint = TacticalCyan,
      modifier = Modifier.size(20.dp))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("Area centre", fontSize = 12.sp, fontWeight = FontWeight.Bold,
        color = TacticalOnSurface)
      Text(coords, fontSize = 13.sp, color = TacticalOnSurfaceVariant, maxLines = 2)
      if (!address.isNullOrBlank()) {
        Text(address, fontSize = 11.sp, color = TacticalOnSurfaceVariant,
          maxLines = 2, lineHeight = 14.sp)
      }
    }
    Icon(
      imageVector = Icons.Default.ContentCopy,
      contentDescription = "Copy coordinates",
      tint = TacticalOnSurfaceVariant,
      modifier = Modifier
        .size(20.dp)
        .clip(CircleShape)
        .clickable {
          clipboard.setText(AnnotatedString(coords))
        }
        .testTag("area_centre_copy")
    )
  }
}

/**
 * Facility resource row with the honest three-way wording the user asked
 * for: a quantitative figure -> "Available"; only a qualitative flag ->
 * "Available — quantity not provided"; absence -> "Not available".
 */
@Composable
fun ResourceHonestyRow(
  label: String,
  icon: ImageVector,
  flag: Boolean,
  quantitativeValueText: String? = null,
  notProvidedSuffix: String = " — quantity not provided"
) {
  val (text, accent) = when {
    !flag -> "Not available" to EmergencyRedBright
    quantitativeValueText != null -> quantitativeValueText to NeonEmerald
    else -> "Available$notProvidedSuffix" to WarningAmber
  }
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top
  ) {
    Icon(icon, contentDescription = null, tint = if (flag) accent else EmergencyRedBright,
      modifier = Modifier.size(16.dp))
    Text(label, fontSize = 13.sp, color = TacticalOnSurfaceVariant, modifier = Modifier.weight(0.55f))
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent,
      textAlign = androidx.compose.ui.text.style.TextAlign.End,
      modifier = Modifier.weight(0.45f), maxLines = 2)
  }
}

/** Compact capacity gauge: "225 / 300" + % caption + progress bar. */
@Composable
fun CapacityGauge(
  available: Int,
  total: Int,
  modifier: Modifier = Modifier,
  accent: Color = NeonEmerald
) {
  val fraction = if (total > 0) available.toFloat() / total.toFloat() else 0f
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerHigh)
      .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
      .padding(14.dp)
      .testTag("capacity_gauge"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(Icons.Default.Groups, contentDescription = null, tint = TacticalOnSurface,
        modifier = Modifier.size(18.dp))
      Text(
        text = if (total > 0) "$available / $total" else "$available",
        fontSize = 20.sp, fontWeight = FontWeight.Black, color = TacticalOnSurface
      )
      Spacer(Modifier.weight(1f))
      Text(
        text = if (total > 0) "${(fraction * 100).toInt()}% available" else "spaces free",
        fontSize = 12.sp, color = TacticalOnSurfaceVariant
      )
    }
    LinearProgressIndicator(
      progress = { fraction.coerceIn(0f, 1f) },
      modifier = Modifier.fillMaxWidth().height(7.dp)
        .clip(RoundedCornerShape(999.dp)),
      color = accent,
      trackColor = ObsidianContainerLow
    )
  }
}

// The icon set used by the two redesigned sheets (single source).
object DetailIcons {
  val water: ImageVector get() = Icons.Default.WaterDrop
  val food: ImageVector get() = Icons.Default.Kitchen
  val power: ImageVector get() = Icons.Default.Bolt
  val sanitation: ImageVector get() = Icons.Default.CleaningServices
  val medical: ImageVector get() = Icons.Default.MedicalServices
  val check: ImageVector get() = Icons.Default.Check
  val close: ImageVector get() = Icons.Default.Close
}
