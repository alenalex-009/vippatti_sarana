# Vippatti Sarana — Project State & Codebase Map

This document describes the entire Android application **as it currently exists in
the working tree** (not from session memory). It covers module layout, runtime
architecture, the data pipelines, the file-by-file inventory, the cleanup pass
performed on the codebase, and the current verification results.

Versions: Kotlin 2.2.10, AGP 9.1.1, Compose BOM 2024.09.00, minSdk 24 / targetSdk 36,
package `com.example`, applicationId `com.aistudio.vippattisarana.gaqgel`.

---

## 1. Build & test

```powershell
# Compile
.\gradlew.bat :app:compileDebugKotlin --no-daemon

# Full JVM unit-test suite (Robolectric + Roborazzi + plain JVM)
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

- Real API keys (`GNEWS_API_KEY`, `FIRMS_MAP_KEY`) live in `app/.env`
  (git-ignored); `app/.env.example` is the committed placeholder fallback.
- `googleServices` uses `MissingGoogleServicesStrategy.WARN`: a missing
  `google-services.json` degrades to warnings, not a failed build. The app
  only depends on `firebase-config` (remote config); it tolerates a missing
  FirebaseApp at runtime.
- Test suites run offline: news/fire/OSRM callers are either mocked or
  exercised through injectable fakes.

---

## 2. Runtime architecture

Single-activity Compose app. `MainActivity` provides a **local, offline auth
gate** (`LoginScreen`) before any relief-network UI; after sign-in it hosts
`VippattiAppRoot`, a `Scaffold` with a bottom-nav (News/Dispatches, Radar Map,
Instructions, Profile) and a collection of modal Dialogs.

```
MainActivity
 ├─ VippattiTheme(darkTheme = uiState.isDarkTheme)
 │   ├─ LoginScreen   (when signed out — local AuthRepository)
 │   └─ VippattiAppRoot
 │       ├─ VippattiBottomNavBar (4 tabs)
 │       ├─ Crossfade{ News/Radar/Instructions/Profile }
 │       ├─ Dialogs: SOS confirm/broadcast, edit profile, situation report,
 │       │            interactive go-bag, incident report, add contact,
 │       │            hazard/safe-zone/event detail
 │       └─ Effects: battery receiver, flashlight (CameraManager),
 │                   SOS siren (ToneGenerator), TTS bulletin, tile-size calc
