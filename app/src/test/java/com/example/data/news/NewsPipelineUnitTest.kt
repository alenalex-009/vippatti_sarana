package com.example.data.news

import com.example.data.news.GNewsJsonParser
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsArticle
import com.example.data.news.NewsCategory
import com.example.data.news.NewsClassifier
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsFilter
import com.example.data.news.NewsPresentation
import com.example.data.news.NewsRepository
import com.example.data.news.NewsScope
import com.example.data.news.NewsTtsBulletin
import com.example.data.news.GNewsCall
import com.example.data.news.GNewsService
import com.example.data.news.NewsQueryFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for the real GNews disaster-news pipeline: query
 * factory, JSON parsing (live-validated GNews schema), classifier, merge
 * dedupe, presentation mapping, TTS bulletin composition and repository
 * cache/quota behavior with a fake service.
 */
class NewsPipelineUnitTest {

  // ------------------------------------------------------- Query factory
  @Test
  fun `queries are live-validated boolean disasters queries`() {
    // Rings come from the resolved place, not from compiled-in geography.
    val place = testPlace()
    val rings = NewsQueryFactory.buildQueries(place)
    assertEquals(3, rings.size)
    assertTrue(rings[0].query.contains("\"Wayanad\""))
    assertTrue(rings[1].query.contains("\"Kerala\""))
    assertEquals(NewsScope.INDIA, rings.last().scope)
    assertTrue(rings.last().query.contains("flood"))
    // Free plan caps max at 10 — the client never asks for more.
    assertEquals(10, NewsQueryFactory.MAX_ARTICLES_PER_REQUEST)
  }

  /**
   * A place as the runtime resolver would return it. The tests name the place
   * they use instead of relying on a district compiled into the app.
   */
  private fun testPlace() =
    com.example.data.location.ResolvedPlace(
      district = "Wayanad",
      state = "Kerala",
      country = "India",
      source = "test resolver"
    )

  private fun threeRings() = NewsQueryFactory.buildQueries(testPlace())

  // ---------------------------------------------------------- JSON parse
  @Test
  fun `parser reads the live-validated gnews schema`() {
    val body = """
      {"totalArticles":206,"articles":[{
        "id":"abc","title":"Idukki flood evacuation ordered",
        "description":"Heavy rain.",
        "content":"Long content",
        "url":"https://example.com/story",
        "image":"https://example.com/img.jpg",
        "publishedAt":"2026-08-30T10:31:05Z",
        "lang":"en",
        "source":{"id":"s1","name":"Malayala Manorama","url":"https://onmanorama.com","country":"in"}
      }]}
    """.trimIndent()
    val articles = GNewsJsonParser.parseArticles(body, NewsScope.MY_AREA)
    assertEquals(1, articles.size)
    val a = articles[0]
    assertEquals("abc", a.id)
    assertEquals("Idukki flood evacuation ordered", a.title)
    assertEquals("Malayala Manorama", a.sourceName)
    // ISO-8601 Z timestamp parsed to real epoch millis.
    assertTrue(a.publishedAtMillis > 1_700_000_000_000L)
    assertEquals(NewsScope.MY_AREA, a.scope)
    assertEquals(206, GNewsJsonParser.parseTotal(body))
  }

  @Test
  fun `parser skips entries without title or url and keeps image null honest`() {
    val body = """
      {"totalArticles":2,"articles":[
        {"id":"x","title":"","url":"https://ok.com","publishedAt":"2026-08-30T10:31:05Z"},
        {"id":"y","title":"Real story","url":"","publishedAt":"2026-08-30T10:31:05Z"},
        {"id":"z","title":"No image story","url":"https://z.com","publishedAt":"bad-date"}
      ]}
    """.trimIndent()
    val articles = GNewsJsonParser.parseArticles(body, NewsScope.INDIA)
    assertEquals(1, articles.size)
    assertEquals("No image story", articles[0].title)
    assertNull(articles[0].imageUrl)
    assertEquals(0L, articles[0].publishedAtMillis)
  }

