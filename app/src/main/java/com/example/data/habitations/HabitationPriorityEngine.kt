package com.example.data.habitations

import com.example.data.model.DataClassification
import com.example.data.model.GeoMath
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.data.suitability.SuitabilityBand
import com.example.data.suitability.TerrainVerdict

/**
 * ============================================================================
 * HABITATION PRIORITIZATION FOR RELOCATION (SIH 26191)
 * ============================================================================
 *
 * "Prioritize vulnerable habitations for immediate, short-term and
 * medium-term relocation" — the ranking half of the problem statement,
 * implemented as a TRANSPARENT multi-criteria engine (no black-box model
 * making life-or-death claims):
 *
 *   hazard exposure 35% • terrain habitability 30% • vulnerability 20% •
 *   disaster history 15%
 *
 * Honesty rules, same as the rest of the codebase:
 * - Population is INPUT, never invented: a missing figure lowers the score
 *   transparently and is stated in the reasons.
 * - EM-DAT history is HISTORICAL context: it may escalate a tier by at most
 *   ONE band and is always labelled as history, never treated as a live hazard.
 * - A habitability verdict that was never assessed is stated as unassessed —
 *   the engine cannot claim a habitation is safe.
 * - Output = score + per-factor reasons + recommended tier + nearest safe
 *   zone distance, so every row is explainable to an authority.
 */

/** A population figure with its honest classification. */
data class PopulationInput(
  val value: Int,
  val classification: DataClassification,
  val source: String
)

/** One habitation to assess — geometry plus everything actually known about it. */
data class Habitation(
  val id: String,
  val name: String,
  val point: GeoPoint,
  val population: PopulationInput? = null,
  /** 0..1 share of residents flagged vulnerable (children/elderly/disabled). */
  val vulnerableShare: Float? = null,
  /** Terrain habitability verdict from the suitability engine, null = unassessed. */
  val terrainVerdict: TerrainVerdict? = null,
  /** Count of EM-DAT archive events matching this locality (history only). */
  val historicalEventCount: Int = 0
)

/** The three planning horizons from the problem statement + a low tail. */
enum class RelocationTier(val label: String, val actionGuide: String) {
  IMMEDIATE("IMMEDIATE", "Relocate ahead of the next rainfall/event; brief the district officer today."),
  SHORT_TERM("SHORT-TERM", "Plan relocation within this season: site visits, consent, transport."),
  MEDIUM_TERM("MEDIUM-TERM", "Add to the annual panchayat plan; monitor hazards each season."),
  LOW("LOW", "No relocation case on current evidence; keep monitoring.")
}

/** One ranked row of the authority output. */
data class HabitationPriority(
  val habitation: Habitation,
  val score: Int,
  val tier: RelocationTier,
  /** Every factor's contribution in plain language, highest first. */
  val reasons: List<String>,
  val nearestSafeZoneId: String?,
  val nearestSafeZoneDistanceMeters: Double?
)

object HabitationPriorityEngine {

  fun rank(
    habitations: List<Habitation>,
    hazards: List<HazardZone>,
    safeZones: List<SafeZone>
  ): List<HabitationPriority> = habitations
    .map { score(it, hazards, safeZones) }
    .sortedWith(compareByDescending<HabitationPriority> { it.score }.thenBy { it.habitation.id })

  fun summary(ranked: List<HabitationPriority>): Map<RelocationTier, Int> =
    ranked.groupingBy { it.tier }.eachCount()