```

State: a single `VippattiUiState` data class in `viewmodel/VippattiUiState.kt` (split
out of the ViewModel), updated via `MutableStateFlow`; all screens read it and call VM
methods. Theme colors are theme-aware Compose getters resolved from one palette
(`Color.kt`).

Honesty rule (enforced across the codebase): simulated data is always labeled
SIMULATED/CACHED; the map is empty whenever the mock toggle is OFF; live
provider status is surfaced per source.

---

## 3. Data pipelines

### Disaster events (live + simulated)
`DisasterDataRepository.refresh()` fans out to three live providers:
- `UsgsEarthquakeProvider` — USGS earthquake feed (public, keyless).
- `FirmsFireProvider` — NASA FIRMS active fires (needs `FIRMS_MAP_KEY`).
- `ImdCapProvider` — IMD CAP alerts (keyless endpoint; per-provider parsing).

Every event is validated, deduplicated by `dedupeKey`, dropped if expired,
written to `DisasterFileCache`, and surfaced as normalized `DisasterEvent`s →
`DisasterEventNormalizer` converts them to `HazardZone`s consumed by the risk
and routing engines. Offline, `loadCachedOnly()` serves the last shards.
`FLOOD_LAYER`/`FloodRasterLayers` were removed this pass (the satellite flood
toggle had no wired renderer — see §6).

### Citizen reports
`IncidentReporting` normalizes user-submitted reports into
`DisasterEvent(USER_REPORT)` records rendered on the map and fed to risk.

### News (real GNews)
`NewsRepository` → `GNewsService` (OkHttp) with `GNewsJsonParser`
(org.json). Category queries from `NewsQueryFactory`, content filtering in
`NewsFilter`, offline `NewsFileCache`, articles mapped to a UI model via
`NewsPresentation` (hero, headline list, live/cached label). The spoken
bulletin is composed from real state in `NewsTtsBulletin`.

### Routing (OSRM)
`OsrmRoutingService` fetches live OSRM routes (driving/walking/foot), falls
back to an offline Haversine/waypoint corridor when live routing is
unavailable, and applies hazard-avoidance penalties (`HazardRoutingPolicy`).
Results are cached in the `@Synchronized LiveRouteCache`.

### Risk & shelter intelligence
- `HazardAnalysisService` — hazards affecting a point + nearest hazard.
- `RiskAssessmentEngine.assess()` — RED/ORANGE/YELLOW/GREEN level + explanation.
- `ActionAdvisor` — plain-language "what to do now" guidance.
- `RelocationPlanner` — recommends a target safe zone by safety + distance.
- `SafeZoneEvaluator` — walkability/distance/feasibility per shelter.
- `ShelterCapacityService` — per-shelter capacity report + overflow recommendation + projected status.

### Weather
`OpenMeteoWeatherService` — real Open-Meteo readings for the current location
(labeled live once a reading lands).

### Report filing
`NdrfEmergencyReportService` (implements `EmergencyReportService`) mocks an
NDRF relay channel and returns an honest receipt; `SituationReportDialog`/
SOS confirmation file reports through it.

### Mock network
`PilotRegionData` — 14 hazard zones + 14 paired safe zones across 13 Indian
states, deliberately small and widely separated, coordinates bound inside
`IndiaGeo`. Data-class marks them SIMULATED; `IndiaMockNetworkTest`
verifies `allCoordinatesInIndia()` and `allZonesSeparatedAndSafe()`.

### Configuration
`RemoteConfigManager` wraps Firebase Remote Config (defaults + realtime
updates) but **degrades to local defaults when FirebaseApp is absent**
(unit tests, local builds). `ConfigRegistry` holds the singleton. Live fields:
`homePadding`, `primaryColorHex`, `secondaryColorHex`, `emergencyBannerText`,
`emergencyBannerEnabled`, `featureRadarEnabled`, `featureDispatchesEnabled`,
`appLogoUrl`.

### Auth (local, offline)
`AuthRepository` (backed by `SharedPrefsAuthStorage`) seeds a demo account
(`demo@vippatti.in` / `vippatti123`), stores salted SHA-256 hashes, honors a
"stay signed in" flag, and supports register/login/logout. `LoginScreen` is the
gate; `ProfileScreen` shows the account email and a sign-out button.

---

## 4. File-by-file inventory

Current as of the full re-organisation (package mirror, giant-file splits).
`git status` is authoritative; line counts are approximate.

### `app/src/main/java/com/example/` — root & config

| File | Lines | Purpose |
|---|---|---|
| `MainActivity.kt` | 478 | Activity, auth gate, `VippattiAppRoot` scaffold, battery/flashlight/siren/TTS effects, all dialogs + tab dispatch |
| `config/RemoteConfigManager.kt` | 102 | Firebase Remote Config wrapper + local fallback |

### `viewmodel/` — state origination

| File | Lines | Purpose |
|---|---|---|
| `VippattiViewModel.kt` | 1114 | Single source of truth for actions; disaster/news/routing/risk orchestration |
| `VippattiUiState.kt` | 269 | `VippattiUiState` data class + `ScreenTab` enum (split out of the VM) |
| `AuthGateViewModel.kt` | 39 | Activity-scoped holder for the login gate's `AuthRepository`: survives rotation, dies with the process (§10) |

### `ui/screens/` — tab screens (giant files split)

| File | Lines | Purpose |
|---|---|---|
| `DispatchesScreen.kt` | 823 | News + dispatch feeds tab (host) |
| `FeedDispatchCard.kt` | 241 | Dispatch feed card UI (extracted from DispatchesScreen) |
| `RadarMapScreen.kt` | 342 | Radar map tab host: overlays, layers, route strip, safe zones |
| `RadarBottomSheets.kt` | 147 | Bottom-sheet UI (expanded/collapsed radar panels) |
| `SafeZoneCards.kt` | 397 | Safe-zone carousel cards |
| `WeatherAndLegend.kt` | 350 | Weather row + disaster legend/layer rows |
| `RiskPanels.kt` | 203 | Personal risk + recommended-action panels |
| `RoutePanels.kt` | 372 | Route intelligence panel + evacuation CTA |
| `EmergencyHuds.kt` | 211 | Live navigation + evacuation HUD overlays |
| `InstructionsScreen.kt` | 321 | Preparedness tab host + routes |
| `InstructionsHome.kt` | 586 | Overview/emergency grid home |
| `InstructionsGroups.kt` | 123 | Topic group listing |
| `InstructionsDetailScreens.kt` | 395 | Module/contacts/kit detail screens |
| `ProfileScreen.kt` | 1181 | Profile/net tab: account + sign-out, theme, SOS, go-bag, contacts, shelter needs, offline pack (S2/S4/S6/S7b extracted to internal composables in-file) |
| `LoginScreen.kt` | 307 | Email/password login & register gate (offline) |

### `ui/components/` — dialogs & map widgets (DisasterDialogs.kt split)

| File | Lines | Purpose |
|---|---|---|
| `OsmDroidRadarMapView.kt` | 1002 | osmdroid map wrapper: layers, markers, overlays, camera, single-teardown lifecycle, GPS locate state machine |
| `PulsingZoneOverlay.kt` | 122 | osmdroid pulsing-zone overlay (group-aware) |
| `BottomNavBar.kt` | 173 | Bottom navigation with remote-config-driven tabs |
| `SosDialogs.kt` | 305 | SOS confirm/broadcast dialogs |
| `GoBagContactDialogs.kt` | 326 | Interactive go-bag + add-contact dialogs |
| `HazardEventDetailDialogs.kt` | 575 | Hazard/event detail + incident report dialogs |
| `SafeZoneDetailDialogs.kt` | 244 | Safe-zone detail dialogs |
| `SituationReportDialog.kt` | 342 | Situation report with optional photo |
| `EditProfileDialog.kt` | 390 | Profile editor |

### `ui/theme/`

| File | Lines | Purpose |
|---|---|---|
| `Theme.kt` | 101 | `VippattiTheme`, palette wiring, remote-config accent colors |
| `Color.kt` | 182 | Semantic color tokens (dark+light) + theme-aware getters |
| `Type.kt` | 36 | Typography |

### `data/model/`

| File | Lines | Purpose |
|---|---|---|
| `UserProfile.kt` | 27 | Editable citizen profile model |
| `GeoMath.kt` | 88 | Pure geodesic distance/bearing/offset helpers |
| `GeoModels.kt` | 43 | `DataClassification`, `DataProvenance` |
| `HazardModels.kt` | 114 | `HazardZone`/`HazardSeverity`/`HazardTrend`/`HazardType` |

### `data/disaster/` (+ `providers/`)

| File | Lines | Purpose |
|---|---|---|
| `DisasterModels.kt` | 350 | `DisasterEvent`, `EventGeometry`, `IndiaGeo`, providers typing |
| `DisasterData.kt` | 117 | Mock-digitized India disaster screen + `EmergencyContact`/`GoBagItem`/`WeatherMetrics`/`MockDisasterRepository` |
| `PilotRegionData.kt` | 593 | 14 danger zones + 14 safe zones (simulated India network) |
| `DisasterDataRepository.kt` | 183 | Provider fan-out pipeline, cache fallback, normalization |
| `DisasterDataProvider.kt` | 30 | Provider interface + classification of live sources |
| `DisasterCache.kt` | 352 | JSON shard cache with atomic tmp+rename writes |
| `IncidentReporting.kt` | 90 | Citizen report → `DisasterEvent(USER_REPORT)` |
| `MapLayers.kt` | 69 | `DisasterLayer` registry + `MarkerGeneralizer` clustering |
| `ZoneDetailMapper.kt` | 255 | Zone → linked live event + per-type detail mapping |
| `providers/UsgsEarthquakeProvider.kt` | 160 | USGS feed fetch/parse |
| `providers/FirmsFireProvider.kt` | 200 | NASA FIRMS feed fetch/parse |
| `providers/ImdCapProvider.kt` | 273 | IMD CAP alerts fetch/parse |

### `data/risk/` `data/routing/` `data/shelters/` `data/news/` `data/weather/` `data/reports/` `data/auth/` `data/instructions/`

| File | Lines | Purpose |
|---|---|---|
| `risk/HazardAnalysisService.kt` | 55 | Affecting/nearest hazard analysis (moved from `data/hazards`) |
| `risk/RiskAssessmentEngine.kt` | 92 | Personal risk level + explanation |
| `risk/ActionAdvisor.kt` | 104 | Actionable guidance text |
| `risk/RelocationPlanner.kt` | 127 | Target safe-zone recommendation |
| `routing/OsrmRoutingService.kt` | 550 | Live OSRM routing + offline fallback + hazard penalties |
| `routing/LiveRouteCache.kt` | 51 | Thread-safe route cache |
| `shelters/SafeZoneEvaluator.kt` | 191 | Shelter feasibility/walkability/distance |
| `shelters/ShelterCapacityService.kt` | 90 | Capacity reports, overflow, projected status |
| `news/NewsRepository.kt` | 129 | News orchestration + cache |
| `news/GNewsService.kt` | 118 | GNews HTTP client |
| `news/GNewsJsonParser.kt` | 124 | org.json parser for GNews responses |
| `news/NewsCache.kt` | 145 | File cache with atomic writes |
| `news/NewsModels.kt` | 91 | `NewsArticle`/`NewsFeed` models |
| `news/NewsPresentation.kt` | 112 | UI presentation mapping |
| `news/NewsFilter.kt` | 49 | Relevance/profile filtering |
| `news/NewsQueryFactory.kt` | 36 | Category → GNews query builder |
| `news/NewsTtsBulletin.kt` | 50 | Bulletin text composition |
| `weather/OpenMeteoWeatherService.kt` | 99 | Open-Meteo weather |
| `reports/EmergencyReportService.kt` | 49 | Report interface + kinds |
| `reports/NdrfEmergencyReportService.kt` | 57 | NDRF relay mock |
| `auth/AuthRepository.kt` | 197 | Offline auth: storage, hashing, repository, demo seed |
| `instructions/DisasterInstructions.kt` | 383 | Preparedness/response content |

### `app/src/test/java/com/example/` (mirrors main packages)

| File | Tests | Coverage |
|---|---|---|
| `ExampleRobolectricTest.kt` | 1 | resource smoke (app_name + seed accent) |
| `GreetingScreenshotTest.kt` | 1 | Roborazzi screenshot smoke |
| `data/auth/AuthRepositoryTest.kt` | 10 | login/register/logout, demo seed, stay-signed-in, errors |
| `data/disaster/DisasterCacheRoundTripTest.kt` | 10 | JSON cache write/read for all geometries |
| `data/disaster/IndiaMockNetworkTest.kt` | 9 | mock geography invariants |
| `data/disaster/ZoneDetailMapperTest.kt` | 14 | zone→event detail mapping |
| `data/news/NewsPipelineUnitTest.kt` | 19 | GNews parse/filter/presentation |
| `data/reports/NdrfEmergencyReportServiceTest.kt` | 3 | NDRF report mapping |
| `data/risk/DecisionEnginesUnitTest.kt` | 11 | risk engine + relocation + action advisor |
| `data/routing/LiveRouteCacheTest.kt` | 6 | route-cache semantics |
| `data/routing/OsrmRequestTest.kt` | 6 | OSRM URL building + offline metrics |
| `data/routing/RoutingAndShelterEvaluationTest.kt` | 7 | shelter evaluation + routing penalties |
| `ui/components/MapRotationLifecycleTest.kt` | 3 | map teardown idempotency, GPS listener dereg, no configChanges workaround |
| `ui/components/MapGpsAndRouteTest.kt` | 14 | GPS locate states/timeout/no-fallback-jump, route-tap bubble absence, green route (§10) |
| `viewmodel/AuthGateViewModelTest.kt` | 6 | rotation keeps no-stay session, logout + process-death rules (§10) |
| `ui/screens/ResponsiveLayoutSmokeTest.kt` | 16 | full-screen render sizes via Robolectric |
| `viewmodel/LiveDataGuardsTest.kt` | 9 | state guards / tool toggles |

### Resources & config
- `res/xml/backup_rules.xml`, `data_extraction_rules.xml` — exclude the
  `vippatti_sarna_auth` SharedPreferences from backups.
- `res/values/strings.xml` — app name; `themes.xml` — launcher activity theme.
- `app/build.gradle.kts` — dependency declarations (see §5).
- `gradle/libs.versions.toml` — version catalog (see §5).
- `app/.env.example` — placeholder API keys; `app/.gitignore` — excludes `.env`.

---

## 5. Dependencies actually used

Active (after the cleanup pass):
- Compose BOM + `material3`, `ui`, `ui-graphics`, `material-icons-core/extended`
- `activity-compose`, `lifecycle-{runtime-compose,runtime-ktx,viewmodel-compose}`
- `core-ktx`, `coil-compose`, `kotlinx-coroutines-{android,core}`
- `firebase-config` (remote config; via BOM)
- `okhttp` 4.10.0 (GNews/OSRM/USGS/FIRMS/IMD/Open-Meteo transport)
- `osmdroid-android` 6.1.20 (map tiles)
- `androidx.preference:preference-ktx` (osmdroid tile prefs)
- Test: `junit`, `androidx.test.ext-junit`, `androidx.test:core`, `roblectric`,
  `roborazzi` (+compose/junit-rule), `androidx.compose.ui:ui-test-junit4`,
  `kotlinx-coroutines-test`, `org.json` (JVM tests)
- AndroidTest: `compose bom`, `ui-test-junit4`, `test-ext-junit`, `test-runner`

**Removed this pass** (zero references found in `app/src/main`): retrofit,
converter-moshi, moshi-kotlin, moshi-kotlin-codegen (ksp), logging-interceptor,
firebase-ai, firebase-storage, firebase-appcheck-recaptcha/debug,
androidx.room (runtime/ktx/compiler-ksp), appcompat, cardview,
com.google.android.material, appcompat material widgets, espresso-core
(androidTest). Corresponding orphaned catalog entries/versions deleted.

**Commented-but-integration-ready** (documented, not active — kept as
reference): accompanist-permissions, androidx.camera.*, DataStore
preferences, navigation-compose, play-services-location, Firestore,
Firebase Auth + Credential Manager + googleid.

Known note: `okhttp` remains at 4.10.0 (CVE-2023-3635 fixed in 4.12.x).
Hazard-avoidance HTTP during routing uses HTTPS everywhere; `android:usesCleartextTraffic`
was removed because no code uses `http://` URLs.

