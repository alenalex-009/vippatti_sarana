package com.example.data.habitations

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.data.suitability.ElevationGrid
import com.example.data.suitability.SuitabilityBand
import com.example.data.suitability.TerrainSuitabilityEngine
import com.example.data.suitability.TerrainVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contracts for the HABITATION PRIORITIZATION engine (SIH 26191 flagship ask:
 * "prioritize vulnerable habitations for immediate, short-term and
 * medium-term relocation"). Scoring must be transparent, deterministic,
 * history-separated, and NEVER fabricate population or hazard inputs.
 */
class HabitationPriorityEngineTest {

  private val origin = GeoPoint(10.0, 76.5)

  private fun hazard(
    id: String, at: GeoPoint = origin, radius: Double = 3_000.0,
    severity: HazardSeverity = HazardSeverity.EXTREME, type: HazardType = HazardType.FLOOD
  ) = HazardZone(
    id = id, name = "Hazard $id", type = type, severity = severity,
    center = at, radiusMeters = radius, riskLevel = severity.label,
    trend = HazardTrend.WORSENING, sourceStatus = "LIVE FEED",
    lastUpdatedMillis = System.currentTimeMillis(),
    provenance = DataProvenance(source = "test", classification = DataClassification.OBSERVED)
  )

  private fun safe(id: String, at: GeoPoint) = SafeZone(
    id = id, name = "Shelter $id", lat = at.lat, lon = at.lon,
    locationNote = "t", capacityTotal = 500, capacityCurrent = 0,
    waterAvailable = true, foodAvailable = true, electricityAvailable = true,
    sanitationAvailable = true, medicalSupport = true, accessibility = "Road",
    womenChildrenSuitability = true, operatingStatus = "OPEN",
    verificationStatus = "FIELD", elevationNote = "",
    provenance = DataProvenance(source = "test")
  )

  private fun hab(
    id: String, pop: Int? = 500, vulnerable: Int = 0,
    verdict: TerrainVerdict? = null, historyCount: Int = 0
  ) = Habitation(
    id = id, name = "Hab $id", point = origin,
    population = pop?.let {
      PopulationInput(value = it, classification = DataClassification.ESTIMATED, source = "test")
    },
    vulnerableShare = if (pop != null && pop > 0) vulnerable.toFloat() / pop else null,
    terrainVerdict = verdict,
    historicalEventCount = historyCount
  )

  private val safeVerdict = TerrainSuitabilityEngine.evaluate(
    ElevationGrid(300.0, 300.1, 300.0, 300.0, 300.0, 30.0), null, false
  )
  private val redVerdict = TerrainSuitabilityEngine.evaluate(
    ElevationGrid(800.0, 830.0, 799.0, 800.0, 800.0, 30.0), null, false
  )

  // ---- tiers ----------------------------------------------------------------

  @Test
  fun `habitation inside an extreme hazard radius is IMMEDIATE`() {
    val result = HabitationPriorityEngine.rank(
      habitations = listOf(hab("a", verdict = safeVerdict)),
      hazards = listOf(hazard("h1")),
      safeZones = listOf(safe("s1", GeoPoint(10.05, 76.5)))
    )
    assertEquals(RelocationTier.IMMEDIATE, result.single().tier)
  }

  @Test
  fun `terrain RED ZONE without a live hazard is at least SHORT_TERM`() {
    val result = HabitationPriorityEngine.rank(
      habitations = listOf(hab("a", verdict = redVerdict)),
      hazards = emptyList(),
      safeZones = listOf(safe("s1", GeoPoint(10.05, 76.5)))
    )
    val r = result.single()
    assertTrue("tier was ${r.tier}", r.tier == RelocationTier.SHORT_TERM || r.tier == RelocationTier.IMMEDIATE)
    assertTrue(r.reasons.any { it.contains("RED ZONE", ignoreCase = true) || it.contains("red-zone", ignoreCase = true) })
  }

  @Test
  fun `safe terrain, no hazards, history-free is LOW priority`() {
    val result = HabitationPriorityEngine.rank(
      habitations = listOf(hab("a", verdict = safeVerdict)),
      hazards = emptyList(),
      safeZones = listOf(safe("s1", GeoPoint(10.05, 76.5)))
    )
    assertEquals(RelocationTier.LOW, result.single().tier)
  }

  @Test
  fun `repeated disaster history escalates by one band, labelled HISTORICAL`() {
    val noHistory = HabitationPriorityEngine.rank(
      listOf(hab("a", verdict = safeVerdict, historyCount = 0)), emptyList(),
      listOf(safe("s1", GeoPoint(10.05, 76.5)))
    ).single()
    val withHistory = HabitationPriorityEngine.rank(
      listOf(hab("a", verdict = safeVerdict, historyCount = 8)), emptyList(),
      listOf(safe("s1", GeoPoint(10.05, 76.5)))
    ).single()
    assertTrue(withHistory.score > noHistory.score)
    assertTrue(withHistory.reasons.any { it.contains("historical", ignoreCase = true) })
    // tier may rise by at most one band from history alone
    assertTrue(noHistory.tier.ordinal - withHistory.tier.ordinal <= 1)
  }

  // ---- ordering & inputs ------------------------------------------------------

  @Test
  fun `ordering is strictly by descending score and deterministic for ties`() {
    val habs = listOf(
      hab("b", pop = 100, vulnerable = 10, verdict = safeVerdict),
      hab("a", pop = 5_000, vulnerable = 2_500, verdict = safeVerdict)
    )
    val ranked = HabitationPriorityEngine.rank(habs, emptyList(), listOf(safe("s1", GeoPoint(10.05, 76.5))))
    assertEquals(listOf("a", "b"), ranked.map { it.habitation.id })
    // determinism: same inputs, shuffled order -> same output
    val again = HabitationPriorityEngine.rank(habs.reversed(), emptyList(), listOf(safe("s1", GeoPoint(10.05, 76.5))))
    assertEquals(ranked.map { it.habitation.id }, again.map { it.habitation.id })
  }

  @Test
  fun `missing population never blocks a tier - the gap is stated`() {
    val r = HabitationPriorityEngine.rank(
      listOf(hab("a", pop = null, verdict = redVerdict)), emptyList(),
      listOf(safe("s1", GeoPoint(10.05, 76.5)))
    ).single()
    assertTrue(r.reasons.any { it.contains("population", ignoreCase = true) && it.contains("not", ignoreCase = true) })
    assertTrue(r.tier == RelocationTier.SHORT_TERM || r.tier == RelocationTier.MEDIUM_TERM)
  }

  @Test
  fun `habitations without a terrain verdict cannot exceed MEDIUM_TERM without live hazard`() {
    val r = HabitationPriorityEngine.rank(
      listOf(hab("a", verdict = null)), emptyList(),
      listOf(safe("s1", GeoPoint(10.05, 76.5)))
    ).single()
    assertTrue("tier was ${r.tier}", r.tier == RelocationTier.MEDIUM_TERM || r.tier == RelocationTier.LOW)
    assertTrue(r.reasons.any { it.contains("not assessed", ignoreCase = true) })
  }

  @Test
  fun `nearest safe zone distance feeds the relocation note`() {
    val r = HabitationPriorityEngine.rank(
      listOf(hab("a", verdict = safeVerdict)), emptyList(),
      listOf(safe("far", GeoPoint(10.20, 76.5)), safe("near", GeoPoint(10.01, 76.5)))
    ).single()
    assertEquals("near", r.nearestSafeZoneId)
    assertTrue((r.nearestSafeZoneDistanceMeters ?: 0.0) < 5_000.0)
  }

  @Test
  fun `score is bounded zero to hundred and every line explains itself`() {
    val r = HabitationPriorityEngine.rank(
      listOf(hab("a", pop = 900, vulnerable = 600, verdict = redVerdict, historyCount = 12)),
      listOf(hazard("h1")), listOf(safe("s1", GeoPoint(10.05, 76.5)))
    ).single()
    assertTrue(r.score in 0..100)
    assertTrue(r.reasons.isNotEmpty())
    assertTrue(r.tier.actionGuide.isNotBlank())
  }

  @Test
  fun `summary counts every tier exactly once`() {
    val ranked = HabitationPriorityEngine.rank(
      listOf(
        hab("hot", verdict = safeVerdict),
        hab("red", verdict = redVerdict),
        hab("calm", verdict = safeVerdict)
      ),
      listOf(hazard("h1")),
      listOf(safe("s1", GeoPoint(10.05, 76.5)))
    )
    // All three sit at the origin inside the extreme hazard -> all IMMEDIATE.
    val s = HabitationPriorityEngine.summary(ranked)
    assertEquals(3, s[RelocationTier.IMMEDIATE])
    assertEquals(3, ranked.size)
    assertTrue(s.values.sum() == ranked.size)
    // and the ranking is ordered by score with stable tie-break by id
    // red scores highest (RED terrain factor); calm < hot tie-break by id
    assertEquals("red", ranked.first().habitation.id)
    assertEquals(listOf("calm", "hot"), ranked.drop(1).map { it.habitation.id })
  }
}
