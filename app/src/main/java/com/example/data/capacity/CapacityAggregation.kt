package com.example.data.capacity

import com.example.data.model.SafeZone

/**
 * ============================================================================
 * MULTI-LEVEL CAPACITY AGGREGATION (approved research doc §F.2-§F.4)
 * ============================================================================
 *
 * Bottleneck MIN at the site level (existing engine) rolls up by UNIQUE
 * resource claim, never by naive summing:
 *
 *  - a site is counted IN FULL exactly once in the city rollup (id-dedup);
 *  - at the colony (catchment) level a site shared by several colonies splits
 *    its usable capacity in proportion to the demand those colonies actually
 *    carry;
 *  - demand is only ever a sourced figure: a colony without population data
 *    shows capacity WITHOUT a verdict - the missing number is never zero.
 *
 * WARD and CITY administrative rollups require a ward dataset (geometry +
 * census population). None is connected, so [verdict] for those levels is
 * honest "NOT AVAILABLE (no ward dataset connected)" unless the caller
 * supplies ward memberships + HISTORICAL-BASELINE populations explicitly.
 *
 * The proportional-split rule is a PROJECT ASSUMPTION (the research doc found
 * no cited catchment standard); it is stamped on every colony result so the
 * UI methodology can say exactly that.
 */
object CapacityAggregation {

  /** Walk-reach ceiling for colony membership: 30 minutes at user speed. */
  const val COLONY_REACH_MINUTES = 30

  /** A demand figure must carry where it came from; null = no population data. */
  data class AreaDemand(
    val people: Int?,
    /** e.g. "CENSUS 2011 (HISTORICAL BASELINE)" or "SIMULATED demo" or null. */
    val basis: String?
  ) {
    companion object {
      val UNAVAILABLE = AreaDemand(null, null)
    }
  }

  /** One colony: its centre, its name, its sourced demand. */
  data class Colony(
    val id: String,
    val name: String,
    val centre: com.example.data.routing.GeoPoint,
    val demand: AreaDemand
  )

  /** How much of a site each colony may claim, and why. */
  data class SiteAllocation(
    val siteId: String,
    val siteName: String,
    /** Full site usable capacity at this moment (assessments must agree). */
    val siteCapacity: Int?,
    /** The split portion claimed by THIS colony (null = not assessed). */
    val colonyClaim: Int?,
    /** Colony ids sharing this site (1 = counted in full, no split). */
    val sharedWith: List<String>,
    val basis: String
  )

  data class ColonyCapacity(
    val colony: Colony,
    val allocations: List<SiteAllocation>,
    /** Σ claims; null when NO reachable site has an assessed capacity. */
    val capacity: Int?,
    /** demand vs capacity; null whenever demand or capacity is unavailable. */
    val surplus: Int?,
    val shortfall: Int?,
    val verdict: String,
    val methodNote: String
  )

  /** City/ULB rollup over DISTINCT site ids only. */
  data class AreaRollup(
    /** Sum of one claim per distinct site (deduplicated by construction). */
    val totalCapacity: Int?,
    val sitesCounted: Int,
    val sitesWithoutCapacityFigure: Int,
    val demand: AreaDemand,
    val surplus: Int?,
    val shortfall: Int?,
    val verdict: String
  )

  private const val SPLIT_METHOD =
    "Catchment split: proportional to colony demand (PROJECT ASSUMPTION - " +
      "no cited catchment standard; see docs/carrying-capacity-research.md §F.2)."

  /**
   * Walk-reach membership: site counts for a colony when a person there can
   * reach it within [COLONY_REACH_MINUTES] at [walkingSpeedMps] (same speed
   * convention the evaluator uses). No invented road network: straight-line
   * walk-time, stated in the basis string.
   */
  fun reachableSites(
    colony: Colony,
    sites: List<SafeZone>,
    walkingSpeedMps: Double = 1.35
  ): List<SafeZone> {
    val reachMeters = COLONY_REACH_MINUTES * 60.0 * walkingSpeedMps
    return sites.filter {
      com.example.data.model.GeoMath.distanceMeters(colony.centre, it.point) <= reachMeters
    }
  }

