package ch.lightsbb.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.lightsbb.api.Connection
import ch.lightsbb.api.Station
import ch.lightsbb.api.TransportApiClient
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

data class SearchUiState(
    val fromQuery: String = "",
    val toQuery: String = "",
    val fromSuggestions: List<Station> = emptyList(),
    val toSuggestions: List<Station> = emptyList(),
    val connections: List<Connection> = emptyList(),
    val isLoadingConnections: Boolean = false,
    val connectionError: String? = null
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    application: Application,
    private val api: TransportApi = TransportApiClient.api
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("sbb_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        SearchUiState(
            fromQuery = prefs.getString("last_from", "") ?: "",
            toQuery = prefs.getString("last_to", "") ?: ""
        )
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Dedicated flows for debounced autocomplete — decoupled from the display query
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

    private fun observeAutocomplete(
        queryFlow: MutableStateFlow<String>,
        onResult: (List<Station>) -> Unit
    ) {
        queryFlow
            .debounce(300)
            .filter { it.length >= 2 }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                flow {
                    try {
                        emit(api.getLocations(query).stations.take(5))
                    } catch (e: Exception) {
                        emit(emptyList())
                    }
                }
            }
            .onEach(onResult)
            .launchIn(viewModelScope)
    }

    fun onFromQueryChange(query: String) {
        _uiState.update {
            it.copy(
                fromQuery = query,
                fromSuggestions = if (query.length < 2) emptyList() else it.fromSuggestions
            )
        }
        fromQueryFlow.value = query
    }

    fun onToQueryChange(query: String) {
        _uiState.update {
            it.copy(
                toQuery = query,
                toSuggestions = if (query.length < 2) emptyList() else it.toSuggestions
            )
        }
        toQueryFlow.value = query
    }

    fun onFromStationSelected(station: Station) {
        _uiState.update { it.copy(fromQuery = station.name ?: "", fromSuggestions = emptyList()) }
        fromQueryFlow.value = ""
    }

    fun onToStationSelected(station: Station) {
        _uiState.update { it.copy(toQuery = station.name ?: "", toSuggestions = emptyList()) }
        toQueryFlow.value = ""
    }

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