  // ---------------------------------------------------------- Classifier
  @Test
  fun `classifier is safety-first and transparent`() {
    assertEquals(NewsCategory.SEVERE_ALERTS,
      NewsClassifier.classify("Three died in landslide", ""))
    assertEquals(NewsCategory.ROAD_IMPACT,
      NewsClassifier.classify("Boulder blocks highway", ""))
    assertEquals(NewsCategory.SHELTER,
      NewsClassifier.classify("Relief camp opened for displaced", ""))
    assertEquals(NewsCategory.GOVERNMENT,
      NewsClassifier.classify("KSDMA issues advisory", ""))
    assertEquals(NewsCategory.WEATHER,
      NewsClassifier.classify("IMD forecast heavy showers", ""))
    assertEquals(NewsCategory.GENERAL,
      NewsClassifier.classify("Tourism season begins", ""))
  }

  // -------------------------------------------------- Disaster relevance
  @Test
  fun `disaster filter keeps only disaster-relevant coverage`() {
    val disaster = article("d", "Idukki flood evacuation ordered", 1L, NewsScope.MY_AREA)
    val general = NewsArticle(
      id = "g", title = "Film festival begins in Kochi", description = "Culture news",
      content = "Cultural content", url = "https://example.com/film", imageUrl = null,
      publishedAtIso = "", publishedAtMillis = 2L, language = "en",
      sourceName = "News Corp", sourceUrl = "https://x.com", scope = NewsScope.MY_AREA,
      category = NewsCategory.GENERAL
    )
    val filtered = NewsFilter.filterForDisaster(listOf(disaster, general))
    assertEquals(listOf("d"), filtered.map { it.id })
  }
  // ------------------------------------------- BUG 7: relevance is stricter
  @Test
  fun `a routine civic story is NOT presented as disaster coverage`() {
    // BUG 7: "rain"/"assistance"/"shelter" appearing only in the body used to be
    // enough to pass the filter, so general news filled the disaster feed.
    val civic = NewsArticle(
      id = "civic", title = "Corporation opens new library",
      description = "The city corporation announced the opening. Residents were advised of the new timings and support assistance.",
      content = "A routine civic update with no hazard involved.",
      url = "https://example.com/library", imageUrl = null,
      publishedAtIso = "", publishedAtMillis = 3L, language = "en",
      sourceName = "Civic Times", sourceUrl = "https://x.com", scope = NewsScope.INDIA,
      category = NewsCategory.GENERAL
    )
    assertFalse(
      "general civic news must not be claimed as disaster coverage",
      NewsFilter.isDisasterRelevant(civic)
    )
  }

  @Test
  fun `a genuine hazard story is still kept`() {
    val flood = article("f2", "Landslide blocks highway, NDRF teams rescue villagers", 4L, NewsScope.INDIA)
    val cyclone = article("c2", "Cyclone warning issued for coastal districts", 5L, NewsScope.INDIA)
    assertTrue(NewsFilter.isDisasterRelevant(flood))
    assertTrue(NewsFilter.isDisasterRelevant(cyclone))
  }

  @Test
  fun `generic impact words alone never qualify - context must share the headline`() {
    // User rule: do not match every article containing generic words like
    // "risk" or "crisis". They qualify ONLY with hazard context in the title.
    val generic = NewsArticle(
      id = "mk", title = "Market volatility raises investor risk",
      description = "Analysts warned of a wider crisis for the sector.",
      content = "Business coverage.", url = "https://example.com/mkt",
      imageUrl = null, publishedAtIso = "", publishedAtMillis = 7L,
      language = "en", sourceName = "Business Daily", sourceUrl = "https://x.com",
      scope = NewsScope.INDIA, category = NewsCategory.GENERAL
    )
    assertFalse("generic risk/crisis prose is not disaster coverage",
      NewsFilter.isDisasterRelevant(generic))
    val qualified = article("mk2",
      "Cyclone response: coastal districts at high risk", 8L, NewsScope.INDIA)
    assertTrue("impact word + hazard context in the headline qualifies",
      NewsFilter.isDisasterRelevant(qualified))
  }

