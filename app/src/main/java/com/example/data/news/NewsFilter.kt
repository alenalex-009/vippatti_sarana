package com.example.data.news

/**
 * ============================================================================
 * DISASTER-RELEVANCE FILTER — keeps the news feed useful and on-topic.
 * ============================================================================
 *
 * GNews keyword queries occasionally surface general news (politics, business,
 * sports) that shares a keyword. Every article — whether it arrives live from
 * the API or from the on-device cache — must match a resilience-focused
 * keyword in its title/description/content before it reaches the dispatches
 * screen. Matching is transparent and conservative; articles that do not
 * match are dropped (never shown), and when nothing matches the feed shows an
 * honest empty state or previous disaster coverage from the cache.
 */
object NewsFilter {

  /**
   * BUG 7 FIX — evidence is split into two tiers.

   * The previous single list mixed specific hazard vocabulary ("cyclone",
   * "landslide", "NDRF") with words that appear in almost any general article
   * ("rain", "alert", "warning", "shelter", "affected", "assistance"). Because
   * ANY of them anywhere in title+description+content passed, routine civic and
   * general news was presented to the user as disaster coverage.
   *
   * An article now counts as disaster-related when it carries a STRONG term, or
   * when a weaker term appears in the TITLE. A weak term buried in body text
   * alone no longer proves relevance.
   */

  /** Specific hazards, agencies and response vocabulary: decisive on its own. */
  private val STRONG_TERMS = listOf(
    "flood", "flash flood", "cyclone", "hurricane", "typhoon", "landslide",
    "landslip", "mudslide", "earthquake", "quake", "aftershock", "tsunami",
    "avalanche", "wildfire", "forest fire", "cloudburst", "blizzard",
    "storm surge", "dam breach", "embankment breach", "eruption", "volcano",
    "severe weather", "extreme weather", "weather warning", "red alert",
    "rainstorm", "high tide", "storm tide",
    "NDRF", "SDRF", "relief camp", "evacuation", "evacuat",
    "rescue operation", "search and rescue", "first responder",
    "death toll", "casualt", "swept away", "washed away", "tremor", "landslips"
  )

  /**
   * Weaker, high-frequency words. On their own these prove nothing, so they only
   * count when they appear in the headline.
   */
  private val WEAK_TERMS = listOf(
    "rain", "rainfall", "storm", "squall", "hail", "lightning", "gale", "monsoon",
    "drought", "heatwave", "heat wave", "coldwave", "cold wave",
    "rescue", "relief", "shelter", "displaced", "victim", "injur",
    "affected", "stranded", "trapped", "emergency", "advisory", "warning", "alert",
    "restoration", "rehabilitation", "assistance", "recover", "response"
  )

  /**
   * Response/impact words that are decisive ONLY together with a hazard,
   * agency or event context term - on their own they are generic news ("risk",
   * "crisis", "response" appear everywhere, per the user rule: do not match
   * every article containing generic words).
   */
  private val CONTEXT_TERMS = listOf(
    "flood", "cyclone", "rain", "storm", "quake", "earthquake", "landslide",
    "fire", "tsunami", "drought", "heatwave", "cloudburst", "avalanche",
    "disaster", "calamity", "relief", "ndrf", "ndma", "sdma", "imd",
    "evacuation", "dam", "breach", "alert", "rescue"
  )
  private val IMPACT_TERMS = listOf(
    "risk", "crisis", "response", "response team", "impact", "damage",
    "disruption", "stranded", "cut off", "disaster-related"
  )

  /** True when the article's own text signals disaster relevance. */
  fun isDisasterRelevant(article: NewsArticle): Boolean {
    val title = article.title.lowercase()
    val body = buildString {
      append(article.description.lowercase())
      append(' ')
      append(article.content.lowercase())
    }
    if (STRONG_TERMS.any { body.contains(it) || title.contains(it) }) return true
    if (WEAK_TERMS.any { title.contains(it) }) return true
    // Impact words alone never qualify; they need hazard/agencies context in
    // the headline itself (keeps generic business/politics stories out).
    return IMPACT_TERMS.any { title.contains(it) } &&
      CONTEXT_TERMS.any { title.contains(it) }
  }

  /** Filters a list, keeping disabled/unmatched articles out. */
  fun filterForDisaster(articles: List<NewsArticle>): List<NewsArticle> =
    articles.filter { isDisasterRelevant(it) }
}
