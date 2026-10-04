package com.example.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.data.model.DataClassification
import com.example.data.routing.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * ============================================================================
 * RUNTIME PLACE RESOLUTION (dynamic-data rule)
 * ============================================================================
 *
 * The app is India-wide, so no district or state name may be baked into the
 * code. Place names for query scoping are resolved AT RUNTIME from the user's
 * own coordinates through the platform reverse geocoder, and are simply absent
 * when the platform cannot resolve them (offline, no geocoder service, no
 * result) - the caller then stays at national scope and says so.
 */
data class ResolvedPlace(
  val district: String? = null,
  val state: String? = null,
  val country: String? = null,
  /**
   * Finer administrative rings, kept NULL when the platform's reverse
   * geocoder genuinely does not return them. The UI renders such levels as
   * "Not available" - an absent ring is never guessed or carried over from
   * another location (user rule: do not fabricate hierarchy).
   */
  val subDistrict: String? = null,
  val villageTown: String? = null,
  val ward: String? = null,
  /** Which real mechanism produced these names, e.g. "Android reverse geocoder". */
  val source: String = SOURCE_ANDROID_GEOCODER,
  val classification: DataClassification = DataClassification.OBSERVED
) {
  /** True when at least one ring smaller than the country could be queried. */
  val isUsable: Boolean
    get() = !district.isNullOrBlank() || !state.isNullOrBlank()

  /** Honest one-line description, e.g. "Munnar, Kerala" or "India-wide only". */
  val summary: String
    get() = listOfNotNull(
      district?.takeIf { it.isNotBlank() },
      state?.takeIf { it.isNotBlank() },
      country?.takeIf { it.isNotBlank() }
    ).joinToString(", ").ifBlank { "India-wide only" }

  /**
   * The hierarchy rows for the currently RESOLVED coordinates, in Indian
   * planning order. Missing levels state "Not available" instead of being
   * silently dropped, so a stale-but-pretty label can never masquerade as
   * current data. Country is omitted (always India by app scope).
   */
  val adminRows: List<Pair<String, String>>
    get() = listOf(
      "State" to (state?.takeIf { it.isNotBlank() } ?: NOT_AVAILABLE),
      "District" to (district?.takeIf { it.isNotBlank() } ?: NOT_AVAILABLE),
      "Sub-district / Taluk" to (subDistrict?.takeIf { it.isNotBlank() } ?: NOT_AVAILABLE),
      "Village / Town" to (villageTown?.takeIf { it.isNotBlank() } ?: NOT_AVAILABLE),
      "Ward / Locality" to (ward?.takeIf { it.isNotBlank() } ?: NOT_AVAILABLE)
    )

  companion object {
    const val SOURCE_ANDROID_GEOCODER = "Android reverse geocoder"
    const val NOT_AVAILABLE = "Not available"
  }
}

/**
 * Resolves a coordinate to a place name. `null` means "could not be resolved"
 * and must never be replaced by a guessed or default district.
 */
fun interface PlaceResolver {
  suspend fun resolve(point: GeoPoint): ResolvedPlace?
}

/** Default resolver for tests and for builds without a usable geocoder. */
val UnresolvedPlaceResolver = PlaceResolver { null }

/**
 * Platform reverse geocoder (Android `Geocoder`). Uses the async listener API
 * on Android 13+ and the synchronous call below it, always off the main thread,
 * and returns null on any failure instead of inventing a name.
 *
 * The `Geocoder` service may be unavailable (no Play services, offline) - that
 * is reported as null, not as an error to be papered over.
 */
class AndroidGeocoderPlaceResolver(
  private val context: Context,
  private val source: String = ResolvedPlace.SOURCE_ANDROID_GEOCODER
) : PlaceResolver {

  override suspend fun resolve(point: GeoPoint): ResolvedPlace? = withContext(Dispatchers.IO) {
    try {
      if (!Geocoder.isPresent()) return@withContext null
      val geocoder = Geocoder(context, Locale.ENGLISH)
      val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
          geocoder.getFromLocation(point.lat, point.lon, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
              if (continuation.isActive) continuation.resume(addresses.firstOrNull())
            }

            override fun onError(errorMessage: String?) {
              if (continuation.isActive) continuation.resume(null)
            }
          })
        }
      } else {
        @Suppress("DEPRECATION")
        geocoder.getFromLocation(point.lat, point.lon, 1)?.firstOrNull()
      }
      address?.let { resolved ->
        ResolvedPlace(
          // subAdminArea is the district ring in India; locality is the
          // town/village; subLocality is the suburb/ward ring. Anything the
          // platform does not return stays NULL -> "Not available".
          district = (resolved.subAdminArea ?: resolved.locality)?.takeIf { it.isNotBlank() },
          state = resolved.adminArea?.takeIf { it.isNotBlank() },
          country = resolved.countryName?.takeIf { it.isNotBlank() },
          // Platform Address rings for India: adminArea=state,
          // subAdminArea=district, locality=town/village, subLocality=
          // locality/ward. Levels the platform does not return (e.g. mandal)
          // stay null -> the UI renders "Not available" (never fabricated).
          villageTown = resolved.locality?.takeIf { it.isNotBlank() },
          ward = resolved.subLocality?.takeIf { it.isNotBlank() }
            ?: resolved.featureName?.takeIf {
              it.isNotBlank() && it != resolved.locality &&
                it != resolved.subAdminArea && it != resolved.adminArea
            },
          source = source
        )
      }
    } catch (_: Exception) {
      null
    }
  }
}