  @Test
  fun `a weak hazard word in the HEADLINE still keeps the story`() {
    val headline = article("w1", "Evacuation begins after river crosses the embankment", 6L, NewsScope.INDIA)
    assertTrue("headline relevance must still count", NewsFilter.isDisasterRelevant(headline))
  }


  // ---------------------------------------------------- Merge / dedupe
  @Test
  fun `merge dedupes by id and title, newest first, closest scope wins ties`() {
    val now = 1_800_000_000_000L
    val a1 = article("id-1", "Flood hits Idukki", now, NewsScope.MY_AREA)
    val a2 = article("id-2", "Flood hits idukki!", now, NewsScope.INDIA) // same title, later scope
    val a3 = article("id-3", "Older story", now - 5_000_000L, NewsScope.MY_STATE)
    val merged = NewsRepository.mergeArticles(listOf(a3, a2, a1))
    assertEquals(2, merged.size)
    assertEquals("id-1", merged[0].id) // tie -> MY_AREA wins over INDIA
    assertEquals("id-3", merged[1].id)  // newest first
  }

  @Test
  fun `title normalization ignores punctuation and case only`() {
    assertEquals(
      NewsRepository.normalizeTitle("Flood HITS Idukki!"),
      NewsRepository.normalizeTitle("flood hits idukki")
    )
  }

  // ------------------------------------------------------- Presentation
  @Test
  fun `presentation maps articles to honest dispatch cards with urls`() {
    val now = 1_800_000_000_000L
    val a = article("id-1", "Severe flood headline", now - 3_600_000L, NewsScope.MY_AREA, category = NewsCategory.SEVERE_ALERTS)
    val cards = NewsPresentation.toFeedDispatches(listOf(a), now, testPlace())
    assertEquals(1, cards.size)
    val c = cards[0]
    assertEquals("https://example.com/story", c.url)
    assertTrue(c.issuedTime.contains("1h ago"))
    // Ring label comes from the resolved place, never from a constant.
    assertTrue(c.location.contains("Wayanad"))
    assertEquals("Read Full Story", c.actionLabel)

    // With no resolved place the card never names a district it was not told.
    val unresolved = NewsPresentation.toFeedDispatches(listOf(a), now)[0]
    assertTrue(unresolved.location.contains("not resolved"))
  }

  @Test
  fun `relative age is honest for unparsed timestamps`() {
    assertEquals("recently", NewsPresentation.relativeAge(0L, 1_800_000_000_000L))
    assertEquals("just now", NewsPresentation.relativeAge(1_800_000_000_100L, 1_800_000_000_000L))
    // 7,200,000 ms elapsed -> "2 hours ago" (spoken, full words).
    assertTrue(NewsPresentation.spokenAge(3_600_000L, 10_800_000L).contains("hours ago"))
  }

  @Test
  fun `hero picks severe or road-impact articles first`() {
    val now = 1_800_000_000_000L
    val weather = article("w", "Rain forecast", now, NewsScope.MY_STATE, category = NewsCategory.WEATHER)
    val severe = article("s", "Evacuation ordered after flood", now - 1000L, NewsScope.MY_AREA, category = NewsCategory.SEVERE_ALERTS)
    assertEquals("s", NewsPresentation.pickHero(listOf(weather, severe))?.id)
    assertEquals("w", NewsPresentation.pickHero(listOf(weather))?.id)
    assertNull(NewsPresentation.pickHero(emptyList()))
  }

  @Test
  fun `category chips filter via the classifier categories`() {
    assertTrue(NewsPresentation.matchesCategory(NewsCategory.SEVERE_ALERTS, "Disaster"))
    assertTrue(NewsPresentation.matchesCategory(NewsCategory.ROAD_IMPACT, "Disaster"))
    assertTrue(NewsPresentation.matchesCategory(NewsCategory.WEATHER, "Weather"))
    assertTrue(NewsPresentation.matchesCategory(NewsCategory.SHELTER, "Shelter Updates"))
    assertTrue(NewsPresentation.matchesCategory(NewsCategory.GOVERNMENT, "Disaster"))
    assertTrue(NewsPresentation.matchesCategory(NewsCategory.GENERAL, "All"))
    assertFalse(NewsPresentation.matchesCategory(NewsCategory.GENERAL, "Disaster"))
  }

