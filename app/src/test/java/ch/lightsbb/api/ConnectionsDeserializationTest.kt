package ch.lightsbb.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the Gson data model classes in [ch.lightsbb.api] correctly deserialise
 * JSON responses from the transport.opendata.ch API.
 *
 * These are pure JVM tests — no Android framework, no networking, no mocking. Each test
 * parses a representative JSON snippet (taken from real API responses) and asserts that
 * the resulting Kotlin objects have the expected field values.
 *
 * ## What is tested
 * - [LocationsResponse]: single station, multiple stations, null ID field.
 * - [ConnectionsResponse]: direct connection with one section, multi-product connection.
 * - [Journey.destination]: the `@SerializedName("to")` mapping that avoids the Kotlin `to` keyword.
 * - [Section]: walking leg (null journey, non-null walk).
 * - Null-field handling: the API sometimes omits fields entirely; Gson should map them to null.
 *
 * ## Why Gson deserialisation needs testing
 * Most bugs here would be silent at compile time — a wrong [com.google.gson.annotations.SerializedName]
 * annotation or a mismatched field name produces no compile error, only a null at runtime.
 * These tests catch that class of mistake early.
 */
class ConnectionsDeserializationTest {

    private val gson = Gson()

    // -------------------------------------------------------------------------
    // /locations endpoint
    // -------------------------------------------------------------------------

    @Test
    fun `deserializes a locations response with a single station`() {
        val json = """
            {
              "stations": [
                {
                  "id": "008503000",
                  "name": "Zürich HB",
                  "coordinate": { "type": "WGS84", "x": 8.540192, "y": 47.378177 }
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, LocationsResponse::class.java)

        assertEquals(1, response.stations.size)
        val station = response.stations[0]
        assertEquals("008503000", station.id)
        assertEquals("Zürich HB", station.name)
        // Verify both coordinate axes — note the API's x=longitude, y=latitude convention.
        assertEquals(8.540192, station.coordinate?.x)
        assertEquals(47.378177, station.coordinate?.y)
    }

    @Test
    fun `deserializes multiple stations and preserves order`() {
        val json = """
            {
              "stations": [
                { "id": "1", "name": "Zürich HB" },
                { "id": "2", "name": "Zürich Stadelhofen" },
                { "id": "3", "name": "Zürich Oerlikon" }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, LocationsResponse::class.java)
        assertEquals(3, response.stations.size)
        // Order must be preserved — results are already sorted by relevance by the API.
        assertEquals("Zürich Stadelhofen", response.stations[1].name)
    }

    @Test
    fun `null station id is preserved as null rather than a default value`() {
        // The API occasionally returns null IDs for certain location types.
        // Gson must map this to Kotlin null, not an empty string or zero.
        val json = """{ "stations": [{ "id": null, "name": "Zürich HB" }] }"""
        val response = gson.fromJson(json, LocationsResponse::class.java)
        assertNull(response.stations[0].id)
    }

    // -------------------------------------------------------------------------
    // /connections endpoint
    // -------------------------------------------------------------------------

    @Test
    fun `deserializes a direct connection with one section`() {
        // Representative real API response for Zürich HB → Bern (IC 1, direct, 57 min).
        val json = """
            {
              "connections": [
                {
                  "from": {
                    "station": { "id": "008503000", "name": "Zürich HB" },
                    "departure": "2024-01-15T10:00:00+0100",
                    "arrival": null,
                    "delay": 0,
                    "platform": "7"
                  },
                  "to": {
                    "station": { "id": "008507000", "name": "Bern" },
                    "arrival": "2024-01-15T10:57:00+0100",
                    "departure": null,
                    "delay": null,
                    "platform": "8"
                  },
                  "duration": "00d00:57:00",
                  "transfers": 0,
                  "products": ["IC"],
                  "sections": [
                    {
                      "journey": {
                        "name": "IC 1",
                        "category": "IC",
                        "number": "1",
                        "operator": "SBB",
                        "to": "Brig"
                      },
                      "walk": null,
                      "departure": {
                        "station": { "id": "008503000", "name": "Zürich HB" },
                        "departure": "2024-01-15T10:00:00+0100",
                        "platform": "7"
                      },
                      "arrival": {
                        "station": { "id": "008507000", "name": "Bern" },
                        "arrival": "2024-01-15T10:57:00+0100",
                        "platform": "8"
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, ConnectionsResponse::class.java)

        assertEquals(1, response.connections.size)
        val conn = response.connections[0]

        // Verify from-stop fields
        assertEquals("Zürich HB", conn.from.station?.name)
        assertEquals("2024-01-15T10:00:00+0100", conn.from.departure)
        assertEquals("7", conn.from.platform)
        assertNull(conn.from.arrival) // from-stop has no arrival, only departure

        // Verify to-stop fields
        assertEquals("Bern", conn.to.station?.name)
        assertEquals("2024-01-15T10:57:00+0100", conn.to.arrival)
        assertEquals("8", conn.to.platform)

        // Verify top-level connection metadata
        assertEquals("00d00:57:00", conn.duration)
        assertEquals(0, conn.transfers)
        assertEquals(listOf("IC"), conn.products)
        assertEquals(1, conn.sections?.size)
    }

    @Test
    fun `journey to field maps to destination via SerializedName annotation`() {
        // The JSON field is named "to", but that conflicts with Kotlin's built-in `to` infix
        // function. @SerializedName("to") remaps it to the `destination` property.
        // This test would fail silently if the annotation were removed — destination would be null.
        val json = """{ "category": "IC", "number": "1", "operator": "SBB", "to": "Brig" }"""
        val journey = gson.fromJson(json, Journey::class.java)
        assertEquals("Brig", journey.destination)
    }

    @Test
    fun `walk section has null journey and non-null walk with duration`() {
        // A walking transfer leg — journey is null, walk carries the estimated minutes.
        val json = """
            {
              "journey": null,
              "walk": { "duration": 4 },
              "departure": null,
              "arrival": null
            }
        """.trimIndent()

        val section = gson.fromJson(json, Section::class.java)
        assertNull(section.journey)
        assertEquals(4, section.walk?.duration)
    }

    @Test
    fun `connection with multiple products deserializes full product list`() {
        // A connection that uses both an intercity train and an S-Bahn.
        val json = """
            {
              "connections": [
                {
                  "from": { "station": { "id": "1", "name": "A" }, "departure": "2024-01-15T09:00:00+0100" },
                  "to":   { "station": { "id": "2", "name": "B" }, "arrival":   "2024-01-15T10:30:00+0100" },
                  "duration": "00d01:30:00",
                  "transfers": 1,
                  "products": ["IC", "S"],
                  "sections": []
                }
              ]
            }
        """.trimIndent()

        val conn = gson.fromJson(json, ConnectionsResponse::class.java).connections[0]
        assertEquals(1, conn.transfers)
        assertEquals(listOf("IC", "S"), conn.products)
        assertTrue(conn.sections!!.isEmpty())
    }
}
