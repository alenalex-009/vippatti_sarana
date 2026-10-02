# Carrying Capacity — Research & Proposed Model

**Status: RESEARCH DELIVERABLE — PROPOSED MODEL PENDING APPROVAL (master spec §9–§14, §30).**
No capacity-engine code was changed based on this document. Existing site-level
behaviour (bottleneck MIN over reported resources, "not provided" ≠ zero) is
unchanged. This report is the gate before Phase 8 (multi-level capacity).

Prepared 2026-10-02. Every quote below was copied verbatim from the fetched
document on that date. Sources that could not be fetched from this machine are
listed explicitly in §E/§J — they are marked, never paraphrased from memory.

---

## 0. Source register (what was actually retrieved)

| # | Source | Hosted at | Retrieved | Used for |
|---|--------|-----------|-----------|----------|
| S1 | **Sphere Handbook 2018** (full English PDF, ~972 KB text extract) | `https://spherestandards.org/wp-content/uploads/Sphere-Handbook-2018-EN.pdf` | ✅ fetched 2026-10-02 | international humanitarian minimum standards |
| S2 | **URDPFI Guidelines 2014 (Unified RCDP & Planning Standards)**, MoHUA Govt of India — NITI Aayog mirror PDF | `https://nitiforstates.gov.in/public-assets/Policy/policy_files/GNC518O000187.pdf` | ✅ fetched 2026-10-02 | Indian official urban-planning guidance incl. "Carrying Capacity" §7.4 |
| S2b | URDPFI Volume-I, NAREDCo mirror | `https://www.naredco.in/notification/pdfs/Volume-I%20Main%20URDPFI%20Guidelines%202014a.pdf` | ✅ fetched (text-layer sparse) | corroboration |
| S3 | **Handbook on Cyclone Shelters** (GoI / NDMA-era guidance, NIDM-hosted) | `https://nidm.gov.in/PDF/safety/public/link3.pdf` | ✅ fetched 2026-10-02 | Indian official shelter-capacity practice |
| S4 | **PIB note "Census 2027: India's First Digital Enumeration Exercise"**, 25 Apr 2026 | `https://static.pib.gov.in/WriteReadData/specificdocs/documents/2026/apr/doc2026425856601.pdf` | ✅ fetched via extract 2026-10-02 | current census status |
| S5 | censusindia.gov.in Primary Census Abstract page | `https://censusindia.gov.in/nca/index.php/pca` | ❌ NOT FETCHED (403 bot-wall) | ward-level PCA — status known only indirectly |
| S6 | CWRIS (Central Water Resources Information System) | `https://cwrims.gov.in/`, `https://www.cwrims.gov.in/`, `https://cwris.net/` | ❌ NOT FETCHED from this machine (connection failed, HTTP 000) | flood gauging data |
| S7 | IMD public site `mausam.imd.gov.in` | ✅ reachable (HTTP 200); the probed JSON API endpoints returned 404 — **no public documented API confirmed** | connectivity probe 2026-10-02 | cyclone/warning products |
| S8 | NDMA SACHET `sachet.ndma.gov.in` | ✅ reachable (HTTP 200); CAP endpoint returned a 308 redirect — CAP feed exists behind redirect, format not verified | connectivity probe | official alerts |
| S9 | GSI `gsi.gov.in` (landslide products) | ✅ reachable (301→site); NLSM-specific portal `nlsm…` ❌ NOT FETCHED (HTTP 000) | connectivity probe | landslide susceptibility |
| S10 | FSI `fsi.gov.in` | ✅ reachable (HTTP 200) — page content (current fire alerts vs annual reports) NOT verified this pass | connectivity probe | fire/forest |
| S11 | NASA FIRMS | already integrated in-app (LIVE provider, public key) — unchanged, documented in app provenance | n/a | fire hotspots |

Classification of guidance type is stated per quote:
**[IN-IN]** Indian official guidance · **[INTL]** international humanitarian guidance · **[ACAD/UNOFFICIAL]** anything else.

