package com.example.data.historical

import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HISTORICAL VISUALS honesty contracts: the bars are pure aggregation over
 * the SAME record list — counts must match, EM-DAT nulls must stay absent
 * (never zero-filled), and an empty selection charts nothing.
 */
class HistoricalVisualsTest {

  private fun event(
    id: String,
    type: String,
    year: Int,
    deaths: Long? = null,
    affected: Long? = null
  ) = HistoricalDisasterEvent(
    id = id,
    group = "Natural",
    subgroup = "Hydrological",
    type = type,
    subtype = "",
    country = "India",
    startDate = HistoricalDate(year),
    impacts = HistoricalImpacts(totalDeaths = deaths, totalAffected = affected),
    source = "EM-DAT test",
    datasetVersion = "test"
  )

  @Test
  fun `type bars count the same records the list shows`() {
    val events = listOf(
      event("a", "Flood", 2001, deaths = 100),
      event("b", "Flood", 2011),
      event("c", "Storm", 2015, affected = 5000),
      event("d", "Storm", 1990),
      event("e", "Storm", 1995)
    )
    val summary = HistoricalVisuals.summarize(events)
    assertEquals(5, summary.recordCount)
    val storm = summary.typeBars.first { it.label == "Storm" }
    assertEquals(3, storm.count)
    assertEquals(1.0, storm.fraction.toDouble(), 1e-6) // the max bar is full width
    assertEquals(2.0 / 3.0, summary.typeBars.first { it.label == "Flood" }.fraction.toDouble(), 1e-6)
  }

  @Test
  fun `decade bars span decades in chronological order`() {
    val events = listOf(
      event("a", "Flood", 1990), event("b", "Flood", 1995),
      event("c", "Flood", 2001), event("d", "Flood", 2011)
    )
    val decades = HistoricalVisuals.summarize(events).decadeBars.map { it.label }
    assertEquals(listOf("1990s", "2000s", "2010s"), decades)
  }

  @Test
  fun `totals only sum records that state a figure - nulls never become zero`() {
    val events = listOf(
      event("a", "Flood", 2001, deaths = 100),
      event("b", "Flood", 2002),                       // states nothing
      event("c", "Flood", 2003, deaths = 0)            // states an explicit ZERO
    )
    val summary = HistoricalVisuals.summarize(events)
    assertEquals(100L, summary.deathsTotal!!)
    assertEquals(2, summary.deathsRecords) // 100 and 0 both "state a figure"
    assertNull(summary.affectedTotal)      // nobody stated affected
    assertEquals(0, summary.affectedRecords)
  }

  @Test
  fun `empty selection produces an empty summary - never a fake chart`() {
    val summary = HistoricalVisuals.summarize(emptyList())
    assertTrue(summary.isEmpty)
    assertEquals(0, summary.recordCount)
    assertTrue(summary.typeBars.isEmpty())
    assertTrue(summary.decadeBars.isEmpty())
  }

  @Test
  fun `bar row fractions are relative to this panel's largest bar`() {
    val events = (1..4).map { event("f$it", "Flood", 2000) } +
      (1..2).map { event("s$it", "Storm", 2005) }
    val summary = HistoricalVisuals.summarize(events)
    assertEquals(1.0, summary.typeBars.first { it.label == "Flood" }.fraction.toDouble(), 1e-6)
    assertEquals(0.5, summary.typeBars.first { it.label == "Storm" }.fraction.toDouble(), 1e-6)
  }
}
