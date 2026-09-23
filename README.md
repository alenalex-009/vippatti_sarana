# Vippatti Sarana

> **Disaster Intelligence & Emergency Response Platform for Vulnerable Communities**

Vippatti Sarana is an Android-based disaster management application designed to help users assess disaster risks, identify safer areas, access evacuation routes, and receive relevant disaster information.

Vippatti Sarana is an **all-India** disaster-management and relocation decision-support application: live data sources are queried across India, and the user's own location drives every assessment. **Idukki, Kerala** appears only as a configurable pilot/demo region (sample shelter network and demonstration records), not as a limitation of scope.

---

## 🚨 Features

- 🗺️ **Disaster Radar** — View hazards, disaster events, safe zones, and evacuation routes.
- 📍 **Risk Assessment** — Assess disaster risk based on the user's location.
- 🏠 **Safe Zone Detection** — Identify and rank nearby safer locations.
- 🛣️ **Evacuation Routing** — Plan evacuation routes with alternative routes.
- 🌋 **Disaster Intelligence** — Uses data from USGS, NASA FIRMS, IMD CAP, and user reports.
- 📰 **Disaster News** — Fetch relevant disaster-related news through GNews.
- 📢 **Emergency Tools** — SOS, emergency contacts, flashlight, siren, and battery information.
- 🔊 **Audio Bulletin** — Provides spoken updates about risk, recommended actions, and relevant news.
- 📝 **Incident Reporting** — Report incidents and provide situation information.
- 📦 **Offline Support** — Caches selected data for use during limited connectivity.
- ⛰️ **Dynamic Red-Zone Check** — "Is my spot unsafe for habitation?" Probes SRTM 30 m slope + live 24 h rainfall + coast exposure at your location and returns a transparent score with reasons (decision-support heuristic, not an official notification).
- 🧭 **Emergency Guidance** — When danger is detected near you, the radar shows the nearest eligible safe zone with one-tap GO routing; with no registered shelter in range it says exactly that, points at 112, and offers a last-resort terrain-safe haven search (labelled DERIVED open terrain).
- 🏛️ **Authority Console** — Field shelter & habitation entry for survey operators (stored on device) feeding the **relocation prioritization dashboard**: habitations ranked IMMEDIATE / SHORT-TERM / MEDIUM-TERM with per-row scoring reasons (SIH 26191).

---

## 🛠️ Tech Stack

| Category         | Technology                           |
| ---------------- | ------------------------------------ |
| Platform         | Android                              |
| Language         | Kotlin                               |
| UI               | Jetpack Compose                      |
| Architecture     | MVVM                                 |
| Maps             | OSMDroid + OpenStreetMap             |
| Routing          | OSRM                                 |
| Terrain          | SRTM 30 m (Open-Meteo Elevation API) + Natural Earth coast grid (offline) |
| Networking       | OkHttp                               |
| Local Storage    | File Cache                           |
| Backend Services | Firebase Remote Config (OTA config; no app backend — by design) |
| News             | GNews API                            |
| Disaster Data    | USGS, NASA FIRMS, IMD CAP            |
| Testing          | JUnit, Robolectric, Compose UI Tests |

---

## 🏗️ Architecture

```text
                    Vippatti Sarana
                          │
                          ▼
                   Jetpack Compose
                          │
                          ▼
                  VippattiViewModel
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    Risk Assessment   Safe Zones      Routing
          │               │               │
          └───────────────┼───────────────┘
                          ▼
                 Emergency Response
```

For the detailed architecture and data flow, see [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## 📂 Project Structure

```text
app/
├── src/main/java/
│   └── com/example/
│       ├── data/
│       │   ├── risk/
│       │   ├── shelters/
│       │   ├── routing/
│       │   ├── reports/
│       │   ├── news/
│       │   └── disaster/
│       │
│       └── ui/
│           └── components/
│
├── build.gradle.kts
└── .env.example

ARCHITECTURE.md
README.md
```

---

## 📥 Download & Install the App

To install **Vippatti Sarana** on your Android device:

1. Go to the **[Releases](../../releases)** section of this repository.
2. Open the **latest release**.
3. Inside the release, you will find a **Google Drive link**.
4. Open the Drive link and download the **APK** file.
5. On your Android device, enable **"Install from unknown sources"** (if prompted) for your browser or file manager.
6. Open the downloaded APK and install the app.

> **Note:** Always download the APK from the **latest release** to ensure you have the most up-to-date version of the app.

---

## ⚙️ Setup (For Developers)

### Requirements

- Android Studio
- JDK 11+
- Android SDK 36
- Android device or emulator

### Environment Variables

Create an `app/.env` file and add the required API keys:

```env
GNEWS_API_KEY=YOUR_GNEWS_API_KEY
FIRMS_MAP_KEY=YOUR_FIRMS_MAP_KEY
```

> **Note:** Do not commit `.env` or API keys to the repository.

### Build

```bash
./gradlew.bat compileDebugKotlin
```

### Run Tests

```bash
./gradlew.bat testDebugUnitTest
```

### Build APK

```bash
./gradlew.bat assembleDebug
```

---

## 🔐 Data Transparency

Vippatti Sarana is designed to distinguish between real data and fallback/demo data.

- Device GPS data is clearly identified when available.
- Fallback location data is explicitly labelled.
- Disaster events retain their respective data sources.
- News is clearly marked as **not an official emergency alert**.
- Historical EM-DAT context is attributed, versioned, and kept separate from live decisions — see [EMDAT_ATTRIBUTION.md](./EMDAT_ATTRIBUTION.md).
- The application avoids fabricating alerts, routes, or verification statuses.

---

## 🚧 Project Status

Vippatti Sarana is an active disaster-management pilot project focused on **risk awareness**, **safe-zone identification**, **evacuation planning**, and **emergency assistance**.

---

## ⚠️ Disclaimer

Vippatti Sarana is a software prototype/pilot and **should not be considered a replacement for official emergency warnings, government advisories, or instructions from emergency authorities**.

---

**Vippatti Sarana — Technology for Safer Communities.**