---

## 6. Cleanup pass performed

Dead code removed (each symbol had zero callers in `app/src/main` and
`app/src/test`):
- GeoMath.pointInPolygon, OsrmRoutingService.haversineDistanceMeters wrapper.
- HazardAnalysisService.maxSeverityWeight/hazardsOfType/isInsideFloodZone/distinctHazardTypeCount.
- ShelterCapacityService.reportAll/networkSummary/NetworkCapacitySummary/loadBalancedOrder.
- DisasterDataRepository.inBounds/withinRadius extensions + ProviderState.freshnessLabel.
- IndiaGeo.isPointInIndia/isWithinPilotCoverage + OVERVIEW_ZOOM + PILOT_* constants.
- GeoModels.MapLayerId enum.
- PilotRegionData.REGION_NAME/REGION_COUNTRY/provenance.
- `FloodRasterLayers.kt` (entire unused NASA GIBS object) + `DisasterLayer.FLOOD_LAYER` entry —
  the satellite-flood toggle had no wired renderer, so the dormant layer was
  removed rather than keeping a dead switch. (GIBS endpoint is documented in
  git history if a real flood-extent overlay is wanted again.)
- RemoteConfigManager.splashLogoUrl/featureReportsEnabled/featureSafeZonesEnabled (never read).
- VippattiViewModel.isSosActive/lastReportReceipt/newsLastFetchedAtMillis (written, never read).
- RiskAssessmentEngine dead `primary?.name` expression in the always-null branch.
- RadarMapScreen duplicate border branch (two identical default colors).
- Color.kt unused tokens: ObsidianBg, EmergencyRedLightBg, EmergencyRedDeep,
  WarningAmberContainer, WarningAmberText (getters + 4 scheme fields; kept the
  `obsidianBg` scheme field because Theme.kt reads it).
- Deleted `res/values/colors.xml` (template colors, zero references).
- Deleted `ExampleUnitTest.kt` (2+2==4 boilerplate).

Kept after verification (audit-flagged but genuinely live):
- `isDisasterSyncing` — read as a reentrancy guard in the VM.
- `isMockMode` — read to gate live-vs-mock report routing.
- `RiskAssessmentEngine.assess()` — used by VM + DecisionEnginesUnitTest.
- `NearestHazard`, `affectingHazards`, `projectedStatus`, `report()` etc. — used.
- `EventGeometry.RasterLayer` — still used by cache + UI for raster events.

Wired/re-enabled this pass:
- Auth gate: `MainActivity` now shows `LoginScreen` when signed out; seed demo
  account on first run; `ProfileScreen` gained `accountEmail` + sign-out.
- AndroidManifest: the temporary `android:configChanges` rotation-stability
  workaround added earlier was REMOVED (§9 replaces it with a real fix);
  unused `VIBRATE` and `WRITE/READ_EXTERNAL_STORAGE` permissions removed;
  `usesCleartextTraffic="true"` removed.

Manifest permissions kept (verified used): INTERNET, ACCESS_NETWORK_STATE,
ACCESS_WIFI_STATE, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CAMERA
(flashlight), MODIFY_AUDIO_SETTINGS (SOS siren).

---

## 7. Verification results

Current working tree (post-cleanup, post-wiring, post-rotation-fix, post-field-fixes):
- `:app:compileDebugKotlin` + `:app:compileDebugUnitTestKotlin` — BUILD SUCCESSFUL
  (no warnings beyond pre-existing Material-icon deprecation hints).
- `:app:testDebugUnitTest` (fresh run, results regenerated) — **17 suites /
  145 tests / 0 failures / 0 errors / 0 skipped**:
  AuthGateViewModel 6, AuthRepository 10, DecisionEngines 11,
  DisasterCacheRoundTrip 10, ExampleRobolectric 1, GreetingScreenshot 1,
  IndiaMockNetwork 9, LiveDataGuards 9, LiveRouteCache 6, MapGpsAndRoute 14,
  MapRotationLifecycle 3, NdrfReport 3, NewsPipeline 19, OsrmRequest 6,
  ResponsiveLayout 16, RoutingAndShelter 7, ZoneDetailMapper 14.
- `:app:assembleDebug` — BUILD SUCCESSFUL.

Git status vs HEAD (`712b606 "updated afternoon"`): 10 modified files (9 from
the earlier fixes + `AndroidManifest.xml`), deleted `FloodRasterLayers.kt`,
`res/values/colors.xml`, `ExampleUnitTest.kt`, and the still-untracked
`data/auth/`, `LoginScreen.kt`, `AuthRepositoryTest.kt` (auth remains
uncommitted — commit it before losing it).

---

## 8. Rotation / tab-switch crash fix (Sep 2026)

### Root cause

The osmdroid `MapView` inside `AndroidView` was torn down from
`DisposableEffect.onDispose` — which fires while the view is **still attached
to the window** — and `setDestroyMode` was left at its default (`true`). This
caused two problems:

