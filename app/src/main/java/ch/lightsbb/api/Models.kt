package ch.lightsbb.api

import com.google.gson.annotations.SerializedName

data class LocationsResponse(
    val stations: List<Station>
)

data class Station(
    val id: String?,
    val name: String?,
    val coordinate: Coordinate?
)

data class Coordinate(
    val type: String?,
    val x: Double?,
    val y: Double?
)

data class ConnectionsResponse(
    val connections: List<Connection>
)

data class Connection(
    val from: Stop,
    val to: Stop,
    val duration: String?,
    val transfers: Int?,
    val products: List<String>?,
    val sections: List<Section>?
)

data class Stop(
    val station: Station?,
    val arrival: String?,
    val departure: String?,
    val delay: Int?,
    val platform: String?
)

data class Section(
    val journey: Journey?,
    val walk: Walk?,
    val departure: Stop?,
    val arrival: Stop?
)

data class Journey(
    val name: String?,
    val category: String?,
    val number: String?,
    val operator: String?,
    @SerializedName("to") val destination: String?
)

data class Walk(
    val duration: Int?
)
