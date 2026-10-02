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
 * honest: real source names, real timestamps. Provenance reads quietly
 * ("News sources publish reports, not government alerts" in the Data &
 * sources strip) instead of a shouty badge on every story.
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
        // BRIEF cards (user: "too much info in the tab") - a one-glance
        // summary line; the full story stays one tap away ("Read Full Story").
        description = truncate(article.description.ifBlank { article.content }, 95),
        // Location metadata = the REAL resolved scope only. The channel
        // (GNews) is provenance, shown once in Data & sources, not stamped
        // on every card as a warning.
        location = article.scope.ringLabel(place),
        actionLabel = "Read Full Story",
        iconType = when (article.category) {
          NewsCategory.SEVERE_ALERTS, NewsCategory.ROAD_IMPACT -> DispatchIconType.FLOOD
          NewsCategory.SHELTER -> DispatchIconType.SHELTER
          NewsCategory.WEATHER -> DispatchIconType.RAIN
          NewsCategory.GOVERNMENT, NewsCategory.GENERAL -> DispatchIconType.LOGISTICS
        },
        url = article.url,
        imageUrl = article.imageUrl
      )
    }

  /** Severe/road-impact article first, else the newest article, else none. */
  fun pickHero(articles: List<NewsArticle>): NewsArticle? =
    articles.firstOrNull {
      it.category == NewsCategory.SEVERE_ALERTS || it.category == NewsCategory.ROAD_IMPACT
    } ?: articles.firstOrNull()

  /**
   * Severity rank of an ARTICLE as stated by its own text (nothing is
   * invented): 3 = named severe event/warning words, 2 = active hazard
   * words, 1 = weather-adjacent, 0 = unrelated. The news tab's severity
   * filter keeps rank >= the selected threshold.
   */
  fun severityRank(article: NewsArticle): Int {
    val text = (article.title + " " + article.description + " " + article.content)
      .lowercase(Locale.getDefault())
    val wordRank = when {
      SEVERE_WORDS.any { text.contains(it) } -> 3
      HAZARD_WORDS.any { text.contains(it) } -> 2
      WEATHER_WORDS.any { text.contains(it) } -> 1
      else -> 0
    }
    // Bug fix: an article the classifier already labelled SEVERE_ALERTS or
    // ROAD_IMPACT must never rank below the hazard band (2) - previously it
    // could score 0 from wording alone and disappear from "Hazard+" while
    // still being an alert-category article. Category is real metadata;
    // this only enforces its own floor, inventing nothing.
    val categoryFloor = when (article.category) {
      NewsCategory.SEVERE_ALERTS, NewsCategory.ROAD_IMPACT -> 2
      else -> 0
    }
    return maxOf(wordRank, categoryFloor)
  }

  /** Whether the article survives the severity threshold ('All' = 0). */
  fun matchesSeverity(article: NewsArticle, threshold: Int): Boolean =
    threshold <= 0 || severityRank(article) >= threshold

  private val SEVERE_WORDS = listOf(
    "cyclone", "hurricane", "typhoon", "red alert", "orange alert",
    "evacuat", "emergency", "disaster", "catastroph", "deadly",
    "killed", "deaths", "landslide", "cloudburst", "tsunami"
  )
  private val HAZARD_WORDS = listOf(
    "flood", "heavy rain", "torrential", "storm", "quake", "earthquake",
    "fire", "wildfire", "heatwave", "cold wave", "alert", "warning",
    "rescue", "dam", "breach", "waterlog", "rain lashed", "rains"
  )
  private val WEATHER_WORDS = listOf(
    "weather", "rain", "wind", "temperature", "humid", "monsoon", "sky"
  )

  /** Filter chips match the classifier's category directly. */
  fun matchesCategory(category: NewsCategory, chip: String): Boolean = when (chip) {
    "All" -> true
    "Disaster" -> category == NewsCategory.SEVERE_ALERTS ||
      category == NewsCategory.ROAD_IMPACT ||
      category == NewsCategory.SHELTER ||
      category == NewsCategory.GOVERNMENT
    "Weather" -> category == NewsCategory.WEATHER
    "Other News" -> category == NewsCategory.GENERAL
    else -> true
  }

  /** Every filter chip label — single source of truth for the dispatches UI. */
  // User rule #7: disaster coverage is the POINT of the feed, so the
  // hazard categories come first and the classifier's leftover "News" bucket
  // gets its OWN chip ("Other News") instead of mixing into disaster coverage.
  // USER RULE (news redesign): minimal, honest set. Spatial/weather
  // visualization lives on the MAP, so no "Weather Radar" chip here. GNews
  // articles are NOT official alerts, so "Severe Alerts" became "Severe
  // Events". Disaster groups every hazard-response category together.
  val CHIP_LABELS =
    listOf("All", "Disaster", "Weather", "Other News")

  /**
   * Only the chips that currently have matching articles (All always present).
   * A chip with nothing to show never appears as a dead button.
   */
  fun filterChipLabels(articles: List<NewsArticle>): List<String> =
    listOf("All") + CHIP_LABELS.drop(1).filter { label ->
      articles.any { matchesCategory(it.category, label) }
    }

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
