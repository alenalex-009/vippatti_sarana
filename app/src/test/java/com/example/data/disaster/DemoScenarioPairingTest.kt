package com.example.data.disaster

import com.example.data.model.GeoMath
import com.example.data.model.HazardType
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * USER-FIX CONTRACTS for the demo scenario (2026-10-02 feedback round):
 *  - tapping a hazard TYPE filter with demo ON places THAT disaster near the
 *    focus and gives it its own shelters - "hazard occurs where I am, route
 *    to safety" is demoable for all five types, not just the hashed primary;
 *  - regional (unfocused) shelters are PAIRED to hazards: every shelter sits
 *    outside its hazard's circle at reachable distance - no loose green pins;
 *  - shelter fan spans near AND farther candidates (adjustable choice), all
 *    deterministic for the same focus.
 */
class DemoScenarioPairingTest {

  private val vizag = GeoPoint(17.6868, 83.2185)

  @Test
  fun `a type-filter secondary hazard sits WALKING distance from the focus`() {
    HazardType.entries.filter {
      it != HazardType.OTHER && it != HazardType.WEATHER_ALERT
    }.forEach { type ->
      val hz = DemoNetworkAroundUser.secondaryHazard(vizag, type)
      val d = GeoMath.distanceMeters(vizag, hz.center)
      assertTrue("$type secondary at ${d}m", d >= 100.0 && d <= 4_000.0)
      // The hazard must actually cover the user's area: focus inside radius+2 km.
      assertTrue("$type must threaten near the user", d < hz.radiusMeters + 2_500)
      assertTrue("labelled demo", hz.sourceStatus.contains("DEMO"))
      assertTrue("type explicit", hz.type == type)
    }
  }

  @Test
  fun `secondary hazards are deterministic and per-type distinct`() {
    val a = DemoNetworkAroundUser.secondaryHazard(vizag, HazardType.EARTHQUAKE)
    val b = DemoNetworkAroundUser.secondaryHazard(vizag, HazardType.EARTHQUAKE)
    val c = DemoNetworkAroundUser.secondaryHazard(vizag, HazardType.FLOOD)
    assertTrue("same place+type = same hazard",
      a.id == b.id && a.center == b.center)
    assertTrue("different type = different scenario spot", a.id != c.id)
  }

  @Test
  fun `shelters serving a hazard clear its circle and stay reachable`() {
    val hz = DemoNetworkAroundUser.secondaryHazard(vizag, HazardType.FLOOD)
    val shelters = DemoNetworkAroundUser.sheltersAroundHazard(
      anchor = hz.center,
      hazardRadiusMeters = hz.radiusMeters,
      hazardBearingFromAnchor = 0.0,
      count = 3,
      maxDistanceKm = 9.0,
      idSalt = "-t"
    )
    assertTrue("at least 2 shelters serve the hazard", shelters.size >= 2)
    val userDistances = shelters.map { GeoMath.distanceMeters(vizag, it.point) / 1000.0 }
    shelters.forEach { sz ->
      val d = GeoMath.distanceMeters(hz.center, sz.point)
      assertTrue("${sz.id} inside the hazard circle", d > hz.radiusMeters + 250)
      assertTrue("shelter must be a real journey, not next door: $userDistances",
        userDistances.min() <= 2.0 && userDistances.max() >= 1.0)
    }
  }

  @Test
  fun `regional pairing gives every demo hazard its own outside-circle shelters`() {
    PilotRegionData.hazardZones.forEach { hz ->
      val paired = DemoNetworkAroundUser.pairedSheltersFor(hz)
      assertTrue("${hz.id}: hazard with no paired shelter", paired.isNotEmpty())
      paired.forEach { sz ->
        val d = GeoMath.distanceMeters(hz.center, sz.point)
        assertTrue("${sz.id} sits inside ${hz.id} circle", d > hz.radiusMeters)
        assertTrue("${sz.id} floats unreachable from its hazard: ${d}m", d < 8_500)
        assertTrue("paired shelter is demo-labelled",
          sz.verificationStatus.contains("SIMULATED"))
      }
    }
  }
}
