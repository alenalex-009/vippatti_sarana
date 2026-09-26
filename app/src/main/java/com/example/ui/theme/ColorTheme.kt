package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ============================================================================
 * APP COLOR THEMES â€” the user-selectable brand palettes
 * ============================================================================
 * Each theme is a COMPLETE system: primary, secondary/accent, the surface
 * elevation ramp, both text colours, outlines and the bottom-navigation
 * colours are designed together, for both the light and the dark appearance.
 * Colours are NOT picked independently per token.
 *
 * ---------------------------------------------------------------------------
 * WHAT A THEME IS NOT ALLOWED TO CHANGE (disaster semantics)
 * ---------------------------------------------------------------------------
 * Three groups of colour are held CONSTANT across every theme, because they
 * carry meaning rather than branding:
 *
 *  1. `emergencyRed*` / `onEmergencyRed*` - danger, SOS, active hazard, relief.
 *  2. `warningAmber`                     - warning / "needs attention".
 *  3. `safeGreen` / `onSafeGreen`         - safe, low risk, "proceed / GO".
 *
 * The map's hazard, safe-zone and route colours are stronger still: they come
 * from `DisasterTypeColors`, a plain non-Compose ARGB table in data/disaster,
 * and are never touched by this file or by VippattiTheme.
 *
 * Choosing "Sunset Orange" therefore restyles buttons, cards, accents, the nav
 * bar and the canvas, but a red alert is still red, a warning is still amber
 * and "you are safe" is still the same green as before.
 *
 * ---------------------------------------------------------------------------
 * READABILITY
 * ---------------------------------------------------------------------------
 * The app ships in the LIGHT appearance by default, so the light palettes
 * carry the real design work:
 *  - `tacticalOnSurface` on `obsidianSurface` >= 12:1 (body text)
 *  - `tacticalOnSurfaceVariant` on `obsidianSurface` >= 4.5:1 (secondary text)
 *  - `onNeonEmerald` on `neonEmerald` >= 4.5:1 (filled buttons, nav icon)
 *  - `neonEmerald` on `obsidianSurface` >= 4.5:1 (outlined/accent text)
 * These ratios are asserted in ColorThemeContrastTest.
 */
enum class ColorTheme {

  /** Default brand theme. Blue/teal - calm, civic, high legibility. */
  VIPPATTI_BLUE,

  /** Green/teal - the original Vippatti Sarana identity. */
  FOREST_GREEN,

  /** Warm orange/amber. Its neutrals are kept desaturated so the orange
   *  never fights the fixed amber WARNING colour, which stays yellower. */
  SUNSET_ORANGE,

  /** Purple/indigo. */
  ROYAL_PURPLE,

  /** Cyan/deep blue - cooler and brighter than Vippatti Blue. */
  OCEAN_CYAN,

  /** Neutral grey/slate - near-zero chroma, maximum restraint. */
  SLATE;

  /** Light-appearance palette for this theme. */
  val lightPalette: VippattiColors
    get() = when (this) {
      VIPPATTI_BLUE -> VippattiBlueLight
      FOREST_GREEN -> ForestGreenLight
      SUNSET_ORANGE -> SunsetOrangeLight
      ROYAL_PURPLE -> RoyalPurpleLight
      OCEAN_CYAN -> OceanCyanLight
      SLATE -> SlateLight
    }

  /** Dark-appearance palette for this theme. */
  val darkPalette: VippattiColors
    get() = when (this) {
      VIPPATTI_BLUE -> VippattiBlueDark
      FOREST_GREEN -> ForestGreenDark
      SUNSET_ORANGE -> SunsetOrangeDark
      ROYAL_PURPLE -> RoyalPurpleDark
      OCEAN_CYAN -> OceanCyanDark
      SLATE -> SlateDark
    }

  /** The palette to install for a given appearance. */
  fun palette(darkTheme: Boolean): VippattiColors =
    if (darkTheme) darkPalette else lightPalette

