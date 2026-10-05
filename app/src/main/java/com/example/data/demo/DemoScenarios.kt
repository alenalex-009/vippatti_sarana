package com.example.data.demo

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
// The app's canonical GeoPoint. data.india.GeoPoint also exists (Phase 1 bbox
// helper) but is not the type the hazard/shelter models carry.
import com.example.data.routing.GeoPoint

/**
 * ============================================================================
 * DETERMINISTIC DEMO SCENARIOS (section 27)
 * ============================================================================
 *
 * Eight named situations a judge can request, each producing the SAME geometry
 * on every run so a demonstration is reproducible.
 *
 * The scenarios demonstrate the app's CORRECT behaviour under awkward inputs —
 * that is their whole purpose. Several of them deliberately have no geometry, or
 * no verified shelter, or no safe route, because those are the cases a real
 * disaster app must handle honestly and most demos hide.
 *
 * EVERY record here is SIMULATED, carries a demo-namespaced id, and is only
 * reachable through demo mode. Nothing in this file is a claim about a real
 * place.
 *
 * Anchors are real Indian coordinates (Visakhapatnam / the Godavari delta area,
 * the declared pilot region) so map context is coherent, but every radius,
 * capacity and shelter record is fabricated FOR DEMONSTRATION and labelled so.
 */
enum class DemoScenario(val id: Int, val title: String, val judgeQuestion: String) {
  FLOOD_EDGE(
    1, "Flood — user at the edge",
    "Does it send me AWAY from the water rather than through it?"
  ),

  FLOOD_CENTRE(
    2, "Flood — user near the centre",
    "With the user surrounded, do I get several viable directions?"
  ),

  EARTHQUAKE_POINT(
    3, "Earthquake — point event, no polygon",
    "Does it invent a danger circle around the epicentre?"
  ),

  FIRE_HOTSPOT(
    4, "Fire — satellite hotspot",
    "Does it draw a fire boundary that was never published?"
  ),

  CYCLONE_SHELTER(
    5, "Cyclone — shelter suitability",
    "Is the recommended centre actually designated for this disaster?"
  ),

  NO_VERIFIED_SHELTER(
    6, "No verified evacuation centre",
    "What does it show when no authoritative facility exists?"
  ),

  ROUTE_THROUGH_HAZARD(
    7, "Route crosses the hazard",
    "Does it call that route safe anyway?"
  ),

  PROVIDER_OFFLINE(
    8, "Provider offline — cached data",
    "Does stale data get relabelled as live?"
  );

  companion object {
    fun byId(id: Int): DemoScenario? = entries.firstOrNull { it.id == id }
  }
}

/** What a scenario produces for the map and the decision panels. */
data class DemoScenarioData(
  val scenario: DemoScenario,
  val userLocation: GeoPoint,
  val hazard: HazardZone?,
  val shelters: List<DemoShelter>,
  /** Plain-language statement of what this scenario is meant to show. */
  val expectation: String
)

/** A simulated shelter. Always labelled; never a real facility. */
data class DemoShelter(
  val id: String,
  val name: String,
  val point: GeoPoint,
  val capacityTotal: Int?,
  val capacityOccupied: Int?,
  val designatedFor: Set<HazardType>,
  val label: String = "SIMULATED / DEMO"
) {
  val capacityKnown: Boolean get() = capacityTotal != null
  val capacityLabel: String
    get() = when {
      capacityTotal == null -> "Capacity unavailable"
      capacityOccupied == null -> "$capacityTotal capacity, occupancy unknown"
      else -> "$capacityOccupied / $capacityTotal occupied"
    }
}

object DemoScenarios {

  // ---- Anchors: real coordinates in the declared pilot region -------------
  /** Visakhapatnam city — the pilot/demo anchor. */
  val VISAKHAPATNAM = GeoPoint(17.6868, 83.2185)

  private val simulatedProvenance = DataProvenance(
    source = "Vippatti Sarana demo scenario (simulated)",
    status = DataProvenance.STATUS_FIELD,
    confidence = 0.0,
    isVerified = false,
    classification = DataClassification.SIMULATED
  )

