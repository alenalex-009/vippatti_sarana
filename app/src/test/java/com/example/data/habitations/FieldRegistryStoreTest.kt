package com.example.data.habitations

import com.example.data.model.SafeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contracts for the FIELD REGISTRY store: operator-entered shelter and
 * habitation records must round-trip losslessly through the wire format,
 * survive replace-all semantics, and never silently drop a record with an
 * invalid geometry (honest rejected list instead).
 */
class FieldRegistryStoreTest {

  private fun shelter(id: String = "sh1") = SafeZone(
    id = id, name = "Govt UP School Nadakkavu", lat = 11.2634, lon = 75.7981,
    locationNote = "Kalpetta ward 3", capacityTotal = 420, capacityCurrent = 12,
    waterAvailable = true, foodAvailable = false, electricityAvailable = true,
    sanitationAvailable = true, medicalSupport = false,
    accessibility = "Motorable road", womenChildrenSuitability = true,
    operatingStatus = "OPEN", verificationStatus = "FIELD RECORD",
    elevationNote = "", landAreaSquareMeters = 3000.0, waterLitresPerDay = 6000.0,
    toiletCount = 12,
    provenance = com.example.data.model.DataProvenance(
      source = "Field entry — operator device",
      classification = com.example.data.model.DataClassification.OBSERVED
    )
  )

  private fun habitation(id: String = "hb1") = Habitation(
    id = id, name = "Pulpally settlement",
    point = com.example.data.routing.GeoPoint(11.98, 75.97),
    population = PopulationInput(230, com.example.data.model.DataClassification.ESTIMATED, "panchayat note"),
    vulnerableShare = 0.32f,
    terrainVerdict = null,
    historicalEventCount = 4
  )

  @Test
  fun `shelter records round-trip through the wire format exactly`() {
    val json = FieldRegistryJson.encodeShelters(listOf(shelter()))
    val back = FieldRegistryJson.decodeShelters(json).records
    assertEquals(1, back.size)
    val s = back.single()
    assertEquals("sh1", s.id)
    assertEquals(11.2634, s.lat, 1e-9)
    assertEquals(75.7981, s.lon, 1e-9)
    assertEquals(420, s.capacityTotal)
    assertEquals(3000.0, s.landAreaSquareMeters!!, 1e-9)
    assertEquals(6000.0, s.waterLitresPerDay!!, 1e-9)
    assertEquals(12, s.toiletCount)
    assertTrue(s.waterAvailable && !s.foodAvailable)
    assertEquals("OPEN", s.operatingStatus)
  }

  @Test
  fun `habitation records round-trip including population and history`() {
    val json = FieldRegistryJson.encodeHabitations(listOf(habitation()))
    val back = FieldRegistryJson.decodeHabitations(json).records
    assertEquals(1, back.size)
    val h = back.single()
    assertEquals("Pulpally settlement", h.name)
    assertEquals(230, h.population!!.value)
    assertEquals(0.32, h.vulnerableShare!!.toDouble(), 1e-3)
    assertEquals(4, h.historicalEventCount)
    assertNull(h.terrainVerdict) // assessed at runtime, never stored
  }

  @Test
  fun `records outside India geometry are rejected with a reason not dropped silently`() {
    val bad = shelter("bad").copy(lat = 0.0, lon = 0.0) // Null Island
    val decoded = FieldRegistryJson.decodeShelters(FieldRegistryJson.encodeShelters(listOf(bad, shelter("ok"))))
    assertEquals(listOf("ok"), decoded.records.map { it.id })
    assertTrue(decoded.rejected.any { it.contains("bad") })
  }

  @Test
  fun `an empty or corrupt file decodes to empty lists`() {
    assertEquals(0, FieldRegistryJson.decodeShelters("").records.size)
    assertEquals(0, FieldRegistryJson.decodeShelters("{not json").records.size)
    assertEquals(0, FieldRegistryJson.decodeHabitations("[]garbage").records.size)
  }

  @Test
  fun `replacing the whole set is atomic at the store level`() {
    val store = InMemoryFieldRegistryStore()
    store.saveShelters(listOf(shelter("a"), shelter("b")))
    store.saveShelters(listOf(shelter("c")))
    assertEquals(listOf("c"), store.loadShelters().map { it.id })
    store.saveHabitations(listOf(habitation("x")))
    assertNotNull(store.loadHabitations().firstOrNull { it.id == "x" })
  }
}
