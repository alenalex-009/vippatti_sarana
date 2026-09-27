package com.example.data.news

import com.example.data.disaster.DispatchIconType
import com.example.data.disaster.DispatchTagType
import com.example.data.disaster.FeedDispatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure mapping from real [NewsArticle]s to the existing dispatch UI model —
 * no Android dependencies, fully unit-testable. Every label produced here is
 * honest: real source names, real timestamps, and an explicit
 * "NOT AN OFFICIAL ALERT" hero badge.
 */
object NewsPresentation {

  fun toFeedDispatches(
    articles: List<NewsArticle>,
    nowMillis: Long,
    /** Place resolved at runtime; null -> the label states the scope is unknown. */
    place: com.example.data.location.ResolvedPlace? = null
  ): List<FeedDispatch> =
    articles.map { article ->
      FeedDispatch(
        id = article.id,
        agency = article.sourceName,
        issuedTime = "Published ${relativeAge(article.publishedAtMillis, nowMillis)}",
        tag = article.category.displayTag,
        tagType = when (article.category) {
          NewsCategory.SEVERE_ALERTS -> DispatchTagType.HIGH_ALERT
          NewsCategory.ROAD_IMPACT -> DispatchTagType.ROAD_CLOSED
          NewsCategory.SHELTER -> DispatchTagType.SHELTER_READY
          NewsCategory.WEATHER -> DispatchTagType.CAPACITY_INFO
          NewsCategory.GOVERNMENT -> DispatchTagType.CAPACITY_INFO
          NewsCategory.GENERAL -> DispatchTagType.CAPACITY_INFO
        },
        title = article.title,
        description = truncate(article.description.ifBlank { article.content }, 220),
        location = "${article.scope.ringLabel(place)} • GNews",
        actionLabel = "Read Full Story",
        iconType = when (article.category) {
          NewsCategory.SEVERE_ALERTS, NewsCategory.ROAD_IMPACT -> DispatchIconType.FLOOD
          NewsCategory.SHELTER -> DispatchIconType.SHELTER
          NewsCategory.WEATHER -> DispatchIconType.RAIN
          NewsCategory.GOVERNMENT, NewsCategory.GENERAL -> DispatchIconType.LOGISTICS
        },
        url = article.url
      )
    }

  /** Severe/road-impact article first, else the newest article, else none. */
  fun pickHero(articles: List<NewsArticle>): NewsArticle? =
    articles.firstOrNull {
      it.category == NewsCategory.SEVERE_ALERTS || it.category == NewsCategory.ROAD_IMPACT
    } ?: articles.firstOrNull()

  /** Filter chips match the classifier's category directly. */
  fun matchesCategory(category: NewsCategory, chip: String): Boolean = when (chip) {
    "All" -> true
    "Severe Alerts" -> category == NewsCategory.SEVERE_ALERTS || category == NewsCategory.ROAD_IMPACT
    "Weather Radar" -> category == NewsCategory.WEATHER
    "Shelter Updates" -> category == NewsCategory.SHELTER
    "Government Bulletins" -> category == NewsCategory.GOVERNMENT
    "Other News" -> category == NewsCategory.GENERAL
    else -> true
  }

  /** Every filter chip label — single source of truth for the dispatches UI. */
  // User rule #7: disaster coverage is the POINT of the feed, so the
  // hazard categories come first and the classifier's leftover "News" bucket
  // gets its OWN chip ("Other News") instead of mixing into disaster coverage.
  val CHIP_LABELS =
    listOf("All", "Severe Alerts", "Weather Radar", "Shelter Updates", "Government Bulletins", "Other News")

  /**
   * Only the chips that currently have matching articles (All always present).
   * A chip with nothing to show never appears as a dead button.
   */
  fun filterChipLabels(articles: List<NewsArticle>): List<String> =
    listOf("All") + CHIP_LABELS.drop(1).filter { label ->
      articles.any { matchesCategory(it.category, label) }
    }

  /** Honest hero badge — a news article is never an official alert. */
  const val HERO_BADGE = "GNEWS • NOT AN OFFICIAL ALERT"

  fun articleSummary(article: NewsArticle, maxChars: Int = 260): String =
    truncate(article.description.ifBlank { article.content }, maxChars)

  fun truncate(text: String, maxChars: Int): String {
    val clean = text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
    return if (clean.length <= maxChars) clean else clean.take(maxChars).trimEnd() + "…"
  }

  /** Honest relative age; unparseable timestamps say "recently", never a lie. */
  fun relativeAge(publishedAtMillis: Long, nowMillis: Long): String {
    if (publishedAtMillis <= 0L) return "recently"
    val age = nowMillis - publishedAtMillis
    return when {
      age < 0L -> "just now"
      age < 60_000L -> "${age / 1_000L}s ago"
      age < 3_600_000L -> "${age / 60_000L}m ago"
      age < 86_400_000L -> "${age / 3_600_000L}h ago"
      age < 7L * 86_400_000L -> "${age / 86_400_000L}d ago"
      else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(publishedAtMillis))
    }
  }

  /** Full-word age for spoken playback. */
  fun spokenAge(publishedAtMillis: Long, nowMillis: Long): String {
    if (publishedAtMillis <= 0L) return "recently"
    val age = nowMillis - publishedAtMillis
    return when {
      age < 0L -> "just now"
      age < 60_000L -> "${(age / 1_000L).coerceAtLeast(1)} seconds ago"
      age < 3_600_000L -> "${(age / 60_000L).coerceAtLeast(1)} minutes ago"
      age < 86_400_000L -> "${(age / 3_600_000L).coerceAtLeast(1)} hours ago"
      else -> "${(age / 86_400_000L).coerceAtLeast(1)} days ago"
    }
  }
}