---

## A. What "carrying capacity" means in this application

Three distinct questions get conflated in everyday usage. The app must keep them separate:

1. **SITE CAPACITY** — how many people a specific shelter/camp site can hold
   before one of its resources (space, water, sanitation, food, power, access)
   is exceeded. → bottleneck maths, output = people.
2. **AREA DEMAND** — how many people *need* shelter (affected/evacuee
   population of a colony, ward, hazard zone). → population data, output = people.
3. **SUITABILITY / RANKING** — which site should a specific person be sent to
   (hazard exposure, distance, vulnerability fit). → weighted score, output = order.

**Master-spec rule (kept):** capacity is a constraint computation; suitability is
a ranking score. A ranking score must never be displayed as "capacity", and a
capacity number must never imply the site is safe from the hazard (that is what
the hazard filter + `DisasterSuitability` advisories handle).

URDPFI — the only **Indian official** definition found — describes carrying
capacity in exactly this multi-constraint way, *not* as a single density
number:

> "Fixation of density norms should be based on carrying capacity analysis
> focusing on parameters - space per person, access to facilities, available
> piped water per capita, mobility and safety factors. The task should be
> settlement specific." — URDPFI 2014 (MoHUA) **[IN-IN]** (S2, fetched)

> "The capacity to hold the population is an indicator for infrastructure
> projection." — URDPFI 2014 **[IN-IN]** (S2)

This directly supports the bottleneck (MIN-over-parameters) shape rather than a
weighted average: URDPFI treats water/access/space as *factors to be assessed*,
each of which can independently cap density.

---

## B. Differences between the levels

| Level | Unit in the app | Capacity meaning | Demand meaning | Aggregation risk |
|---|---|---|---|---|
| **Site/shelter** | `SafeZone` record | MIN of its own resource lines | n/a | none (leaf) |
| **Colony/community** | neighbourhood of ~1–3 km around a habitation cluster | sum of *non-overlapping usable* site capacities reachable from it | local affected population (sourced or UNAVAILABLE) | two adjacent colonies counting the same shelter |
| **Ward** | municipal ward polygon (only if a ward geometry+population source is connected — see §E) | sites inside ward + sites whose *service area* covers the ward | ward population × evacuee share | double counting sites shared across wards; treating Census 2011 as current |
| **City/ULB** | all wards | unique underlying resources summed once | city demand vs city supply gap | counting a ward-level shelter again as a city-level facility |
| **Evacuation capacity** | a rate, not a stock | people/day (or people/night) the route+site system can move | time pressure from the hazard | confusing "people that fit" with "people that can arrive before impact" |
| **Service/infrastructure capacity** | water/t/power lines | per-day flows | — | stock vs flow confusion (L stored vs L/day) |

Note honestly: **no Indian official definition of "colony capacity" exists** in
the sources fetched — URDPFI speaks of *settlement-specific* carrying capacity
and neighbourhood-level planning norms. "Colony" here is therefore defined as a
**PROJECT DEFINITION** (a user-facing catchment band), not a statutory unit.

## C. Data required per level

- **Site**: bed/plottable spaces or land m² and per-person area norm; water L/day; toilets (count); food status; power; access notes; current occupancy; verification status + provenance. *(Model already carries all of these on `SafeZone`.)*
- **Colony**: membership of colonies (geographic assignment rule), each member site's *unique* usable capacity, colony population (source-labelled) or UNAVAILABLE.
- **Ward**: ward geometry (which sites lie inside / serve it), ward population from Census (HISTORICAL BASELINE) or an ESTIMATED projection with stated method, hazard-excluded land area.
- **City**: deduplicated union of ward sites + city-level facilities (hospitals, storage), each counted once with an owner-level tag.
- **Evacuation**: route travel time + throughput (OSRM gives time/distance; throughput is UNAVAILABLE without road-class data), impact time window (hazard-specific; UNAVAILABLE for most providers).
- **Demand**: affected population. **Nothing in the current app sources this** except user household size (self-declared FIELD record) and SIMULATED demo figures.

