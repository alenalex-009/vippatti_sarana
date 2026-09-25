package com.example.data.historical

/**
 * ============================================================================
 * HISTORICAL VISUAL SUMMARY (pure aggregation, zero UI, zero new data)
 * ============================================================================
 *
 * The EM-DAT panel used to be a wall of text rows. This turns THE EXACT SAME
 * filtered record list into scannable bars — nothing is invented, nothing is
 * re-scoped: every bar is a COUNT of the records already shown below it, so
 * the visual and the list can never disagree.
 *
 * Honesty rules (unit-tested):
 *  - totals only sum records that STATE the figure (EM-DAT nulls stay nulls);
 *  - a bar fraction is count / max count of THIS panel (relative, labelled);
 *  - empty input => empty summary (rendered as "nothing to chart", never a
 *    fake zero-baseline chart).
 */
object HistoricalVisuals {

  data class BarRow(val label: String, val count: Int, val fraction: Float)

  data class Summary(
    val recordCount: Int,
    val typeBars: List<BarRow>,
    val decadeBars: List<BarRow>,
    /** Sum of stated deaths across these records (null = no record stated it). */
    val deathsTotal: Long?,
    val deathsRecords: Int,
    val affectedTotal: Long?,
    val affectedRecords: Int
  ) {
    val isEmpty: Boolean get() = recordCount == 0
  }

  fun summarize(events: List<HistoricalDisasterEvent>, maxTypeBars: Int = 6): Summary {
    if (events.isEmpty()) return Summary(0, emptyList(), emptyList(), null, 0, null, 0)

    val typeCounts = events.groupingBy { it.type.ifBlank { "Other" } }.eachCount()
    val typeSorted = typeCounts.entries.sortedByDescending { it.value }.take(maxTypeBars)
    val typeMax = typeSorted.maxOf { it.value }.coerceAtLeast(1)
    val typeBars = typeSorted.map { (label, count) ->
      BarRow(label, count, count.toFloat() / typeMax)
    }

    val decadeCounts = events
      .map { (it.startDate.year / 10) * 10 }
      .groupingBy { it }
      .eachCount()
    val decadeMax = decadeCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val decadeBars = decadeCounts.toSortedMap().map { (decade, count) ->
      BarRow("${decade}s", count, count.toFloat() / decadeMax)
    }

    val deathRecords = events.mapNotNull { it.impacts.totalDeaths }
    val affectedRecords = events.mapNotNull { it.impacts.totalAffected }
    return Summary(
      recordCount = events.size,
      typeBars = typeBars,
      decadeBars = decadeBars,
      deathsTotal = deathRecords.sum().takeIf { deathRecords.isNotEmpty() },
      deathsRecords = deathRecords.size,
      affectedTotal = affectedRecords.sum().takeIf { affectedRecords.isNotEmpty() },
      affectedRecords = affectedRecords.size
    )
  }
}