  private const val DEMO_TS = 1_757_000_000_000L // fixed -> deterministic freshness labels

  /**
   * Builds the fixture for a scenario. Pure and deterministic: identical input
   * yields byte-identical output, so a judge's run matches a rehearsed one.
   */
  fun build(scenario: DemoScenario): DemoScenarioData = when (scenario) {
    DemoScenario.FLOOD_EDGE -> floodEdge()
    DemoScenario.FLOOD_CENTRE -> floodCentre()
    DemoScenario.EARTHQUAKE_POINT -> earthquakePoint()
    DemoScenario.FIRE_HOTSPOT -> fireHotspot()
    DemoScenario.CYCLONE_SHELTER -> cycloneShelter()
    DemoScenario.NO_VERIFIED_SHELTER -> noVerifiedShelter()
    DemoScenario.ROUTE_THROUGH_HAZARD -> routeThroughHazard()
    DemoScenario.PROVIDER_OFFLINE -> providerOffline()
  }

  /** All scenarios, in demo order, for a scenario picker. */
  fun all(): List<DemoScenarioData> = DemoScenario.entries.map { build(it) }

  // -- Scenario 1: user at the EDGE of a flood polygon ----------------------
  private fun floodEdge(): DemoScenarioData {
    // Hazard centred ~3 km east of the user. The user sits just inside its
    // western edge, so the correct move is WEST — away from the centre.
    val hazardCenter = offset(VISAKHAPATNAM, 3.0, 90.0) // due east
    val hazard = zone(
      id = "demo-s1-flood",
      name = "Simulated flood affected area",
      type = HazardType.FLOOD,
      center = hazardCenter,
      radiusMeters = 3_000.0,
      severity = HazardSeverity.HIGH
    )
    return DemoScenarioData(
      scenario = DemoScenario.FLOOD_EDGE,
      // User at the western rim of the circle.
      userLocation = offset(hazardCenter, 2_900.0, 270.0),
      hazard = hazard,
      shelters = listOf(
        DemoShelter(
          id = "demo-s1-shelter-west",
          name = "Simulated shelter (west, outward)",
          point = offset(hazardCenter, 5_000.0, 270.0),
          capacityTotal = 400,
          capacityOccupied = 120,
          designatedFor = setOf(HazardType.FLOOD)
        ),
        DemoShelter(
          id = "demo-s1-shelter-near-east",
          name = "Simulated shelter (east, toward hazard centre)",
          point = offset(hazardCenter, 3_500.0, 90.0),
          capacityTotal = 250,
          capacityOccupied = 40,
          designatedFor = setOf(HazardType.FLOOD)
        )
      ),
      expectation = "The nearer shelter sits EAST, through the hazard. The farther one WEST " +
        "keeps the evacuation path outside the affected area, so outward must win on " +
        "exposure even though it is not the closest."
    )
  }

  // -- Scenario 2: user near the CENTRE, several directions viable ----------
  private fun floodCentre(): DemoScenarioData {
    val hazardCenter = offset(VISAKHAPATNAM, 0.0, 0.0)
    val hazard = zone(
      id = "demo-s2-flood",
      name = "Simulated flood affected area (centre case)",
      type = HazardType.FLOOD,
      center = hazardCenter,
      radiusMeters = 4_000.0,
      severity = HazardSeverity.EXTREME
    )
    return DemoScenarioData(
      scenario = DemoScenario.FLOOD_CENTRE,
      userLocation = offset(hazardCenter, 600.0, 45.0), // inside, near centre
      hazard = hazard,
      shelters = listOf(
        DemoShelter(
          id = "demo-s2-north",
          name = "Simulated shelter (north)",
          point = offset(hazardCenter, 6_500.0, 0.0),
          capacityTotal = 600,
          capacityOccupied = 90,
          designatedFor = setOf(HazardType.FLOOD)
        ),
        DemoShelter(
          id = "demo-s2-south",
          name = "Simulated shelter (south)",
          point = offset(hazardCenter, 6_200.0, 180.0),
          capacityTotal = 500,
          capacityOccupied = 300,
          designatedFor = setOf(HazardType.FLOOD)
        ),
        DemoShelter(
          id = "demo-s2-east",
          name = "Simulated shelter (east)",
          point = offset(hazardCenter, 5_800.0, 90.0),
          capacityTotal = null, // capacity genuinely unknown -> must say so
          capacityOccupied = null,
          designatedFor = setOf(HazardType.FLOOD)
        )
      ),
      expectation = "Surrounded by the hazard, several directions are viable. Ranking must " +
        "reflect exposure, capacity and designation — not simply pick the first or nearest. " +
        "The eastern shelter has UNKNOWN capacity, which must be stated, not zeroed."
    )
  }

