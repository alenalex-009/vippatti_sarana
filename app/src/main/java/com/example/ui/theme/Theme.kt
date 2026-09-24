package com.example.ui.theme

import android.graphics.Color.parseColor
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.example.config.ConfigRegistry
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * ONE source of truth for the application theme. VippattiTheme receives the
 * app-level isDarkTheme state (from VippattiViewModel.uiState) and provides
 * both the Material color scheme and the global [VippattiColors] palette, so
 * every token (NeonEmerald, ObsidianSurface, TacticalOnSurface, ...) resolves
 * theme-aware everywhere. Switching themes is instantaneous app-wide.
 */
val LocalVippattiColors = staticCompositionLocalOf { DarkVippattiColors }

private fun vippattiDarkScheme(p: VippattiColors) = darkColorScheme(
  primary = p.neonEmerald,
  onPrimary = p.onNeonEmerald,
  primaryContainer = p.neonEmeraldContainer,
  onPrimaryContainer = p.onNeonEmeraldContainer,
  secondary = p.tacticalCyan,
  onSecondary = p.onTacticalCyan,
  secondaryContainer = p.tacticalCyanContainer,
  onSecondaryContainer = p.onTacticalCyanContainer,
  background = p.obsidianBg,
  onBackground = p.tacticalOnSurface,
  surface = p.obsidianSurface,
  onSurface = p.tacticalOnSurface,
  surfaceVariant = p.obsidianContainerHighest,
  onSurfaceVariant = p.tacticalOnSurfaceVariant,
  outline = p.tacticalOutline,
  outlineVariant = p.tacticalOutlineVariant,
  error = p.emergencyRedBright,
  onError = p.onEmergencyRed,
  errorContainer = p.emergencyRedContainer,
  onErrorContainer = p.onEmergencyRedContainer
)

private fun vippattiLightScheme(p: VippattiColors) = lightColorScheme(
  primary = p.neonEmerald,
  onPrimary = p.onNeonEmerald,
  primaryContainer = p.neonEmeraldContainer,
  onPrimaryContainer = p.onNeonEmeraldContainer,
  secondary = p.tacticalCyan,
  onSecondary = p.onTacticalCyan,
  secondaryContainer = p.tacticalCyanContainer,
  onSecondaryContainer = p.onTacticalCyanContainer,
  background = p.obsidianBg,
  onBackground = p.tacticalOnSurface,
  surface = p.obsidianSurface,
  onSurface = p.tacticalOnSurface,
  surfaceVariant = p.obsidianContainerHighest,
  onSurfaceVariant = p.tacticalOnSurfaceVariant,
  outline = p.tacticalOutline,
  outlineVariant = p.tacticalOutlineVariant,
  error = p.emergencyRedBright,
  onError = p.onEmergencyRed,
  errorContainer = p.emergencyRedContainer,
  onErrorContainer = p.onEmergencyRedContainer
)

@Composable
fun VippattiTheme(
  darkTheme: Boolean = false, // Daylight-first: the light palette matches the light map
  content: @Composable () -> Unit,
) {
  val remoteConfig by ConfigRegistry.manager.configState.collectAsStateWithLifecycle()
  
  val basePalette = if (darkTheme) DarkVippattiColors else LightVippattiColors
  
  val primaryOverride = try {
      if (remoteConfig.primaryColorHex.isNotBlank()) Color(parseColor(remoteConfig.primaryColorHex)) else basePalette.neonEmerald
  } catch (e: Exception) { basePalette.neonEmerald }

  val secondaryOverride = try {
      if (remoteConfig.secondaryColorHex.isNotBlank()) Color(parseColor(remoteConfig.secondaryColorHex)) else basePalette.tacticalCyan
  } catch (e: Exception) { basePalette.tacticalCyan }

  val palette = basePalette.copy(
      neonEmerald = primaryOverride,
      tacticalCyan = secondaryOverride
  )
  
  val colorScheme = if (darkTheme) vippattiDarkScheme(palette) else vippattiLightScheme(palette)

  CompositionLocalProvider(LocalVippattiColors provides palette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}
