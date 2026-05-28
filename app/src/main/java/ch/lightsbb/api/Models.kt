package ch.lightsbb.api

import com.google.gson.annotations.SerializedName

/**
 * Root response object returned by the `GET /locations` endpoint.
 *
 * The transport.opendata.ch API returns a list of matching stations when queried
 * with a partial name. Results are ordered by relevance score descending.
 *
 * Example request:
 * ```
 * GET https://transport.opendata.ch/v1/locations?query=Zür&type=station
 * ```
 */
data class LocationsResponse(
    /** Up to 10 stations matching the query string, ordered by relevance. */
    val stations: List<Station>
)

/**
 * A single transit station or stop returned by the locations endpoint.
 *
 * Both [id] and [coordinate] may be null for non-station location types (addresses,
 * points of interest). When the request includes `type=station` these fields are
 * almost always present, but defensive null-handling is still required because the
 * API contract does not guarantee them.
 */
data class Station(
    /**
     * Unique opaque identifier for this station assigned by the Swiss timetable authority
     * (e.g. `"008503000"` for Zürich HB). Can be passed to [TransportApi.getConnections]
     * instead of a name for unambiguous station matching.
     */
    val id: String?,

    /** Human-readable station name as shown on departure boards (e.g. `"Zürich HB"`). */
    val name: String?,

    /** WGS-84 geographic coordinates of the station. Null for non-station results. */
    val coordinate: Coordinate?
)

/**
 * Geographic position of a station in the WGS-84 coordinate system.
 *
 * **Axis ordering warning:** the API uses `x` for longitude and `y` for latitude,
 * which is the reverse of the conventional `(latitude, longitude)` ordering used by
 * most mapping libraries. Swap them if passing coordinates to Google Maps or Mapbox.
 */
data class Coordinate(
    /** Coordinate reference system identifier — always `"WGS84"` for this API. */
    val type: String?,

    /** Longitude (east–west axis), e.g. `8.540192` for Zürich HB. */
    val x: Double?,

    /** Latitude (north–south axis), e.g. `47.378177` for Zürich HB. */
    val y: Double?
)

/**
 * Root response object returned by the `GET /connections` endpoint.
 *
 * Contains a flat list of [Connection] objects sorted by departure time ascending.
 * The list length is bounded by the `limit` query parameter (this app requests 5).
 *
 * Example request:
 * ```
 * GET https://transport.opendata.ch/v1/connections?from=Zürich&to=Bern&limit=5
 * ```
 */
data class ConnectionsResponse(
    /** Ordered list of connections, earliest departure first. */
    val connections: List<Connection>
)

/**
 * A complete journey from one station to another, potentially involving one or more transfers.
 *
 * A [Connection] is composed of one or more [Section]s:
 * - A direct train has a single section with a non-null [Section.journey].
 * - A connection requiring a walk between platforms includes a [Section] where
 *   [Section.walk] is non-null and [Section.journey] is null.
 *
 * The [from] and [to] stops reflect the endpoints of the full journey, not individual legs.
 */
data class Connection(
    /** First departure of the entire journey (origin station and time). */
    val from: Stop,

    /** Final arrival of the entire journey (destination station and time). */
    val to: Stop,

    /**
     * Total travel time in the API's custom duration format: `"00d00:57:00"` (days:hours:minutes:seconds).
     * Use [ch.lightsbb.util.formatDuration] to convert this into a human-readable label like `"57m"`.
     */
    val duration: String?,

    /** Number of transfers required. `0` indicates a direct connection without changes. */
    val transfers: Int?,

    /**
     * Transport products (vehicle types) used in this connection, e.g. `["IC"]`, `["IC", "S"]`.
     * Possible values include `IC`, `EC`, `IR`, `RE`, `S`, `B` (bus), `T` (tram), etc.
     */
    val products: List<String>?,

    /**
     * Ordered list of individual legs that make up this connection.
     * Iterating these provides the per-section platform, time, and vehicle details
     * shown in the expanded view of [ch.lightsbb.ui.ResultsScreen].
     */
    val sections: List<Section>?
)

/**
 * A single stop event — a departure from or an arrival at a station.
 *
 * The API uses [Stop] for three different roles:
 * - **[Connection.from]**: [departure] is set, [arrival] is null.
 * - **[Connection.to]**: [arrival] is set, [departure] is null.
 * - **[Section.departure] / [Section.arrival]**: either or both may be set depending on the leg.
 *
 * All timestamps are ISO 8601 strings with a UTC offset, e.g. `"2024-01-15T10:00:00+0100"`.
 * Use [ch.lightsbb.util.formatTime] to extract the `HH:MM` portion for display.
 */
data class Stop(
    /** The station at which this stop event occurs. */
    val station: Station?,

    /** ISO 8601 arrival timestamp, or null when this stop is departure-only. */
    val arrival: String?,

    /** ISO 8601 departure timestamp, or null when this stop is arrival-only. */
    val departure: String?,

    /**
     * Real-time delay in minutes relative to the scheduled time.
     * Null when no real-time data is available from the network operator.
     */
    val delay: Int?,

    /**
     * Platform or track identifier as printed on the timetable and departure boards
     * (e.g. `"7"`, `"3B"`, `"A"`). Null if the platform is not yet assigned.
     */
    val platform: String?
)

/**
 * One leg of a multi-section journey.
 *
 * Exactly one of [journey] or [walk] will be non-null at any given time:
 * - [journey] is populated for vehicle legs (train, bus, tram).
 * - [walk] is populated for pedestrian transfer legs between adjacent stops.
 *
 * The [departure] and [arrival] stops describe the endpoints of this specific leg,
 * which may differ from the overall [Connection.from] and [Connection.to].
 */
data class Section(
    /** Vehicle service information for a train/bus/tram leg. Null for walking legs. */
    val journey: Journey?,

    /** Walk information for a pedestrian transfer leg. Null for vehicle legs. */
    val walk: Walk?,

    /** Departure stop for this leg. */
    val departure: Stop?,

    /** Arrival stop for this leg. */
    val arrival: Stop?
)

/**
 * Details about the vehicle service operating a single section leg.
 *
 * The [destination] field represents the **terminus of the vehicle service**, not
 * necessarily the passenger's destination. For example, a passenger travelling from
 * Zürich to Bern on IC 1 will see `"Brig"` as the destination because that is where
 * the train terminates, even though they alight at Bern.
 *
 * **Serialisation note:** the JSON field is named `"to"`, but this conflicts with
 * Kotlin's built-in `to` infix function used for [Pair] construction. The field is
 * therefore mapped to [destination] via [SerializedName].
 */
data class Journey(
    /** Full service name combining category and number, e.g. `"IC 1"`, `"S8"`. */
    val name: String?,

    /** Transport category abbreviation, e.g. `"IC"`, `"RE"`, `"S"`, `"B"`. */
    val category: String?,

    /** Service number within the category, e.g. `"1"` for IC 1. */
    val number: String?,

    /** Operating company name, e.g. `"SBB"`, `"BLS"`, `"TPF"`. */
    val operator: String?,

    /**
     * Final terminus of this vehicle service (JSON field name: `"to"`).
     * See class KDoc for why this differs from the passenger's destination.
     */
    @SerializedName("to") val destination: String?
)

/**
 * Metadata for a pedestrian transfer leg between two stops.
 */
data class Walk(
    /** Estimated walking time in minutes. Null if the API did not provide an estimate. */
    val duration: Int?
)