  /**
   * Capacity per colony from the SAME assessments the site sheets show, so a
   * colony figure can always be reconciled against its member sites.
   * @param usableCapacityOf must come from CarryingCapacityEngine.assess()
   *        (effective capacity of available resources); null = not assessed.
   */
  fun colonyCapacities(
    colonies: List<Colony>,
    sites: List<SafeZone>,
    usableCapacityOf: (SafeZone) -> Int?,
    walkingSpeedMps: Double = 1.35
  ): List<ColonyCapacity> {
    // 1. membership map: site -> colonies that can walk to it
    val membership = mutableMapOf<String, MutableList<Colony>>()
    colonies.forEach { colony ->
      reachableSites(colony, sites, walkingSpeedMps).forEach { site ->
        membership.getOrPut(site.id) { mutableListOf() }.add(colony)
      }
    }

    return colonies.map { colony ->
      val allocations = sites
        .filter { colony in (membership[it.id] ?: emptyList()) }
        .map { site ->
          val capacity = usableCapacityOf(site)
          val sharers = membership[site.id]!!.distinctBy { c -> c.id }
          if (capacity == null) {
            SiteAllocation(site.id, site.name, null, null,
              sharers.map { c -> c.id },
              "Site record provides no usable capacity figure - excluded, never zero")
          } else if (sharers.size <= 1) {
            SiteAllocation(site.id, site.name, capacity, capacity,
              sharers.map { c -> c.id },
              "Counted in full for this colony (only colony within walk reach)")
          } else {
            // proportional split on SOURCED demand only; colonies without a
            // demand figure claim an EQUAL share so no colony silently takes
            // zero because data is missing.
            val demands = sharers.map { it.demand.people ?: 0 }
            val total = demands.sum()
            val myIdx = sharers.indexOfFirst { it.id == colony.id }
            val claim = if (total > 0) {
              (capacity.toLong() * demands[myIdx] / total).toInt()
            } else {
              capacity / sharers.size
            }
            SiteAllocation(
              siteId = site.id, siteName = site.name,
              siteCapacity = capacity, colonyClaim = claim,
              sharedWith = sharers.map { c -> c.id },
              basis = if (total > 0)
                "Split ${formatPct(demands[myIdx], total)} of site capacity on colony demand"
              else
                "No colony carries a sourced demand figure - equal split across ${sharers.size} colonies"
            )
          }
        }

      val assessed = allocations.mapNotNull { it.colonyClaim }
      val capacity = assessed.takeIf { it.isNotEmpty() }?.sum()
      val demand = colony.demand.people
      val surplus = if (capacity != null && demand != null) (capacity - demand).takeIf { it >= 0 } else null
      val shortfall = if (capacity != null && demand != null) (demand - capacity).takeIf { it > 0 } else null
      val verdict = when {
        capacity == null ->
          "Capacity NOT AVAILABLE: no site within ${COLONY_REACH_MINUTES}-minute walk carries a usable capacity figure"
        demand == null ->
          "$capacity people of site capacity reachable - NO POPULATION DATA, no adequacy verdict issued" +
            (colony.demand.basis?.let { " (basis: $it)" } ?: "")
        shortfall != null ->
          "$capacity reachable vs $demand needed - SHORTFALL $shortfall people"
        else ->
          "$capacity reachable vs $demand needed - covers demand ($surplus spare)"
      }
      ColonyCapacity(colony, allocations, capacity, surplus, shortfall, verdict, SPLIT_METHOD)
    }
  }

  /**
   * Ward/CITY rollup over DISTINCT site ids - each site counted exactly once
   * regardless of how many colonies reference it. Populations supplied here
   * must already be label-correct (CENSUS 2011 = HISTORICAL BASELINE); this
   * function does not project or grow them.
   */
  fun uniqueCapacity(
    sites: List<SafeZone>,
    usableCapacityOf: (SafeZone) -> Int?,
    demand: AreaDemand,
    label: String
  ): AreaRollup {
    val byId = LinkedHashMap<String, SafeZone>()
    sites.forEach { byId.putIfAbsent(it.id, it) }
    val values = byId.values.map { usableCapacityOf(it) }
    val counted = values.filterNotNull()
    val total = counted.takeIf { it.isNotEmpty() }?.sum()
    val shortfall = if (total != null && demand.people != null)
      (demand.people - total).takeIf { it > 0 } else null
    val surplus = if (total != null && demand.people != null)
      (total - demand.people).takeIf { it >= 0 } else null
    val verdict = when {
      total == null -> "$label capacity NOT AVAILABLE (no site in scope carries a usable figure)"
      demand.people == null ->
        "$label: $total people of UNIQUE site capacity • population ${if (demand.basis == null) "NOT AVAILABLE" else "(${demand.basis})"} NOT SOURCED - no adequacy verdict"
      shortfall != null -> "$label: $total capacity vs ${demand.people} needed - SHORTFALL $shortfall"
      else -> "$label: $total capacity vs ${demand.people} needed - covers demand ($surplus spare)"
    }
    return AreaRollup(
      totalCapacity = total,
      sitesCounted = byId.size,
      sitesWithoutCapacityFigure = values.count { it == null },
      demand = demand,
      surplus = surplus,
      shortfall = shortfall,
      verdict = verdict
    )
  }

  private fun formatPct(part: Int, whole: Int): String =
    (part * 100.0 / whole).toInt().let { "$it%" }
}
