package com.example

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.disaster.DisasterFileCache
import com.example.data.disaster.ZoneDetailMapper
import com.example.data.location.AndroidGeocoderPlaceResolver
import com.example.data.news.NewsFileCache
import com.example.ui.components.AddContactDialog
import com.example.ui.components.DisasterEventDetailDialog
import com.example.ui.components.IncidentReportDialog
import com.example.ui.components.EditProfileDialog
import com.example.ui.components.SituationReportDialog
import com.example.ui.components.InteractiveBagDialog
import com.example.ui.components.HazardZoneDetailDialog
import com.example.ui.components.SafeZoneDetailDialog
import com.example.ui.components.SosBroadcastDialog
import com.example.ui.components.SosConfirmDialog
import com.example.ui.components.VippattiBottomNavBar
import com.example.data.auth.AuthRepository
import com.example.data.auth.SharedPrefsAuthStorage
import com.example.ui.screens.DispatchesScreen
import com.example.ui.screens.InstructionsScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RadarMapScreen
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.ScreenTab
import com.example.viewmodel.AuthGateViewModel
import com.example.viewmodel.TORCH_REASON_PERMISSION
import com.example.viewmodel.TorchState
import com.example.viewmodel.VippattiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Application context for auth storage: the repository outlives any single
    // Activity instance (retained by AuthGateViewModel across rotation), so it
    // must never hold the destroyed Activity.
    val appContext = applicationContext
    setContent {
      val context = LocalContext.current

      // Local offline auth gate: seeded demo account + stay-signed-in session
      // (see AuthRepository). No network, no Google accounts — deliberately.
      // The repository lives in an activity-scoped ViewModel (NOT remember):
      // remember {} is rebuilt on every Activity recreation, which dropped the
      // in-process session flag and bounced stay-signed-out users back to the
      // login screen on every rotation. The ViewModel survives recreation, so
      // rotation keeps the session; process death still re-reads the store.
      val authGate: AuthGateViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthGateViewModel(
              AuthRepository(SharedPrefsAuthStorage(appContext)).apply { seedDemoAccount() }
            ) as T
        }
      )
      val authRepository = authGate.repository
      var signedIn by remember { mutableStateOf(authGate.isSignedIn()) }
      // First-run onboarding: shown once, before the auth gate, and never
      // again once completed. Signed-in sessions skip it entirely. The flag
      // is written only on skip / sign-in / final-page completion, never for
      // page changes (see OnboardingCompletion).
      val onboardingStore = remember {
        com.example.ui.screens.OnboardingCompletion(
          appContext.getSharedPreferences(
            com.example.ui.screens.OnboardingCompletion.PREFS_NAME,
            MODE_PRIVATE
          )
        )
      }
      var onboardingDone by remember { mutableStateOf(onboardingStore.isCompleted()) }
      val completeOnboarding = {
        onboardingStore.setCompleted()
        onboardingDone = true
      }

      // Production ViewModel: file-backed GNews cache AND file-backed disaster
      // provider shards survive app restarts; the osmdroid tile-cache dir feeds
      // the REAL offline map-cache size shown on the Profile screen.
      val coastGridRef = remember { mutableStateOf<com.example.data.suitability.CoastDistanceGrid?>(null) }
      val coastGridTried = remember { mutableStateOf(false) }
      val viewModel: VippattiViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VippattiViewModel(
              newsCache = NewsFileCache(File(cacheDir, "news_cache")),
              disasterCache = DisasterFileCache(File(cacheDir, "disaster_cache")),
              tileCacheDirProvider = { File(cacheDir, "osmdroid/tiles") },
              // Dynamic-data rule: district/state names for news scoping are
              // resolved from the device's own coordinates at runtime.
              placeResolver = AndroidGeocoderPlaceResolver(applicationContext),
              // TERRAIN HABITABILITY: the offline coast-distance grid (built
              // from public-domain Natural Earth data) loads once, lazily, and
              // returns null honestly if the asset is missing/corrupt.
              coastGridProvider = {
                if (coastGridRef.value == null && !coastGridTried.value) {
                  coastGridTried.value = true
                  coastGridRef.value = try {
                    applicationContext.assets
                      .open(com.example.data.suitability.CoastDistanceGrid.ASSET)
                      .use { com.example.data.suitability.CoastDistanceGrid.load(it) }
                  } catch (_: Exception) {
                    null
                  }
                }
                coastGridRef.value
              },
              // HISTORICAL DISASTER INTELLIGENCE (EM-DAT). The prepared archive
              // ships as an app asset - no network call, and never reported as
              // a live feed. A missing asset yields an honest UNAVAILABLE state.
              historicalProvider = com.example.data.historical.BundledHistoricalDataProvider(
                reader = { assetName ->
                  try {
                    applicationContext.assets.open(assetName)
                      .bufferedReader()
                      .use { it.readText() }
                  } catch (_: Exception) {
                    null
                  }
                }
              )
            ) as T
        }
      )
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      VippattiTheme(darkTheme = uiState.isDarkTheme) {
        // HISTORICAL (EM-DAT) record sheet. Opened only from the historical
        // panel or a historical map marker, never from a live hazard marker.
        uiState.historicalDetailEvent?.let { historical ->
          com.example.ui.screens.HistoricalEventDetailDialog(
            event = historical,
            onDismiss = { viewModel.closeHistoricalEventDetail() }
          )
        }
        if (signedIn) {
          VippattiAppRoot(
            viewModel = viewModel,
            accountEmail = authRepository.currentUserEmail,
            onSignOut = {
              authRepository.logout()
              signedIn = false
            }
          )
        } else if (!onboardingDone) {
          com.example.ui.screens.OnboardingFlowScreen(
            onFinish = { completeOnboarding() }
          )
        } else {
          LoginScreen(
            onLogin = { email, password, staySignedIn ->
              authRepository.login(email, password, staySignedIn).also {
                if (it.ok) signedIn = true
              }
            },
            onRegister = { email, password, staySignedIn ->
              authRepository.register(email, password, staySignedIn).also {
                if (it.ok) signedIn = true
              }
            }
          )
        }
      }
    }
  }
}

