package com.example.data.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * User rule #7 (second pass): "weather news details should be filtered based
 * on the selection of severity" + "the tab should be brief - too much info".
 * Contracts here pin the SEVERITY classifier (rank derived strictly from the
 * article's own words - nothing invented) and the truncated card summary.
 */
class NewsSeverityFilterTest {

  private fun article(title: String, description: String = "", category: NewsCategory = NewsCategory.WEATHER) =
    NewsArticle(
      id = "art-${title.hashCode()}",
      title = title,
      description = description,
      content = description,
      url = "https://example.com/a",
      imageUrl = null,
      publishedAtIso = "2026-09-27T04:00:00Z",
      publishedAtMillis = 1_800_000_000_000L,
      language = "en",
      sourceName = "Test Paper",
      sourceUrl = "https://example.com",
      scope = NewsScope.MY_AREA,
      category = category
    )

  @Test
  fun `named severe events rank 3, active hazard words rank 2, plain weather rank 1`() {
    assertEquals(3, NewsPresentation.severityRank(article("Cyclone makes landfall, evacuation ordered")))
    assertEquals(2, NewsPresentation.severityRank(article("Heavy rain lashes the coast")))
    assertEquals(2, NewsPresentation.severityRank(article("Dam breach warning issued")))
    assertEquals(1, NewsPresentation.severityRank(article("Weather remains humid this week")))
    assertEquals(0, NewsPresentation.severityRank(article("Film festival opens downtown")))
  }

  @Test
  fun `threshold keeps only articles at or above it`() {
    val severe = article("Red alert: landslide risk in the hills")
    val hazard = article("Flood water rises in two districts")
    val mild = article("Light rain expected tomorrow")
    assertTrue(NewsPresentation.matchesSeverity(severe, 3))
    assertFalse(NewsPresentation.matchesSeverity(hazard, 3))
    assertTrue(NewsPresentation.matchesSeverity(hazard, 2))
    assertTrue(NewsPresentation.matchesSeverity(severe, 2))
    assertFalse(NewsPresentation.matchesSeverity(mild, 2))
    // threshold 0 = everything passes (the 'All' state)
    assertTrue(NewsPresentation.matchesSeverity(mild, 0))
  }

  @Test
  fun `feed dispatch summaries are BRIEF - one glance`() {
    val long = article(
      "Cyclone warning",
      description = "A ".repeat(120) + "end-of-story"
    )
    val dispatch = NewsPresentation.toFeedDispatches(
      listOf(long), System.currentTimeMillis(), null
    ).first()
    assertTrue(
      "card description must stay short (user: too much info): ${dispatch.description.length}",
      dispatch.description.length <= 100
    )
  }
}
