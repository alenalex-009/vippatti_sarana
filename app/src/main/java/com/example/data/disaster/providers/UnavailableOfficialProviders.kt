package com.example.data.disaster.providers

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterEvent
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.ProviderFailureKind
import com.example.data.disaster.ProviderResult

/**
 * ============================================================================
 * EXPLICITLY UNAVAILABLE OFFICIAL SOURCES (section 8)
 * ============================================================================
 *
 * CWC, NDEM/NRSC and INCOIS are the authoritative Indian sources for river
 * levels, inundation/landslide layers and tsunami bulletins. During the Phase-2
 * source audit none could be reached legitimately:
 *
 *   CWC    (cwc.gov.in/en/hydrology) — no documented open programmatic feed for
 *           live gauge readings; the data sits behind a portal UI.
 *   NDEM   (ndem.nrsc.gov.in)        — authorised NRSC/ISRO login required. Not
 *           scraped, and no third-party substitute adopted.
 *   INCOIS (incois.gov.in)          — bulletin portal; no bulk machine-readable
 *           feed identified.
 *
 * They are implemented as real providers that RETURN AN HONEST FAILURE rather
 * than being omitted from the registry. That distinction matters for the
 * acceptance criteria and for the demo: the app can say "CWC river level:
 * unavailable — no open feed" instead of showing an empty river layer that
 * reads as "no flood risk".
 *
 * Consequence that MUST hold downstream: an unavailable river level stays
 * UNKNOWN. It is never turned into zero, and rainfall is never used to
 * estimate a gauge reading.
 */
sealed class UnavailableOfficialProvider(
  override val providerId: DisasterSource,
  private val reasonText: String
) : DisasterDataProvider {

  override suspend fun fetchIndiaEvents(): ProviderResult =
    ProviderResult.Failure(reasonText, ProviderFailureKind.FAILED)

  val reason: String get() = reasonText
}

/** Central Water Commission — live river gauge levels. */
class CwcGaugeUnavailableProvider : UnavailableOfficialProvider(
  DisasterSource.CWC_GAUGE,
  "Central Water Commission river-gauge data is unavailable — no open programmatic " +
    "feed. River level remains UNKNOWN; it is never estimated from rainfall."
)

/** NDEM / NRSC — official multi-hazard and inundation layers. */
class NdemUnavailableProvider : UnavailableOfficialProvider(
  DisasterSource.NDEM,
  "NDEM/NRSC official inundation and shelter layers require an authorised NRSC " +
    "login. Unavailable in this build; no third-party substitute is used."
)

/** INCOIS — Indian Tsunami Early Warning Centre. */
class IncoisUnavailableProvider : UnavailableOfficialProvider(
  DisasterSource.INCOIS,
  "INCOIS tsunami bulletins are published through a portal with no bulk feed. " +
    "No tsunami status is inferred by this app."
)