  private fun score(
    hab: Habitation,
    hazards: List<HazardZone>,
    safeZones: List<SafeZone>
  ): HabitationPriority {
    val reasons = mutableListOf<String>()

    // --- hazard exposure (35%) ------------------------------------------------
    val covering = hazards.filter { GeoMath.isWithinRadius(hab.point, it.center, it.radiusMeters) }
    val worstSeverity = covering.maxOfOrNull { it.severity.weight } ?: 0
    val exposureScore = worstSeverity * 25.0 // 0, 25..100
    if (covering.isNotEmpty()) {
      reasons.add(
        "Inside ${covering.size} active hazard area(s); worst severity ${covering.maxByOrNull { it.severity.weight }!!.severity.label} — LIVE hazard exposure."
      )
    } else {
      reasons.add("No live hazard area covers this habitation right now.")
    }

    // --- terrain habitability (30%) --------------------------------------------
    val terrainScore = when (hab.terrainVerdict?.band) {
      SuitabilityBand.RED_ZONE -> {
        reasons.add("Terrain assessed as a dynamic RED ZONE (slope/rainfall/coast analysis).")
        100.0
      }
      SuitabilityBand.HIGH_RISK -> {
        reasons.add("Terrain rated HIGH RISK — relocation candidate.")
        70.0
      }
      SuitabilityBand.CAUTION -> {
        reasons.add("Terrain rated CAUTION — review before treating as safe.")
        40.0
      }
      SuitabilityBand.SAFE -> {
        reasons.add("Terrain rates SAFE (gentle slope, no live rainfall escalation).")
        10.0
      }
      null -> {
        reasons.add("Terrain not assessed — the engine cannot claim this site is safe.")
        30.0
      }
    }

    // --- vulnerability (20%): population scale + vulnerable share --------------
    var vulnScore = 0.0
    val pop = hab.population
    if (pop != null && pop.value > 0) {
      val scale = (pop.value / 5_000f).coerceIn(0f, 1f) * 30.0
      val share = (hab.vulnerableShare ?: 0f).coerceIn(0f, 1f) * 70.0
      vulnScore = (scale + share).coerceAtMost(100.0)
      if (hab.vulnerableShare != null) {
        reasons.add("Population ${pop.value} (${pop.classification.label.lowercase()} source) with ${"%.0f".format(hab.vulnerableShare * 100)}% vulnerable residents.")
      } else {
        reasons.add("Population ${pop.value} (${pop.classification.label.lowercase()}) — vulnerable share not provided, so it scores nothing, not zero-risk.")
      }
    } else {
      reasons.add("Population not provided for this habitation — exposure size cannot be weighted.")
    }

    // --- historical context (15%), escalation capped at one band ---------------
    val historyScore = (hab.historicalEventCount.coerceAtMost(10)) * 10.0
    if (hab.historicalEventCount > 0) {
      reasons.add(
        "${hab.historicalEventCount} matching events in the EM-DAT historical archive — HISTORICAL context, never a live hazard."
      )
    }

    val total = (
      exposureScore * W_EXPOSURE +
        terrainScore * W_TERRAIN +
        vulnScore * W_VULNERABILITY +
        historyScore * W_HISTORY
      ).toInt().coerceIn(0, 100)

    // --- tier: rule-based first, then the bounded history escalation -----------
    var tier = when {
      covering.isNotEmpty() && (worstSeverity >= HazardSeverity.HIGH.weight) -> RelocationTier.IMMEDIATE
      covering.isNotEmpty() -> RelocationTier.IMMEDIATE
      hab.terrainVerdict?.band == SuitabilityBand.RED_ZONE -> RelocationTier.SHORT_TERM
      hab.terrainVerdict?.band == SuitabilityBand.HIGH_RISK -> RelocationTier.SHORT_TERM
      hab.terrainVerdict == null -> RelocationTier.MEDIUM_TERM
      hab.terrainVerdict?.band == SuitabilityBand.CAUTION -> RelocationTier.MEDIUM_TERM
      else -> RelocationTier.LOW
    }
    if (hab.historicalEventCount >= HISTORY_ESCALATION_COUNT && tier != RelocationTier.IMMEDIATE) {
      tier = RelocationTier.entries[tier.ordinal - 1]
      reasons.add("Recurring disaster history escalates the plan horizon by one band.")
    }

    // --- nearest registered safe zone (distance only, feasibility is judged by //
    // SafeZoneEvaluator elsewhere) ----------------------------------------------
    val nearest = safeZones.minByOrNull { GeoMath.distanceMeters(hab.point, it.point) }
    val distance = nearest?.let { GeoMath.distanceMeters(hab.point, it.point) }
    if (nearest != null && distance != null) {
      reasons.add("Nearest registered safe zone ${nearest.name}: ${GeoMath.formatKm(distance)} away.")
    } else {
      reasons.add("No registered safe zone available to measure against.")
    }

    return HabitationPriority(
      habitation = hab,
      score = total,
      tier = tier,
      reasons = reasons,
      nearestSafeZoneId = nearest?.id,
      nearestSafeZoneDistanceMeters = distance
    )
  }

  private const val W_EXPOSURE = 0.35
  private const val W_TERRAIN = 0.30
  private const val W_VULNERABILITY = 0.20
  private const val W_HISTORY = 0.15
  /** Matching archive events needed to raise the planning horizon one band. */
  const val HISTORY_ESCALATION_COUNT = 5
}
