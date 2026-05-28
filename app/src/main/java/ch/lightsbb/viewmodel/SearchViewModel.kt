package ch.lightsbb.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.lightsbb.api.Connection
import ch.lightsbb.api.Station
import ch.lightsbb.api.TransportApi
import ch.lightsbb.api.TransportApiClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable snapshot of the entire UI state shared across both screens.
 *
 * A new instance is emitted by [SearchViewModel.uiState] on every state change.
 * Because a single [SearchViewModel] is scoped to the Activity and shared between the
 * search and results screens, both screens observe the same flow — results are visible
 * on the results screen immediately after a successful search, with no extra data passing
 * through the navigation back-stack.
 *
 * @property fromQuery Current text in the "From" input field.
 * @property toQuery Current text in the "To" input field.
 * @property fromSuggestions Live autocomplete stations for [fromQuery]. Cleared when the
 *   query drops below 2 characters, when a station is selected, or when a search begins.
 * @property toSuggestions Live autocomplete stations for [toQuery]. Same clearing rules.
 * @property connections Results from the most recent successful connection search. Retained
 *   until the next search begins, so the user can navigate back and still see the results.
 * @property isLoadingConnections True while the connection search API call is in flight.
 *   The search button is replaced with a spinner during this period.
 * @property connectionError Error message from the most recent failed connection search, or
 *   null when the last search succeeded or no search has been attempted yet.
 */
data class SearchUiState(
    val fromQuery: String = "",
    val toQuery: String = "",
    val fromSuggestions: List<Station> = emptyList(),
    val toSuggestions: List<Station> = emptyList(),
    val connections: List<Connection> = emptyList(),
    val isLoadingConnections: Boolean = false,
    val connectionError: String? = null
)