  /**
   * The two swatches shown in the picker: [primary, secondary].
   * Real colours rather than images, so they always match what is installed.
   */
  val previewColors: Pair<Color, Color>
    get() = lightPalette.neonEmerald to lightPalette.tacticalCyan

  companion object {
    /** The theme a fresh install starts on. */
    val DEFAULT: ColorTheme = VIPPATTI_BLUE

    /** Tolerant parser: unknown/legacy values fall back to [DEFAULT]. */
    fun fromStorage(raw: String?): ColorTheme =
      entries.firstOrNull { it.name == raw } ?: DEFAULT
  }
}

// ============================================================================
// FIXED SEMANTIC VALUES - identical in every theme.
// Kept as shared constants so the invariant is enforced by construction rather
// than by remembering to copy six values into six palettes.
// ============================================================================

// Light-appearance semantics.
private val L_RED = Color(0xFFDC2626)
private val L_RED_BRIGHT = Color(0xFFD92D20)
private val L_RED_CONTAINER = Color(0xFFFDE4E1)
private val L_ON_RED = Color(0xFFFFFFFF)
private val L_ON_RED_CONTAINER = Color(0xFFA80011)
private val L_AMBER = Color(0xFFD97706)
private val L_SAFE = Color(0xFF047857)
private val L_ON_SAFE = Color(0xFFFFFFFF)

// Dark-appearance semantics.
private val D_RED = Color(0xFFDC2626)
private val D_RED_BRIGHT = Color(0xFFFF5449)
private val D_RED_CONTAINER = Color(0xFF690005)
private val D_ON_RED = Color(0xFFFFDAD6)
private val D_ON_RED_CONTAINER = Color(0xFFFFDAD6)
private val D_AMBER = Color(0xFFF59E0B)
private val D_SAFE = Color(0xFF34D399)
private val D_ON_SAFE = Color(0xFF00301C)

/**
 * Builds a complete palette. The caller supplies only the themeable (brand)
 * tokens; the semantic tokens are always taken from the shared constants above
 * so they cannot drift between themes.
 */
private fun palette(
  primary: Color,
  primaryContainer: Color,
  onPrimary: Color,
  onPrimaryContainer: Color,
  secondary: Color,
  secondaryContainer: Color,
  onSecondary: Color,
  onSecondaryContainer: Color,
  background: Color,
  surface: Color,
  containerLowest: Color,
  containerLow: Color,
  container: Color,
  containerHigh: Color,
  containerHighest: Color,
  containerBright: Color,
  onSurface: Color,
  onSurfaceVariant: Color,
  outline: Color,
  outlineVariant: Color,
  navBg: Color,
  navBorder: Color,
  navInactive: Color,
  red: Color,
  redBright: Color,
  redContainer: Color,
  onRed: Color,
  onRedContainer: Color,
  amber: Color,
  safe: Color,
  onSafe: Color
) = VippattiColors(
  neonEmerald = primary,
  neonEmeraldContainer = primaryContainer,
  onNeonEmerald = onPrimary,
  onNeonEmeraldContainer = onPrimaryContainer,
  tacticalCyan = secondary,
  tacticalCyanContainer = secondaryContainer,
  onTacticalCyan = onSecondary,
  onTacticalCyanContainer = onSecondaryContainer,
  obsidianBg = background,
  obsidianSurface = surface,
  obsidianContainerLowest = containerLowest,
  obsidianContainerLow = containerLow,
  obsidianContainer = container,
  obsidianContainerHigh = containerHigh,
  obsidianContainerHighest = containerHighest,
  obsidianBright = containerBright,
  tacticalOnSurface = onSurface,
  tacticalOnSurfaceVariant = onSurfaceVariant,
  tacticalOutline = outline,
  tacticalOutlineVariant = outlineVariant,
  emergencyRed = red,
  emergencyRedBright = redBright,
  emergencyRedContainer = redContainer,
  onEmergencyRed = onRed,
  onEmergencyRedContainer = onRedContainer,
  tacticalNavBg = navBg,
  tacticalNavBorder = navBorder,
  tacticalNavInactive = navInactive,
  warningAmber = amber,
  safeGreen = safe,
  onSafeGreen = onSafe
)