  @Test
  fun `filter chips only show labels with matching articles`() {
    val now = 1_800_000_000_000L
    val severe = article("s", "Idukki flood evacuation ordered", now, NewsScope.MY_AREA, category = NewsCategory.SEVERE_ALERTS)
    val weather = article("w", "IMD heavy rain forecast", now, NewsScope.MY_STATE, category = NewsCategory.WEATHER)
    val chips = NewsPresentation.filterChipLabels(listOf(severe, weather))
    // All is always present; Shelter/Government have no articles -> no dead chips.
    assertEquals(listOf("All", "Disaster", "Weather"), chips)
    assertEquals(listOf("All"), NewsPresentation.filterChipLabels(emptyList()))
  }

  // ------------------------------------------------------ TTS bulletin
  @Test
  fun `tts bulletin speaks only real state and labels provenance`() {
    val now = 1_800_000_000_000L
    val articles = listOf(
      article("a", "Flood worsens in Idukki", now - 600_000L, NewsScope.MY_AREA)
    )
    val text = NewsTtsBulletin.compose(
      riskLevelLabel = "RED",
      recommendedActionTitle = "MOVE TO HIGHER GROUND NOW",
      actionExplanation = "Your location is inside the flood area.",
      articles = articles,
      nowMillis = now,
      isFromCache = false
    )
    assertTrue(text.contains("personal risk level is RED"))
    assertTrue(text.contains("MOVE TO HIGHER GROUND NOW"))
    assertTrue(text.contains("Flood worsens in Idukki"))
    assertTrue(text.contains("Headline 1"))
    assertTrue(text.contains("not official government alerts"))
  }

  @Test
  fun `tts bulletin with no articles says so honestly`() {
    val text = NewsTtsBulletin.compose(
      riskLevelLabel = "GREEN",
      recommendedActionTitle = null,
      actionExplanation = null,
      articles = emptyList(),
      nowMillis = 1_800_000_000_000L,
      isFromCache = true
    )
    assertTrue(text.contains("No disaster news articles are currently loaded"))
    assertTrue(text.contains("offline news cache"))
  }

  // ------------------------------------------------------ Repository
  @Test
  fun `repository refresh fetches all scopes, dedupes, caches and orders newest first`() = runTest {
    val now = 1_800_000_000_000L
    val cache = MemoryNewsCache()
    val repo = NewsRepository(
      service = FakeGNewsService(
        myArea = listOf(article("a1", "Idukki flood", now, NewsScope.MY_AREA)),
        myState = listOf(article("s1", "Kerala rain", now - 1_000_000L, NewsScope.MY_STATE)),
        india = listOf(article("i1", "India floods", now - 2_000_000L, NewsScope.INDIA))
      ),
      cache = cache,
      apiKeyProvider = { "real-key" },
      clock = { now }
    )
    val feed = repo.refresh(threeRings())
    assertEquals(3, feed.articles.size)
    assertEquals("a1", feed.articles[0].id)
    assertFalse(feed.isFromCache)
    assertEquals(now, feed.lastFetchedAtMillis)
    // All three shards cached.
    assertEquals(
      3,
      listOfNotNull(
        cache.read(NewsScope.MY_AREA),
        cache.read(NewsScope.MY_STATE),
        cache.read(NewsScope.INDIA)
      ).size
    )
  }

  @Test
  fun `repository stops the scope cascade on quota error and keeps cache`() = runTest {
    val now = 1_800_000_000_000L
    val cache = MemoryNewsCache()
    cache.write(
      NewsScope.MY_AREA,
      listOf(article("c1", "Cached Idukki flood", now - 60_000L, NewsScope.MY_AREA)),
      now - 60_000L
    )
    val repo = NewsRepository(
      service = FakeGNewsService(myArea = emptyList(), quotaHitOnMyArea = true),
      cache = cache,
      apiKeyProvider = { "real-key" },
      clock = { now }
    )
    val feed = repo.refresh(threeRings())
    // Quota on the first scope -> cascade stops, cache serves the district feed.
    assertNotNull(feed.error)
    assertEquals(NewsErrorKind.QUOTA_EXCEEDED, feed.error!!.kind)
    assertEquals(1, feed.articles.size)
    assertEquals("c1", feed.articles[0].id)
    assertTrue(feed.isFromCache)
  }