  // -- Scenario 3: earthquake POINT, explicitly no polygon -------------------
  private fun earthquakePoint(): DemoScenarioData = DemoScenarioData(
    scenario = DemoScenario.EARTHQUAKE_POINT,
    userLocation = VISAKHAPATNAM,
    // hazard == null: a point epicentre has no area, so no zone is produced.
    hazard = null,
    shelters = listOf(
      DemoShelter(
        id = "demo-s3-shelter",
        name = "Simulated open assembly ground",
        point = offset(VISAKHAPATNAM, 4_000.0, 45.0),
        capacityTotal = null,
        capacityOccupied = null,
        designatedFor = setOf(HazardType.EARTHQUAKE)
      )
    ),
    expectation = "A magnitude-derived 'felt area' circle is not published by USGS, so none " +
      "is drawn. The earthquake appears as an event with magnitude, depth and place, and " +
      "shelter guidance says structural safety is NOT ASSESSED."
  )

  // -- Scenario 4: FIRMS-style hotspot, no perimeter ------------------------
  private fun fireHotspot(): DemoScenarioData = DemoScenarioData(
    scenario = DemoScenario.FIRE_HOTSPOT,
    userLocation = VISAKHAPATNAM,
    // A single detection point. NOT a fire area.
    hazard = null,
    shelters = listOf(
      DemoShelter(
        id = "demo-s4-shelter",
        name = "Simulated shelter (upwind side)",
        point = offset(VISAKHAPATNAM, 6_000.0, 270.0),
        capacityTotal = 200,
        capacityOccupied = null,
        designatedFor = setOf(HazardType.FIRE)
      )
    ),
    expectation = "Hotspots are single satellite overpass pixels. The app must say " +
      "'satellite fire hotspot' and must not shade a fire boundary, invent a wind " +
      "direction, or treat pixel count as burned area."
  )

  // -- Scenario 5: cyclone, suitability by designation ----------------------
  private fun cycloneShelter(): DemoScenarioData {
    val anchor = offset(VISAKHAPATNAM, 1.0, 0.0)
    return DemoScenarioData(
      scenario = DemoScenario.CYCLONE_SHELTER,
      userLocation = anchor,
      hazard = zone(
        id = "demo-s5-cyclone",
        name = "Simulated cyclone warning area",
        type = HazardType.CYCLONE,
        center = anchor,
        radiusMeters = 20_000.0,
        severity = HazardSeverity.HIGH
      ),
      shelters = listOf(
        DemoShelter(
          id = "demo-s5-designated",
          name = "Simulated DESIGNATED cyclone shelter",
          point = offset(anchor, 14_000.0, 315.0),
          capacityTotal = 800,
          capacityOccupied = 210,
          designatedFor = setOf(HazardType.CYCLONE, HazardType.FLOOD)
        ),
        DemoShelter(
          id = "demo-s5-generic",
          name = "Simulated general hall (not cyclone-designated)",
          point = offset(anchor, 2_000.0, 0.0),
          capacityTotal = 300,
          capacityOccupied = 30,
          designatedFor = setOf(HazardType.FLOOD)
        )
      ),
      expectation = "A closer general hall loses to a farther designated cyclone shelter. " +
        "Suitability is driven by designation, and storm-surge exposure is reported as " +
        "NOT ASSESSED rather than assumed safe because the site is inland."
    )
  }

