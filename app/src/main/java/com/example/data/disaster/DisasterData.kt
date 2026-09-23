package com.example.data.disaster

/**
 * One feed card in the Disaster & Weather Intelligence tab. Instances are
 * mapped from REAL GNews articles (see data/news/NewsPresentation.kt) — no
 * fabricated dispatch entries exist in this codebase.
 */
data class FeedDispatch(
  val id: String,
  val agency: String,
  val issuedTime: String,
  val tag: String,
  val tagType: DispatchTagType,
  val title: String,
  val description: String,
  val location: String,
  val actionLabel: String,
  val iconType: DispatchIconType,
  /** Real publisher URL — "Read Full Story" opens it in the browser. */
  val url: String? = null
)

enum class DispatchTagType {
  HIGH_ALERT,
  SHELTER_READY,
  ROAD_CLOSED,
  CAPACITY_INFO
}

enum class DispatchIconType {
  RAIN,
  SHELTER,
  FLOOD,
  LOGISTICS
}

data class EmergencyContact(
  val id: String,
  val name: String,
  val role: String,
  val phone: String,
  val locationNote: String,
  val initials: String,
  val colorHex: Long
)

data class GoBagItem(
  val id: String,
  val name: String,
  val detail: String,
  val isChecked: Boolean = false,
  val iconName: String
)

// Weather readings for the India network (live Open-Meteo feed; empty until
// the first reading lands).
//
// Every field defaults to EMPTY and the UI renders an explicit "no data"
// state per cell until OpenMeteoWeatherService delivers a real reading.
// Nothing here fabricates weather — empty means empty, not invented.
data class WeatherMetrics(
  val currentTemp: String = "",
  val rainfallIntensity: String = "",
  val windGust: String = "",
  val trend3h: String = "",
  val surgeForecast: String = "",
  /**
   * PHASE 3: the PROVIDER's own observation time (Open-Meteo `current.time` +
   * `utc_offset_seconds`). 0L = the payload carried no usable timestamp, which
   * the UI shows as "observation time unknown" - it is never estimated.
   */
  val observedAtMillis: Long = 0L
)

/**
 * Profile data (go-bag checklist + seed kin contacts).
 * Disaster intelligence (alerts / breaking alerts / dispatch feed) now flows
 * ONLY from the real GNews pipeline in data/news — nothing here fabricates
 * alerts, news or dispatches anymore.
 */
object MockDisasterRepository {

  val defaultGoBagItems = listOf(
    GoBagItem("item-1", "Go-Bag", "Waterproof 15L", true, "backpack"),
    GoBagItem("item-2", "ID & Papers", "Sealed zip pouch", true, "description"),
    GoBagItem("item-3", "Prescription Meds", "7-day supply min", false, "medication"),
    GoBagItem("item-4", "Clean Water", "2L per person", true, "water_bottle"),
    GoBagItem("item-5", "LED Flashlight & Batteries", "Water-resistant torch", false, "flashlight"),
    GoBagItem("item-6", "Power Bank & Cable", "10,000mAh charged", true, "battery_charging_full"),
    GoBagItem("item-7", "Emergency Whistle", "High decibel signal", false, "notifications_active"),
    GoBagItem("item-8", "Non-perishable Rations", "High protein bars (3 days)", true, "restaurant")
  )

  // Kin contacts start EMPTY — seeded fake people with invented numbers
  // were removed: contacts must come from the user via Add Contact.
}
