package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ============================================================================
 * VIPPATTI SARANA — GLOBAL THEME SYSTEM (single source of truth)
 * ============================================================================
 * One immutable palette per theme (dark + light). Every named token below is
 * a THEME-AWARE composable getter backed by [LocalVippattiColors] (see
 * Theme.kt), which VippattiTheme provides from the app-level isDarkTheme
 * state in the ViewModel. Switching the theme instantly re-resolves every
 * token in every screen, dialog, bottom nav bar, switch and button — no
 * screen keeps a private copy of theme colors.
 *
 * Dark mode  = deep obsidian surfaces, mint emerald accent, red emergency.
 * Light mode = an INTENTIONAL palette (not a simple inversion): pale sage
 *              background, white elevated cards, deep emerald accent, the
 *              same red emergency language with light-suitable containers.
 */

/**
 * App-wide palette of every semantic color the UI consumes.
 * Field names mirror the public token names one-to-one.
 */
data class VippattiColors(
  // Brand / accent (Vippatti Sarana green)
  val neonEmerald: Color,
  val neonEmeraldContainer: Color,
  val onNeonEmerald: Color,
  val onNeonEmeraldContainer: Color,
  // Secondary operations cyan
  val tacticalCyan: Color,
  val tacticalCyanContainer: Color,
  val onTacticalCyan: Color,
  val onTacticalCyanContainer: Color,
  // Surface elevation ramp
  val obsidianBg: Color,
  val obsidianSurface: Color,
  val obsidianContainerLowest: Color,
  val obsidianContainerLow: Color,
  val obsidianContainer: Color,
  val obsidianContainerHigh: Color,
  val obsidianContainerHighest: Color,
  val obsidianBright: Color,
  // Content / outlines
  val tacticalOnSurface: Color,
  val tacticalOnSurfaceVariant: Color,
  val tacticalOutline: Color,
  val tacticalOutlineVariant: Color,
  // Emergency / severe alert reds
  val emergencyRed: Color,
  val emergencyRedBright: Color,
  val emergencyRedContainer: Color,
  val onEmergencyRed: Color,
  val onEmergencyRedContainer: Color,
  // Bottom navigation
  val tacticalNavBg: Color,
  val tacticalNavBorder: Color,
  val tacticalNavInactive: Color,
  // Amber warning
  val warningAmber: Color,
  /**
   * SEMANTIC "safe / low risk / proceed" green.
   *
   * This is deliberately NOT a themeable brand colour. A handful of surfaces
   * use green to mean "you are safe" rather than "this is the app accent" - the
   * Profile safety status, RiskLevel.GREEN, the shelter "GO" guidance and the
   * evacuation action. Those must keep the exact same green in every theme, or
   * a safety signal would silently change meaning when the user picks a
   * different palette.
   *
   * General chrome that merely wants "the accent colour" uses [neonEmerald].
   */
  val safeGreen: Color,
  /** Content colour that meets contrast on top of [safeGreen]. */
  val onSafeGreen: Color
)

/** Dark theme palette — the established Vippatti Sarana tactical identity. */
val DarkVippattiColors = VippattiColors(
  neonEmerald = Color(0xFF6DFFBA),
  neonEmeraldContainer = Color(0xFF00E599),
  onNeonEmerald = Color(0xFF003822),
  onNeonEmeraldContainer = Color(0xFF00613E),
  tacticalCyan = Color(0xFFA6E6FF),
  tacticalCyanContainer = Color(0xFF14D1FF),
  onTacticalCyan = Color(0xFF003543),
  onTacticalCyanContainer = Color(0xFF00566B),
  obsidianBg = Color(0xFF111316),
  obsidianSurface = Color(0xFF111316),
  obsidianContainerLowest = Color(0xFF0C0E11),
  obsidianContainerLow = Color(0xFF1A1C1F),
  obsidianContainer = Color(0xFF1E2023),
  obsidianContainerHigh = Color(0xFF282A2D),
  obsidianContainerHighest = Color(0xFF333538),
  obsidianBright = Color(0xFF37393D),
  tacticalOnSurface = Color(0xFFE2E2E6),
  tacticalOnSurfaceVariant = Color(0xFFBACBBE),
  tacticalOutline = Color(0xFF849589),
  tacticalOutlineVariant = Color(0xFF3B4A41),
  emergencyRed = Color(0xFFDC2626),
  emergencyRedBright = Color(0xFFFF5449),
  emergencyRedContainer = Color(0xFF93000A),
  onEmergencyRed = Color(0xFF690005),
  onEmergencyRedContainer = Color(0xFFFFDAD6),
  tacticalNavBg = Color(0xFF17362B),
  tacticalNavBorder = Color(0xFF204B3D),
  tacticalNavInactive = Color(0xFF8DCEB8),
  warningAmber = Color(0xFFF59E0B),
  // Fixed across every theme (see VippattiColors.safeGreen).
  safeGreen = Color(0xFF34D399),
  onSafeGreen = Color(0xFF00301C)
)

/**
 * Light theme palette — intentionally designed (not inverted): pale sage
 * canvas, white cards, deep emerald accent, dark green-tinted text, light
 * red/amber containers with dark content for contrast.
 */