/**
 * Shared ViewModel for the search and results screens.
 *
 * Scoped to the [ch.lightsbb.MainActivity] lifecycle so a single instance persists across
 * the search → results → back navigation cycle. This avoids re-fetching connections when
 * the user navigates back to search and then forward to results again.
 *
 * ## Autocomplete pipeline
 * Each text field drives its own [MutableStateFlow]. Typing updates the display query
 * immediately (zero latency for the user), while the actual API call is gated by a chain
 * of operators that keep network traffic minimal:
 *
 * 1. **[debounce] (300 ms)** — waits for a pause in typing before forwarding the value,
 *    dropping all intermediate keystrokes.
 * 2. **[filter] (≥ 2 chars)** — ignores queries too short to return meaningful results.
 * 3. **[distinctUntilChanged]** — skips the fetch if the stabilised query is identical to
 *    the last one sent (e.g. the user deleted and retyped the same character).
 * 4. **[flatMapLatest]** — cancels any in-flight request the moment a new query value
 *    arrives. Without this, a slow response for an old query could overwrite a faster
 *    response for the current query, showing the user stale suggestions.
 *
 * Errors inside the `flow { }` block are caught per-emission rather than at the top level.
 * This keeps the pipeline alive after a transient network failure — the next keystroke will
 * trigger a fresh attempt.
 *
 * ## Persistence
 * The last successfully searched from/to station pair is written to [android.content.SharedPreferences]
 * under the key `"sbb_prefs"`. These values pre-populate the fields on the next app launch
 * so the user does not have to re-type their most common route.
 *
 * ## Testability
 * The [api] constructor parameter defaults to [TransportApiClient.api] in production but
 * can be replaced with a [io.mockk.mockk]'d [TransportApi] in unit tests, avoiding real
 * HTTP calls without requiring a DI framework.
 *
 * @param application Required by [AndroidViewModel]; used solely to access [android.content.SharedPreferences].
 * @param api The transport API client. Defaults to the process-lifetime singleton.
 *   Override in tests to inject a mock.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    application: Application,
    private val api: TransportApi = TransportApiClient.api
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("sbb_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        // Pre-populate from the last successful search so the user's common route is ready.
        SearchUiState(
            fromQuery = prefs.getString("last_from", "") ?: "",
            toQuery = prefs.getString("last_to", "") ?: ""
        )
    )

    /**
     * Observable UI state. Collect this in Compose with
     * [androidx.lifecycle.compose.collectAsStateWithLifecycle] to automatically pause
     * collection when the composable is not visible (e.g. app in background).
     */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Separate flows that feed the debounce pipeline. They are intentionally decoupled from
    // the display query: emitting into them does not block or delay what the user sees in
    // the text field — the display updates synchronously, while the API call waits for the
    // debounce window to expire.
    private val fromQueryFlow = MutableStateFlow(_uiState.value.fromQuery)
    private val toQueryFlow = MutableStateFlow(_uiState.value.toQuery)

    init {
        observeAutocomplete(fromQueryFlow) { stations ->
            _uiState.update { it.copy(fromSuggestions = stations) }
        }
        observeAutocomplete(toQueryFlow) { stations ->
            _uiState.update { it.copy(toSuggestions = stations) }
        }
    }

    /**
     * Wires [queryFlow] to the autocomplete API with debounce, filtering, and cancellation.
     *
     * This function is called twice in [init] — once for the "from" field and once for the
     * "to" field — so both autocomplete pipelines share identical logic with different input
     * flows and output callbacks.
     *
     * The coroutine is launched in [viewModelScope] and cancelled automatically when the
     * ViewModel is cleared (i.e. when the Activity is finished).
     *
     * @param queryFlow Source flow of raw query strings as the user types.
     * @param onResult Callback invoked on the main thread with each new list of suggestions.
     */
    private fun observeAutocomplete(
        queryFlow: MutableStateFlow<String>,
        onResult: (List<Station>) -> Unit
    ) {
        queryFlow
            .debounce(300)           // wait for 300 ms of typing silence
            .filter { it.length >= 2 } // ignore very short queries
            .distinctUntilChanged()  // skip if the stabilised value hasn't changed
            .flatMapLatest { query ->
                // flatMapLatest cancels the previous inner flow (and its HTTP request) the
                // moment a new query arrives, preventing race conditions between responses.
                flow {
                    try {
                        emit(api.getLocations(query).stations.take(5))
                    } catch (e: Exception) {
                        // Emit empty rather than propagating — a network blip should not
                        // kill the entire autocomplete pipeline for the session.
                        emit(emptyList<Station>())
                    }
                }
            }
            .onEach(onResult)
            .launchIn(viewModelScope)
    }

    /**
     * Called on every keystroke in the "From" field.
     *
     * Updates [SearchUiState.fromQuery] immediately so the text field reflects the change
     * without any delay. Simultaneously pushes the new value into [fromQueryFlow] to
     * trigger the debounced autocomplete pipeline. If the query drops below 2 characters,
     * any previously shown suggestions are cleared so the dropdown doesn't display stale
     * results from a longer prior query.
     *
     * @param query The complete current value of the text field after the keystroke.
     */
    fun onFromQueryChange(query: String) {
        _uiState.update {
            it.copy(
                fromQuery = query,
                fromSuggestions = if (query.length < 2) emptyList() else it.fromSuggestions
            )
        }
        fromQueryFlow.value = query
    }

    /**
     * Called on every keystroke in the "To" field. Behaviour mirrors [onFromQueryChange].
     *
     * @param query The complete current value of the text field after the keystroke.
     */
    fun onToQueryChange(query: String) {
        _uiState.update {
            it.copy(
                toQuery = query,
                toSuggestions = if (query.length < 2) emptyList() else it.toSuggestions
            )
        }
        toQueryFlow.value = query
    }

    /**
     * Called when the user taps a station name in the "From" autocomplete dropdown.
     *
     * Sets the query text to the station's display name and dismisses the suggestion list.
     * Also resets [fromQueryFlow] to an empty string so the debounce pipeline does not
     * immediately re-trigger a fetch for the newly selected station name — the user has
     * already made their choice.
     *
     * @param station The station the user selected.
     */
    fun onFromStationSelected(station: Station) {
        _uiState.update { it.copy(fromQuery = station.name ?: "", fromSuggestions = emptyList()) }
        fromQueryFlow.value = "" // suppress re-fetch for the selected name
    }

    /**
     * Called when the user taps a station name in the "To" autocomplete dropdown.
     * Behaviour mirrors [onFromStationSelected].
     *
     * @param station The station the user selected.
     */
    fun onToStationSelected(station: Station) {
        _uiState.update { it.copy(toQuery = station.name ?: "", toSuggestions = emptyList()) }
        toQueryFlow.value = "" // suppress re-fetch for the selected name
    }

    /**
     * Fetches the next 5 departing connections between the current from and to stations.
     *
     * Returns early without setting [SearchUiState.isLoadingConnections] if either station
     * field is blank — there is nothing visible to indicate a guard-return, so triggering
     * the loading state would show a spinner with no subsequent result.
     *
     * On success:
     * - [SearchUiState.connections] is populated with the API response.
     * - The from/to pair is persisted to [android.content.SharedPreferences] for next launch.
     * - Both autocomplete suggestion lists are cleared (they would be confusing on top of results).
     * - [onSuccess] is invoked to trigger navigation to the results screen.
     *
     * On failure:
     * - [SearchUiState.connectionError] is set with the exception message.
     * - [SearchUiState.isLoadingConnections] is reset to false.
     * - The connection list remains empty; the error is displayed below the search button.
     *
     * @param onSuccess Invoked on the main thread after a successful API response.
     *   In [ch.lightsbb.MainActivity] this calls `navController.navigate("results")`.
     */
    fun searchConnections(onSuccess: () -> Unit) {
        val from = _uiState.value.fromQuery.trim()
        val to = _uiState.value.toQuery.trim()
        if (from.isBlank() || to.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingConnections = true,
                    connectionError = null,
                    connections = emptyList(),
                    fromSuggestions = emptyList(),
                    toSuggestions = emptyList()
                )
            }
            try {
                val result = api.getConnections(from, to)
                prefs.edit()
                    .putString("last_from", from)
                    .putString("last_to", to)
                    .apply()
                _uiState.update { it.copy(connections = result.connections, isLoadingConnections = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingConnections = false,
                        connectionError = e.message ?: "Connection failed"
                    )
                }
            }
        }
    }
}