## D. Which data is actually available today

1. Site-level resource lines on demo + field-registry `SafeZone` records — SIMULATED (demo) or FIELD (operator-entered via Authority Console). Verified govt registry: **none connected** (model is ready for one).
2. Measured terrain elevation (Open-Meteo SRTM) — LIVE/CACHED lookup, site-level.
3. Coast distance from a precomputed Natural Earth land grid — DERIVED, offline.
4. Routes (OSRM) — routing only, not throughput.
5. Household size of the logged-in user — FIELD self-declared, one household.
6. Census 2011 ward-level PCA exists as published data, but the censusindia.gov.in API/page is bot-walled from this environment (S5, 403) — **not yet integrated**.
7. Historical disaster archive (EM-DAT) — HISTORICAL, no population-of-ward data.

## E. Which data is unavailable (must render as NOT AVAILABLE, never 0)

- Current (non-Census-2011) population at ward/colony level — Census 2027 is the next exercise (S4, PIB, fetched: "Census 2027 will mark India's first digital enumeration"); **Census 2011 is a HISTORICAL BASELINE only**, per master-spec §12.
- Structural/building safety data for shelters (earthquake).
- Slope/runout/stability per site (landslide) — GSI NLSM portal unreachable (S9).
- Storm-surge inundation extents per site — Survey-of-India level data not machine-accessible (NDMA handbook instructs *local* survey per site: S3 quote in §G).
- River-gauge current levels — CWRIS unreachable from this machine (S6): **no flood "live" claim possible beyond IMD rainfall + EM-DAT history + user reports**.
- Shelter occupancy for real registries (no registry connected).
- Route throughput / road capacity (no dataset).
- FSI current fire alerts — S10 not content-verified; FIRMS remains the only live fire feed (already honest).

## F. Proposed model (PHASE 8 — awaiting approval, do not implement yet)

### F.1 Site level (retain current engine; cite norms)

```
site_capacity = MIN over AVAILABLE resource lines of:
    floor_area_m2 / area_norm_per_person
    water_L_per_day / water_norm_per_person_per_day
    toilets * persons_per_toilet_norm
    shelter_spaces_reported
    food / power / access  (when a per-line figure exists)
missing line  -> excluded from MIN, listed under "not assessed"
               -> confidence drops; NEVER treated as 0
```

Norm values (constants, configurable, each with provenance class):

| norm | value | class | citation |
|---|---|---|---|
| covered living space | **3.5 m²/person** (4.5 m² cold climates) | [INTL] Sphere | S1, fetched: "Minimum 3.5 square metres of living space per person, excluding cooking space, bathing area and sanitation facility" |
| camp total plot area | **45 m²/person** (30 m² where communal services are outside) | [INTL] Sphere | S1: "45 square metres for each person in camp-type settlements, including household plots… 30 square metres… where communal services can be provided outside" |
| covered:plot ratio | ≥ **1:2** | [INTL] Sphere | S1: "Minimum ratio between covered living space and plot size is 1:2; move as soon as possible to 1:3 or more." |
| drinking+domestic water | **15 L/person/day** | [INTL] Sphere | S1: "Minimum of 15 litres per person per day" |
| toilets, first phase rapid-onset | **1 per 50** | [INTL] Sphere | S1: "communal toilets… minimum ratio of 1 per 50 people, which must be improved as soon as possible" |
| toilets, medium term | **1 per 20** (3:1 female:male) | [INTL] Sphere | S1: "A medium-term minimum ratio is 1 per 20 people, with a ratio of 3:1 for female to male toilets." |
| cyclone-shelter floor area | **≥3 sq ft/person (~0.28 m²) floor + terrace counted separately; 2 sq ft/person called inhuman** | [IN-IN] GoI cyclone handbook | S3: "an area of 2 sq ft/person has generally been provided for sheltering purposes. Such a density may lead to suffocation and inhuman environment… at a rate of 3 sq.ft. per person… a four room school could be assumed to accommodate easily upto 1000 persons." |
| cyclone-shelter utilisation | **50–60 % of vulnerable population** expected to use shelter | [IN-IN] GoI handbook | S3: "about 50-60% of total population of vulnerable locations may be using the cyclone shelter during emergrncies." *(sic)* |
| school water supply | **45 L/day/person** (National Building Code of India, as cited by handbook) | [IN-IN] NBC via S3 | S3: "According to the National Building Code of India, for schools a water supply of 45 lts per day per person is required." |

