package ch.lightsbb.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionsDeserializationTest {

    private val gson = Gson()

    // --- /locations response ---

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
        assertEquals(8.540192, station.coordinate?.x)
        assertEquals(47.378177, station.coordinate?.y)
    }

    @Test
    fun `deserializes multiple stations`() {
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
        assertEquals("Zürich Stadelhofen", response.stations[1].name)
    }

    @Test
    fun `null station id is preserved as null`() {
        val json = """{ "stations": [{ "id": null, "name": "Zürich HB" }] }"""
        val response = gson.fromJson(json, LocationsResponse::class.java)
        assertNull(response.stations[0].id)
    }

    // --- /connections response ---

    @Test
    fun `deserializes a direct connection with one section`() {
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

        assertEquals("Zürich HB", conn.from.station?.name)
        assertEquals("2024-01-15T10:00:00+0100", conn.from.departure)
        assertEquals("7", conn.from.platform)
        assertNull(conn.from.arrival)

        assertEquals("Bern", conn.to.station?.name)
        assertEquals("2024-01-15T10:57:00+0100", conn.to.arrival)
        assertEquals("8", conn.to.platform)

        assertEquals("00d00:57:00", conn.duration)
        assertEquals(0, conn.transfers)
        assertEquals(listOf("IC"), conn.products)
        assertEquals(1, conn.sections?.size)
    }

    @Test
    fun `journey to field maps to destination via SerializedName`() {
        val json = """{ "category": "IC", "number": "1", "operator": "SBB", "to": "Brig" }"""
        val journey = gson.fromJson(json, Journey::class.java)
        assertEquals("Brig", journey.destination)
    }

    @Test
    fun `walk section has null journey and non-null walk`() {
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
    fun `connection with multiple products deserializes product list`() {
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
