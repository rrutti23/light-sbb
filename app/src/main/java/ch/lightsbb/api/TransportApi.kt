package ch.lightsbb.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface TransportApi {
    @GET("locations")
    suspend fun getLocations(
        @Query("query") query: String,
        @Query("type") type: String = "station"
    ): LocationsResponse

    @GET("connections")
    suspend fun getConnections(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("limit") limit: Int = 5
    ): ConnectionsResponse
}

object TransportApiClient {
    private const val BASE_URL = "https://transport.opendata.ch/v1/"

    val api: TransportApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TransportApi::class.java)
    }
}
