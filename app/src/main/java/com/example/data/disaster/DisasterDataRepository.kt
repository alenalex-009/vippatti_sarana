package com.example.data.disaster

import com.example.data.model.HazardZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Per-provider state surfaced to the UI (freshness label + last update time
 * + honest status, e.g. "Live source unavailable").
 */
data class ProviderState(
  val source: DisasterSource,
  val eventCount: Int,
  val fetchedAtMillis: Long,
  val isFromCache: Boolean,
  val isLive: Boolean,
  val statusMessage: String?,
  /**
   * Why this provider returned no fresh data: UNCONFIGURED, AUTHENTICATION_FAILED
   * or FAILED. Null means the provider answered (AVAILABLE) - the only case in
   * which a surface may label it live.
   */
  val failureKind: ProviderFailureKind? = null
)

/** Full repository snapshot consumed by the ViewModel + map renderer. */
data class DisasterFeed(
  val events: List<DisasterEvent>,
  val providerStates: List<ProviderState>,
  val userReports: List<DisasterEvent>,
  val isAnyLive: Boolean
)

/**
 * ============================================================================
 * DISASTER DATA REPOSITORY — the single data layer between providers and UI.
 * ============================================================================
 *
 * Pipeline per provider:
 *   fetch -> validate -> dedupe (source event id) -> drop expired ->
 *   write cache -> emit; offline: serve the last valid cached shard.
 *
 * LIVE vs MOCK: the repository only ever holds REAL fetched events (plus
 * explicitly submitted user reports). Mock-network hazards are NEVER mixed
 * in here — they remain a separate, clearly labeled concept owned by the
 * ViewModel (mock toggle).
 */
