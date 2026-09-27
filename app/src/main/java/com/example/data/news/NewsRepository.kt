package com.example.data.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates the real GNews disaster-news feed:
 *
 *  - district -> state -> nation scope cascade, each cached separately;
 *  - quota-aware: the cascade stops at the first failure, and a fresh cache
 *    (<= 30 min) is served on cold start without spending any request;
 *  - deduped by article id AND normalized title, newest first, with the
 *    closest scope winning duplicates;
 *  - honest errors only — cached articles survive offline, nothing is
 *    fabricated.
 */
class NewsRepository(
  private val service: GNewsService,
  private val cache: NewsCache,
  private val apiKeyProvider: () -> String,
  private val clock: () -> Long = System::currentTimeMillis,
  /** Storage I/O dispatcher — tests inject the scheduler's dispatcher so runs are deterministic. */
  private val storageDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {

  /**
   * Manual sync: always goes to the live GNews API (cache is updated).
   *
   * [queries] carries the runtime search rings (district/state/national) built
   * from the resolved place. The default is the national ring only - a caller
   * that has not resolved a location must not query a province it assumed.
   */
  suspend fun refresh(
    queries: List<NewsQueryFactory.ScopedNewsQuery> = NewsQueryFactory.buildQueries(null)
  ): NewsFeed {
    val apiKey = apiKeyProvider().trim()
    if (apiKey.isBlank() || apiKey == GNEWS_PLACEHOLDER_KEY) {
      return cachedOnlyFeed(NewsError(NewsErrorKind.NO_API_KEY, NO_KEY_MESSAGE))
    }
    val now = clock()
    val collected = mutableListOf<NewsArticle>()
    var error: NewsError? = null
    for (scoped in queries) {
      val scope = scoped.scope
      when (val call = service.search(scope, scoped.query, apiKey)) {
        is GNewsCall.Success -> {
          collected += call.articles
          // Never overwrite a useful shard with an empty live result — an
          // empty scope keeps its previous (past) coverage for the fallback.
          if (call.articles.isNotEmpty()) {
            withContext(storageDispatcher) { cache.write(scope, call.articles, now) }
          }
        }
        is GNewsCall.Failure -> {
          // Quota/auth/network failure — stop the cascade; remaining scopes
          // fall back to their cache shards inside cachedOnlyFeed.
          error = call.error
          break
        }
      }
    }
    if (collected.isEmpty()) {
      return cachedOnlyFeed(error)
    }
    val articles = mergeArticles(NewsFilter.filterForDisaster(collected))
    return NewsFeed(
      articles = articles,
      hero = NewsPresentation.pickHero(articles),
      lastFetchedAtMillis = now,
      isFromCache = false,
      error = error
    )
  }

  /**
   * Cold start: serve the cache instantly when it is fresh (offline survival
   * + quota protection); otherwise perform a live refresh.
   */
  suspend fun ensureLoaded(
    queries: List<NewsQueryFactory.ScopedNewsQuery> = NewsQueryFactory.buildQueries(null)
  ): NewsFeed {
    val cached = withContext(storageDispatcher) { readAllUsableCached() }
    val articles = mergeArticles(NewsFilter.filterForDisaster(cached.flatMap { it.articles }))
    val newestFetch = cached.maxOfOrNull { it.fetchedAtMillis }
    if (newestFetch != null && NewsCachePolicy.isFresh(newestFetch, clock())) {
      return NewsFeed(
        articles = articles,
        hero = NewsPresentation.pickHero(articles),
        lastFetchedAtMillis = newestFetch,
        isFromCache = true,
        error = null
      )
    }
    return refresh(queries)
  }

  private suspend fun cachedOnlyFeed(error: NewsError?): NewsFeed {
    val cached = withContext(storageDispatcher) { readAllUsableCached() }
    val articles = mergeArticles(NewsFilter.filterForDisaster(cached.flatMap { it.articles }))
    return NewsFeed(
      articles = articles,
      hero = NewsPresentation.pickHero(articles),
      lastFetchedAtMillis = cached.maxOfOrNull { it.fetchedAtMillis },
      isFromCache = articles.isNotEmpty(),
      error = error
    )
  }

  private fun readAllUsableCached(): List<CachedFeed> =
    NewsScope.values().mapNotNull { cache.read(it) }
      .filter { NewsCachePolicy.isUsableStale(it.fetchedAtMillis, clock()) }

  companion object {

    /**
     * Dedupes by id and normalized title, sorts newest first; on exact-time
     * ties the closer scope (MY_AREA) wins so local coverage is preserved.
     */
    fun mergeArticles(all: List<NewsArticle>): List<NewsArticle> {
      val seenIds = HashSet<String>()
      val seenTitles = HashSet<String>()
      val out = mutableListOf<NewsArticle>()
      // User rule #7: disaster coverage LEADS the feed. Articles are
      // bucketed disaster-first (anything the classifier tagged GENERAL
      // sorts after every hazard category); recency orders within a bucket.
      for (article in all.sortedWith(
        compareBy<NewsArticle> { if (it.category == NewsCategory.GENERAL) 1 else 0 }
          .thenByDescending { it.publishedAtMillis }
          .thenBy { it.scope.ordinal }
      )) {
        val titleKey = normalizeTitle(article.title)
        if (article.id in seenIds || titleKey in seenTitles) continue
        seenIds += article.id
        seenTitles += titleKey
        out += article
      }
      return out
    }

    fun normalizeTitle(title: String): String =
      title.lowercase()
        .replace(Regex("[^a-z0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
  }
}