@Composable
fun VippattiAppRoot(
  viewModel: VippattiViewModel,
  accountEmail: String? = null,
  onSignOut: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current

  // REAL device battery level.
  // Sticky ACTION_BATTERY_CHANGED broadcast gives an immediate reading.
  DisposableEffect(Unit) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        if (level >= 0 && scale > 0) {
          val percent = (level * 100 / scale).coerceIn(0, 100)
          val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
          viewModel.onBatteryChanged(percent, charging)
        }
      }
    }
    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    onDispose { context.unregisterReceiver(receiver) }
  }

  // ------------------------------------------------------------------ torch
  // Real hardware torch with an EXPLICIT, honest result path: the CAMERA
  // permission is requested on first use (it used to be declared but never
  // requested, so setTorchMode threw SecurityException into an empty catch and
  // the button silently did nothing while still reporting "ON"), the flash unit
  // is checked for existence, and the outcome is reported back to the ViewModel.
  var torchRetryKey by remember { mutableStateOf(0) }
  var cameraPermissionAsked by remember { mutableStateOf(false) }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      torchRetryKey++ // retry the torch now that the permission exists
    } else {
      viewModel.onTorchResult(false, TORCH_REASON_PERMISSION, permissionDenied = true)
    }
  }

  LaunchedEffect(uiState.torchState, torchRetryKey) {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    if (uiState.torchState != TorchState.ON) {
      // OFF / UNAVAILABLE / PERMISSION_DENIED: make sure the hardware is really
      // off, so a denied request never leaves a stale torch burning.
      runCatching {
        cameraManager?.cameraIdList?.firstOrNull()?.let { id ->
          cameraManager.setTorchMode(id, false)
        }
      }
      return@LaunchedEffect
    }
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
      PackageManager.PERMISSION_GRANTED
    if (!granted) {
      if (!cameraPermissionAsked) {
        cameraPermissionAsked = true
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
      } else {
        viewModel.onTorchResult(false, TORCH_REASON_PERMISSION, permissionDenied = true)
      }
      return@LaunchedEffect
    }
    if (cameraManager == null) {
      viewModel.onTorchResult(false, "Camera service is unavailable on this device.")
      return@LaunchedEffect
    }
    val flashCameraId = cameraManager.cameraIdList.firstOrNull { id ->
      runCatching {
        cameraManager.getCameraCharacteristics(id)
          .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
      }.getOrDefault(false)
    }
    if (flashCameraId == null) {
      viewModel.onTorchResult(false, "This device has no flash unit.")
      return@LaunchedEffect
    }
    val turnedOn = runCatching { cameraManager.setTorchMode(flashCameraId, true) }.isSuccess
    viewModel.onTorchResult(
      turnedOn,
      if (turnedOn) null else "The camera service refused to switch the torch on."
    )
  }

  // Real SOS alarm siren, tied to the EXPLICIT sirenState. The ViewModel owns the
  // visible countdown + hard auto-stop, so the tone always ends when the state
  // returns to IDLE (and a Stop is reachable from every tab via ActiveToolsBar).
  LaunchedEffect(uiState.isSirenOn) {
    if (uiState.isSirenOn) {
      withContext(Dispatchers.IO) {
        var toneGen: ToneGenerator? = null
        try {
          toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
          while (isActive && uiState.isSirenOn) {
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1200)
            delay(1400)
          }
        } catch (e: Exception) {
          // Graceful fallback if tone generator cannot acquire audio stream
        } finally {
          toneGen?.release()
        }
      }
    }
  }

  // Turn off flashlight on component disposal to avoid draining battery
  DisposableEffect(Unit) {
    onDispose {
      try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val cameraId = cameraManager?.cameraIdList?.firstOrNull()
        if (cameraId != null) {
          cameraManager.setTorchMode(cameraId, false)
        }
      } catch (_: Exception) {}
    }
  }

  // Measure the REAL osmdroid tile-cache size for the Profile honesty card.
  LaunchedEffect(Unit) { viewModel.updateTileCacheBytes() }

  // Single-slot, explicitly short snackbar.
  // The previous version suspended on showSnackbar and only cleared the state
  // afterwards, so the message stayed non-null while visible — and re-appeared
  // after an Activity recreation (rotation). It is now consumed immediately, any
  // previous message is dismissed first (never queued), and it always expires.
  LaunchedEffect(uiState.snackbarMessage) {
    val message = uiState.snackbarMessage ?: return@LaunchedEffect
    viewModel.clearSnackbar()
    snackbarHostState.currentSnackbarData?.dismiss()
    snackbarHostState.showSnackbar(
      message = message,
      duration = SnackbarDuration.Short,
      withDismissAction = true
    )
  }

  // REAL TextToSpeech engine — speaks the bulletin the ViewModel composed from
  // real state (risk level, recommended action, live GNews headlines). The old
  // fake playback timer is gone: the engine itself reports completion.
  var ttsStatus by remember { mutableStateOf<Int?>(null) }
  val ttsEngine = remember { TextToSpeech(context) { status -> ttsStatus = status } }
  DisposableEffect(Unit) {
    onDispose {
      ttsEngine.stop()
      ttsEngine.shutdown()
      if (uiState.isAudioPlaying) viewModel.onTtsBulletinFinished()
    }
  }
  LaunchedEffect(uiState.isAudioPlaying, uiState.audioBulletinText, ttsStatus) {
    if (!uiState.isAudioPlaying) {
      ttsEngine.stop()
      return@LaunchedEffect
    }
    val bulletin = uiState.audioBulletinText
    if (bulletin.isBlank()) return@LaunchedEffect
    when (ttsStatus) {
      TextToSpeech.SUCCESS -> {
        ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) { }
          override fun onDone(utteranceId: String?) { viewModel.onTtsBulletinFinished() }
          override fun onError(utteranceId: String?) { viewModel.onTtsUnavailable() }
          override fun onError(utteranceId: String?, errorCode: Int) { viewModel.onTtsUnavailable() }
        })
        if (ttsEngine.speak(bulletin, TextToSpeech.QUEUE_FLUSH, null, "sarana_bulletin") == TextToSpeech.ERROR) {
          viewModel.onTtsUnavailable()
        }
      }
      null -> Unit // Engine still initializing — this effect re-runs when ttsStatus arrives.
      else -> viewModel.onTtsUnavailable()
    }
  }

  // The only real way to reach help from this build: open the dialer on 112.
  // Both SOS dialogs and the active-tools bar reuse it.
  val dialEmergencyServices: () -> Unit = {
    try {
      context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
    } catch (e: Exception) {
      Toast.makeText(context, "Cannot open the dialer: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      VippattiBottomNavBar(
        currentTab = uiState.currentTab,
        onTabSelected = { viewModel.setTab(it) }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(ObsidianSurface)
        .statusBarsPadding()
        .padding(bottom = innerPadding.calculateBottomPadding())
    ) {
      val remoteConfig by com.example.config.ConfigRegistry.manager.configState.collectAsStateWithLifecycle()

      Crossfade(
        targetState = uiState.currentTab,
        animationSpec = tween(durationMillis = 250),
        label = "tab_crossfade"
      ) { tab ->
        Column(modifier = Modifier.padding(remoteConfig.homePadding.dp)) {
            if (remoteConfig.emergencyBannerEnabled && remoteConfig.emergencyBannerText.isNotBlank()) {
                androidx.compose.material3.Text(
                    text = remoteConfig.emergencyBannerText,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Red)
                        .padding(16.dp)
                )
            }
            if (remoteConfig.appLogoUrl.isNotBlank() && tab == ScreenTab.PROFILE) {
                // Example of placing remote logo on Profile tab (or it could be in a top bar)
                coil.compose.AsyncImage(
                    model = remoteConfig.appLogoUrl,
                    contentDescription = "App Logo",
                    modifier = Modifier.size(100.dp).align(androidx.compose.ui.Alignment.CenterHorizontally),
                    error = androidx.compose.ui.res.painterResource(id = android.R.drawable.sym_def_app_icon)
                )
            }

            // Global device-tool status with a real Stop, visible on EVERY tab, so a
            // running siren/torch (or an armed local SOS) is always dismissible.
            ActiveToolsBar(
              uiState = uiState,
              onStopDeviceTools = { viewModel.stopAllDeviceTools() },
              onCancelSos = { viewModel.cancelSosBroadcast() }
            )

            // Screen Content
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
          ScreenTab.NEWS_DISPATCHES -> DispatchesScreen(
            uiState = uiState,
            onSync = { viewModel.syncData() },
            onToggleAudio = { viewModel.toggleAudioBulletin() },
            onSelectCategory = { viewModel.setNewsCategory(it) },
            onNavigateToEvacRoute = { viewModel.startEvacuationRoute() },
            onNavigateTab = { viewModel.setTab(it) },
            onToggleHistoricalLayer = { viewModel.toggleHistoricalLayer() },
            onHistoricalFiltersChange = { viewModel.setHistoricalFilters(it) },
            onClearHistoricalFilters = { viewModel.clearHistoricalFilters() },
            onSelectHistoricalEvent = { viewModel.openHistoricalEventDetail(it) }
          )

          ScreenTab.          RADAR_MAP -> RadarMapScreen(
            uiState = uiState,
            onSelectBestSafeZone = { viewModel.selectBestSafeZone() },
            onSelectSafeZone = { viewModel.selectSafeZone(it) },
            onSetTravelMode = { viewModel.setTravelMode(it) },
            onStartEvacuation = { viewModel.startEvacuationRoute() },
            onStopEvacuation = { viewModel.stopLiveNavigation() },
            onNextNavigationStep = { viewModel.nextNavigationStep() },
            onLoadAlternativeRoutes = { viewModel.loadAlternativeRoutes() },
            onOpenSensorBroadcast = { viewModel.triggerSosBroadcast() },
            onClearRoute = { viewModel.clearActiveRoute() },
            // REAL hardware GPS fixes replace the India-centre fallback location.
            onRealGpsFix = { lat, lon -> viewModel.applyRealGpsFix(lat, lon) },
            onOpenHazardDetail = { viewModel.openHazardDetail(it) },
            onOpenSafeZoneDetail = { viewModel.openSafeZoneDetail(it) },
            // REAL disaster-data integration: layers, incident reports, event details.
            onToggleLayer = { viewModel.toggleLayer(it) },
            onOpenIncidentReport = { viewModel.openIncidentReportDialog() },
            onOpenDisasterEventDetail = { viewModel.openDisasterEventDetail(it) },
            onOpenHistoricalEventDetail = { viewModel.openHistoricalEventDetail(it) },
            onToggleMockData = { viewModel.toggleMockData() },
            onRequestFallbackRoute = { viewModel.requestOfflineFallbackRoute() },
            // PHASE 3: retry the live Open-Meteo reading without touching the rest.
            onRetryWeather = { viewModel.refreshWeather(force = true) },
            // EMERGENCY GUIDANCE: nearest safe zone + terrain haven actions.
            onGuidanceGo = { viewModel.acceptEmergencyGuidance() },
            onGuidanceDismiss = { viewModel.dismissEmergencyGuidance() },
            onSearchTerrainHaven = { viewModel.searchTerrainHaven() },
            onRouteToTerrainHaven = { viewModel.routeToTerrainHaven() }
          )

          ScreenTab.INSTRUCTIONS -> InstructionsScreen(
            uiState = uiState,
            onToggleTheme = { viewModel.toggleTheme() },
            onToggleOfflineAccess = { viewModel.toggleOfflineCache(it) },
            
            onOpenInteractiveBag = { viewModel.openInteractiveBagDialog() },
            onToggleFlashlight = { viewModel.toggleFlashlight() },
            onToggleSiren = { viewModel.toggleSiren() }
          )

          ScreenTab.PROFILE -> ProfileScreen(
            uiState = uiState,
            accountEmail = accountEmail,
            onSignOut = onSignOut,
            onToggleTheme = { viewModel.toggleTheme() },
            onSetSafety = { viewModel.setUserSafety(it) },
            onBroadcastSos = { viewModel.triggerSosBroadcast() },
            onOpenAddContact = { viewModel.openAddContactDialog() },
            onOpenEditProfile = { viewModel.openEditProfileDialog() },
            onOpenSituationReport = { viewModel.openSituationReportDialog() }
          )
        }
       }
      }
      }

      // Modal Dialogs
      // Modal Dialogs
      // SOS confirmation gate — nothing is broadcast before an explicit YES.
      if (uiState.showSosConfirmDialog) {
        SosConfirmDialog(
          locationLabel = uiState.sosLocationLabel,
          batteryLabel = uiState.batteryLabel,
          onConfirm = { viewModel.confirmSosBroadcast() },
          onDismiss = { viewModel.dismissSosConfirmDialog() },
          onCallEmergencyServices = dialEmergencyServices
        )
      }

      if (uiState.showEditProfileDialog) {
        EditProfileDialog(
          profile = uiState.userProfile,
          onDismiss = { viewModel.closeEditProfileDialog() },
          onSave = { viewModel.updateUserProfile(it) }
        )
      }

      if (uiState.showSituationReportDialog) {
        SituationReportDialog(
          reporterName = uiState.userProfile.fullName,
          locationLabel = uiState.sosLocationLabel,
          batteryLabel = uiState.batteryLabel,
          isSubmitting = uiState.isSubmittingReport,
          onDismiss = { viewModel.closeSituationReportDialog() },
          onSubmit = { message, photoUri -> viewModel.submitSituationReport(message, photoUri) }
        )
      }

      if (uiState.showSosBroadcastDialog) {
        SosBroadcastDialog(
          locationLabel = uiState.sosLocationLabel,
          batteryLabel = uiState.batteryLabel,
          medicalTagLabel = uiState.userProfile.medicalTag,
          relaysLabel = uiState.priorityRelaysLabel,
          onDismiss = { viewModel.dismissSosDialog() },
          onCancelSos = { viewModel.cancelSosBroadcast() },
          onCallEmergencyServices = dialEmergencyServices
        )
      }

      if (uiState.showInteractiveBagDialog) {
        InteractiveBagDialog(
          items = uiState.goBagItems,
          onToggleItem = { viewModel.toggleGoBagItem(it) },
          onDismiss = { viewModel.closeInteractiveBagDialog() }
        )
      }

      // REAL disaster-event detail (tapped USGS/FIRMS/IMD/user marker).
      uiState.disasterEventDetail?.let { event ->
        DisasterEventDetailDialog(
          event = event,
          onDismiss = viewModel::closeDisasterEventDetail
        )
      }

      // Citizen incident reporting (USER_REPORT / REPORTED / unverified).
      if (uiState.showIncidentReportDialog) {
        IncidentReportDialog(
          locationLabel = uiState.sosLocationLabel,
          isGpsAvailable = !uiState.isUserLocationFallback,
          onDismiss = viewModel::closeIncidentReportDialog,
          onSubmit = { category, severityLabel, description ->
            viewModel.submitIncidentReport(category, severityLabel, description)
          }
        )
      }

      uiState.hazardDetailZone?.let { hazard ->
        // Disaster-aware detail flow: tapped zone -> linked backend event
        // (live provider / citizen report; null for mock-network zones) +
        // live viable shelters -> per-type mapped detail for the popup.
        val sourceEvent = ZoneDetailMapper.findSourceEvent(
          zone = hazard,
          providerEvents = uiState.disasterEvents,
          reportEvents = uiState.userIncidentReports.map { it.toDisasterEvent() }
        )
        HazardZoneDetailDialog(
          zone = hazard,
          detail = ZoneDetailMapper.map(
            zone = hazard,
            event = sourceEvent,
            feasibleSafeZones = uiState.rankedShelters.map { it.zone }
          ),
          onDismiss = { viewModel.closeHazardDetail() }
        )
      }

      uiState.safeZoneDetail?.let { shelter ->
        SafeZoneDetailDialog(
          zone = shelter,
          evaluation = uiState.selectedEvaluation?.takeIf { it.zone.id == shelter.id },
          onDismiss = { viewModel.closeSafeZoneDetail() },
          onSelectAndRoute = { viewModel.selectSafeZone(shelter) },
          // Carrying-capacity verdict for THIS site (null when not assessed).
          capacityAssessment = uiState.capacityAssessments[shelter.id]
        )
      }
      if (uiState.showAddContactDialog) {
        AddContactDialog(
          onDismiss = { viewModel.closeAddContactDialog() },
          onAddContact = { name, rel, phone, loc ->
            viewModel.addContact(name, rel, phone, loc)
          }
        )
      }
    }
  }
}