// ============================================================================
// 1. VIPPATTI BLUE (default)
// ============================================================================

private val VippattiBlueLight = palette(
  primary = Color(0xFF0B5FA5),
  primaryContainer = Color(0xFFCFE6F8),
  onPrimary = Color(0xFFFFFFFF),
  onPrimaryContainer = Color(0xFF052A45),
  secondary = Color(0xFF0E7490),
  secondaryContainer = Color(0xFFCFF0F7),
  onSecondary = Color(0xFFFFFFFF),
  onSecondaryContainer = Color(0xFF04343F),
  background = Color(0xFFF1F6FA),
  surface = Color(0xFFF1F6FA),
  containerLowest = Color(0xFFE6EEF5),
  containerLow = Color(0xFFFFFFFF),
  container = Color(0xFFEAF1F7),
  containerHigh = Color(0xFFE0EAF2),
  containerHighest = Color(0xFFD5E2EC),
  containerBright = Color(0xFFC6D6E3),
  onSurface = Color(0xFF0E1A24),
  onSurfaceVariant = Color(0xFF44596B),
  outline = Color(0xFF8AA3B5),
  outlineVariant = Color(0xFFC9DBE7),
  navBg = Color(0xFFF5F9FC),
  navBorder = Color(0xFFD0E1EE),
  navInactive = Color(0xFF55708A),
  red = L_RED, redBright = L_RED_BRIGHT, redContainer = L_RED_CONTAINER,
  onRed = L_ON_RED, onRedContainer = L_ON_RED_CONTAINER,
  amber = L_AMBER, safe = L_SAFE, onSafe = L_ON_SAFE
)

private val VippattiBlueDark = palette(
  primary = Color(0xFF7FC4F5),
  primaryContainer = Color(0xFF0A5B9E),
  onPrimary = Color(0xFF00243F),
  onPrimaryContainer = Color(0xFFCFE6F8),
  secondary = Color(0xFF62CBE8),
  secondaryContainer = Color(0xFF0B5A6E),
  onSecondary = Color(0xFF00363F),
  onSecondaryContainer = Color(0xFFCFF0F7),
  background = Color(0xFF0F1418),
  surface = Color(0xFF0F1418),
  containerLowest = Color(0xFF0A0E11),
  containerLow = Color(0xFF171D22),
  container = Color(0xFF1B2228),
  containerHigh = Color(0xFF252D34),
  containerHighest = Color(0xFF303940),
  containerBright = Color(0xFF3B454D),
  onSurface = Color(0xFFE1E6EA),
  onSurfaceVariant = Color(0xFFB3C2CD),
  outline = Color(0xFF7D8E9A),
  outlineVariant = Color(0xFF38454E),
  navBg = Color(0xFF15222B),
  navBorder = Color(0xFF24404F),
  navInactive = Color(0xFF94BBD4),
  red = D_RED, redBright = D_RED_BRIGHT, redContainer = D_RED_CONTAINER,
  onRed = D_ON_RED, onRedContainer = D_ON_RED_CONTAINER,
  amber = D_AMBER, safe = D_SAFE, onSafe = D_ON_SAFE
)

// ============================================================================
// 2. FOREST GREEN (the original identity)
// ============================================================================