1. **Double teardown.** Compose's `onDispose` ran `cleanup() → onDetach()`
   while the view was still attached. When the view subsequently left the
   window, `onDetachedFromWindow()` auto-called `onDetach()` a second time
   (`mDestroyModeOnDetach` defaults to `true`). osmdroid 6.1.20's
   `onDetach()` tears down overlays, the tile provider's internal state, the
   projection and the repository — a second pass against an already-destroyed
   map, and any overlay/pulse callback still running between the two detaches
   operated against the destroyed machinery. (The draw path itself is guarded
   by a try/catch, but the intermediate state is fatal for location overlay
   callbacks and can NPE on `MapViewRepository` / `MarkerInfoWindow` if any
   overlay construction races the detach.)

2. **Leaked location listener.** `cleanup()` never called
   `disableMyLocation()`. The `GpsMyLocationProvider`'s `LocationListener`
   stayed registered on the `LocationManager` across Activity recreation. On
   each rotation a new listener was added while old ones lingered, causing
   duplicate callbacks into dead or inconsistent map views.

3. **Workaround band-aid.** An uncommitted `android:configChanges` attribute
   was added to `MainActivity` to suppress Activity recreation entirely —
   avoiding the crash but disabling the real rotation lifecycle (violates the
   "don't suppress orientation changes" requirement).

### Fix (3 files)

**`AndroidManifest.xml`** — removed the uncommitted
`android:configChanges="orientation|screenSize|smallestScreenSize|keyboard|keyboardHidden"`
attribute, restoring normal Activity recreation on rotation.

**`OsmDroidRadarMapView.kt`** (705 lines) — the lifecycle rework:
- `mapView.setDestroyMode(false)` in `initMapView` so osmdroid's
  `onDetachedFromWindow()` does NOT also call `onDetach()` — the app owns
  exactly one teardown.
- Teardown moved from `DisposableEffect.onDispose` to `AndroidView.onRelease`,
  which fires after the view is actually removed from the composition window.
- `cleanup()` rewritten: first calls `disableMyLocation()` to unregister the
  GPS `LocationListener` from the `LocationManager` (preventing leaked/duplicate
  callbacks across recreations), then clears overlay lists, calls
  `onDetach()` exactly once, and nulls all references. The method is now
  **idempotent**: a second call (e.g. both `onRelease` and a lifecycle
  edge-case) is a safe no-op.
- `enableLocationTracking()` now returns early if `mapView == null`, making
  post-release recomposition a no-op.
- The `DisposableEffect(lifecycleOwner)` retains only the lifecycle observer
  (ON_RESUME → `onResume()`, ON_PAUSE → `onPause()`, removeObserver on
  dispose); cleanup is no longer called from it.

**`app/src/test/.../MapRotationLifecycleTest.kt`** — 3 new regression tests:
- `map engine survives repeated init-cleanup rotation cycles`: init →
  interact → cleanup → second cleanup → re-init × 3 — no crash (guards the
  double-teardown and idempotency).
- `cleanup removes the gps location listener from the location manager`: uses
  `ShadowLocationManager` with GPS enabled; asserts listener is registered
  after init and fully removed after cleanup (guards the leaked-listener
  duplicate-callback path).
- `main activity does not suppress orientation-change recreation`: reads the
  merged manifest's `ActivityInfo.configChanges` and asserts none of the five
  suppressed flags (`ORIENTATION|SCREEN_SIZE|SMALLEST_SCREEN_SIZE|KEYBOARD|
  KEYBOARD_HIDDEN`) are present — a bit-mask check, not a raw `==0` (Robolectric
  injects `ORIENTATION|SCREEN_LAYOUT` by default).

### Verified