  // -- Scenario 6: honest empty state ---------------------------------------
  private fun noVerifiedShelter(): DemoScenarioData = DemoScenarioData(
    scenario = DemoScenario.NO_VERIFIED_SHELTER,
    userLocation = VISAKHAPATNAM,
    hazard = zone(
      id = "demo-s6-flood",
      name = "Simulated flood affected area",
      type = HazardType.FLOOD,
      center = offset(VISAKHAPATNAM, 2.0, 0.0),
      radiusMeters = 3_500.0,
      severity = HazardSeverity.HIGH
    ),
    shelters = emptyList(),
    expectation = "No authoritative evacuation-centre registry is reachable, so the app must " +
      "state 'No verified evacuation centre found for this area' instead of promoting a " +
      "school, hospital or community hall POI into a shelter."
  )

  // -- Scenario 7: the only route crosses the hazard ------------------------
  private fun routeThroughHazard(): DemoScenarioData {
    val hazardCenter = offset(VISAKHAPATNAM, 4.0, 0.0)
    return DemoScenarioData(
      scenario = DemoScenario.ROUTE_THROUGH_HAZARD,
      userLocation = offset(hazardCenter, 4_200.0, 270.0),
      hazard = zone(
        id = "demo-s7-flood",
        name = "Simulated flood affected area",
        type = HazardType.FLOOD,
        center = hazardCenter,
        radiusMeters = 5_000.0,
        severity = HazardSeverity.EXTREME
      ),
      shelters = listOf(
        DemoShelter(
          id = "demo-s7-near",
          name = "Simulated shelter (closest, across the hazard)",
          point = offset(hazardCenter, 3_000.0, 90.0),
          capacityTotal = 300,
          capacityOccupied = 20,
          designatedFor = setOf(HazardType.FLOOD)
        )
      ),
      expectation = "The nearest candidate is only reachable by cutting through the affected " +
        "area. The route must be flagged as crossing hazard exposure AND marked " +
        "'route safety unverified' — a computed path is never called safe."
    )
  }

  // -- Scenario 8: offline, cached/stale labelling --------------------------
  private fun providerOffline(): DemoScenarioData = DemoScenarioData(
    scenario = DemoScenario.PROVIDER_OFFLINE,
    userLocation = VISAKHAPATNAM,
    hazard = zone(
      id = "demo-s8-cached-flood",
      name = "Simulated cached flood record (from cache, not live)",
      type = HazardType.FLOOD,
      center = offset(VISAKHAPATNAM, 5.0, 90.0),
      radiusMeters = 4_500.0,
      severity = HazardSeverity.MODERATE
    ),
    shelters = listOf(
      DemoShelter(
        id = "demo-s8-shelter",
        name = "Simulated shelter (cached record)",
        point = offset(VISAKHAPATNAM, 9.0, 90.0),
        capacityTotal = 450,
        capacityOccupied = 150,
        designatedFor = setOf(HazardType.FLOOD)
      )
    ),
    expectation = "With providers unreachable the app serves cached records labelled CACHED / " +
      "STALE with their retrieval time. Nothing may be relabelled LIVE, and the recorded " +
      "timestamp stays fixed so the staleness is visible."
  )

  // -- helpers ---------------------------------------------------------------

  private fun zone(
    id: String,
    name: String,
    type: HazardType,
    center: GeoPoint,
    radiusMeters: Double,
    severity: HazardSeverity
  ) = HazardZone(
    id = id,
    name = name,
    type = type,
    severity = severity,
    center = center,
    radiusMeters = radiusMeters,
    riskLevel = severity.label,
    trend = HazardTrend.STABLE,
    sourceStatus = "SIMULATED demo scenario — not a real observation",
    lastUpdatedMillis = DEMO_TS,
    provenance = simulatedProvenance
  )

  /** Great-circle offset, matching GeoMath.offsetPoint so distances are exact. */
  private fun offset(origin: GeoPoint, meters: Double, bearingDeg: Double): GeoPoint =
    com.example.data.model.GeoMath.offsetPoint(origin, bearingDeg, meters)
}