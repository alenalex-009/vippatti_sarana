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
  /**
   * Remote brand override (audit B3). Passed IN rather than read from the
   * ConfigRegistry here so this composable has no Firebase side effect and
   * the override is testable in isolation; MainActivity supplies the live
   * value. Defaults keep every existing call site and Robolectric test intact.
   */
  config: AppRemoteConfig = AppRemoteConfig(),
  /**
   * The user-selected brand palette. Defaults to [ColorTheme.DEFAULT] so every
   * existing call site keeps the approved default appearance.
   */
  colorTheme: ColorTheme = ColorTheme.DEFAULT,
  content: @Composable () -> Unit,
) {
  // Two independent feature paths, composed in one place: the user's chosen
  // brand palette (colorTheme, per light/dark) forms the base, and the remote
  // config brand hexes - when present - are applied over it as a FULL
  // contrast-safe quartet by ThemeOverride (re-derives on-primary/container
  // roles + validates the hex; B3). Never a bare single-colour swap.
  val palette = remember(darkTheme, config, colorTheme) {
    ThemeOverride.apply(
      base = colorTheme.palette(darkTheme),
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