class DisasterDataRepository(
  private val providers: List<DisasterDataProvider>,
  private val cache: DisasterCache,
  private val clock: () -> Long = System::currentTimeMillis,
  /** Cache I/O dispatcher — tests inject the scheduler's dispatcher so runs are deterministic. */
  private val cacheDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {

  /** Manual sync: query every provider in parallel, update cache, return the merged feed. */
  suspend fun refresh(): DisasterFeed {
    val now = clock()
    // Fire all provider fetches concurrently — wait time = slowest provider,
    // not the sum (USGS + NASA FIRMS + IMD each have their own client/timeouts).
    val results = coroutineScope {
      providers.map { provider -> async { provider.providerId to provider.fetchIndiaEvents() } }
        .awaitAll()
        .toMap()
    }
    val states = mutableListOf<ProviderState>()
    val allEvents = mutableListOf<DisasterEvent>()
    for (provider in providers) {
      when (val result = results[provider.providerId] ?: continue) {
        is ProviderResult.Success -> {
          val valid = result.events
            .filter { it.isValid(now) }
            .dedupeBySourceEventId()
          withContext(cacheDispatcher) {
            cache.write(
              provider.providerId,
              CachedProviderFeed(valid, result.fetchedAtMillis, null)
            )
          }
          allEvents += valid
          states += ProviderState(
            source = provider.providerId,
            eventCount = valid.size,
            fetchedAtMillis = result.fetchedAtMillis,
            isFromCache = false,
            isLive = DisasterCachePolicy.isFresh(result.fetchedAtMillis, now),
            statusMessage = null,
            failureKind = null // AVAILABLE: a valid provider response arrived
          )
        }
        is ProviderResult.Failure -> {
          // Offline/auth failure: keep the last valid cached shard (if usable).
          val cached = withContext(cacheDispatcher) { cache.read(provider.providerId) }
          val usable = cached
            ?.takeIf { DisasterCachePolicy.isUsable(it.fetchedAtMillis, now) }
            ?.let { feed ->
              CachedProviderFeed(
                feed.events.filter { it.isValid(now) }.dedupeBySourceEventId(),
                feed.fetchedAtMillis,
                feed.statusMessage
              )
            }
          usable?.let { allEvents += it.events }
          states += ProviderState(
            source = provider.providerId,
            eventCount = usable?.events?.size ?: 0,
            fetchedAtMillis = usable?.fetchedAtMillis ?: 0L,
            isFromCache = usable != null,
            isLive = false,
            statusMessage = result.reason,
            // The provider's own classification travels with the state so the
            // UI can separate "not configured" from "rejected" from "failed".
            failureKind = result.kind
          )
        }
      }
    }
    return DisasterFeed(
      events = allEvents.dedupeBySourceEventId(),
      providerStates = states,
      userReports = emptyList(),
      isAnyLive = states.any { it.isLive }
    )
  }

  /**
   * True when some provider has NO state in [states] — i.e. it has never
   * produced a usable cached shard (e.g. NASA FIRMS on a device that cached
   * only USGS/IMD shards). A cache-first cold start must not let such a
   * source stay invisible forever: the caller uses this to trigger one
   * background refresh for the uncovered providers.
   */
  fun hasMissingProvider(states: List<ProviderState>): Boolean =
    providers.any { provider -> states.none { it.source == provider.providerId } }

  /**
   * Cold start: serve usable cached shards instantly (offline survival); a
   * fresh (< 15 min) cache avoids network entirely (quota/battery friendly).
   * Returns null when nothing usable is cached — the ViewModel then fetches.
   */
  suspend fun loadCachedOnly(): DisasterFeed? {
    val now = clock()
    val states = mutableListOf<ProviderState>()
    val allEvents = mutableListOf<DisasterEvent>()
    var anyFresh = false
    for (provider in providers) {
      val cached = withContext(cacheDispatcher) { cache.read(provider.providerId) }
      val usable = cached?.takeIf { DisasterCachePolicy.isUsable(it.fetchedAtMillis, now) }
      if (usable != null) {
        val valid = usable.events.filter { it.isValid(now) }.dedupeBySourceEventId()
        allEvents += valid
        val fresh = DisasterCachePolicy.isFresh(usable.fetchedAtMillis, now)
        anyFresh = anyFresh || fresh
        states += ProviderState(
          source = provider.providerId,
          eventCount = valid.size,
          fetchedAtMillis = usable.fetchedAtMillis,
          isFromCache = true,
          isLive = fresh,
          statusMessage = null
        )
      }
    }
    if (states.isEmpty()) return null
    return DisasterFeed(
      events = allEvents.dedupeBySourceEventId(),
      providerStates = states,
      userReports = emptyList(),
      isAnyLive = anyFresh
    )
  }
}

// ============================================================================
// Pure pipeline operations (top-level so any consumer can use them directly).
// ============================================================================

/** Event is valid when not expired and inside the fetch honesty window. */
fun DisasterEvent.isValid(nowMillis: Long): Boolean {
  if (status == EventStatus.EXPIRED) return false
  if (expiresAtMillis != null && expiresAtMillis <= nowMillis) return false
  if (observedAtMillis <= 0L) return false
  // Events older than the cache max age are not actionable intelligence.
  return nowMillis - observedAtMillis <= DisasterCachePolicy.MAX_AGE_MILLIS
}

/** Dedupe by source + source event id, newest first. */
fun List<DisasterEvent>.dedupeBySourceEventId(): List<DisasterEvent> {
  val seen = HashSet<String>()
  val out = mutableListOf<DisasterEvent>()
  for (event in sortedByDescending { it.updatedAtMillis }) {
    if (event.dedupeKey in seen) continue
    seen += event.dedupeKey
    out += event
  }
  return out
}

/**
 * Events eligible for hazard-ZONE conversion (risk engine + map circles).
 *
 * NASA FIRMS detections are satellite fire/hotspot OBSERVATIONS: the provider
 * semantics describe a hotspot measurement, not an official fire hazard area,
 * so they are never inflated into a circle here — they render as fire
 * observation markers instead (deployDisasterEvents). Citizen reports and the
 * other live providers keep their zone semantics. Pure + unit-testable.
 */
fun liveZoneEvents(events: List<DisasterEvent>, nowMillis: Long): List<DisasterEvent> =
  events.filter { it.source != DisasterSource.NASA_FIRMS && it.isValid(nowMillis) }

/** Hazard zones for the risk/evaluator/routing engines (live events only). */
fun toHazardZones(events: List<DisasterEvent>): List<HazardZone> =
  events.mapNotNull { DisasterEventNormalizer.toHazardZone(it) }

