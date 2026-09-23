package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant


/**
 * ============================================================================
 * INSTRUCTION POSTERS — the app's own disaster-safety infographics
 * ============================================================================
 * The four source PNGs supplied in <repo>/InstructionsImages (Flood,
 * Earthquake, Landslide, Fire) are shipped as drawable-nodpi resources:
 *
 *   InstructionsImages/Flood.png      -> res/drawable-nodpi/instructions_poster_flood.png
 *   InstructionsImages/Earthquake.png -> res/drawable-nodpi/instructions_poster_earthquake.png
 *   InstructionsImages/Landslide.png  -> res/drawable-nodpi/instructions_poster_landslide.png
 *   InstructionsImages/Fire.png       -> res/drawable-nodpi/instructions_poster_fire.png
 *
 * The images are byte-identical copies (only the file NAME was made
 * resource-safe); nothing was re-drawn, re-compressed or cropped.
 *
 * WHY drawable-nodpi: the posters are large raster artwork (1024x1536 and
 * 941x1672 px) whose teaching value is in the small captions printed inside
 * the graphic. In a density-specific folder they would be resampled (and, in
 * a low-density bucket, upscaled into a much larger bitmap). nodpi keeps a 1:1
 * pixel mapping and lets Image() scale them proportionally instead.
 *
 * Each poster is ONE combined infographic that already contains the whole
 * BEFORE / DURING / AFTER journey for its disaster, so it is shown once per
 * disaster (above the phase-specific written guidance), never once per phase.
 * The written instruction content of the module is untouched and stays the
 * authoritative, fully localized source of guidance.
 */

/** A shipped poster: its drawable plus the aspect ratio measured from the file. */
private data class DisasterPoster(
  val drawableRes: Int,
  /** width / height, measured from the shipped PNG (see the pixel sizes above). */
  val aspectRatio: Float
)

/**
 * Disaster id (from the existing DisasterInstructions categories) -> poster.
 * "flood", "earthquake", "landslide" and "fire" are exactly the four ids the
 * instruction data model already uses, so no new id scheme is introduced.
 */
private val disasterPosters: Map<String, DisasterPoster> = mapOf(
  "flood" to DisasterPoster(R.drawable.instructions_poster_flood, 1024f / 1536f),
  "earthquake" to DisasterPoster(R.drawable.instructions_poster_earthquake, 1024f / 1536f),
  "landslide" to DisasterPoster(R.drawable.instructions_poster_landslide, 941f / 1672f),
  "fire" to DisasterPoster(R.drawable.instructions_poster_fire, 941f / 1672f)
)

/** Poster drawable for a disaster id, or null when that id has no poster. */
internal fun disasterPosterRes(categoryId: String): Int? =
  disasterPosters[categoryId]?.drawableRes

/** Measured aspect ratio (width / height) of a disaster poster, or null. */
internal fun disasterPosterAspectRatio(categoryId: String): Float? =
  disasterPosters[categoryId]?.aspectRatio

