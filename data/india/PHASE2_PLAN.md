# IMPLEMENTATION PLAN — Phase 2: real end-to-end pipeline

Derived from an actual audit of the repository (136 main Kotlin files, 780
existing tests). This plan records what EXISTS, what is BROKEN/MISSING, and what
changes.

## Audit findings — reuse, do not rebuild

Already implemented and working (do NOT touch):
- `DisasterEvent` / `EventGeometry` (POINT/POLYGON/LINE/MULTIPOINT/UNLOCATED) — good.
- `DisasterDataRepository` — parallel fetch, validate, dedupe, cache, offline fallback.
- `DisasterCache` + `DisasterCachePolicy` — freshness windows already correct.
- `SafeZoneEvaluator` — **user-relative evacuation already implemented**
  (`insideCircleRatio` from USER origin, `evacuationExposureMeters`,
  phase-1 hard rejections, phase-2 explainable ranking). This is section 12 and
  it is already correct — preserve exactly.
- `DisasterSuitability` — per-type rules with explicit "NOT ASSESSED" lines.
- `InferenceGuard` / `MaybeNumber` / `IndiaProvenance` (Phase 1).
- `GeoMath` — haversine, bearing, closest-approach, circle-intersection ratio.
- Keys already in `app/.env` → `BuildConfig`, never hardcoded. Section 33 satisfied.
- Auth/onboarding/news/historical all intact.

## Gaps found (the actual work)

### GAP 1 — SACHET provider points at the WRONG endpoint
`NdmaCapProvider` extends `ImdCapProvider` with
`RSS_NDMA_URL = https://cap-sources.s3.amazonaws.com/in-ndma-en/rss.xml`
(the WMO S3 bucket), NOT the real `sachet.ndma.gov.in` site. Consequence:
- no two-step fetch, so no authority-published polygons at all
- the S3 bucket is a different publisher's mirror, not SACHET itself

Also `ImdCapProvider` has an explicit comment saying ETag/If-None-Match is
NOT implemented because the SACHET endpoint was "unverified from this
environment". I have now verified it.

### GAP 2 — No ETag conditional-GET anywhere
Section 2 mandates it. Verified server behaviour (2026-10-04):
- RSS index: `ETag: W/"80851-1791134070750"` — weak ETag, honours it.
- FetchXMLFile: `ETag: "bbAS5UgazsFkq/Y6AilQ6Y9ikxAVs2G1ozLZUWNfJFM="`.
- **BUT** sending `If-None-Match` with a matching ETag returns `200` + full
  body, NOT `304`. The origin ignores conditional GET.
  => The client must compare the returned ETag to the stored one and keep the
  cached XML when they match. Waiting for 304 would re-download forever.
- Polygon endpoint currently returns Akamai **403** from this IP (worked at
  21:17, blocked by 22:51). Polygons are therefore genuinely optional at runtime
  and MUST degrade to "geometry unavailable" per section 3.

### GAP 3 — no geometry validation
Nothing checks vertex count / lat / lon range / India bounds / self-intersection.
A malformed polygon currently becomes a `Polygon` if it has ≥3 parseable points.

### GAP 4 — rule-14 violation in the normalizer
`DisasterEventNormalizer.derivedQuakeRadiusMeters` computes `2^M` km from
magnitude and returns it as a `HazardZone` radius. Section 0 rule 14 forbids
converting an epicentre into a danger radius unless the source supplies it.
It is labelled ESTIMATED in text, but it still renders as a hazard circle.
Must stop synthesising this for point earthquakes.

### GAP 5 — every shelter is simulated
`PilotRegionData` is the only `SafeZone` source; all capacities are mock and
`verificationStatus` is simulated. Section 13 requires LIVE mode to show real
facilities only, and say "No verified evacuation centre found" otherwise.

### GAP 6 — SafeZone capacity is non-nullable
`SafeZone.capacityTotal: Int` / `capacityCurrent: Int` are non-null and
`availableCapacity = total - current`. Section 16 requires nullable capacity and
"Capacity incomplete — N constraints unavailable" rather than 0.

### GAP 7 — no provider registry usable at runtime
`IndiaSourceRegistry` (Phase 1) exists but is not surfaced anywhere and lacks
integration status / failure behaviour fields required by section 9.

### GAP 8 — IMD/CWC/NDEM/INCOIS explicit unavailable providers
Only IMD has a provider (and it's the S3 CAP bucket). CWC/NDEM/INCOIS have no
abstraction at all, so there is nothing to show an honest unavailable state.

## Build order (dependency-driven)

1. `SachetEtagStore` + `ConditionalFetch` — ETag correctness (GAP 2).
2. `SachetCapProvider` — real site, RSS→CAP→Polygon URL, 3 requests deep (GAP 1).
3. `GeometryValidator` — validate/reject, never repair (GAP 3).
4. `SachetPolygonParser` — parse `FetchPolygonXMLFile` `<polygon>` text.
5. Stop quake radius synthesis (GAP 4) — route through InferenceGuard.
6. `ShelterRegistry` import-ready schema, all numerics nullable (GAP 5/6).
7. `ProviderRegistry` with integration status + failure behaviour (GAP 7).
8. Unavailable providers for CWC/NDEM/INCOIS (GAP 8).
9. Wire into repository + ViewModel; keep LIVE/DEMO separation.
10. Tests for every item in section 30.
11. Full suite + debug APK.

## Non-goals for this phase
- No UI redesign (section 29 comes last, and only after the pipeline works).
- No bundling of the 555 MB NWIC archives into assets (section 5/32).
- No new auth/onboarding work (section 28).
