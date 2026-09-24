package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.PreferenceManager
import com.example.data.disaster.MapFocus
import com.example.data.disaster.PilotRegionData
import com.example.data.disaster.DisasterEvent
import com.example.data.disaster.DisasterLayer
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.DisasterType
import com.example.data.disaster.EventGeometry
import com.example.data.disaster.FireIntensityScale
import com.example.data.disaster.MarkerGeneralizer
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.data.routing.RouteResult
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlinx.coroutines.delay

/**
 * Turn-by-turn live navigation status reported by the map engine.
 */
data class LiveNavStatus(
  val isActive: Boolean = false,
  val nextInstruction: String = "",
  val distanceRemainingMeters: Float = 0f,
  val isOffRoute: Boolean = false,
  val totalDistanceKm: Double = 0.0,
  val totalDurationMins: Int = 0,
  val travelProfileLabel: String = "ON FOOT",
  val isArrived: Boolean = false
)

/**
 * Google-style light basemap: Esri World Light Grey Base. Keyless + no
 * watermark/rate-limit games (the reason we dropped CARTO Positron: its
 * anonymous tiles come back stamped "API KEY REQUIRED" once the shared
 * quota is hit, and osmdroid happily caches those forever). Esri's URL
 * order is z/y/x (NOT osmdroid's XYTileSource z/x/y), so getTileURLString
 * is overridden. Tiles (c) Esri — used under Esri's free attribution terms;
 * OSM data attribution stays in the banner below.
 */
private val LightBasemap = object : org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase(
  "Esri Light Gray",
  0, 19, 256, "",
  arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/"),
  "Tiles (c) Esri and the GIS User Community; boundaries © OpenStreetMap"
) {
  override fun getTileURLString(pMapTileIndex: Long): String =
    baseUrl +
      org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex) + "/" +
      org.osmdroid.util.MapTileIndex.getY(pMapTileIndex) + "/" +
      org.osmdroid.util.MapTileIndex.getX(pMapTileIndex) +
      mImageFilenameEnding
}

private const val ROUTE_COLOR = 0xFF00E297.toInt()     // High-visibility emergency green
private const val ROUTE_WIDTH = 10.0f

/**
 * Explicit states of the GPS "locate me" request driven by the recenter
 * button. Every state has a user-visible message — the button never silently
 * jumps to the labeled fallback area and never presents fallback coordinates
 * as a real GPS fix.
 */
enum class GpsRequestState {
  IDLE,
  REQUESTING,
  SUCCESS,
  NO_PERMISSION,
  PERMANENTLY_DENIED,
  PROVIDER_DISABLED,
  UNAVAILABLE,
  TIMEOUT
}

/**
 * THE single map engine of the application ? OSMDroid + OpenStreetMap with
 * OSRM road routing.
 *
 * Visual language (no tiny dot markers for zones):
 *   HAZARD    = large faded pulsing circles in the DISASTER-TYPE color
 *               (flood blue, fire orange, cyclone purple ... — see
 *               DisasterTypeColors; legend row above the map keys each color)
 *   SAFE ZONE = large faded pulsing safe circles (emerald = open, amber = full)
 *   USER      = hardware GPS location overlay (dot + accuracy ring)
 *   ROUTE     = OSRM evacuation polyline
 */