private val ForestGreenLight = palette(
  // Slightly deeper than the original #008459: at #008459 the accent reached
  // only 4.36:1 on this canvas and 4.48:1 under its own button label, i.e.
  // marginally under WCAG AA. #00714C clears both (5.59:1 / 5.75:1) while
  // staying the same recognisable Vippatti green.
  primary = Color(0xFF00714C),
  primaryContainer = Color(0xFFB9F4D9),
  onPrimary = Color(0xFFEFFCF6),
  onPrimaryContainer = Color(0xFF004D33),
  secondary = Color(0xFF0F766E),
  secondaryContainer = Color(0xFFCFF2EE),
  onSecondary = Color(0xFFFFFFFF),
  onSecondaryContainer = Color(0xFF043733),
  background = Color(0xFFF2F7F4),
  surface = Color(0xFFF2F7F4),
  containerLowest = Color(0xFFE8EFEA),
  containerLow = Color(0xFFFFFFFF),
  container = Color(0xFFEDF3EF),
  containerHigh = Color(0xFFE3ECE6),
  containerHighest = Color(0xFFD9E5DD),
  containerBright = Color(0xFFCFE0D5),
  onSurface = Color(0xFF11251C),
  onSurfaceVariant = Color(0xFF4E6659),
  outline = Color(0xFF9DB4A5),
  outlineVariant = Color(0xFFD3E2D9),
  navBg = Color(0xFFF6FAF7),
  navBorder = Color(0xFFD3EBDD),
  navInactive = Color(0xFF59745F),
  red = L_RED, redBright = L_RED_BRIGHT, redContainer = L_RED_CONTAINER,
  onRed = L_ON_RED, onRedContainer = L_ON_RED_CONTAINER,
  amber = L_AMBER, safe = L_SAFE, onSafe = L_ON_SAFE
)

private val ForestGreenDark = palette(
  primary = Color(0xFF6DFFBA),
  primaryContainer = Color(0xFF00E599),
  onPrimary = Color(0xFF003822),
  onPrimaryContainer = Color(0xFF00613E),
  secondary = Color(0xFF5EEAD4),
  secondaryContainer = Color(0xFF115E59),
  onSecondary = Color(0xFF003731),
  onSecondaryContainer = Color(0xFF99F6E4),
  background = Color(0xFF111316),
  surface = Color(0xFF111316),
  containerLowest = Color(0xFF0C0E11),
  containerLow = Color(0xFF1A1C1F),
  container = Color(0xFF1E2023),
  containerHigh = Color(0xFF282A2D),
  containerHighest = Color(0xFF333538),
  containerBright = Color(0xFF37393D),
  onSurface = Color(0xFFE2E2E6),
  onSurfaceVariant = Color(0xFFBACBBE),
  outline = Color(0xFF849589),
  outlineVariant = Color(0xFF3B4A41),
  navBg = Color(0xFF17362B),
  navBorder = Color(0xFF204B3D),
  navInactive = Color(0xFF8DCEB8),
  red = D_RED, redBright = D_RED_BRIGHT, redContainer = D_RED_CONTAINER,
  onRed = D_ON_RED, onRedContainer = D_ON_RED_CONTAINER,
  amber = D_AMBER, safe = D_SAFE, onSafe = D_ON_SAFE
)

// ============================================================================
// 3. SUNSET ORANGE
// ============================================================================

private val SunsetOrangeLight = palette(
  primary = Color(0xFFC2410C),
  primaryContainer = Color(0xFFFFE0CC),
  onPrimary = Color(0xFFFFFFFF),
  onPrimaryContainer = Color(0xFF5A1A00),
  secondary = Color(0xFFB45309),
  secondaryContainer = Color(0xFFFDEBC8),
  onSecondary = Color(0xFFFFFFFF),
  onSecondaryContainer = Color(0xFF4A2000),
  background = Color(0xFFFDF7F3),
  surface = Color(0xFFFDF7F3),
  containerLowest = Color(0xFFF7ECE5),
  containerLow = Color(0xFFFFFFFF),
  container = Color(0xFFFBEFE7),
  containerHigh = Color(0xFFF4E4D9),
  containerHighest = Color(0xFFECD9CB),
  containerBright = Color(0xFFE2C8B6),
  onSurface = Color(0xFF2B1A10),
  onSurfaceVariant = Color(0xFF6A4C39),
  outline = Color(0xFFB79C88),
  outlineVariant = Color(0xFFE6D4C4),
  navBg = Color(0xFFFDF9F5),
  navBorder = Color(0xFFEEDACB),
  navInactive = Color(0xFF7A5B47),
  // The brand orange is deliberately deeper/redder than the fixed amber
  // WARNING colour, which stays clearly yellow, so a warning can never be
  // mistaken for the app accent.
  red = L_RED, redBright = L_RED_BRIGHT, redContainer = L_RED_CONTAINER,
  onRed = L_ON_RED, onRedContainer = L_ON_RED_CONTAINER,
  amber = L_AMBER, safe = L_SAFE, onSafe = L_ON_SAFE
)

