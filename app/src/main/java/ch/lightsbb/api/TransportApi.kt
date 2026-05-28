package ch.lightsbb.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the transport.opendata.ch v1 REST API.
 *
 * This is a free, publicly accessible API with no authentication or API key required.
 * No official rate-limit is documented, but the autocomplete pipeline already debounces
 * requests at 300 ms in [ch.lightsbb.viewmodel.SearchViewModel] to be a courteous client.
 *
 * All methods are `suspend` functions so they integrate cleanly with coroutines and
 * [androidx.lifecycle.ViewModel.viewModelScope]. Retrofit handles the thread dispatch
 * automatically — these functions suspend on a background I/O thread and resume on the
 * caller's dispatcher.
 *
 * Full API documentation: https://transport.opendata.ch/
 */
interface TransportApi {

    /**
     * Searches for stations (and optionally other location types) matching a partial name.
     *
     * Used to power the live autocomplete dropdowns in [ch.lightsbb.ui.SearchScreen].
     * The API returns up to 10 results ordered by relevance. Setting [type] to `"station"`
     * filters out address and point-of-interest results that would not be valid journey endpoints.
     *
     * @param query Partial station name to search for. The API performs well from 2 characters
     *   onward; [ch.lightsbb.viewmodel.SearchViewModel] enforces this minimum before calling.
     * @param type Location type filter. Defaults to `"station"` to exclude non-transit results.
     * @return A [LocationsResponse] containing up to 10 matching stations.
     * @throws retrofit2.HttpException on a non-2xx HTTP response.
     * @throws java.io.IOException on network failure.
     */
    @GET("locations")
    suspend fun getLocations(
        @Query("query") query: String,
        @Query("type") type: String = "station"
    ): LocationsResponse

    /**
     * Fetches the next departing connections between two stations.
     *
     * The API resolves both [from] and [to] against its own location database, so either
     * a station name (e.g. `"Zürich HB"`) or a numeric station ID (e.g. `"008503000"`)
     * can be passed. Station names work reliably for well-known stations but may be ambiguous
     * for smaller stops with similar names — in those cases the ID is more precise.
     *
     * Connections are returned from the current server time onward, sorted by departure
     * time ascending. There is no pagination; call again with a `date`/`time` parameter
     * (not yet implemented in this app) to fetch later departures.
     *
     * @param from Origin station name or ID.
     * @param to Destination station name or ID.
     * @param limit Maximum number of connections to return. Capped at 16 by the API.
     *   Defaults to 5 — enough for a quick glance without excessive loading time.
     * @return A [ConnectionsResponse] with up to [limit] connections.
     * @throws retrofit2.HttpException on a non-2xx HTTP response.
     * @throws java.io.IOException on network failure.
     */
    @GET("connections")
    suspend fun getConnections(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("limit") limit: Int = 5
    ): ConnectionsResponse
}

/**
 * Process-lifetime singleton Retrofit client for [TransportApi].
 *
 * [api] is created lazily on first access and reused for the lifetime of the process.
 * Both Retrofit and OkHttp are internally thread-safe and relatively expensive to
 * initialise (especially OkHttp's connection pool), so a single shared instance is the
 * correct pattern.
 *
 * **Testing:** do not interact with this object in unit tests. Instead, pass a mock
 * [TransportApi] directly to [ch.lightsbb.viewmodel.SearchViewModel] via its `api`
 * constructor parameter to avoid real network calls.
 */
object TransportApiClient {

    private const val BASE_URL = "https://transport.opendata.ch/v1/"

    /**
     * Lazily initialised [TransportApi] implementation backed by Gson for JSON deserialisation.
     *
     * Gson is configured with its default settings, which means unknown JSON fields are
     * silently ignored (forward-compatible) and missing nullable fields default to null
     * (consistent with the nullable types in [Models]).
     */
    val api: TransportApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TransportApi::class.java)
    }
}