- `:app:compileDebugKotlin` — BUILD SUCCESSFUL
- `:app:compileDebugUnitTestKotlin` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest` — **15 suites / 125 tests / 0 failures**
- `:app:assembleDebug` — BUILD SUCCESSFUL

### Remaining risks / follow-ups

- **On-device rotation validation still required**: the lifecycle properties
  are exercised under Robolectric (no real window / tile rendering); on-device
  rotation, tab switching with `Crossfade`, and background/foreground
  transitions should still be validated manually with logcat for any remaining
  osmdroid warnings.
- **First-run permission gate**: `initMapView` calls `enableMyLocation()`
  unconditionally; when permission is denied osmdroid internally catches the
  `SecurityException` (logged, not fatal). This is unchanged by this fix.
- **`PulsingZoneOverlay` pulse loops**: after `cleanup()` the overlay list is
  cleared and `mapView.onDetach()` removes all overlays from the
  `OverlayManager`; post-detach `postInvalidate()` calls are a no-op on the
  detached view. No additional guard is needed, but the overlay's
  `onDetach()` override (if any) should be reviewed if the overlay is ever
  subclassed.
- **`onDetachedFromWindow` override** is not set. If a future container
  (e.g. `ViewPager2`) re-attaches the view without recomposition, the
  `setDestroyMode(false)` strategy prevents the map being destroyed twice but
  also means the map won't self-teardown if removed from the window without
  going through Compose's `onRelease`. This is the correct trade-off for
  Compose `AndroidView`; outside Compose, use `setDestroyMode(true)`.

---

## 9. Field-issue fixes (Sep 2026): logout on rotate, GPS button, blank bubble, red route

### Issue 1 — landscape rotation logs the user out (NOT a Firebase logout)

Root cause: there is no Firebase auth in this app (local offline
`AuthRepository` by design). `isLoggedIn() = storage.isLoggedIn() &&
(storage.isStaySignedIn() || processLoggedIn)`, and `processLoggedIn` is an
instance field. The login gate built the repository with `remember {}` in
`setContent`, so every rotation created a NEW repository with
`processLoggedIn = false`. Users logged in WITHOUT "stay signed in" were
bounced to `LoginScreen` on every rotation — the UI navigating to login
after recreation, not a real logout (and not a crash; the earlier map
teardown crash was already fixed in §8).

Fix: new `viewmodel/AuthGateViewModel.kt` holds the ONE `AuthRepository`
(created with the application context, seeded once). `MainActivity` obtains
it via `viewModel(factory)` — the ViewModelStore survives configuration
changes, so rotation keeps the in-process session; process death creates a
fresh instance that re-reads the store (stay-signed-in restart rule and
explicit logout unchanged). No `configChanges` workaround, no exception
suppression. Tests: `viewmodel/AuthGateViewModelTest.kt` (6).

### Issue 2 — GPS button unresponsive / slow, silent fallback jump

Root cause (traced button → provider → VM → controller): the recenter
button called `recenterUser()`, which centers on
`locationOverlay.myLocation` or — with no fix available — silently jumped
the camera to `FALLBACK_USER_LOCATION`, presenting fallback coordinates as
the user's GPS position. No feedback, no timeout (cold GPS via
`GpsMyLocationProvider.requestLocationUpdates` can take 30s+), no
denied/permanently-denied/provider-off states.

Fix (`OsmDroidRadarMapView.kt`, osmdroid implementation kept): explicit
`GpsRequestState` machine (IDLE/REQUESTING/SUCCESS/NO_PERMISSION/
PERMANENTLY_DENIED/PROVIDER_DISABLED/UNAVAILABLE/TIMEOUT) with a status
banner + button spinner; permission tracking distinguishes first-ask from
don't-ask-again (via `shouldShowRequestPermissionRationale`); provider-off
checked via `LocationManager`; locate order is overlay fix → OS last-known
→ exactly-one one-shot listener with a 15s timeout (cancelled on
fix/timeout/cleanup, repeat presses ignored); the camera moves ONLY on a
valid device fix, which is also reported through `onRealGpsFix` so the
ViewModel replaces the fallback honestly; `recenterUser()` no longer jumps
to fallback. Tests: `MapGpsAndRouteTest.kt` GPS half (9 tests incl.
timeout via idled main looper, single-listener, cleanup cancellation).

### Issue 3 — blank speech-bubble box on tapping zones/routes

Source: osmdroid, not Compose. `Polyline extends PolyOverlayWithIW`, whose
constructor installs `MapViewRepository.getDefaultPolylineInfoWindow()` — a
`BasicInfoWindow(R.layout.bonuspack_bubble)` with empty title/snippet — on
EVERY route polyline; `onClickDefault` opens it on tap. The route sits on
top of the pulsing zone overlays, so taps on zones near/under the route
showed the blank bubble too. (No Markers/InfoWindows/Popups exist anywhere
in app code — verified by grep + `javap` on the 6.1.20 artifact.)

Fix: `displayRoute` sets a click listener returning false — no bubble can
open and taps fall through to the zone overlays (detail dialogs preserved).
Route geometry, panel and dialogs untouched. Tests: simulated tap on the
laid-out route asserts not-consumed + `isInfoWindowOpen == false`; zone tap
still fires its handler.

### Issue 4 — recommended route drawn red

Source: `displayRoute` painted `ROUTE_COLOR_DANGER` (0xFFFF1744) whenever
`routeSafetyStatus == DANGER`. In a disaster app the recommended corridor
almost always passes near hazards (`HazardRoutingPolicy` marks entering →
DANGER), so the recommended route was routinely red.

Fix: the active/recommended polyline always draws `ROUTE_COLOR` green
(0xFF00E297); live-solid vs offline-dashed distinction kept; danger
semantics stay where they belong — the route panel's ROUTE SAFETY
label/warnings/score bar and the hazard overlays. Alternatives are chips
only (never drawn on the map); the primary chip keeps its emerald
highlight. Dead `ROUTE_COLOR_DANGER` const removed; no geometry/engine
changes. Tests: DANGER/CAUTION/SAFE all green, reroute replaces (not
stacks) the polyline, dash semantics preserved.

### Physical-device validation: NOT performed

`adb` is present but `adb devices` lists no attached device, and this
environment cannot install/run the APK. The manual validation checklist
(login → rotate both ways; GPS allow/deny/off/timeout; zone + route taps;
green route + rerouting) must still be run on a physical device with
logcat. Do not treat the unit-test results above as device validation.

---

## 10. Known limitations / recommended follow-ups

- **Device validation still needed**: GPS fixes, TTS, siren, flashlight,
  rotation stability, and live OSRM/GNews/USGS/FIRMS/IMD/Open-Meteo calls can
  only be verified on a real device/emulator with `.env` keys configured.
- **OkHttp 4.10.0** → bump to 4.12.x (CVE-2023-3635). Low risk; left out of this
  pass to avoid an unexpected network fetch during the build.
- `release` build has `isMinifyEnabled = false`; a shrinker pass will need
  osmdroid/Compose keep rules.
- `google-services.json` is committed without a SHA-1 fingerprint restriction
  (dev convenience; tighten before store release).
- Remote config defaults (`RemoteConfigManager`) are minimal; seed
  `homePrimaryColor`/`homeSecondaryColor` only if Firebase console tuning is desired.

---

## 11. Phase 2 — data status & provenance (Sep 2026)

One status vocabulary now backs every data surface. It is built ON TOP of the
 existing `DataProvenance` / `DataClassification` (`GeoModels.kt`); no model was
replaced and no real data source was changed.

| Piece | File | Role |
| --- | --- | --- |
| `DataStatus`, `RecordStamp`, `statusOf`, `Freshness`, `freshnessOf` | `data/model/DataStatus.kt` | shared status enum + per-record metadata (source, retrieved/event time, coverage, error, classification) |
| `ProviderState.dataStatus()`, `.cacheNote`, `.recordStamp()` | `data/disaster/ProviderProvenance.kt` | maps the real per-provider repository state into the shared vocabulary |
| `StatusBadge`, `StampLine`, `SimulatedWarningBar`, `StaleWarningBar`, `UnavailablePanel`, `dataStatusColor` | `ui/components/Provenance.kt` | one visual vocabulary for every surface |

Honesty rules encoded in `ProviderProvenance.kt`: a provider shard is only
`SUCCESS` (LIVE) when this session fetched it and reported no failure; a cache
hit is `STALE` even inside its freshness window; a failed provider is never
`SUCCESS` and its own reason string is shown unchanged.

Integration points (existing screens, no redesign):

- Map status row (`WeatherAndLegend.kt`) adds one `StatusBadge` + source-name chip
  per reporting provider; the demo chip colour is model-derived, not ad hoc.
- Weather row (`WeatherAndLegend.kt`, `RadarBottomSheets.kt`): `isWeatherLive` is
  now derived from `weatherStatus` (`SUCCESS` only for a reading fetched this
  session, `STALE` when a refresh failed but a real earlier reading is shown,
  `UNAVAILABLE` when nothing exists), and the bottom sheet shows a real
  provenance line (source, retrieval age, "Time unknown" when never fetched).
- Dispatches banner (`DispatchesScreen.kt`): the ONLINE/OFFLINE/ERROR label and
  the pulsing dot colour come from `newsStatus` (live / cached / error / empty),
  replacing the previous `label.startsWith("ONLINE")` string sniffing.
- Hazard + safe-zone detail dialogs badge their provenance with `statusOf`.

Verification: `:app:testDebugUnitTest` → **180 tests, 0 failures, 0 skipped**
(22 suites), including 9 new Phase 2 provenance contracts;
`:app:compileDebugKotlin` and `assembleDebug` → BUILD SUCCESSFUL, APK at
`app/build/outputs/apk/debug/app-debug.apk`.

Remaining limitations: `SimulatedWarningBar` / `StaleWarningBar` are implemented
but not yet placed on a screen (`UnavailablePanel` is wired to the weather line);
no new external API was added, EM-DAT and the authority dashboard are untouched.

---

## 12. Phase 3 — live weather integration (Sep 2026)

Priority integration: the existing Open-Meteo provider (keyless, no signup).
Nothing about the endpoint changed; the failure and time handling did.

| Before | Now |
| --- | --- |
| `fetchNow()` returned `WeatherMetrics?`; offline, timeout, HTTP error, bad payload and unexpected exceptions all collapsed to `null` | `fetchReading()` returns `WeatherReading.Success` / `WeatherReading.Failure(kind, detail)` with five honest kinds (`NO_CONNECTION`, `TIMEOUT`, `HTTP_ERROR`, `BAD_PAYLOAD`, `REQUEST_FAILED`) |
| the provider's `current.time` was parsed away | `WeatherMetrics.observedAtMillis` carries the real observation time (`current.time` + `utc_offset_seconds`), 0 = unknown |
| UI could only say "no feed" | `weatherErrorMessage` carries the provider/exception detail; `UnavailablePanel` shows it with a working **Retry** |

Status mapping (`data/weather/WeatherReading.kt`, Phase 2 vocabulary):
NO_CONNECTION/TIMEOUT -> `UNAVAILABLE`, other kinds -> `ERROR`, and any failure
with a real earlier reading -> `STALE`. No failure can ever map to `SUCCESS`,
and a failed refresh never overwrites the values already on screen.

Request/transport: `buildUrl()` is a pure, tested function (metric units,
`timezone=auto`, no key in the URL); the client keeps 8 s connect / 12 s read
timeouts and adds a 20 s call timeout; the call runs on `Dispatchers.IO`.

UI: the weather row tag and the provenance line (source, fetched age,
observation age, error) plus the retry panel live in the radar bottom sheet;
retry calls `refreshWeather(force = true)`.

Verification: **187 tests, 0 failures, 0 skipped** (23 suites, +7 Phase 3
contracts); `compileDebugKotlin` and `assembleDebug` successful.

Live check: the exact request URL was fetched on 2026-09-20 and returned
HTTP 200 with `utc_offset_seconds: 19800`, `current.time: "2026-09-20T08:00"`,
`temperature_2m`, `precipitation`, `wind_speed_10m` and a 48-hour
`hourly.temperature_2m` series - the same fields the parser reads. The parsing
path was therefore validated against a real response, but the app's own HTTP
call was not exercised end to end from a device (no emulator/device here).

---

## 13. Dynamic-data rule compliance (Sep 2026)

Permanent rule: dynamic, data-driven, India-wide. No district/state may be
compiled in; nothing is named that was not resolved.

**Audit result.**

| Item | Verdict |
| --- | --- |
| Weather | already dynamic (coordinates in, provider values out) |
| USGS / FIRMS / IMD queries | India-wide bounding box - provider requirement, labelled |
| `IndiaGeo.CENTER_LAT/LON` | fallback map centre only, shown as "INDIA FALLBACK" |
| `PilotRegionData` | multi-region SIMULATED demo network, hidden unless the SIMULATED DEMO toggle is ON, all records carry SIMULATED provenance |
| `NewsQueryFactory.MY_AREA_QUERY` / `MY_STATE_QUERY` | **violation, fixed** - "Idukki"/"Kerala" were compiled into the search queries |
| `NewsScope.label` | **violation, fixed** - enum carried "Idukki District"/"Kerala" |
| Dispatches empty-state copy, Kerala-only instruction lines | **violation, fixed** |

**Fix: runtime place resolution** (`data/location/PlaceResolver.kt`).
`AndroidGeocoderPlaceResolver` reverse-geocodes the user's own coordinates
(async listener on Android 13+, sync below, always off the main thread) into a
`ResolvedPlace(district, state, country, source)`. It returns `null` when the
platform cannot resolve (offline / no geocoder) - never a guessed district.

- `NewsQueryFactory.buildQueries(place)` builds district -> state -> national
  rings from those names, sanitizes them (`"` stripped, whitespace collapsed),
  skips a state ring identical to the district, and ALWAYS includes the
  national ring. No place -> national ring only.
