# Disaster Data-Source Audit (spec §17–§21)

Probed live from this machine on 2026-10-02 (HTTP status actually observed —
not assumed). The app integrates only what works; everything else is shown as
unavailable, never faked.

| Disaster | Source | Data type | Freshness class | Coverage | Update freq | Access | Integrated here? | What the app may show |
|---|---|---|---|---|---|---|---|---|
| EARTHQUAKE | USGS FDSN `earthquake.usgs.gov/fdsnws/event/1/query` | seismic events ≥M4.5 (query-bounded) | LIVE (feed says "Latest Earthquakes") | global, incl. India region | ~5 min | no-key HTTPS JSON | ✅ `UsgsEarthquakeProvider` — probed today: 144 M4.5+ global / 1 within 1500 km of central India | real quake markers + magnitudes |
| FIRE | NASA FIRMS NRT VIIRS | active-fire detections with FRP | RECENT (nominal ~3 h latency) | India bounding box (MAP_KEY area API) | NRT | key required (`FIRMS_MAP_KEY` in gitignored app/.env) | ✅ `FirmsFireProvider` (graceful NOT_CONFIGURED when key absent) | hotspot markers, size=FRP, clustered; labelled RECENT |
| CYCLONE / FLOOD / HEAVY RAIN | IMD via WMO CAP feed `cap-sources.s3.amazonaws.com/in-imd-en/rss.xml` | official CAP alerts, classified FLOOD/CYCLONE/HEAVY_RAINFALL/WEATHER_ALERT | LIVE-issued, time-bounded validity | India official warnings area | on issuance (quiet in fair season) | no-key HTTPS XML | ✅ `ImdCapProvider` — probed today: HTTP 200, index live | official-alert markers only when an alert exists |
| Alerts (all types) | NDMA CAP feed `in-ndma-en/rss.xml` | national alerts | LIVE | India | on issuance | no-key HTTPS | probe OK (200); same CAP parser route available | (currently IMD-only; NDMA index reachable if we add it) |
| FLOOD water levels | CWRIS `cwrims.gov.in` / `cwris.gov.in` | river gauging stations | would be LIVE | India rivers | hourly/daily | **DNS unresolvable from this machine (both, 2026-10-02)**; site is behind a JS portal anyway | ❌ NOT integrated — no reliable machine access found | app states: "Flood water-level data is not connected — official IMD/NDMA flood alerts are shown instead" |
| LANDSLIDE alerts | GSI NLSM / forecasting centre | susceptibility maps / forecasts | n/a | India | n/a | **no public machine-readable API found**; gsi.gov.in reachable but products are GIS downloads, not per-location alerts | ❌ NOT integrated | app states: "No live landslide alert source is connected — citizen reports and historical records only" |
| Rain (nowcast input) | Open-Meteo forecast API | precipitation | LIVE/CACHED | global | hourly | no-key HTTPS JSON | ✅ used for terrain/rain context | rainfall figures with RECENT/CACHED label |
| Sea state / imagery | Bhuvan / ISRO portals | flood extents, imagery | various | India | various | probe: 404 on the guessed WMS path; real services require KoshS3 keys | ❌ not integrated | not shown |

## What the map shows when Demo Mode is OFF (real pipeline, verified in code)

USGS earthquakes + FIRMS fires + IMD CAP alerts + citizen field reports. At
national zoom, dense fire detections are clustered by `MarkerGeneralizer`
(marker reports how many detections it stands for + max FRP), and the layer
system applies zoom-based level-of-detail + nearby-first filtering — so fire
volume cannot flood the view (spec §17/§20). Per-type filter chips select a
type; when a filtered type currently has zero displayed events the map shows
an explicit honesty line (§21) instead of an unexplained empty view.

## Missing-data policy (unchanged)

No live source ⇒ no markers, stated plainly. Historical EM-DAT records remain
HISTORICAL and are drawn only on the explicitly-toggled historical layer.
Demo candidates appear only with SIMULATION pill ON and stay SIMULATED-labelled.