private val SunsetOrangeDark = palette(
  primary = Color(0xFFFFB68C),
  primaryContainer = Color(0xFFB23A00),
  onPrimary = Color(0xFF5A1A00),
  onPrimaryContainer = Color(0xFFFFE0CC),
  secondary = Color(0xFFF7C463),
  secondaryContainer = Color(0xFF8A3D05),
  onSecondary = Color(0xFF3F1B00),
  onSecondaryContainer = Color(0xFFFDEBC8),
  background = Color(0xFF16110E),
  surface = Color(0xFF16110E),
  containerLowest = Color(0xFF110C0A),
  containerLow = Color(0xFF1F1815),
  container = Color(0xFF241C18),
  containerHigh = Color(0xFF2F2521),
  containerHighest = Color(0xFF3B2F29),
  containerBright = Color(0xFF473931),
  onSurface = Color(0xFFF0E4DC),
  onSurfaceVariant = Color(0xFFD6BEA9),
  outline = Color(0xFFA68C79),
  outlineVariant = Color(0xFF4A382F),
  navBg = Color(0xFF2B1D14),
  navBorder = Color(0xFF54301B),
  navInactive = Color(0xFFDFA47C),
  red = D_RED, redBright = D_RED_BRIGHT, redContainer = D_RED_CONTAINER,
  onRed = D_ON_RED, onRedContainer = D_ON_RED_CONTAINER,
  amber = D_AMBER, safe = D_SAFE, onSafe = D_ON_SAFE
)

// ============================================================================
// 4. ROYAL PURPLE
// ============================================================================

private val RoyalPurpleLight = palette(
  primary = Color(0xFF5B21B6),
  primaryContainer = Color(0xFFE9DDFB),
  onPrimary = Color(0xFFFFFFFF),
  onPrimaryContainer = Color(0xFF2E1065),
  secondary = Color(0xFF4F46E5),
  secondaryContainer = Color(0xFFDDE1FB),
  onSecondary = Color(0xFFFFFFFF),
  onSecondaryContainer = Color(0xFF1E1B4B),
  background = Color(0xFFF7F5FC),
  surface = Color(0xFFF7F5FC),
  containerLowest = Color(0xFFEDE9F6),
  containerLow = Color(0xFFFFFFFF),
  container = Color(0xFFF1EEF9),
  containerHigh = Color(0xFFE8E3F4),
  containerHighest = Color(0xFFDDD6EE),
  containerBright = Color(0xFFCFC6E4),
  onSurface = Color(0xFF1C1430),
  onSurfaceVariant = Color(0xFF534A6B),
  outline = Color(0xFF9C93B3),
  outlineVariant = Color(0xFFD8D1E6),
  navBg = Color(0xFFFAF8FD),
  navBorder = Color(0xFFE1DAF0),
  navInactive = Color(0xFF635A7A),
  red = L_RED, redBright = L_RED_BRIGHT, redContainer = L_RED_CONTAINER,
  onRed = L_ON_RED, onRedContainer = L_ON_RED_CONTAINER,
  amber = L_AMBER, safe = L_SAFE, onSafe = L_ON_SAFE
)