Reconciliation with the current code: our configured constants are 4.5 m², 15 L, 50 persons/toilet.
- 15 L and 50/toilet sit **exactly on Sphere** (50 = first-phase ratio).
- 4.5 m² sits between Sphere's 3.5 minimum and its 4.5 cold-climate figure.
→ **Proposed change:** keep the three current values as the app's CONFIGURED defaults (they are within cited norms and labelled "configured, not official Indian standard"), but (a) surface the source band (3.5–4.5; 1:50 → 1:20 by phase) in the methodology panel, and (b) add a distinct **cyclone-shelter mode** using the Indian 3 sq ft floor figure when the active hazard is CYCLONE (multi-storey stacking is legitimate there per S3, whereas camp-style 3.5 m² is not — this is a real Indian-official vs international divergence and the app must show which regime produced a number).

### F.2 Colony level (project definition, [PROJECT])

```
usable_capacity(site, colony) = site_capacity * phase_factor     # occupancy already deducted
colony_capacity = Σ unique-claim portions of member sites, where a site's
                  capacity is split across the colonies that reach it on foot
                  (reach rule: walking <= 30 min at user-set speed)
```

**Anti-double-count rule (this app's rule, method [ACAD/UNOFFICIAL]):** each
site distributes capacity to reachable colonies proportionally to colony demand;
if only one colony reaches a site it counts fully there. No fetched source
gives a catchment-allocation standard (UN-Habitat/OCHA pages not fetched this
pass — §G/F.4 note), so the proportional-split rule is labelled PROJECT
ASSUMPTION in the UI methodology panel, not as research.

```
colony_status = compare(colony_capacity, colony_demand)
  demand UNAVAILABLE -> show capacity with "no population data - no verdict"
```

### F.3 Ward level (only when a ward dataset is connected)

- Sites assigned by point-in-polygon; boundary sites distributed as F.2.
- Demand = Census-2011 ward population **[HISTORICAL BASELINE]** × evacuee share
  (share itself is UNAVAILABLE unless hazard-specific; the GoI cyclone 50–60 %
  utilisation band [IN-IN, S3] is the only sourced share and applies ONLY to
  cyclone). Any current-year projection = **ESTIMATED + method string** (master
  spec: never silently grow 2011).
- Until ward geometries + PCA data are integrated (S5 unreachable from this
  machine), the app shows: "Ward capacity: NOT AVAILABLE (no ward dataset
  connected)". No fake ward rollup.

### F.4 City / ULB level

```
city_capacity = Σ site_capacity over DISTINCT site IDs (owner-tag dedup)
city_gap      = compare(city_capacity, city_demand)     # demand as at F.3
```
Every contributor row carries its level tag so a ward figure and a city figure
can be reconciled (sum of unique sites is identical by construction).

### F.5 Confidence model [PROJECT, transparent]

`confidence = f(fraction of resource lines actually measured, verification
status of record, age of data)` → HIGH / MEDIUM / LOW chips, each with the
exact list of "lines not assessed". No numeric pseudo-score is shown to normal
users; it appears only in the details collapse.

### F.6 What is explicitly NOT adopted

- `ward area × density` as a capacity (master spec §12; URDPFI §A above
  demands parameter-by-parameter assessment "settlement specific").
