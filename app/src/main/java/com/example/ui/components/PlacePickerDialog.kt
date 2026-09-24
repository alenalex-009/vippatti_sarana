package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.location.PlaceCandidate
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.WarningAmber

/**
 * PLACE PICKER — "look at a state/city/district" without needing GPS.
 * Searches real OSM places, lists India-only results, and choosing one puts
 * the whole app into a clearly-labelled VIEW MODE (never presented as the
 * user's own position).
 */
@Composable
fun PlacePickerDialog(
  query: String,
  isSearching: Boolean,
  candidates: List<PlaceCandidate>,
  error: String?,
  show: Boolean,
  onQueryChange: (String) -> Unit,
  onPick: (PlaceCandidate) -> Unit,
  onDismiss: () -> Unit
) {
  if (!show) return
  Dialog(onDismissRequest = onDismiss) {
    PlacePickerCard(
      query = query,
      isSearching = isSearching,
      candidates = candidates,
      error = error,
      onQueryChange = onQueryChange,
      onPick = onPick,
      onDismiss = onDismiss
    )
  }
}

/** The picker's content, outside any Dialog window (testable + reusable). */
@Composable
fun PlacePickerCard(
  query: String,
  isSearching: Boolean,
  candidates: List<PlaceCandidate>,
  error: String?,
  onQueryChange: (String) -> Unit,
  onPick: (PlaceCandidate) -> Unit,
  onDismiss: () -> Unit
) {
  Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(ObsidianSurface)
        .border(1.dp, TacticalCyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
        .padding(16.dp)
    ) {
      Text(
        "LOOK AT ANOTHER PLACE",
        fontSize = 12.sp, fontWeight = FontWeight.Black,
        color = TacticalCyan, letterSpacing = 1.sp
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "Type a district, city or state — e.g. \"Visakhapatnam\" or \"Assam\". " +
          "You will see its hazards, shelters and news. It is NOT your real location.",
        fontSize = 11.sp, color = TacticalOnSurfaceVariant, lineHeight = 15.sp
      )
      Spacer(Modifier.height(10.dp))
      TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search a place in India\u2026", fontSize = 12.sp) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null, tint = TacticalOnSurfaceVariant) },
        trailingIcon = {
          if (isSearching) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp).testTag("place_search_spinner"),
              strokeWidth = 2.dp, color = TacticalCyan
            )
          }
        },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TacticalOnSurface),
        colors = TextFieldDefaults.colors(
          focusedContainerColor = ObsidianContainerLow,
          unfocusedContainerColor = ObsidianContainerLow,
          focusedIndicatorColor = TacticalCyan,
          unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().testTag("place_search_input")
      )
      Spacer(Modifier.height(8.dp))
      error?.let {
        Text(it, fontSize = 11.sp, color = WarningAmber, modifier = Modifier.testTag("place_search_error"))
        Spacer(Modifier.height(6.dp))
      }
      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        items(candidates, key = { it.displayName }) { candidate ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(ObsidianContainerLow)
              .clickable { onPick(candidate) }
              .padding(horizontal = 12.dp, vertical = 10.dp)
              .testTag("place_candidate_${candidate.name.lowercase().replace(' ', '_')}"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier.size(30.dp).clip(CircleShape)
                .background(NeonEmerald.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Place, null, tint = NeonEmerald, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Text(
                candidate.name,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
              )
              Text(
                candidate.displayName,
                fontSize = 10.sp, color = TacticalOnSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
      if (!isSearching && candidates.isEmpty() && error == null && query.length < 2) {
        Text(
          "Start typing to search India-wide places.",
          fontSize = 11.sp, color = TacticalOnSurfaceVariant,
          modifier = Modifier.padding(vertical = 12.dp)
        )
      }
      Spacer(Modifier.height(8.dp))
      Box(
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .clip(RoundedCornerShape(10.dp))
          .clickable(onClick = onDismiss)
          .padding(horizontal = 18.dp, vertical = 8.dp)
      ) {
        Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurfaceVariant)
      }
    }
}

/** Banner shown app-wide while a CHOSEN place is being viewed. */
@Composable
fun PlaceViewBanner(
  label: String,
  onExit: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 10.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(WarningAmber.copy(alpha = 0.16f))
      .border(1.dp, WarningAmber.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
      .padding(horizontal = 10.dp, vertical = 6.dp)
      .testTag("place_view_banner"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(Icons.Default.Place, null, tint = WarningAmber, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(6.dp))
    Text(
      "VIEWING: ${label.substringBefore(',').ifBlank { label }} \u2014 not your location",
      fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface,
      maxLines = 1, overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )
    Text(
      "BACK TO ME",
      fontSize = 10.sp, fontWeight = FontWeight.Black, color = TacticalCyan,
      modifier = Modifier
        .clickable(onClick = onExit)
        .padding(horizontal = 6.dp, vertical = 2.dp)
        .testTag("place_view_exit")
    )
  }
}