@Composable
fun OsmDroidRadarMapView(
  hazardZones: List<HazardZone>,
  safeZones: List<SafeZone>,
  selectedSafeZone: SafeZone?,
  activeRoute: RouteResult?,
  travelMode: String, // "foot" or "driving"
  onClearRoute: () -> Unit,
  onHazardZoneTapped: (HazardZone) -> Unit,
  onSafeZoneTapped: (SafeZone) -> Unit,
  onRealGpsFix: (latitude: Double, longitude: Double) -> Unit,
  /** REAL provider events (USGS/FIRMS/IMD/user reports) rendered per layer toggles. */
  disasterEvents: List<com.example.data.disaster.DisasterEvent> = emptyList(),
  /** User-controlled layer visibility (all default-on layers behave as before). */
  enabledLayers: Set<com.example.data.disaster.DisasterLayer> = com.example.data.disaster.DisasterLayer.entries.toSet(),
  /** Tap on a rendered disaster event marker. */
  onDisasterEventTapped: (com.example.data.disaster.DisasterEvent) -> Unit = {},
  /**
   * HISTORICAL (EM-DAT) records to draw. The ViewModel already filters these to
   * "layer enabled + has source coordinates", so an empty list means "draw
   * nothing" and no placeholder is ever used.
   */
  historicalEvents: List<com.example.data.historical.HistoricalDisasterEvent> = emptyList(),
  /** Tap on a historical marker — opens the historical sheet, not a hazard one. */
  onHistoricalEventTapped: (com.example.data.historical.HistoricalDisasterEvent) -> Unit = {},
  /** NEARBY-FIRST: the user's location; distant data folds away at city zoom. */
  focusPoint: GeoPoint? = null,
  /** One-shot camera fly-to when the user picks a place to view. */
  cameraJumpTarget: GeoPoint? = null,
  /** Non-null while a CHOSEN place is being viewed instead of the GPS. */
  viewingPlaceLabel: String? = null,
  onExitPlaceView: () -> Unit = {},
  /** Called once after the place-view camera fly-to has been executed. */
  onCameraJumpConsumed: () -> Unit = {},
  modifier: Modifier = Modifier,
  // Overlay-aware spacing so the floating map controls / attribution banner
  // never sit underneath the screen's risk strip, HUD or bottom sheet on any
  // device size or font scale. Both default to 0 (no overlay above the map).
  topOverlayPadding: Dp = 0.dp,
  bottomOverlayPadding: Dp = 0.dp
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var hasLocationPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    )
  }

  // Permission result becomes user-visible state: denial must never silently
  // degrade to the India-fallback view without explanation (audit item 11).
  var showPermissionRationale by remember { mutableStateOf(false) }

  // Tracks whether a permission request was ever launched: without this, a
  // first-ever press (shouldShowRationale == false) is indistinguishable from
  // a permanently-denied ("don't ask again") press.
  var locationPermissionAsked by remember { mutableStateOf(hasLocationPermission) }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    hasLocationPermission = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
      (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
    locationPermissionAsked = true
    // Only nag with the rationale after an explicit denial (not on first ask).
    showPermissionRationale = !hasLocationPermission
  }

  LaunchedEffect(Unit) {
    if (!hasLocationPermission) {
      locationPermissionAsked = true
      permissionLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION
        )
      )
    }
  }

  val mapState = remember {
    // Application context on purpose: the holder outlives a single Activity
    // instance across configuration changes and must never retain the Activity.
    OsmMapControllerHolder(context.applicationContext) { /* HUD handled by parent screen */ }
  }

  // GPS locate state drives the recenter button feedback + status banner.
  // (Plain reads: the holder owns the mutableStateOf delegates; reading the
  // values here still subscribes this composition to changes.)
  val gpsState = mapState.gpsRequestState
  val gpsMessage = mapState.gpsStatusMessage

  // Success banners auto-dismiss; errors stay until dismissed or retried.
  LaunchedEffect(gpsState) {
    if (gpsState == GpsRequestState.SUCCESS) {
      delay(4000)
      mapState.clearGpsStatus()
    }
  }

  LaunchedEffect(travelMode) { mapState.setTravelMode(travelMode) }

  LaunchedEffect(selectedSafeZone) {
    if (selectedSafeZone != null) mapState.focusOnSafeZone(selectedSafeZone)
  }

  // NEARBY-FIRST focus: the map folds distant data while the camera is at
  // city scale; the user's real location is the focus. The "see everything"
  // choice is local map state, not app state — it is a viewing preference.
  var showAllRegion by remember { mutableStateOf(false) }
  LaunchedEffect(focusPoint?.lat, focusPoint?.lon) {
    mapState.setFocus(focusPoint?.let { GeoPoint(it.lat, it.lon) })
  }
  LaunchedEffect(showAllRegion) { mapState.setShowAllRegion(showAllRegion) }

  // Place-view camera: fly to a chosen place (and stop following GPS while the
  // user is looking somewhere else). Consumed by the parent after firing.
  LaunchedEffect(cameraJumpTarget) {
    cameraJumpTarget?.let {
      mapState.stopFollowingAndCenter(GeoPoint(it.lat, it.lon))
      onCameraJumpConsumed()
    }
  }

  // REDEPLOY ZONE OVERLAYS WHEN STATE CHANGES (audit item 4). The factory runs
  // exactly once, so hazard/safe-zone lists that arrive after first composition
  // (disaster sync completes, mock toggle flips, live events expire) previously
  // never reached the map. drawRoute stays guarded separately below.
  LaunchedEffect(hazardZones) { mapState.deployHazardZones(hazardZones, onHazardZoneTapped) }
  LaunchedEffect(safeZones) { mapState.deploySafeZones(safeZones, onSafeZoneTapped) }
  LaunchedEffect(
    disasterEvents, enabledLayers,
    enabledLayers.contains(DisasterLayer.EARTHQUAKES),
    enabledLayers.contains(DisasterLayer.ACTIVE_FIRES),
    enabledLayers.contains(DisasterLayer.OFFICIAL_ALERTS),
    enabledLayers.contains(DisasterLayer.USER_REPORTS)
  ) {
    mapState.deployDisasterEvents(disasterEvents, enabledLayers, onDisasterEventTapped)
  }
  // HISTORICAL (EM-DAT) layer: off by default, and only ever receives records
  // that carry the dataset's own coordinates.
  LaunchedEffect(historicalEvents) {
    mapState.deployHistoricalEvents(historicalEvents, onHistoricalEventTapped)
  }

  // Draws the OSRM evacuation polyline whenever a route is computed, and
  // REMOVES it when the ViewModel clears the corridor — the map never keeps
  // a polyline that state no longer knows about.
  LaunchedEffect(activeRoute) {
    if (activeRoute != null) mapState.displayRoute(activeRoute)
    else mapState.clearRouteOverlay()
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> mapState.mapView?.onResume()
        Lifecycle.Event.ON_PAUSE -> mapState.mapView?.onPause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    AndroidView(
      factory = { _ ->
        val view = mapState.initMapView(
          context = context.applicationContext,
          hazardZones = hazardZones,
          safeZones = safeZones,
          onHazardZoneTapped = onHazardZoneTapped,
          onSafeZoneTapped = onSafeZoneTapped,
          onRealGpsFix = onRealGpsFix
        )
        view
      },
      update = { _ ->
        if (hasLocationPermission) mapState.enableLocationTracking()
      },
      // The MapView is torn down HERE — when the view is actually removed from
      // composition — not in DisposableEffect.onDispose, which fires while the
      // view is still attached (and possibly still drawing during Crossfade /
      // activity recreation). Teardown runs exactly once, after the view has
      // been released. See OsmMapControllerHolder.cleanup().
      onRelease = { mapState.cleanup() },
      modifier = Modifier.fillMaxSize()
    )

    // GPS-permission rationale (audit item 11): a denial must be explained,
    // with a real retry action — never a silent India-fallback.
    if (showPermissionRationale && !hasLocationPermission) {
      Row(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = topOverlayPadding + 8.dp, start = 10.dp, end = 10.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(ObsidianContainer.copy(alpha = 0.96f))
          .border(1.dp, EmergencyRed.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "Location OFF — risk & routes use a generic India-centre view, not your position.",
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium,
          color = TacticalOnSurface,
          modifier = Modifier.weight(1f, fill = false)
        )
        IconButton(
          onClick = {
            permissionLauncher.launch(
              arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
              )
            )
          },
          modifier = Modifier.size(30.dp)
        ) {
          Icon(
            Icons.Default.MyLocation,
            contentDescription = "Retry location permission",
            tint = NeonEmerald,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    // GPS locate status (Issue 2): every locate state carries a message —
    // locating spinner, success, permission, provider-off, timeout. Errors
    // stay until dismissed or retried; success auto-dismisses above.
    if (gpsMessage != null) {
      Row(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = topOverlayPadding + 8.dp, start = 10.dp, end = 10.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(ObsidianContainer.copy(alpha = 0.96f))
          .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp)
          .testTag("gps_status_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (gpsState == GpsRequestState.REQUESTING) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = NeonEmerald
          )
        }
        Text(
          text = gpsMessage,
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium,
          color = when (gpsState) {
            GpsRequestState.SUCCESS -> NeonEmerald
            GpsRequestState.REQUESTING -> TacticalOnSurface
            else -> WarningAmber
          },
          modifier = Modifier.weight(1f, fill = false)
        )
        IconButton(
          onClick = { mapState.clearGpsStatus() },
          modifier = Modifier.size(30.dp)
        ) {
          Icon(
            Icons.Default.Close,
            contentDescription = "Dismiss location message",
            tint = TacticalOnSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }

    // ---------------- Floating map controls (Google-style: few, right edge) --
    // Layer cycling + route-clearing were moved OUT of the always-visible
    // stack: the light basemap is the design language now, and "clear route"
    // appears only while a route actually exists (context-sensitive control).
    Column(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = topOverlayPadding + 12.dp, end = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      MapControlButton(Icons.Default.Add, "Zoom In", TacticalOnSurface, "osmdroid_zoom_in_button") {
        mapState.zoomIn()
      }
      MapControlButton(Icons.Default.Remove, "Zoom Out", TacticalOnSurface, "osmdroid_zoom_out_button") {
        mapState.zoomOut()
      }
      MapControlButton(
        Icons.Default.MyLocation,
        if (gpsState == GpsRequestState.REQUESTING) "Locating…" else "Recenter My Location",
        NeonEmerald,
        "osmdroid_recenter_button",
        loading = gpsState == GpsRequestState.REQUESTING
      ) {
        // Permanently-denied = user checked "don't ask again" (or the OEM
        // auto-denied): asking again is pointless, point at Settings instead.
        // First-ever presses must NOT be misread as permanently denied.
        val activity = context as? Activity
        val permanentlyDenied = !hasLocationPermission && locationPermissionAsked &&
          activity != null &&
          !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
          ) &&
          !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.ACCESS_COARSE_LOCATION
          )
        mapState.requestRecenter(
          context = context,
          hasPermission = hasLocationPermission,
          permissionPermanentlyDenied = permanentlyDenied,
          onPermissionRequest = {
            locationPermissionAsked = true
            permissionLauncher.launch(
              arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
              )
            )
          },
          onFix = onRealGpsFix
        )
      }
      if (activeRoute != null) {
        MapControlButton(Icons.Default.Close, "Clear Route", EmergencyRed, "osmdroid_clear_route_button") {
          // Route clearing flows through the ViewModel (single source of
          // truth): state resets, then this map removes its polyline because
          // activeRoute becomes null.
          onClearRoute()
        }
      }
    }

    // ---------------- NEARBY-FIRST chip: distant data folded, honestly ------
    if (mapState.nearbyHiddenCount > 0) {
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = bottomOverlayPadding + 8.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(ObsidianContainer.copy(alpha = 0.95f))
          .border(1.dp, TacticalCyan.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
          .clickable { showAllRegion = !showAllRegion }
          .padding(horizontal = 14.dp, vertical = 8.dp)
          .testTag("map_see_all_chip")
      ) {
        Text(
          if (showAllRegion) "Showing all of India — tap for NEARBY ONLY"
          else "${mapState.nearbyHiddenCount} more alerts farther away — SEE ALL",
          fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacticalCyan
        )
      }
    }

    // ---------------- Region + attribution banner ----------------------------
    Column(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(start = 8.dp, bottom = bottomOverlayPadding + 8.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(ObsidianContainer.copy(alpha = 0.9f))
        .border(0.5.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
        .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
      Text(
        text = "Tiles (c) Esri · data (c) OpenStreetMap · routing OSRM",
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = TacticalOnSurfaceVariant
      )
    }
  }
}

@Composable
private fun MapControlButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  tint: androidx.compose.ui.graphics.Color,
  testTag: String,
  loading: Boolean = false,
  onClick: () -> Unit
) {
  IconButton(
    onClick = onClick,
    modifier = Modifier
      .size(36.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(ObsidianContainer.copy(alpha = 0.95f))
      .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(8.dp))
      .testTag(testTag)
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = tint
      )
    } else {
      Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
  }
}