  @Test
  fun `repository serves past cached disaster news when no live disaster exists`() = runTest {
    val now = 1_800_000_000_000L
    val cache = MemoryNewsCache()
    cache.write(
      NewsScope.MY_AREA,
      listOf(article("p1", "Idukki landslide relief camp", now - 3_600_000L, NewsScope.MY_AREA)),
      now - 3_600_000L
    )
    val repo = NewsRepository(
      service = FakeGNewsService(myArea = emptyList(), myState = emptyList(), india = emptyList()),
      cache = cache,
      apiKeyProvider = { "real-key" },
      clock = { now }
    )
    val feed = repo.refresh()
    // No live disaster anywhere -> the honest past coverage is served from cache.
    assertEquals(listOf("p1"), feed.articles.map { it.id })
    assertTrue(feed.isFromCache)
    assertNull(feed.error)
  }

  @Test
  fun `repository serves a fresh cache on cold start without any network call`() = runTest {
    val now = 1_800_000_000_000L
    val cache = MemoryNewsCache()
    cache.write(
      NewsScope.MY_AREA,
      listOf(article("a1", "Idukki flood", now, NewsScope.MY_AREA)),
      now - 60_000L
    )
    val repo = NewsRepository(
      service = FakeGNewsService(myArea = emptyList(), failIfCalled = true),
      cache = cache,
      apiKeyProvider = { "real-key" },
      clock = { now }
    )
    val feed = repo.ensureLoaded(threeRings())
    assertEquals(1, feed.articles.size)
    assertTrue(feed.isFromCache)
  }

  @Test
  fun `repository refuses placeholder keys honestly`() = runTest {
    val repo = NewsRepository(
      service = FakeGNewsService(failIfCalled = true),
      cache = MemoryNewsCache(),
      apiKeyProvider = { "YOUR_GNEWS_API_KEY_HERE" },
      clock = { 0L }
    )
    val feed = repo.refresh()
    assertNotNull(feed.error)
    assertEquals(NewsErrorKind.NO_API_KEY, feed.error!!.kind)
    assertTrue(feed.articles.isEmpty())
  }

  // ---------------------------------------------------------- helpers
  private fun article(
    id: String,
    title: String,
    publishedAt: Long,
    scope: NewsScope,
    category: NewsCategory = NewsCategory.GENERAL
  ): NewsArticle = NewsArticle(
    id = id,
    title = title,
    description = "Real description of $title",
    content = "Real content of $title",
    url = "https://example.com/story",
    imageUrl = null,
    publishedAtIso = "",
    publishedAtMillis = publishedAt,
    language = "en",
    sourceName = "Malayala Manorama",
    sourceUrl = "https://onmanorama.com",
    scope = scope,
    category = category
  )

  private class FakeGNewsService(
    private val myArea: List<NewsArticle> = emptyList(),
    private val myState: List<NewsArticle> = emptyList(),
    private val india: List<NewsArticle> = emptyList(),
    private val quotaHitOnMyArea: Boolean = false,
    private val failIfCalled: Boolean = false
  ) : GNewsService {
    var calls = 0
    /** Queries the repository actually asked for (proves the rings are dynamic). */
    val queries = mutableListOf<String>()

    override suspend fun search(scope: NewsScope, query: String, apiKey: String): GNewsCall {
      calls++
      queries += query
      check(!failIfCalled) { "network must not be called in this test" }
      return when (scope) {
        NewsScope.MY_AREA ->
          if (quotaHitOnMyArea) GNewsCall.Failure(
            NewsError(NewsErrorKind.QUOTA_EXCEEDED, "Daily GNews request quota reached.")
          )
          else GNewsCall.Success(myArea, myArea.size)
        NewsScope.MY_STATE -> GNewsCall.Success(myState, myState.size)
        NewsScope.INDIA -> GNewsCall.Success(india, india.size)
      }
    }
  }
}