/**
 * Always-visible device-tool status with a real Stop action.
 *
 * This replaces the old snackbar-only feedback for Light/Siren, which left a
 * running siren (60 s) or torch (indefinite) with no way to switch it off from
 * any tab other than Instructions — the "popup that cannot be dismissed" report.
 * It is compact, non-blocking and disappears the moment nothing is active.
 */
@Composable
internal fun ActiveToolsBar(
  uiState: com.example.viewmodel.VippattiUiState,
  onStopDeviceTools: () -> Unit,
  onCancelSos: () -> Unit
) {
  val sirenOn = uiState.isSirenOn
  val torchOn = uiState.isFlashlightOn
  val sosActive = uiState.isSosActive
  if (!sirenOn && !torchOn && !sosActive) return

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(EmergencyRed.copy(alpha = 0.14f))
      .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    if (sirenOn) {
      ToolStatusRow(
        label = "SOS SIREN ACTIVE",
        detail = "Auto-stops in ${uiState.sirenSecondsLeft}s",
        onStop = onStopDeviceTools,
        testTag = "active_tool_siren_stop"
      )
    }
    if (torchOn) {
      ToolStatusRow(
        label = "FLASHLIGHT ON",
        detail = "Torch is running — tap STOP to switch it off",
        onStop = onStopDeviceTools,
        testTag = "active_tool_torch_stop"
      )
    }
    if (sosActive) {
      ToolStatusRow(
        label = "LOCAL SOS RECORD ACTIVE",
        detail = "Not transmitted — no authority has been notified",
        onStop = onCancelSos,
        testTag = "active_tool_sos_stop"
      )
    }
  }
}

@Composable
private fun ToolStatusRow(
  label: String,
  detail: String,
  onStop: () -> Unit,
  testTag: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.padding(end = 8.dp)) {
      Text(
        text = label,
        color = EmergencyRed,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
        fontSize = 12.sp
      )
      Text(
        text = detail,
        color = TacticalOnSurface,
        fontSize = 12.sp
      )
    }
    TextButton(onClick = onStop, modifier = Modifier.testTag(testTag)) {
      Text("STOP", color = EmergencyRed, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
    }
  }
}