/**
 * Controller holder managing the map lifecycle, hardware GPS overlay, large
 * pulsing hazard/safe-zone overlays, OSRM route polyline and tap events.
 */
class OsmMapControllerHolder(
  private val appContext: Context,
  private val onLiveNavStatusChanged: (LiveNavStatus) -> Unit
) {

  var mapView: MapView? = null
    private set

  private var locationOverlay: MyLocationNewOverlay? = null
  private var currentTravelMode: String = "foot"
  private var currentRoutePolyline: Polyline? = null

  /** Archived (EM-DAT) markers — kept separate from every live overlay list. */
  private val historicalEventOverlays = mutableListOf<MarkerOverlay>()

  // Marker lists (pin-or-dot and classic overlays share the MarkerOverlay base).
  private val hazardZoneOverlays = mutableListOf<MarkerOverlay>()
  private val safeZoneOverlays = mutableListOf<MarkerOverlay>()
  private val disasterEventOverlays = mutableListOf<MarkerOverlay>()

  // Last-deployed data so a zoom/focus change can redeploy without the caller.
  private var lastHazardZones: List<HazardZone> = emptyList()
  private var lastHazardTap: (HazardZone) -> Unit = {}
  private var lastSafeTap: (SafeZone) -> Unit = {}
  private var lastEventTap: (com.example.data.disaster.DisasterEvent) -> Unit = {}
  private var lastHistoricalTap: (com.example.data.historical.HistoricalDisasterEvent) -> Unit = {}
  private var lastSafeZones: List<SafeZone> = emptyList()
  private var lastDisasterEvents: List<com.example.data.disaster.DisasterEvent> = emptyList()
  private var lastEnabledLayers: Set<com.example.data.disaster.DisasterLayer> =
    com.example.data.disaster.DisasterLayer.entries.toSet()
  private var lastHistoricalEvents: List<com.example.data.historical.HistoricalDisasterEvent> =
    emptyList()

  /** Re-deploy every overlay layer (zoom crossed a detail threshold, focus moved, ...). */
  fun redeployAll() {
    // Each deploy* call stops + removes its own stale overlays and keeps the
    // previously registered tap handlers via default arguments.
    deployHazardZones(lastHazardZones)
    deploySafeZones(lastSafeZones)
    deployDisasterEvents(lastDisasterEvents, lastEnabledLayers)
    deployHistoricalEvents(lastHistoricalEvents)
  }

  /**
   * NEARBY-FIRST state (Google Maps behaviour): while the camera is at city
   * scale, only data around [focusPoint] is drawn unless [showAllRegion] was
   * explicitly requested via the "see all" chip. The ViewModel owns focus
   * changes; the holder just reads them at deploy time + on zoom.
   */
  var focusPoint: GeoPoint? = null
  var showAllRegion: Boolean = false
    private set
  private var zoomRedeploy: (() -> Unit)? = null
  private var zoomWasCity: Boolean? = null

  private var tileSourceIndex = 0
  private val tileSources by lazy {
    listOf(
      // Google-style light basemap (CARTO Positron on OSM data) — muted grey
      // canvas so hazard pins carry ALL the colour, the way Flood Hub works.
      LightBasemap,
      TileSourceFactory.MAPNIK,
      TileSourceFactory.OpenTopo
    )
  }

  fun initMapView(
    context: Context,
    hazardZones: List<HazardZone>,
    safeZones: List<SafeZone>,
    onHazardZoneTapped: (HazardZone) -> Unit,
    onSafeZoneTapped: (SafeZone) -> Unit,
    onRealGpsFix: (latitude: Double, longitude: Double) -> Unit
  ): MapView {
    // 1. Secure osmdroid configuration + tile cache (offline-first tiles).
    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    val osmConfig = Configuration.getInstance()
    osmConfig.load(context, sharedPrefs)
    osmConfig.userAgentValue = "${context.packageName}-DisasterRelief/2.0 (Android; OSMDroid)"
    val basePath = File(context.cacheDir, "osmdroid")
    val tilePath = File(basePath, "tiles-v2") // v2: stale CARTO-watermark tiles under "tiles" must never serve the Esri switch
    osmConfig.osmdroidBasePath = basePath
    osmConfig.osmdroidTileCache = tilePath

    // 2. Create MapView ? opens directly on the India network region.
    val view = MapView(context).apply {
      // Disable osmdroid's auto-detach-on-removal so teardown happens EXACTLY
      // once, from AndroidView.onRelease (OsmMapControllerHolder.cleanup).
      // With the default destroy-mode the framework also calls onDetach() from
      // onDetachedFromWindow when the view leaves the window — a second,
      // unguarded teardown of an already-detached map (the source of the
      // rotation/tab-switch crashes seen here before this fix).
      setDestroyMode(false)
      setTileSource(tileSources[0])
      setMultiTouchControls(true)
      zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
      // Google-style entry: start at CITY scale, not a whole-continent view.
      // With no GPS yet the camera sits on the labeled India fallback centre;
      // the first real fix re-centres it (enableFollowLocation is active).
      controller.setZoom(MapFocus.RADIUS_CIRCLE_MIN_ZOOM + 0.5)
      controller.setCenter(
        OsmGeoPoint(PilotRegionData.DEFAULT_MAP_CENTER.lat, PilotRegionData.DEFAULT_MAP_CENTER.lon)
      )
      setOnTouchListener { v, event ->
        when (event.action) {
          android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
            v.parent?.requestDisallowInterceptTouchEvent(true)
          }
          android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
            v.parent?.requestDisallowInterceptTouchEvent(false)
          }
        }
        false
      }
    }
    mapView = view

    // NEARBY-FIRST re-deploy when the camera crosses the city-scale threshold:
    // zooming into a district pulls in that area's events, zooming out folds
    // them away again. Re-deploy only fires on THRESHOLD CROSSINGS (not on
    // every frame) so panning stays cheap.
    zoomWasCity = MapFocus.isCityScale(view.zoomLevelDouble)
    view.setMapListener(object : org.osmdroid.events.MapListener {
      override fun onZoom(event: org.osmdroid.events.ZoomEvent): Boolean {
        val nowCity = MapFocus.isCityScale(event.zoomLevel)
        if (nowCity != zoomWasCity) {
          zoomWasCity = nowCity
          redeployAll()
        }
        return false
      }
      override fun onScroll(event: org.osmdroid.events.ScrollEvent): Boolean = false
    })

    // 3. Hardware GPS location overlay ? REAL fixes reported to the ViewModel.
    val myLoc = object : MyLocationNewOverlay(GpsMyLocationProvider(context), view) {
      override fun onLocationChanged(
        location: android.location.Location?,
        source: org.osmdroid.views.overlay.mylocation.IMyLocationProvider?
      ) {
        super.onLocationChanged(location, source)
        location?.let { fix -> onRealGpsFix(fix.latitude, fix.longitude) }
      }
    }.apply {
      enableMyLocation()
      enableFollowLocation()
      isDrawAccuracyEnabled = true
    }
    locationOverlay = myLoc
    view.overlays.add(myLoc)

    // 4. Deploy LARGE pulsing hazard zones (danger areas).
    deployHazardZones(hazardZones, onHazardZoneTapped)

    // 5. Deploy LARGE pulsing safe zones (safe areas).
    deploySafeZones(safeZones, onSafeZoneTapped)

    return view
  }

  /** Disaster-colored hazard marks: small pin dots at overview zoom, honest
   * real-radius pulsing areas once the zoom can show them (Google-style).
   * NEARBY FIRST: at city scale only zones within 50 km of the focus render,
   * unless showAllRegion was requested. */
  fun deployHazardZones(
    hazardZones: List<HazardZone>,
    onHazardZoneTapped: (HazardZone) -> Unit = lastHazardTap
  ) {
    val view = mapView ?: return
    lastHazardZones = hazardZones
    lastHazardTap = onHazardZoneTapped
    hazardZoneOverlays.forEach { overlay ->
      overlay.stop()
      view.overlays.remove(overlay)
    }
    hazardZoneOverlays.clear()

    val zoom = view.zoomLevelDouble
    val visible = nearbyFilter(hazardZones, zoom) { it.center }
    visible.forEach { zone ->
      val overlay = PinOrAreaOverlay(
        center = OsmGeoPoint(zone.center.lat, zone.center.lon),
        radiusMeters = zone.radiusMeters,
        colorArgb = hazardColor(zone),
        pulsePeriodMs = HAZARD_PULSE_MS,
        // The overlay reads the LIVE zoom on every draw: dots at country
        // scale morph into honest real-radius areas once zoomed in enough.
        areaFromZoom = MapFocus.RADIUS_CIRCLE_MIN_ZOOM,
        showHalo = zone.severity.weight >= 3,
        onTapped = { onHazardZoneTapped(zone) }
      )
      hazardZoneOverlays.add(overlay)
      // Insert UNDER the location overlay so the user dot stays visible.
      view.overlays.add(0, overlay)
    }
    refreshNearbyUi()
    view.invalidate()
  }

  /**
   * NEARBY-FIRST rule shared by every layer: while the camera is at city
   * scale AND the user has not pressed "show all", only data within 50 km of
   * the focus point is drawn. At country scale, or when zoomed in with the
   * focus unknown, everything the layer normally shows renders.
   */
  private fun <T> nearbyFilter(
    items: List<T>,
    zoom: Double,
    pointOf: (T) -> GeoPoint
  ): List<T> = when {
    focusPoint == null -> items
    !MapFocus.isCityScale(zoom) -> items      // country view: see everything
    showAllRegion -> items                     // user asked for the whole view
    else -> items.filter { MapFocus.isNear(focusPoint, pointOf(it)) }
  }

  private fun nearbyActive(zoom: Double): Boolean =
    focusPoint != null && MapFocus.isCityScale(zoom) && !showAllRegion

  /** True while the NEARBY-FIRST window is active (city zoom + focus known). */
  var nearbyHiddenCount by mutableStateOf(0)
    private set

  private fun refreshNearbyUi() {
    val view = mapView ?: return
    nearbyHiddenCount = distantEventCount()
  }

  /** How many of the last-deployed events live OUTSIDE the nearby window (for the chip). */
  fun distantEventCount(): Int {
    val view = mapView ?: return 0
    val zoom = view.zoomLevelDouble
    if (nearbyActive(zoom)) {
      return lastDisasterEvents.count { e ->
        val p = com.example.data.disaster.MapFocus.eventPoint(e)
        p != null && !MapFocus.isNear(focusPoint, p)
      }
    }
    return 0
  }

  fun setFocus(point: GeoPoint?) {
    if (focusPoint?.lat == point?.lat && focusPoint?.lon == point?.lon) return
    focusPoint = point
    redeployAll()
  }

  fun setShowAllRegion(value: Boolean) {
    if (showAllRegion == value) return
    showAllRegion = value
    redeployAll()
  }

  /**
   * Green-toned large faded safe-area circles; full shelters get amber/red.
   * Idempotent.
   *
   * A tap opens READ-ONLY information only. It deliberately does NOT select the
   * shelter or start a route any more: a single tap on the map used to both open
   * a modal dialog AND silently re-route to that shelter, which is what made map
   * taps feel like an unwanted popup. Routing now requires the explicit
   * "Route To This Safe Zone" action inside the info panel.
   */
  fun deploySafeZones(
    safeZones: List<SafeZone>,
    onSafeZoneTapped: (SafeZone) -> Unit = lastSafeTap
  ) {
    val view = mapView ?: return
    lastSafeZones = safeZones
    lastSafeTap = onSafeZoneTapped
    safeZoneOverlays.forEach { overlay ->
      overlay.stop()
      view.overlays.remove(overlay)
    }
    safeZoneOverlays.clear()

    val zoom = view.zoomLevelDouble
    safeZones.forEach { zone ->
      val color = when {
        zone.capacityStatus == com.example.data.model.CapacityStatus.FULL -> 0xFFF59E0B.toInt() // amber = full
        else -> 0xFF00E297.toInt() // emerald = available
      }
      val overlay = PinOrAreaOverlay(
        center = OsmGeoPoint(zone.lat, zone.lon),
        radiusMeters = SAFE_ZONE_RADIUS_METERS,
        colorArgb = color,
        pulsePeriodMs = SAFE_ZONE_PULSE_MS,
        // Shelters matter at walking scale: show their (small, honest) area
        // from city zoom up, dots beyond that.
        areaFromZoom = MapFocus.SAFE_DOT_MIN_ZOOM,
        onTapped = { onSafeZoneTapped(zone) }
      )
      safeZoneOverlays.add(overlay)
      view.overlays.add(0, overlay)
    }
    view.invalidate()
  }

  /**
   * Renders REAL provider events (USGS/FIRMS/IMD/user reports) as compact
   * circular markers honoring the user's layer toggles. Overview clustering
   * uses MarkerGeneralizer at national zoom; nothing is invented when a layer
   * is off or a provider returned nothing. Idempotent — stale markers removed
   * first, and the location overlay + route polyline are never touched.
   */
  fun deployDisasterEvents(
    events: List<DisasterEvent>,
    enabledLayers: Set<DisasterLayer>,
    onEventTapped: (DisasterEvent) -> Unit = lastEventTap
  ) {
    val view = mapView ?: return
    lastDisasterEvents = events
    lastEnabledLayers = enabledLayers
    lastEventTap = onEventTapped
    disasterEventOverlays.forEach { overlay ->
      overlay.stop()
      view.overlays.remove(overlay)
    }
    disasterEventOverlays.clear()

    val zoom = view.zoomLevelDouble
    val selected = enabledLayers.intersect(
      setOf(
        com.example.data.disaster.DisasterLayer.EARTHQUAKES,
        com.example.data.disaster.DisasterLayer.ACTIVE_FIRES,
        com.example.data.disaster.DisasterLayer.OFFICIAL_ALERTS,
        com.example.data.disaster.DisasterLayer.USER_REPORTS
      )
    )
    if (selected.isEmpty()) {
      view.invalidate()
      return
    }

    val renderable0 = events.mapNotNull { event ->
      val layer = when (event.disasterType) {
        DisasterType.EARTHQUAKE -> DisasterLayer.EARTHQUAKES
        DisasterType.WILDFIRE -> DisasterLayer.ACTIVE_FIRES
        DisasterType.HEAVY_RAINFALL,
        DisasterType.CYCLONE,
        DisasterType.FLOOD,
        DisasterType.WEATHER_ALERT -> DisasterLayer.OFFICIAL_ALERTS
        DisasterType.LANDSLIDE,
        DisasterType.OTHER ->
          if (event.source == DisasterSource.USER_REPORT) DisasterLayer.USER_REPORTS
          else DisasterLayer.OFFICIAL_ALERTS
      }
      // Zoom-based level-of-detail rule comes from the layer itself.
      if (layer in selected && layer.isVisibleAt(zoom, enabled = true)) event else null
    }
    // NEARBY FIRST at city scale (distant events fold into the "see all" chip).
    val renderable = nearbyFilter(renderable0, zoom) { e ->
      com.example.data.disaster.MapFocus.eventPoint(e) ?: GeoPoint(0.0, 0.0)
    }
    // Overview clustering — collapses dense fire detections at national zoom
    // and reports, per marker, how many detections it stands for and the
    // strongest FRP measured among them.
    val clustered = MarkerGeneralizer.clusters(renderable, zoom)

    clustered.forEach { cluster ->
      val event = cluster.representative
      val point = when (val g = event.geometry) {
        is EventGeometry.Point -> GeoPoint(g.lat, g.lon)
        is EventGeometry.MultiPoint -> g.points.firstOrNull()
        is EventGeometry.Line -> g.points.firstOrNull()
        is EventGeometry.Polygon -> g.ring.firstOrNull()
        is EventGeometry.RasterLayer -> null
        // No source geometry -> no marker. The alert stays in the feed/detail UI.
        is EventGeometry.Unlocated -> null
      } ?: return@forEach
      // Marker size carries real intensity: the detection's own FRP plus the
      // number of detections this marker represents, both bounded by
      // FireIntensityScale.MAX_MARKER_SCALE. No measurement -> base size.
      val scale = FireIntensityScale.markerScale(cluster.maxFrpMegawatts, cluster.memberCount)
      val overlay = PinOrAreaOverlay(
        center = OsmGeoPoint(point.lat, point.lon),
        radiusMeters = DISASTER_MARKER_RADIUS_METERS * scale,
        colorArgb = disasterMarkerColor(event),
        pulsePeriodMs = DISASTER_PULSE_MS,
        // Alert points are exact source coordinates: a crisp dot pin at any
        // zoom, its small honest area only at street level.
        areaFromZoom = 13.0,
        dotRadiusPx = 10f + (scale.toFloat() - 1f) * 4f,
        showHalo = event.severity.weight >= 3,
        onTapped = { onEventTapped(event) }
      )
      disasterEventOverlays.add(overlay)
      view.overlays.add(0, overlay)
    }
    refreshNearbyUi()
    view.invalidate()
  }

  /**
   * HISTORICAL EVENT LAYER (EM-DAT).
   *
   * Archived events are drawn ONLY when they carry the dataset's own
   * coordinates, in a distinct muted tone with a slow pulse, so a 1987 flood
   * cannot read as an active hazard zone. Idempotent like every other deploy
   * call: stale overlays are removed first and the location overlay, the route
   * polyline and every live hazard overlay are left untouched.
   */
  fun deployHistoricalEvents(
    events: List<com.example.data.historical.HistoricalDisasterEvent>,
    onEventTapped: (com.example.data.historical.HistoricalDisasterEvent) -> Unit = lastHistoricalTap
  ) {
    val view = mapView ?: return
    lastHistoricalEvents = events
    lastHistoricalTap = onEventTapped
    historicalEventOverlays.forEach { overlay ->
      overlay.stop()
      view.overlays.remove(overlay)
    }
    historicalEventOverlays.clear()

    events.filter { it.isMappable }.forEach { event ->
      val lat = event.latitude ?: return@forEach
      val lon = event.longitude ?: return@forEach
      val overlay = PinOrAreaOverlay(
        center = OsmGeoPoint(lat, lon),
        radiusMeters = HISTORICAL_MARKER_RADIUS_METERS,
        colorArgb = HISTORICAL_MARKER_COLOR,
        // A deliberately slow pulse keeps it visually distinct from live alerts.
        pulsePeriodMs = HISTORICAL_PULSE_MS,
        areaFromZoom = 99.0, // history is NEVER an area claim: dot only
        dotRadiusPx = 9f,
        onTapped = { onEventTapped(event) }
      )
      historicalEventOverlays.add(overlay)
      view.overlays.add(0, overlay)
    }
    view.invalidate()
  }

  /** Zone color follows the DISASTER TYPE (flood blue, fire orange ...). */
  private fun disasterMarkerColor(event: DisasterEvent): Int {
    if (event.source == DisasterSource.USER_REPORT) return 0xFFF59E0B.toInt()
    return com.example.data.disaster.DisasterTypeColors.argbFor(
      com.example.data.disaster.DisasterEventNormalizer.toHazardType(event.disasterType)
    )
  }

  private fun hazardColor(zone: HazardZone): Int =
    com.example.data.disaster.DisasterTypeColors.argbFor(zone.type)

  // ------------------------------------------------------------ controls

  fun setTravelMode(mode: String) {
    currentTravelMode = if (mode == "driving") "driving" else "foot"
  }

  fun enableLocationTracking() {
    val view = mapView ?: return
    locationOverlay?.let {
      if (!it.isMyLocationEnabled) {
        it.enableMyLocation()
        it.enableFollowLocation()
      }
    }
  }

  // ------------------------------------------------ GPS locate (Issue 2)

  /** Current locate request state — observed by the recenter button + banner. */
  var gpsRequestState by mutableStateOf(GpsRequestState.IDLE)
    private set

  /** User-visible message for [gpsRequestState]; null hides the banner. */
  var gpsStatusMessage by mutableStateOf<String?>(null)
    private set

  private var oneShotListener: LocationListener? = null
  private var oneShotActive = false
  private val mainHandler = Handler(Looper.getMainLooper())
  private var timeoutRunnable: Runnable? = null

  /** Dismisses the banner (success auto-dismisses; errors stay until this). */
  fun clearGpsStatus() {
    gpsStatusMessage = null
    if (gpsRequestState != GpsRequestState.REQUESTING) gpsRequestState = GpsRequestState.IDLE
  }

  /**
   * GPS "locate me" flow for the recenter button. Delivers immediate feedback
   * ([GpsRequestState.REQUESTING] + spinner/banner), then centers in order:
   * live overlay fix → OS last-known → one-shot listener with a hard
   * [LOCATION_TIMEOUT_MS] timeout. Exactly one one-shot listener ever exists
   * (repeat presses while locating are ignored; it is removed on fix,
   * timeout and [cleanup]). The camera moves ONLY on a valid device fix —
   * never on the labeled fallback area — and a valid fix is reported through
   * [onFix] so the ViewModel replaces the fallback honestly.
   */
  fun requestRecenter(
    context: Context,
    hasPermission: Boolean,
    permissionPermanentlyDenied: Boolean,
    onPermissionRequest: () -> Unit,
    onFix: (latitude: Double, longitude: Double) -> Unit
  ) {
    if (oneShotActive) return
    cancelOneShot()
    if (!hasPermission) {
      if (permissionPermanentlyDenied) {
        gpsRequestState = GpsRequestState.PERMANENTLY_DENIED
        gpsStatusMessage = "Location permission blocked — enable it in Settings to use GPS."
      } else {
        gpsRequestState = GpsRequestState.NO_PERMISSION
        gpsStatusMessage = "Location permission needed for GPS."
        onPermissionRequest()
      }
      return
    }
    val locationManager =
      context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
      gpsRequestState = GpsRequestState.UNAVAILABLE
      gpsStatusMessage = "Location unavailable on this device."
      return
    }
    val gpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    val networkOn = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    if (!gpsOn && !networkOn) {
      gpsRequestState = GpsRequestState.PROVIDER_DISABLED
      gpsStatusMessage = "Location services are OFF — enable GPS or network location."
      return
    }
    gpsRequestState = GpsRequestState.REQUESTING
    gpsStatusMessage = "Locating…"
    val view = mapView

    // Fast path 1: the overlay already holds a real device fix (reported to
    // the ViewModel when it arrived — just center on it).
    locationOverlay?.myLocation?.let { fix ->
      view?.controller?.animateTo(fix)
      gpsRequestState = GpsRequestState.SUCCESS
      gpsStatusMessage = "Centered on your GPS location."
      return
    }
    // Fast path 2: the OS last-known location — real device data (may be
    // stale, so it is labeled as such and also reported as a real fix).
    val lastKnown = try {
      locationManager.getLastKnownLocation(
        if (gpsOn) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
      )
    } catch (_: SecurityException) { null }
    if (lastKnown != null) {
      view?.controller?.animateTo(OsmGeoPoint(lastKnown.latitude, lastKnown.longitude))
      gpsRequestState = GpsRequestState.SUCCESS
      gpsStatusMessage = "Centered on your last known GPS location."
      onFix(lastKnown.latitude, lastKnown.longitude)
      return
    }
    // Slow path: one-shot listener with a hard timeout. The camera does NOT
    // move until a valid fix arrives — no disaster/routing wait, no fallback.
    val listener = LocationListener { location ->
      cancelOneShot()
      mapView?.controller?.animateTo(OsmGeoPoint(location.latitude, location.longitude))
      gpsRequestState = GpsRequestState.SUCCESS
      gpsStatusMessage = "Centered on your GPS location."
      onFix(location.latitude, location.longitude)
    }
    oneShotListener = listener
    oneShotActive = true
    try {
      if (gpsOn) locationManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper()
      )
      if (networkOn) locationManager.requestLocationUpdates(
        LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper()
      )
    } catch (_: SecurityException) {
      cancelOneShot()
      gpsRequestState = GpsRequestState.NO_PERMISSION
      gpsStatusMessage = "Location permission needed for GPS."
      return
    }
    val timeout = Runnable {
      cancelOneShot()
      gpsRequestState = GpsRequestState.TIMEOUT
      gpsStatusMessage =
        "Could not get a GPS fix in ${LOCATION_TIMEOUT_MS / 1000}s — try outdoors with a clear sky view."
    }
    timeoutRunnable = timeout
    mainHandler.postDelayed(timeout, LOCATION_TIMEOUT_MS)
  }

  /** Removes the one-shot listener + timeout; safe to call when idle. */
  private fun cancelOneShot() {
    timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
    timeoutRunnable = null
    oneShotListener?.let { listener ->
      try {
        (appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
          ?.removeUpdates(listener)
      } catch (_: Exception) { }
    }
    oneShotListener = null
    oneShotActive = false
  }

  fun recenterUser() {
    val myLoc = locationOverlay?.myLocation
    if (myLoc != null) {
      mapView?.controller?.animateTo(myLoc)
    } else {
      // No fix: say so honestly instead of silently jumping the camera to
      // the labeled fallback area (that jump presented fallback coordinates
      // as the user's real GPS location).
      gpsRequestState = GpsRequestState.UNAVAILABLE
      gpsStatusMessage = "No GPS fix yet — press locate once a fix is available."
    }
  }

  fun zoomIn() { mapView?.controller?.zoomIn() }

  fun zoomOut() { mapView?.controller?.zoomOut() }

  fun cycleTileSource() {
    val mv = mapView ?: return
    tileSourceIndex = (tileSourceIndex + 1) % tileSources.size
    mv.setTileSource(tileSources[tileSourceIndex])
    Toast.makeText(appContext, "Map Layer: ${tileSources[tileSourceIndex].name()}", Toast.LENGTH_SHORT).show()
  }

  fun focusOnSafeZone(zone: SafeZone) {
    mapView?.controller?.animateTo(OsmGeoPoint(zone.lat, zone.lon))
  }

  /** Fly to a chosen place and stop GPS-following while viewing elsewhere. */
  fun stopFollowingAndCenter(point: GeoPoint) {
    val view = mapView ?: return
    locationOverlay?.disableFollowLocation()
    view.controller.animateTo(OsmGeoPoint(point.lat, point.lon))
    view.invalidate()
  }

  /** Re-enable GPS following (when leaving place-view). */
  fun resumeGpsFollowing() {
    locationOverlay?.enableFollowLocation()
  }

  /** Removes the route polyline (called when ViewModel state clears the corridor). */
  fun clearRouteOverlay() {
    currentRoutePolyline?.let { mapView?.overlays?.remove(it) }
    currentRoutePolyline = null
    mapView?.invalidate()
    onLiveNavStatusChanged(LiveNavStatus(isActive = false))
  }

  /**
   * Renders the current evacuation polyline (called on route changes).
   * Live OSRM road routes draw SOLID (exact road pathway); the instant
   * offline corridor draws DASHED so it reads as a provisional preview
   * until the road route swaps in — never mistaken for surveyed roads.
   *
   * The RECOMMENDED route always draws GREEN ([ROUTE_COLOR]) — including
   * routes whose safety status is CAUTION/DANGER. Danger semantics live in
   * the route panel ("ROUTE SAFETY" label + hazard warnings) and in the
   * hazard overlays, never in the polyline color: a red recommended route
   * reads as "do not use" while red zones read as "danger here", and the
   * two meanings collided on the map.
   *
   * Tapping the polyline NEVER shows osmdroid's default blank
   * bonuspack_bubble InfoWindow: every Polyline is born with one
   * (MapViewRepository.getDefaultPolylineInfoWindow, empty title/snippet),
   * and onClickDefault opens it. The click listener below declines the tap
   * (returns false) so no bubble opens and the tap falls through to zone
   * overlays underneath.
   */
  fun displayRoute(route: RouteResult) {
    val mv = mapView ?: return
    currentRoutePolyline?.let { mv.overlays.remove(it) }
    if (route.pathPoints.isEmpty()) return
    val points = route.pathPoints.map { OsmGeoPoint(it.lat, it.lon) }
    val polyline = Polyline(mv).apply {
      setPoints(points)
      outlinePaint.color = ROUTE_COLOR
      outlinePaint.strokeWidth = ROUTE_WIDTH
      outlinePaint.strokeCap = Paint.Cap.ROUND
      outlinePaint.strokeJoin = Paint.Join.ROUND
      outlinePaint.pathEffect =
        if (route.isLiveOsrm) null
        else android.graphics.DashPathEffect(floatArrayOf(28f, 22f), 0f)
      // Click-through: no InfoWindow bubble, taps reach zones below.
      setOnClickListener(Polyline.OnClickListener { _, _, _ -> false })
    }
    currentRoutePolyline = polyline
    // Insert above zones, below the user location overlay.
    mv.overlays.add(polyline)
    mv.invalidate()
  }

  /**
   * Releases the map engine (called exactly once per MapView from
   * AndroidView.onRelease). Order matters for rotation / tab-switch safety:
   *   1. disableMyLocation() unregisters the GPS LocationListener from the
   *      LocationManager FIRST — otherwise fix callbacks keep firing into the
   *      destroyed map, and each recreation registers a duplicate listener.
   *   2. Zone/event overlays are dropped and the MapView teardown runs
   *      against a screen that is no longer drawing (setDestroyMode(false)
   *      guarantees the framework does not also detach from
   *      onDetachedFromWindow — exactly one detach, ever).
   *   3. mapView + locationOverlay are nulled so every later call
   *      (deploy*, enableLocationTracking, controls) becomes a safe no-op.
   *   Idempotent: a second call (e.g. dispose after release) is a no-op.
   */
  fun cleanup() {
    val view = mapView ?: return
    // ROTATION/CRASH FIX — strict, ordered teardown:
    // 1. Cancel the pending one-shot locate.
    cancelOneShot()
    gpsRequestState = GpsRequestState.IDLE
    gpsStatusMessage = null
    // 2. Unregister the GPS listener BEFORE touching overlays so no fix can be
    //    delivered into a half-torn-down map (a fix during teardown previously
    //    re-entered the ViewModel pipeline mid-recreation).
    locationOverlay?.disableMyLocation()
    locationOverlay = null
    // 3. Stop every pulsing overlay BEFORE removing it, so no queued
    //    postInvalidate() can reach the detached MapView (each overlay used to
    //    schedule a redraw every 66 ms forever).
    (hazardZoneOverlays + safeZoneOverlays + disasterEventOverlays).forEach { it.stop() }
    hazardZoneOverlays.forEach { view.overlays.remove(it) }
    hazardZoneOverlays.clear()
    safeZoneOverlays.forEach { view.overlays.remove(it) }
    safeZoneOverlays.clear()
    disasterEventOverlays.forEach { view.overlays.remove(it) }
    disasterEventOverlays.clear()
    currentRoutePolyline = null
    // 4. Detach exactly once — destroy-mode is off, so the framework will not
    //    also detach from onDetachedFromWindow.
    view.onDetach()
    mapView = null
  }

  companion object {
    private const val HAZARD_PULSE_MS = 2600L
    private const val SAFE_ZONE_PULSE_MS = 3600L
    private const val DISASTER_PULSE_MS = 2200L

    /** Historical markers: muted slate, slower pulse, larger radius (archived
     * events describe a whole district, not a measured point). */
    private const val HISTORICAL_PULSE_MS = 5200L
    private const val HISTORICAL_MARKER_RADIUS_METERS = 2600.0
    private const val HISTORICAL_MARKER_COLOR = 0xFF64748B.toInt()
    private const val SAFE_ZONE_RADIUS_METERS = 900.0
    private const val DISASTER_MARKER_RADIUS_METERS = 1200.0
    /** Hard timeout for the GPS one-shot locate before reporting TIMEOUT. */
    private const val LOCATION_TIMEOUT_MS = 15_000L
  }
}