val LightVippattiColors = VippattiColors(
  // Kept in step with ColorTheme.FOREST_GREEN.lightPalette, which is the same
  // green deepened to clear WCAG AA (see ColorTheme).
  neonEmerald = Color(0xFF00714C),
  neonEmeraldContainer = Color(0xFFB9F4D9),
  onNeonEmerald = Color(0xFFEFFCF6),
  onNeonEmeraldContainer = Color(0xFF004D33),
  tacticalCyan = Color(0xFF0369A1),
  tacticalCyanContainer = Color(0xFFBEE8FA),
  onTacticalCyan = Color(0xFF062A38),
  onTacticalCyanContainer = Color(0xFF075985),
  obsidianBg = Color(0xFFF2F7F4),
  obsidianSurface = Color(0xFFF2F7F4),
  obsidianContainerLowest = Color(0xFFE8EFEA),
  obsidianContainerLow = Color(0xFFFFFFFF),
  obsidianContainer = Color(0xFFEDF3EF),
  obsidianContainerHigh = Color(0xFFE3ECE6),
  obsidianContainerHighest = Color(0xFFD9E5DD),
  obsidianBright = Color(0xFFCFE0D5),
  tacticalOnSurface = Color(0xFF11251C),
  tacticalOnSurfaceVariant = Color(0xFF4E6659),
  tacticalOutline = Color(0xFF9DB4A5),
  tacticalOutlineVariant = Color(0xFFD3E2D9),
  emergencyRed = Color(0xFFDC2626),
  emergencyRedBright = Color(0xFFD92D20),
  emergencyRedContainer = Color(0xFFFDE4E1),
  onEmergencyRed = Color(0xFFFFFFFF),
  onEmergencyRedContainer = Color(0xFFA80011),
  tacticalNavBg = Color(0xFFF6FAF7),
  tacticalNavBorder = Color(0xFFD3EBDD),
  tacticalNavInactive = Color(0xFF59745F),
  warningAmber = Color(0xFFD97706),
  // Fixed across every theme (see VippattiColors.safeGreen).
  safeGreen = Color(0xFF047857),
  onSafeGreen = Color(0xFFFFFFFF)
)

// ============================================================================
// THEME-AWARE TOKENS — the same names the whole codebase already consumes.
// Each getter reads the single global palette, so one theme switch updates
// every screen, dialog, bottom bar, switch and button instantly.
// ============================================================================

// Brand / accent (Vippatti Sarana green)
val NeonEmerald: Color @Composable get() = LocalVippattiColors.current.neonEmerald
val NeonEmeraldContainer: Color @Composable get() = LocalVippattiColors.current.neonEmeraldContainer
val OnNeonEmerald: Color @Composable get() = LocalVippattiColors.current.onNeonEmerald
val OnNeonEmeraldContainer: Color @Composable get() = LocalVippattiColors.current.onNeonEmeraldContainer

// Secondary operations cyan
val TacticalCyan: Color @Composable get() = LocalVippattiColors.current.tacticalCyan
val TacticalCyanContainer: Color @Composable get() = LocalVippattiColors.current.tacticalCyanContainer
val OnTacticalCyan: Color @Composable get() = LocalVippattiColors.current.onTacticalCyan
val OnTacticalCyanContainer: Color @Composable get() = LocalVippattiColors.current.onTacticalCyanContainer

// Surface elevation ramp
val ObsidianSurface: Color @Composable get() = LocalVippattiColors.current.obsidianSurface
val ObsidianContainerLowest: Color @Composable get() = LocalVippattiColors.current.obsidianContainerLowest
val ObsidianContainerLow: Color @Composable get() = LocalVippattiColors.current.obsidianContainerLow
val ObsidianContainer: Color @Composable get() = LocalVippattiColors.current.obsidianContainer
val ObsidianContainerHigh: Color @Composable get() = LocalVippattiColors.current.obsidianContainerHigh
val ObsidianContainerHighest: Color @Composable get() = LocalVippattiColors.current.obsidianContainerHighest
val ObsidianBright: Color @Composable get() = LocalVippattiColors.current.obsidianBright

// Content / outlines
val TacticalOnSurface: Color @Composable get() = LocalVippattiColors.current.tacticalOnSurface
val TacticalOnSurfaceVariant: Color @Composable get() = LocalVippattiColors.current.tacticalOnSurfaceVariant
val TacticalOutline: Color @Composable get() = LocalVippattiColors.current.tacticalOutline
val TacticalOutlineVariant: Color @Composable get() = LocalVippattiColors.current.tacticalOutlineVariant

// Emergency / severe alert reds
val EmergencyRed: Color @Composable get() = LocalVippattiColors.current.emergencyRed
val EmergencyRedBright: Color @Composable get() = LocalVippattiColors.current.emergencyRedBright
val EmergencyRedContainer: Color @Composable get() = LocalVippattiColors.current.emergencyRedContainer
val OnEmergencyRed: Color @Composable get() = LocalVippattiColors.current.onEmergencyRed
val OnEmergencyRedContainer: Color @Composable get() = LocalVippattiColors.current.onEmergencyRedContainer

// Bottom navigation
val TacticalNavBg: Color @Composable get() = LocalVippattiColors.current.tacticalNavBg
val TacticalNavBorder: Color @Composable get() = LocalVippattiColors.current.tacticalNavBorder
val TacticalNavInactive: Color @Composable get() = LocalVippattiColors.current.tacticalNavInactive

// Amber warning
val WarningAmber: Color @Composable get() = LocalVippattiColors.current.warningAmber

// Semantic "safe / proceed" green - intentionally NOT themeable, so a safety
// signal never changes meaning when the user picks a different color theme.
val SafeGreen: Color @Composable get() = LocalVippattiColors.current.safeGreen
val OnSafeGreen: Color @Composable get() = LocalVippattiColors.current.onSafeGreen