- `GNewsService.search(scope, query, key)` now takes the query string, so the
  client holds no geography at all.
- `NewsRepository.refresh(queries)` / `ensureLoaded(queries)` default to the
  national ring, so a caller that resolved nothing cannot query a province it
  assumed.
- `VippattiViewModel` resolves the place on the first fix and after 2 km of
  movement (6 s timeout), stores it in `resolvedPlace`, and re-scopes the feed
  only when the resolved names actually change (cache-first, so no extra call).
- `NewsScope.ringLabel(place)` and `VippattiUiState.newsScopeNote` state the
  real scope, or "India-wide - district/state not resolved from location".
- Kerala-only instruction lines now carry the existing `region` field, which
  the Instructions detail screen renders as "Regional: ...".

Verification: **194 tests, 0 failures, 0 skipped** (24 suites; +7 dynamic-scope
contracts). `compileDebugKotlin` and `assembleDebug` successful, APK at
`app/build/outputs/apk/debug/app-debug.apk`. Tests that used to assert the
Idukki/Kerala constants now pass their own `ResolvedPlace` and assert the query
and label follow it.

Unverified: the Android `Geocoder` path itself (needs a device with geocoder
service); the app's GNews call was not exercised live in this pass.

---

## 14. Phase 3 - official-alert geometry honesty (Sep 2026)

