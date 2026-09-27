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
}
