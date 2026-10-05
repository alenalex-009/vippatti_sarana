# PHASE 1A — India-wide Source Audit

Audit date: 2026-10-04. All "live" claims below were verified by an actual HTTP
call from the dev machine on that date. Nothing here is assumed.

## Headline finding

The existing app is **pilot-area shaped**. The data architecture, not the data
volume, is the binding constraint: hazard areas, safe zones and routing are all
modelled around one region and around inventing geometry where none exists.

The single most important discovery is that **official India-wide hazard
polygons already exist and are publicly reachable** via NDMA SACHET, but they sit
behind a two-step fetch that the current code does not perform. That changes
what the product can honestly claim.

---

## 1. What the current code already does well

Preserved deliberately; not rewritten.

- MVVM + Compose + single Activity, no backend. Matches the target architecture.
- A provenance/freshness concept already exists (`ProviderProvenance`,
  `DataStatus`) — the new `IndiaProvenance` mirrors its vocabulary rather than
  replacing it.
- User-relative evacuation logic exists: a user at the edge of a hazard is not
  routed toward the hazard centre. This is correct behaviour and is preserved.
- Live providers already wired for USGS and FIRMS, with real endpoints.
- Demo/simulated mode is separated from live mode.

## 2. The binding architectural gaps

| Gap | Consequence |
|---|---|
| No India-wide geographic reference | "Near me" and "near this hazard" are undefined outside the pilot area |
| Hazard geometry is demo-generated in places where no real geometry exists | Violates rules 1–4, 13–15 |
| No distinction between an observation and an area | An earthquake point or a fire hotspot can read as a hazard zone |
| Route safety implied by routing | Violates rule 16 — an OSRM path is not a safe path |
| Missing values silently defaulted | Violates rules 6, 8 |
| No machine-checked rule enforcement | The critical rules were conventions, not constraints, so they regressed |

## 3. Source audit — verified live

### Open, no credentials

| Source | Endpoint | Verified | Yields |
|---|---|---|---|
| NDMA SACHET | `CapFeed`, `ScreenReader`, `cap_public_website/rss/rss_india.xml` | HTTP 200 | 99 live CAP alerts; 14 distinct issuing authorities |
| USGS FDSN | `fdsnws/event/1/query?format=geojson` | HTTP 200 | India-bbox earthquake events |
| Open-Meteo | `api.open-meteo.com/v1/forecast` | HTTP 200 | current + hourly weather |

Capture detail: senders in one feed pull were AP SDMA (49), IMD Kolkata (12),
CWC (11), and IMD Chennai/Guwahati/Gangtok/Bengaluru/Thiruvananthapuram/
Agartala/Mumbai/Goa/Hyderabad plus Mizoram SDMA. That is pan-India coverage
from one endpoint, not a pilot region.

### Requires a key

| Source | Note |
|---|---|
| NASA FIRMS | `api/area/csv/{MAP_KEY}/VIIRS_SNPP_NRT/{lon,lat,lon,lat}/{days}` returned live hotspot rows for the India bbox. Key stays in `local.properties`, never in source. |

### Official but NOT reachable — and no substitute adopted

| Source | Status | Consequence in this app |
|---|---|---|
| NDEM / NRSC | login required | official inundation + shelter layers unavailable |
| NRSC Bhuvan thematic | manual per-layer download | landslide/flood susceptibility unavailable |
| CWC live gauges | no open feed found | river level stays UNKNOWN; never estimated from rainfall |
| Live road closures / traffic | no pan-India open feed | routes are reported "unverified", never "safe" |
| INCOIS tsunami | portal-only | no tsunami inference |

Per rule 19, none of these were replaced with a third-party dataset. The
consequence is carried in the UI as honest unavailability instead.

## 4. The SACHET two-step fetch (the key technical finding)

The RSS is an **index**, not CAP XML. Each `<item>` carries a title (often in a
regional language — the capture returned Bengali first), an empty
`<description/>`, the issuing authority in `<author>`, and a link to
`FetchXMLFile?identifier=<id>`.

The CAP document at that link has one `<cap:info>` per language, so the `en-IN`
block must be selected explicitly. `<cap:area>` usually carries `areaDesc` and
LGD geocodes but **no inline polygon**.

Geometry is published separately, referenced by a
`<cap:parameter><cap:valueName>Polygon URL</cap:valueName>`. Fetching that for a
live Darjeeling alert returned 80 KB of real `lat,lon` vertices.

Consequence: the app can display **authority-published hazard areas for any
Indian district**, rather than generating its own. This is the difference
between a demo and an instrument.

## 5. Bundled datasets — checksums verified

Canonical copies live under `Vippatti_India_Data/`; MD5s computed, not assumed.

| Dataset | Size | MD5 | Class |
|---|---|---|---|
| state boundaries | 20.8 MB | `2defdd09afc8292d2a0268e9095919fe` | STATIC |
| district boundaries | 71.2 MB | `a3d1d885c26197f277fc264585bb3463` | STATIC |
| subdistrict boundaries | 132.4 MB | `77a001bc094e8ec8ea5b173711f10e73` | STATIC |
| village boundaries (AP only) | 22.7 MB | `e2357f61c02bb686f8e8a6b57ff3f6e1` | STATIC, partial |
| Census 2011 XLSX | 333.8 MB | `52034cb4d70b502aa3522fe71f510872` | HISTORICAL |

Two caveats recorded rather than glossed:

- Village coverage is **Andhra Pradesh only**. The hierarchy must degrade
  honestly outside AP rather than inventing a village.
- **NWIC redistribution terms are UNVERIFIED.** These files must not be bundled
  into a shipped APK until that is confirmed. This is a licensing blocker, not
  a technical one.

## 6. Rejected datasets

Kaggle mirrors such as `India State District Wise Daily Updates` and
`State Wise Disaster Vulnerable Areas` package census-era figures in a
current-looking schema. Using them would breach rules 11 and 12 by presenting
historical numbers as present risk. Not used.

## 7. Rule enforcement made mechanical

Rules 13–17 are now code, not conventions:

| Rule | Enforced by |
|---|---|
| 13 observation ≠ evacuation zone | `InferenceGuard.observationToEvacuationZone` |
| 14 epicentre ≠ danger radius | `InferenceGuard.dangerRadiusFromEpicentre` |
| 15 hotspot ≠ fire perimeter | `InferenceGuard.hotspotToPerimeter` |
| 16 route ≠ safe route | `InferenceGuard.routeToSafeRoute` |
| 17 no closure data = unavailable | `RouteSafetyStatus.UNVERIFIED_NO_DATA` |
| 6/8 missing ≠ zero | `MaybeNumber` |
| 11/12 historical ≠ current | `InferenceGuard.isPresentlyValid` |

## 8. Known limitations at the end of Phase 1

- Village-level boundaries exist only for AP; other states degrade to district.
- No official India-wide shelter dataset is reachable, so shelter data remains
  unavailable rather than derived from generic POIs.
- River/flood gauge state is UNKNOWN; no live feed is available.
- NWIC licensing is unresolved; the boundary files are not shippable yet.
- The 555 MB of boundary archives stay on disk outside the repo and outside the
  APK. An on-device, low-memory India-wide boundary index is Phase 2 work.
