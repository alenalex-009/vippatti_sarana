package com.example.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AUDIT B9 CONTRACT (source-level, same pattern as RadarSheetVisibilityTest):
 * opening the Map tab must NEVER auto-fire the system location dialog.
 * The permission ask may only happen from an explicit user tap.
 *
 * A compose/Robolectric test cannot observe the real ActivityResultLauncher,
 * so the guarantee is pinned structurally: the map file must not contain a
 * LaunchedEffect that calls permissionLauncher.launch, and the only launch
 * sites must sit inside click handlers of the user-facing buttons.
 */
class LocationPermissionUxTest {

  private val source: String by lazy {
    File(
      System.getProperty("user.dir"),
      "src/main/java/com/example/ui/components/OsmDroidRadarMapView.kt"
    ).readText()
  }

  @Test
  fun `no LaunchedEffect may launch the permission dialog`() {
    // find every LaunchedEffect block and assert none launches permission
    val launches = Regex("LaunchedEffect\\([^)]*\\)\\s*\\{[\\s\\S]{0,300}?permissionLauncher\\.launch")
      .findAll(source).toList()
    assertTrue("auto-firing permission prompt found: $launches", launches.isEmpty())
  }

  @Test
  fun `every permission launch sits inside an onClick handler`() {
    val lines = source.lines()
    val launchIndices = lines.indices.filter { "permissionLauncher.launch" in lines[it] }
    assertTrue("expected the permission launcher to exist", launchIndices.isNotEmpty())
    launchIndices.forEach { idx ->
      // walk back up to 20 lines: must find a user-event callback before
      // reaching a LaunchedEffect or function declaration. Both accepted
      // forms are only invoked from a tap: onClick = { } on the banner
      // button, and onPermissionRequest = { } passed INTO requestRecenter,
      // which the holder only fires from the recenter button press.
      var foundOnClick = false
      for (j in idx downTo maxOf(0, idx - 20)) {
        val l = lines[j]
        if ("onClick" in l || "onPermissionRequest" in l) { foundOnClick = true; break }
        if ("LaunchedEffect" in l || "fun " in l) break
      }
      assertTrue(
        "permission launch at line ${idx + 1} is NOT behind a user tap",
        foundOnClick
      )
    }
  }

  @Test
  fun `unasked state offers a friendly opt-in banner`() {
    assertTrue(
      "the never-asked banner text must exist",
      source.contains("Turn on location to see hazards near YOU")
    )
    assertTrue(
      "the opt-in button must carry a stable test tag",
      source.contains("location_permission_enable_button")
    )
    assertFalse(
      "the denial rationale and the never-asked opt-in must be distinguishable",
      source.contains("showPermissionRationale && !hasLocationPermission")
    )
  }

  // ------------------------------------------------------------- location
  // warning card (user rule 2026-10-06): the map must open CLEAN; the warning
  // exists only after an explicit GPS-button tap while permission is off.

  @Test
  fun `the warning card is gated behind an explicit GPS-button tap`() {
    // The card renders only when this flag is on; the flag must start false
    // and be set to true ONLY inside the GPS button's click handler.
    val declarations = source.lines().filter {
      it.contains("var showLocationUnavailableWarning by rememberSaveable")
    }
    assertTrue(
      "the warning visibility flag must be declared once via rememberSaveable, starting hidden",
      declarations.size == 1 && declarations.first().contains("mutableStateOf(false)")
    )
    // Every assignment that turns it ON must sit inside the recenter button
    // handler (between the button's test tag and the requestRecenter call).
    val buttonIdx = source.indexOf("osmdroid_recenter_button\"")
    val recenterIdx = source.indexOf("mapState.requestRecenter(", buttonIdx)
    assertTrue("GPS button handler not found", buttonIdx >= 0 && recenterIdx > buttonIdx)
    val handler = source.substring(buttonIdx, recenterIdx)
    assertTrue(
      "the GPS tap must arm the warning when permission is unavailable",
      handler.contains("if (!hasLocationPermission) {") &&
        handler.contains("showLocationUnavailableWarning = true")
    )
    // And nowhere else may arm it.
    val armedOutsideHandler = Regex("showLocationUnavailableWarning\\s*=\\s*true")
      .findAll(source).count()
    assertTrue(
      "the warning must be armed in exactly one place (the GPS tap)",
      armedOutsideHandler == 1
    )
  }

  @Test
  fun `the warning card carries a real close control`() {
    assertTrue(
      "the card must exist",
      source.contains("location_unavailable_warning_card")
    )
    assertTrue(
      "the X close must have a stable test tag",
      source.contains("location_warning_dismiss")
    )
    // 44dp minimum touch target for the dismiss button.
    val dismissIdx = source.indexOf("location_warning_dismiss")
    val blockStart = source.lastIndexOf("IconButton", dismissIdx)
    val block = source.substring(blockStart, dismissIdx)
    assertTrue(
      "the dismiss IconButton must be at least 44dp",
      Regex("size\\(4[4-9]\\.dp\\)|size\\(([5-9]\\d|1\\d\\d)\\.dp\\)").containsMatchIn(block)
    )
    assertTrue(
      "dismissing must clear the visibility flag",
      source.contains("onDismiss = { showLocationUnavailableWarning = false }")
    )
  }

  @Test
  fun `the warning card is not rendered unconditionally on map load`() {
    // The old behaviour rendered the banner whenever !hasLocationPermission
    // regardless of user action; the render gate must now include the flag.
    val gateIdx = source.indexOf("if (showLocationUnavailableWarning && !hasLocationPermission)")
    assertTrue(
      "the render gate must require BOTH the user action and the missing permission",
      gateIdx >= 0
    )
    assertFalse(
      "no fallback path may render the card without the user-action gate",
      source.contains("if (!hasLocationPermission) {\n      LocationUnavailableWarningCard")
    )
  }
}