/** Test tags — one source of truth shared by the screens and the tests. */
internal object PosterTags {
  fun image(categoryId: String) = "instructions_poster_image_$categoryId"
  const val VIEWER = "instructions_poster_viewer"
  const val VIEWER_IMAGE = "instructions_poster_viewer_image"
  const val CLOSE = "instructions_poster_viewer_close"
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f

/** Double-tap zoom level — enough to read the captions printed in the artwork. */
private const val DOUBLE_TAP_SCALE = 3f

/**
 * The safety poster card for one disaster.
 *
 * Layout contract (portrait phones):
 *  - the card is full width and the IMAGE keeps its natural aspect ratio, so
 *    nothing is stretched, cropped or shrunk into an unreadable thumbnail;
 *  - rounded corners and one hairline border, matching the other instruction
 *    cards; comfortable padding so the artwork is never crowded;
 *  - tapping the poster opens the zoom reader ([InstructionPosterZoomOverlay]),
 *    which is what makes the small in-artwork captions readable;
 *  - every visible label is a localized string resource and the image carries a
 *    localized content description — no guidance lives only inside the art.
 */
@Composable
internal fun DisasterInstructionPoster(
  categoryId: String,
  disasterTitle: String,
  onOpen: () -> Unit,
  modifier: Modifier = Modifier
) {
  val poster = disasterPosters[categoryId] ?: return
  val description = stringResource(R.string.instructions_poster_content_desc, disasterTitle)
  val openLabel = stringResource(R.string.instructions_poster_open)
  val painter = painterResource(poster.drawableRes)

  // Prefer the decoded bitmap's own ratio (so a future artwork swap still
  // renders correctly); fall back to the ratio measured from the shipped PNG —
  // the same value, so the very first frame never jumps.
  val intrinsic = painter.intrinsicSize
  val ratio =
    if (intrinsic.width.isFinite() && intrinsic.height.isFinite() && intrinsic.height > 0f) {
      intrinsic.width / intrinsic.height
    } else {
      poster.aspectRatio
    }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
      .padding(10.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 2.dp, end = 2.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        imageVector = Icons.Default.ZoomIn,
        contentDescription = null,
        tint = TacticalCyan,
        modifier = Modifier.size(16.dp)
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.instructions_poster_title).uppercase(),
          fontSize = 11.sp,
          fontWeight = FontWeight.Black,
          color = TacticalOnSurface,
          letterSpacing = 0.6.sp
        )
        Text(
          text = stringResource(R.string.instructions_poster_subtitle),
          fontSize = 10.sp,
          color = TacticalOnSurfaceVariant,
          lineHeight = 13.sp
        )
      }
    }

    Image(
      painter = painter,
      contentDescription = null, // the tappable container carries the description
      contentScale = ContentScale.Fit,
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(ratio)
        .clip(RoundedCornerShape(10.dp))
        .background(ObsidianContainer)
        .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
        .semantics { contentDescription = description }
        .testTag(PosterTags.image(categoryId))
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 2.dp, end = 2.dp, top = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = Icons.Default.ZoomIn,
        contentDescription = null,
        tint = TacticalCyan,
        modifier = Modifier.size(14.dp)
      )
      Text(
        text = openLabel,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalCyan,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

/**
 * Compact card thumbnail of the approved poster, used on the Instructions
 * home screen so each disaster choice is visually recognisable.
 *
 * This is deliberately decorative navigation (a small Crop fit keeps the four
 * cards uniform); the FULL, uncropped poster is shown at natural aspect ratio
 * on the disaster screen — nothing instructional is lost to the crop.
 */
@Composable
internal fun DisasterPosterThumbnail(categoryId: String, modifier: Modifier = Modifier) {
  val poster = disasterPosters[categoryId] ?: return
  Image(
    painter = painterResource(poster.drawableRes),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = modifier
      .size(width = 44.dp, height = 62.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(ObsidianContainer)
      .testTag("instructions_poster_thumb_$categoryId")
  )
}

/**
 * Full-screen reader for one poster — rendered at the root of the Instructions
 * module, so it covers the whole module without touching navigation.
 *
 * Zoom is not a gimmick here: each poster packs a 4-5 column, 3-phase
 * infographic into ~1000 px, so the captions printed inside the artwork only
 * become legible once magnified. Scale is clamped to MIN_SCALE..MAX_SCALE and
 * panning is clamped to the magnified bounds, so the poster can never be flung
 * off-screen or cropped by the gesture itself.
 */
@Composable
internal fun InstructionPosterZoomOverlay(
  categoryId: String,
  disasterTitle: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val poster = disasterPosters[categoryId] ?: return
  val description = stringResource(R.string.instructions_poster_content_desc, disasterTitle)

  var scale by rememberSaveable { mutableStateOf(MIN_SCALE) }
  var offsetX by rememberSaveable { mutableStateOf(0f) }
  var offsetY by rememberSaveable { mutableStateOf(0f) }
  var viewport by remember { mutableStateOf(IntSize.Zero) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF06090F))
      .testTag(PosterTags.VIEWER)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.instructions_poster_title).uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = TacticalOnSurface,
            letterSpacing = 0.6.sp
          )
          Text(
            text = disasterTitle,
            fontSize = 11.sp,
            color = TacticalOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(ObsidianContainer)
            .testTag(PosterTags.CLOSE)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.instructions_poster_close),
            tint = TacticalOnSurface,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .clipToBounds()
          .onSizeChanged { viewport = it }
      ) {
        Image(
          painter = painterResource(poster.drawableRes),
          contentDescription = description,
          contentScale = ContentScale.Fit,
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
              scaleX = scale,
              scaleY = scale,
              translationX = offsetX,
              translationY = offsetY
            )
            .pointerInput(viewport) {
              detectTransformGestures { _, pan, zoom, _ ->
                val nextScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                val maxX = (viewport.width * (nextScale - 1f)) / 2f
                val maxY = (viewport.height * (nextScale - 1f)) / 2f
                scale = nextScale
                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                if (nextScale <= MIN_SCALE) {
                  offsetX = 0f
                  offsetY = 0f
                }
              }
            }
            .pointerInput(Unit) {
              detectTapGestures(
                onDoubleTap = {
                  if (scale > MIN_SCALE) {
                    scale = MIN_SCALE
                    offsetX = 0f
                    offsetY = 0f
                  } else {
                    scale = DOUBLE_TAP_SCALE
                  }
                }
              )
            }
            .testTag(PosterTags.VIEWER_IMAGE)
        )
      }

      Text(
        text = stringResource(R.string.instructions_poster_hint),
        fontSize = 10.sp,
        color = TacticalOnSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp)
      )
    }
  }
}