private val RoyalPurpleDark = palette(
  primary = Color(0xFFC4B0FF),
  primaryContainer = Color(0xFF4C1D95),
  onPrimary = Color(0xFF2A0E5C),
  onPrimaryContainer = Color(0xFFE9DDFB),
  secondary = Color(0xFFA5A8FF),
  secondaryContainer = Color(0xFF3730A3),
  onSecondary = Color(0xFF1E1B4B),
  onSecondaryContainer = Color(0xFFDDE1FB),
  background = Color(0xFF121017),
  surface = Color(0xFF121017),
  containerLowest = Color(0xFF0D0B12),
  containerLow = Color(0xFF1A1822),
  container = Color(0xFF1E1B28),
  containerHigh = Color(0xFF292534),
  containerHighest = Color(0xFF342F41),
  containerBright = Color(0xFF3F394D),
  onSurface = Color(0xFFE9E4F0),
  onSurfaceVariant = Color(0xFFC2B8D2),
  outline = Color(0xFF948BA8),
  outlineVariant = Color(0xFF3D3550),
  navBg = Color(0xFF221B36),
  navBorder = Color(0xFF3A2E58),
  navInactive = Color(0xFFB5A5E0),
  red = D_RED, redBright = D_RED_BRIGHT, redContainer = D_RED_CONTAINER,
  onRed = D_ON_RED, onRedContainer = D_ON_RED_CONTAINER,
  amber = D_AMBER, safe = D_SAFE, onSafe = D_ON_SAFE
)

// ============================================================================
// 5. OCEAN CYAN
// ============================================================================

private val OceanCyanLight = palette(
  primary = Color(0xFF0E7490),
  primaryContainer = Color(0xFFCFF0F7),
  onPrimary = Color(0xFFFFFFFF),
  onPrimaryContainer = Color(0xFF04343F),
  secondary = Color(0xFF155E75),
  secondaryContainer = Color(0xFFCDEBF3),
  onSecondary = Color(0xFFFFFFFF),
  onSecondaryContainer = Color(0xFF04303D),
  background = Color(0xFFF0F7F9),
  surface = Color(0xFFF0F7F9),
  containerLowest = Color(0xFFE4EFF3),
  containerLow = Color(0xFFFFFFFF),
  container = Color(0xFFE9F2F5),
  containerHigh = Color(0xFFDFEBEF),
  containerHighest = Color(0xFFD4E4E9),
  containerBright = Color(0xFFC2D8E0),
  onSurface = Color(0xFF0B1F26),
  onSurfaceVariant = Color(0xFF42606B),
  outline = Color(0xFF8AA8B2),
  outlineVariant = Color(0xFFC9DEE5),
  navBg = Color(0xFFF4FAFB),
  navBorder = Color(0xFFCFE2E8),
  navInactive = Color(0xFF54737E),
  red = L_RED, redBright = L_RED_BRIGHT, redContainer = L_RED_CONTAINER,
  onRed = L_ON_RED, onRedContainer = L_ON_RED_CONTAINER,
  amber = L_AMBER, safe = L_SAFE, onSafe = L_ON_SAFE
)

