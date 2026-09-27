package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.config.AppRemoteConfig

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
  config: AppRemoteConfig = AppRemoteConfig(),
  content: @Composable () -> Unit,
) {
  // The base palette, then the remote brand applied as a full quartet. Passing
  // `config` in (rather than reading ConfigRegistry here) keeps this composable
  // free of any Firebase side effect and makes the override testable in isolation.
  val palette = remember(darkTheme, config) {
    ThemeOverride.apply(
      base = if (darkTheme) DarkVippattiColors else LightVippattiColors,
      primaryHex = config.primaryColorHex,
      secondaryHex = config.secondaryColorHex,
      dark = darkTheme
    )
  }

  val colorScheme = if (darkTheme) vippattiDarkScheme(palette) else vippattiLightScheme(palette)

  CompositionLocalProvider(LocalVippattiColors provides palette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = VippattiShapes,
      content = content
    )
  }
}