Found during the Phase 3 scope inspection: `CapAlertParser` pinned every IMD CAP
alert that has no `<polygon>` to `IndiaGeo.CENTER_LAT/LON`. That is a fabricated
location - the alert appeared as a located hazard blob in central India - and it
contradicted the cache rule ("unlocatable shard dropped, never pinned to a fake
spot") added earlier.

Fix, small and per-file:

| File | Change |
| --- | --- |
| `DisasterModels.kt` | new `EventGeometry.Unlocated(areaLabel)` + `GeometryType.UNLOCATED`; `toHazardZone` returns null for it |
| `ImdCapProvider.kt` | polygon-less alert -> `Unlocated(areaDesc)`, `latitude`/`longitude` null |
| `DisasterCache.kt` | `unlocatedArea` field; UNLOCATED round-trips with its area text and no coordinates |
| `OsmDroidRadarMapView.kt` | no marker for unlocated records (nothing at an invented point) |
| `HazardEventDetailDialogs.kt` | Location line reads "Kerala (no polygon from source)" or "Not provided by source" |

Consequences, all honest: the alert is still fetched, cached, listed and opened
in the detail sheet with the provider's own `areaDesc`; it simply produces no map
marker and no local hazard zone, because its location is genuinely unknown.

Verification: **198 tests, 0 failures, 0 skipped** (25 suites; +4 geometry
contracts in `CapAlertGeometryHonestyTest`). `compileDebugKotlin` and
`assembleDebug` successful, APK at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 15. Phase 3 - FIRMS provider status honesty (Sep 2026)

`ProviderResult.Failure.isAuthProblem` was written by FIRMS (blank key and
HTTP 403) and **read by nobody**: the repository copied only the reason string
into `ProviderState.statusMessage`, and the Phase 2 mapper turned any non-null
message into `DataStatus.ERROR` - so a deliberately unconfigured optional
source appeared as a red error, indistinguishable from a rejected credential.

Replaced with a real classification that the UI consumes:

| Layer | Change |
| --- | --- |
| `DisasterDataProvider.kt` | `isAuthProblem` -> `kind: ProviderFailureKind` = `UNCONFIGURED` / `AUTHENTICATION_FAILED` / `FAILED` (AVAILABLE = `Success`) |
| `FirmsFireProvider.kt` | blank/placeholder MAP_KEY -> `UNCONFIGURED`; HTTP 403 -> `AUTHENTICATION_FAILED`; other HTTP/parse/IO -> `FAILED` |
| `DisasterDataRepository.kt` | `ProviderState.failureKind` carries the kind (null = AVAILABLE); cache-hit branch records none |
| `DataStatus.kt` | new `NOT_CONFIGURED("Not configured")` state |
| `ProviderProvenance.kt` | UNCONFIGURED + no cache -> `NOT_CONFIGURED`; any other failure + no cache -> `ERROR`; any failure with usable cache -> `STALE`; `cacheNote` explains "live source not configured" |
| `Provenance.kt` | neutral badge colour + panel wording for NOT_CONFIGURED |
| `VippattiUiState.kt` | aggregate label now separates `N FAILED`, `N UNAVAILABLE`, `N NOT CONFIGURED` |
| `WeatherAndLegend.kt` | provider chip shows the failure kind next to the source name |

The FIRMS failure path still carries the provider's own reason text, and no
failure can produce fabricated fire events (`Failure` has no event list).

Verification: **207 tests, 0 failures, 0 skipped** (26 suites; +9 FIRMS status
contracts in `FirmsProviderStatusTest`, plus the re-pointed provider fixtures in
`ProvenanceStatusTest`). `compileDebugKotlin` and `assembleDebug` successful.

Initially unverified (no MAP_KEY in the build). **Since 2026-09-20 a real
MAP_KEY is configured** in `app/.env` (git-ignored: `app/.gitignore:2:.env`), and
the key-accepted path was verified against the live service:

- request `https://firms.modaps.eosdis.nasa.gov/api/area/csv/<MAP_KEY>/VIIRS_SNPP_NRT/67.5,6.0,98.0,37.5/2`
  -> HTTP 200, `text/plain`, real detections inside the India bbox;
- the live header is exactly the column set `FirmsCsvParser` requires
  (`latitude,longitude,...,confidence,...,frp,daynight`);
- `BuildConfig.FIRMS_MAP_KEY` no longer holds the placeholder after a rebuild,
  and the provider is wired as `FirmsFireProvider(mapKeyProvider = { BuildConfig.FIRMS_MAP_KEY })`;
- 4 contracts in `FirmsCsvLiveSchemaTest` pin the live schema (acq_time 716 ->
  07:16 UTC, VIIRS confidence "n" -> NOMINAL, out-of-bbox rows dropped, HTML
  error pages yield no events).

Still not verified: the HTTP 403 branch (requires a deliberately bad key) and
any on-device run. The two-day FIRMS lookback sits inside the seven-day cache
age limit, so every returned detection survives validation.

---

## 16. Fire layer intensity work (Sep 2026)

With live FIRMS data arriving, two things that ignored the provider's own
measurement were fixed.

**Intensity-based clustering** (`MapLayers.kt`): `MarkerGeneralizer.clusters()`
replaces the severity-only cell picker and returns, per marker, the
representative event, `memberCount` and `maxFrpMegawatts`.

- Representative ranking is now: hazard severity, then the provider's FRP inside
  the same severity band, then recency - so a severe quake can never be hidden
  behind a weak fire, and inside a fire cell the strongest measured detection
  wins. The old code silently kept whichever tie arrived first.
- A detection with no FRP never outranks a measured one.
- Polygons/lines are still rendered directly; at zoom >= 8 nothing is clustered.
- The map draws `radiusMeters = base * FireIntensityScale.markerScale(maxFrp, memberCount)`,
  so marker size now carries real intensity (bounded by `MAX_MARKER_SCALE`).

**Fire radiative power in the detail sheet** (`HazardEventDetailDialogs.kt`):
the FRP line now reads "48.0 MW (high intensity)", with the qualifier derived
from the real MW value; a detection without FRP still reads "Not available".

**New scale** (`FireIntensity.kt`): `FireIntensity` (low/moderate/high/extreme/
unknown) with CONFIGURED, documented thresholds (5 / 20 / 100 MW - explicitly
not an official NASA or IMD scale), `severityFor(frp, confidence)` and
`markerScale(frp, members)`. `FirmsFireProvider` now derives detection severity
from FRP + the provider's confidence flag instead of hardcoding `MODERATE` for
every detection; a HIGH-confidence measured detection is escalated one step and
an unmeasured one stays conservative MODERATE.

Verification: **220 tests, 0 failures, 0 skipped** (28 suites; +9 contracts in
`FireLayerIntensityTest`). `compileDebugKotlin` and `assembleDebug` successful,
APK at `app/build/outputs/apk/debug/app-debug.apk`. The now-unused
`MarkerGeneralizer.generalize()` compat wrapper was removed rather than left as
dead code.

---

## 17. Carrying Capacity & Relocation Feasibility (SIH milestone, Sep 2026)

New package `data/capacity`. Flow: candidate site -> capacity assessment ->
feasibility result -> limiting resource -> relocation planning.

| Piece | File |
| --- | --- |
| `CapacityResource`, `ResourceDataState`, `ResourceCapacity`, `RelocationDemand`, `FeasibilityStatus`, `CapacityAssessment` | `capacity/CarryingCapacityModels.kt` |
| Multi-constraint maths, effective capacity, limiting resource, verdicts | `capacity/CarryingCapacityEngine.kt` |
| Backend wire format (JSON round-trip) | `capacity/CapacityAssessmentJson.kt` |
| Optional per-site inputs `landAreaSquareMeters` / `waterLitresPerDay` / `toiletCount` | `model/HazardModels.kt` (SafeZone) |
| Feasibility-aware assignment + `capacityAssessment` / `feasibilityNote` | `risk/RelocationPlanner.kt` |
| Assessment per candidate + demand in state | `viewmodel/VippattiViewModel.kt`, `VippattiUiState.kt` |
| Feasibility block (all required fields) in the shelter sheet; capacity line in the relocation panel | `ui/components/SafeZoneDetailDialogs.kt`, `ui/screens/ProfileScreen.kt`, `MainActivity.kt` |

Rules encoded:

- Effective capacity = minimum over the constraints that HAVE a value. A missing
  input is `NOT_PROVIDED` and is excluded - never counted as zero.
- A RECORDED absence (water/sanitation/food flagged unavailable, or a zero/
  negative area, volume or toilet count) is a real fact and legitimately caps
  the site at 0, becoming the limiting resource.
- `ACCESS` is reported qualitatively: no road-capacity provider exists, so no
  number is invented for it.
- Statuses: `FEASIBLE` / `INFEASIBLE` / `INSUFFICIENT_DATA` (no demand figure, or
  no usable site input) / `SIMULATED` (a verdict exists but every contributing
  resource is simulated). `meetsRequirement` carries the raw arithmetic even
  when the status is SIMULATED.
- Configured planning figures (4.5 m²/person, 15 L/person/day, 50 persons per
  toilet) live in one object, are surfaced as assumptions in the UI and are
  explicitly not an official standard.
- DEMAND is RESOLVED, never assumed: `PopulationDemandResolver` picks the best
  usable population record in the documented order (verified relocation demand >
  authority/field estimate > affected population, approval required > baseline
  population, approval required > SIMULATED demo demand > the citizen's own
  household declaration). With nothing usable the verdict is INSUFFICIENT_DATA.
- Assignment honours capacity: the best-ranked site that can absorb the demand
  wins, skipped sites are listed with their shortfall (`skippedSites`, both
  structured and in prose), and a site whose verdict is INSUFFICIENT_DATA is
  treated as unproven (not infeasible). With no assessments supplied the planner
  behaves exactly as before.

The pilot SIMULATED network gained labelled demo inputs on three zones so the
limiting-resource logic is visible end to end: Idukki Bypass (space-limited,
225 free spaces), Shimla Ridge (water-limited, 2,400 L/day ≈ 160 people).

---

## 18. Population Assessment & Relocation Demand (SIH 26191, Sep 2026)

New package `data/population`. A household size is NOT a habitation population,
and a baseline census count is NOT a relocation demand - so they are separate
records that cannot be silently converted into each other.

| Piece | File |
| --- | --- |
| `PopulationScope` (household → habitation → ward → village → … ), `PopulationRole` (baseline / affected / relocation demand), `PopulationClassification` (VERIFIED / ESTIMATED / USER_DECLARED / SIMULATED / NOT_PROVIDED), `PopulationSourceKind`, `PopulationRecord`, `PopulationAssessment` | `population/PopulationModels.kt` |
| Priority resolution, approval policy, rejected-record reasons | `population/PopulationDemandResolver.kt` |
| Source boundary (contract only - nothing connected) | `population/PopulationProvider.kt` |
| Wire format for a census/registrar/authority feed | `population/PopulationRecordJson.kt` |
| Demand provenance (`scope`, `scopeName`, `classification`, `role`, `derivedFromRole`, `referenceMillis`, `confidence`) on `RelocationDemand`, plus `roleLabel` / `scopeLabel` | `capacity/CarryingCapacityModels.kt` |
| Population fields carried through the assessment wire format, null-preserving | `capacity/CapacityAssessmentJson.kt` |
| Population block (baseline / affected / demand / source status) and skipped-site list | `ui/screens/ProfileScreen.kt`, `ui/components/SafeZoneDetailDialogs.kt` |

Rules encoded:

- **NO population provider is connected.** `NoPopulationProvider` returns an
  empty list; the honest outcome is INSUFFICIENT_DATA. `PopulationRecordJson` is
  the integration boundary a census/registrar or SDMA feed will use - it is a
  contract, not a live source, and nothing claims otherwise.
- A value is never invented: a record with no value (or ≤ 0) is REJECTED and the
  reason is kept, so "not measured" can never be read as zero.
- Baseline population is refused as demand unless
  `PopulationDemandPolicy.allowBaselineAsDemand` is set; when approved, the
  resulting demand is downgraded to ESTIMATED with `derivedFromRole =
  BASELINE_TOTAL` and labelled "Baseline population - not confirmed relocation
  demand" / "derived from baseline population". Same for affected population.
- The citizen's household declaration is scoped `HOUSEHOLD`, classified
  `USER_DECLARED`, is the last resort, and is labelled "not a census figure"
  everywhere it appears - never the population of an area.
- Narrowest area wins, then the most recent reference, then the higher declared
  confidence, then id: deterministic, never list order.
- A SIMULATED demand makes the whole verdict SIMULATED even on real site
  records, because the answer rests on demonstration data either way.

Behaviour: `PopulationPipelineBehaviorTest` drives the REAL `VippattiViewModel`
through the whole pipeline (cold start with no source -> registry comes online
mid-session -> registry goes down again) and asserts the observable transitions:
the demand switches from the household figure to the ward figure and back, the
verdicts are recomputed rather than left stale, the baseline is never promoted
to demand, re-syncing is idempotent, and a SIMULATED source yields SIMULATED
verdicts with `isVerified = false`. Driving it caught a real defect: an exception
from a population source escaped `viewModelScope.launch` (no error handling at
all). `refreshPopulation` now catches it - rethrowing cancellation - keeps the
records already fetched, and reports `populationSourceError` ("Previous figures
kept"), which the Profile panel shows instead of silently downgrading or
crashing.

Verification: **285 tests, 0 failures, 0 errors, 0 skipped** (33 suites; +32
contracts in `PopulationDemandTest`, `PopulationRecordJsonTest`, plus new
engine/planner and JSON round-trip cases, +10 behaviour contracts). `compileDebugKotlin` and
`assembleDebug` successful, APK at `app/build/outputs/apk/debug/app-debug.apk`
(21.5 MB). Not device-verified: the population UI is unit-tested logic only, and
no census/registrar endpoint was called (none is connected).

Not verified / still needs real data: no census or relief-registry population
feed (demand is user-declared only); shelter records remain SIMULATED demo data,
so live verdicts read SIMULATED; no shelter-registry backend consumes
`CapacityAssessmentJson` yet; no on-device check of the new sheet and panel
rendering.
---

## 19. SIH 26191 platform phase (Sep 2026): red zones, guidance, authority console

Three new packages answer the problem statement directly. Everything below is
unit-tested JVM logic — **none of it is device-verified yet.**

### Dynamic red zones (`data/suitability`) — COMPLETE (JVM-verified)
- `TerrainSuitabilityEngine` — pure scoring over SRTM stencil slope × live 24 h
  rainfall × coast proximity. Hard rule: slope ≥ 35 % ⇒ RED ZONE. Escalation:
  extreme rain (≥150 mm/24 h) on ≥10 % slopes can push CAUTION ⇒ RED ZONE.
  Coastal <5 m sites can never claim SAFE. Missing factor ⇒ excluded + said so
  (`INSUFFICIENT_DATA` with no elevation — never a guessed score).
- `TerrainProbeService` — one stencil URL (5 points) + one rainfall URL to the
  keyless Open-Meteo endpoints (schemas live-verified 2026-09-22); injectable
  transport, typed `ElevationUnavailable`.
- `CoastDistanceGrid` + `tools/coast_distance_prepare.py` — 0.25° nearest-coast
  distance grid (km, byte-encoded) over the India bbox from **public-domain
  Natural Earth land polygons**; asset `app/src/main/assets/geo/
  coast_distance_india.bin` (~16 KB); spot-checks in tests (Chennai/Kochi ≤12
  km, Madurai >40 km, Delhi ≥200 km, Bay of Bengal = 0).
- Tests: `TerrainSuitabilityEngineTest` (17), `CoastDistanceGridTest` (6),
  `TerrainProbeServiceTest` (5).

### Emergency guidance (`data/shelters`, radar UI) — COMPLETE (JVM-verified)
- `EmergencyGuidance.forSituation()` — RED/ORANGE ⇒ nearest **feasible** zone
  card with GO (distance-first selection; ineligible shelters surface their
  real rejection reasons); no shelters in state ⇒ honest NoShelterKnown + 112;
  YELLOW/GREEN never nag; an active destination suppresses the card.
- `SafeHavenFinder` — ring search (1/2.5/5 km, 8 bearings, probe budget) for
  the nearest terrain-SAFE point; routed as a `haven-*` DERIVED destination
  (`routeToTerrainHaven`) with provenance `classification = DERIVED`; never
  survives demo-hide (id-prefixed exemptions in the visibility guard).
- UI: `EmergencyGuidanceCard` + `TerrainSelfAssessmentChip` ("is MY spot a red
  zone?", explicit tap only) rendered above the radar map; wired via
  `VippattiViewModel` (`emergencyGuidance`, `terrainHaven`,
  `terrainSelfAssessment` state; `assessTerrainHere` etc.).
- Tests: `EmergencyGuidanceTest` (8), `SafeHavenFinderTest` (5),
  `EmergencyGuidanceStateTest` (6), `TerrainSelfAssessmentTest` (5),
  `EmergencySurfacesRenderTest` (6 Robolectric render contracts).

### Authority console (`data/habitations`, ui/screens) — COMPLETE (JVM-verified)
- `HabitationPriorityEngine` — transparent multi-criteria rank: hazard exposure
  0.35 / terrain 0.30 / vulnerability 0.20 / EM-DAT history 0.15 (history
  escalates at most ONE band and is always labelled historical). Tier rules:
  live-hazard coverage ⇒ IMMEDIATE; RED/HIGH_RISK terrain ⇒ SHORT_TERM;
  unassessed terrain can exceed MEDIUM only with a live hazard; per-row
  reasons + action guide; deterministic tie-break by id.
- Field registry: `FieldRegistryJson` (lossless round-trip, out-of-India
  records REJECTED with reasons, corrupt ⇒ empty + reason),
  `FileFieldRegistryStore` (atomic tmp+rename, private cache dir),
  `DemoHabitations` (labelled SIMULATED ring derived from PilotRegionData,
  shown until real records exist).
- VM: field shelters join `zonesInScope` **even with the demo switch off**
  (they are real records); map draws them in every mode; `openAuthority-
  Dashboard(liveTerrainScan)` + `runRanking` (opt-in per-site SRTM probes,
  capped, cancellation-safe) + save/delete actions. Entry: Profile →
  AUTHORITY CONSOLE; console = 3 tabs (ranking with tier chips + expandable
  reasons, shelter form, habitation form).
- Tests: `HabitationPriorityEngineTest` (10), `FieldRegistryStoreTest` (5),
  `AuthorityDashboardTest` (4, incl. the demo-hidden field-shelter-live
  contract).

### Other repo state after this phase
- OkHttp 4.10.0 → **4.12.0** (CVE-2023-3635); CI workflow
  `.github/workflows/android-ci.yml`; debug signing falls back to AGP's
  generated keystore when `debug.keystore` is absent; seeded FAKE emergency
  contacts removed (contacts list starts EMPTY with guidance text);
  raw EM-DAT XLSX, AI-Studio debris and stale build logs untracked.
- **Verification:** `:app:testDebugUnitTest` → 447 tests / 0 failures / 0
  errors / 0 skipped (53 suites); `:app:assembleDebug` BUILD SUCCESSFUL.
- **Not verified:** everything on-device (guidance card in landscape, haven
  probing a real hill town, Natural Earth grid on tablet densities, live
  Open-Meteo probe calls from the APK).

### Remaining honest gaps
- No shelter-registry backend: registry lives only on the operator device
  (`FieldRegistryJson` is the export/import wire format for a future server).
- No population API: dashboard demand is field-entered or SIMULATED demo —
  never a census claim.
- Coast grid is coarse (~27 km cells): terrain-level coast signal, not a
  parcel claim; storm-surge modelling is NOT implemented.

---

## 20. UX redesign pass (Sep 2026): Home-first journey

Applying Microsoft's 7 UI principles to the citizen experience (all new code
unit-tested; nothing in the data layer changed):

- **New `ScreenTab.HOME`, now the default landing tab** — `ui/screens/HomeScreen.kt`:
  risk hero ("IS MY AREA SAFE RIGHT NOW?" with honest pending/calm/alert/
  danger states + one CTA that navigates), terrain self-check chip moved here
  from the map, guidance card mirrors here so danger is actionable without
  finding the map tab, four labeled next-step rows, quiet DATA STATUS footer.
- **5-tab bottom nav** (Home/Map/News/Guide/Profile, journey-ordered) with
  screen-reader semantics (`label + (current tab)`), AutoMirrored icons.
- **Radar calmed**: bottom sheet starts collapsed to a one-line destination
  peek; terrain chip removed (it lives on Home now).
- **Back behaviour**: system back walks console -> tab -> Home instead of
  exiting from any screen (BackHandler in VippattiAppRoot).
- **Typography/accessibility on the new surfaces**: no text below 10 sp
  (most ≥ 11), line-heights ≥ font size, `…` glyph in progress copy.
- Verified: full suite 55 suites / 459 tests / 0 failures; assembleDebug
  green; Home UX contracts in `HomeScreenUxTest` (5).