private val OceanCyanDark = palette(
  primary = Color(0xFF67D6EE),
  primaryContainer = Color(0xFF0B5A6E),
  onPrimary = Color(0xFF00363F),
  onPrimaryContainer = Color(0xFFCFF0F7),
  secondary = Color(0xFF6FD2E8),
  secondaryContainer = Color(0xFF0C4A5C),
  onSecondary = Color(0xFF00303D),
  onSecondaryContainer = Color(0xFFCDEBF3),
  background = Color(0xFF0D1416),
  surface = Color(0xFF0D1416),
  containerLowest = Color(0xFF090F11),
  containerLow = Color(0xFF151D1F),
  container = Color(0xFF192224),
  containerHigh = Color(0xFF232D2F),
  containerHighest = Color(0xFF2E393C),
  containerBright = Color(0xFF394549),
  onSurface = Color(0xFFDDE8EB),
  onSurfaceVariant = Color(0xFFAFC3C9),
  outline = Color(0xFF7E959B),
  outlineVariant = Color(0xFF34464A),
  navBg = Color(0xFF0F2B33),
  navBorder = Color(0xFF1C4854),
  navInactive = Color(0xFF8CCBDA),
  red = D_RED, redBright = D_RED_BRIGHT, redContainer = D_RED_CONTAINER,
  onRed = D_ON_RED, onRedContainer = D_ON_RED_CONTAINER,
  amber = D_AMBER, safe = D_SAFE, onSafe = D_ON_SAFE
)

// ============================================================================
// 6. SLATE (monochrome)
// ============================================================================

private val SlateLight = palette(
  primary = Color(0xFF475569),
  primaryContainer = Color(0xFFE2E8F0),
  onPrimary = Color(0xFFFFFFFF),
  onPrimaryContainer = Color(0xFF1E293B),
  secondary = Color(0xFF5C6B80),
  secondaryContainer = Color(0xFFE8EDF3),
  onSecondary = Color(0xFFFFFFFF),
  onSecondaryContainer = Color(0xFF293648),
  background = Color(0xFFF5F7F9),
  surface = Color(0xFFF5F7F9),
  containerLowest = Color(0xFFE9EDF1),
  containerLow = Color(0xFFFFFFFF),
  container = Color(0xFFEEF1F5),
  containerHigh = Color(0xFFE4E9EE),
  containerHighest = Color(0xFFD9DFE6),
  containerBright = Color(0xFFC7D0D9),
  onSurface = Color(0xFF1F2937),
  onSurfaceVariant = Color(0xFF4B5563),
  outline = Color(0xFF94A3B8),
  outlineVariant = Color(0xFFD2DAE3),
  navBg = Color(0xFFF8FAFB),
  navBorder = Color(0xFFDDE3E9),
  navInactive = Color(0xFF5F6B7A),
  red = L_RED, redBright = L_RED_BRIGHT, redContainer = L_RED_CONTAINER,
  onRed = L_ON_RED, onRedContainer = L_ON_RED_CONTAINER,
  amber = L_AMBER, safe = L_SAFE, onSafe = L_ON_SAFE
)

private val SlateDark = palette(
  primary = Color(0xFFBAC7D6),
  primaryContainer = Color(0xFF3C4A5C),
  onPrimary = Color(0xFF1E2937),
  onPrimaryContainer = Color(0xFFE2E8F0),
  secondary = Color(0xFFA9B6C6),
  secondaryContainer = Color(0xFF45536A),
  onSecondary = Color(0xFF1F2A38),
  onSecondaryContainer = Color(0xFFE8EDF3),
  background = Color(0xFF121417),
  surface = Color(0xFF121417),
  containerLowest = Color(0xFF0D0F12),
  containerLow = Color(0xFF1A1D21),
  container = Color(0xFF1E2125),
  containerHigh = Color(0xFF282C31),
  containerHighest = Color(0xFF33383E),
  containerBright = Color(0xFF3E444B),
  onSurface = Color(0xFFE4E8EC),
  onSurfaceVariant = Color(0xFFB6BEC7),
  outline = Color(0xFF8C949E),
  outlineVariant = Color(0xFF3B4046),
  navBg = Color(0xFF232830),
  navBorder = Color(0xFF3A414A),
  navInactive = Color(0xFFADB6C0),
  red = D_RED, redBright = D_RED_BRIGHT, redContainer = D_RED_CONTAINER,
  onRed = D_ON_RED, onRedContainer = D_ON_RED_CONTAINER,
  amber = D_AMBER, safe = D_SAFE, onSafe = D_ON_SAFE
)