- Any weighted-average "capacity = 0.2×land + 0.2×water…" (weights appear only
  in **suitability ranking**, already separated in `DisasterSuitability` /
  `SafeZoneEvaluator`, never in a capacity claim).
- Counting terrace + floor area together without stating both (S3 does stack
  them for cyclone shelters — legitimate there, labelled and shown per level).

## G. Disaster-specific modifications (cite-linked)

| hazard | capacity-side change | source class |
|---|---|---|
| CYCLONE | Indian 3 sq ft floor regime + terrace stacking allowed; 10 km coastal survey band informs "shore-exposed" flag; utilisation band 50–60 % | [IN-IN] S3 |
| FLOOD | covered-space 3.5 m² camp regime; water line becomes decisive (flood contamination) — currently the demo sites that were "water-limited" illustrate this | [INTL] S1 |
| EARTHQUAKE | structural safety UNKNOWN on every record → capacity stands but verdict must say "BUILDING SAFETY NOT ASSESSED" (already implemented in `DisasterSuitability`) | — |
| LANDSLIDE | slope/runout unassessed → higher-ground bonus explicitly *not* a safety claim (implemented) | — |
| FIRE | fuel/smoke unassessed → open-area suitability advisory only | — |

## H. Confidence / provenance rules (display contract)

1. Every capacity number carries LIVE / CACHED / FIELD / SIMULATED / DERIVED /
   ESTIMATED / HISTORICAL exactly as the underlying record labels it; a
   SIMULATED site can never contribute to a "verified capacity" aggregate.
2. Aggregates inherit the *weakest* contributing label (a colony sum containing
   one SIMULATED site is shown SIMULATED).
3. "Not assessed" lines are listed by name next to every verdict.
4. Historical census figures are stamped **HISTORICAL BASELINE — 2011** wherever
   used, never presented as current population.

## I. Worked example (demo, deterministic — matches current demo generator)

Site `demo-sz-…-hall`: reported land 1 200 m², water 2 400 L/day, toilets 12,
spaces 300, occupancy 30.
- area: 1 200 / 4.5 = 266
- water: 2 400 / 15 = 160  ← **limiting**
- toilets: 12 × 50 = 600
- spaces: 300 (30 occupied → 270 usable)
- food/power/access: not provided → not assessed, confidence MEDIUM
→ **Effective capacity: 160 people (water). Limiting resource: water.
Not assessed: food, power, access. SIMULATED.** (Current engine already renders
this; the report cites where the 15 L / 4.5 m² / 50-per-toilet numbers come from.)

Colony: two colonies within 30-min walk of the hall, demand 120 and 90 →
capacity 160 split 160×(120/210)=91 and ×(90/210)=69 [PROJECT ASSUMPTION shown in
methodology]. Colony A: 91 < 120 → SHORTFALL 29. Colony B: 69 < 90 → SHORTFALL 21.
City: hall counted once (id-dedup) — 160 city site-capacity from this site.

## J. Limitations & honesty ledger

1. **S5/S6/S9 not fetched from this machine** (bot-walled / unreachable). Ward
   integration and live flood gauges therefore cannot be claimed as feasible
   from here; they stay "source known, access unresolved".
2. No catchment/double-count standard was found in fetched sources — F.2 is a
   labelled project rule, not research.
3. Sphere values are humanitarian minima, **not Indian legal requirements** —
   UI must not cite them as "government norms" (they are labelled [INTL] in the
   methodology panel).
4. The GoI cyclone handbook figures (S3) are design guidance for purpose-built
   shelters; applying 3 sq ft to any building overstates safety — the app only
   applies that regime when hazard==CYCLONE *and* the record is a designated
   shelter type; otherwise the 3.5 m² camp regime governs.
5. Census 2011 remains the latest *conducted* decadal data at the time of
   writing; Census 2027 is announced (S4) — any "current population" in this
   app will be ESTIMATED or UNAVAILABLE, per spec.
6. This document does not change any number shown in the app today; Phase 8
   implementation begins **only after user approval** (master spec §30).
