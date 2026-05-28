package ch.lightsbb.viewmodel

import android.app.Application
import android.content.SharedPreferences
import ch.lightsbb.api.Connection
import ch.lightsbb.api.ConnectionsResponse
import ch.lightsbb.api.LocationsResponse
import ch.lightsbb.api.Station
import ch.lightsbb.api.Stop
import ch.lightsbb.api.TransportApi
import ch.lightsbb.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [SearchViewModel] state management and coroutine behaviour.
 *
 * ## Test strategy
 * [SearchViewModel] is tested directly (not via a [androidx.lifecycle.ViewModelProvider])
 * by constructing it with mock dependencies injected through its `api` constructor parameter.
 * This avoids real network calls and makes assertions on [SearchViewModel.uiState] synchronous
 * and deterministic.
 *
 * ## Coroutine control
 * [SearchViewModel] launches coroutines on [androidx.lifecycle.ViewModel.viewModelScope], which
 * is bound to [kotlinx.coroutines.Dispatchers.Main]. [MainDispatcherRule] replaces Main with a
 * [kotlinx.coroutines.test.StandardTestDispatcher] before each test. Each `runTest` call passes
 * the **same** dispatcher so that both the ViewModel's coroutines and the test's time-control
 * functions ([advanceTimeBy], [advanceUntilIdle]) share a single scheduler — without this, time
 * advancement inside `runTest` would not advance the ViewModel's debounce timers.
 *
 * ## Mock setup
 * - [mockApi] is a MockK mock of [TransportApi]; each test stubs only the calls it exercises.
 * - [mockPrefs] and [mockEditor] simulate [android.content.SharedPreferences] without touching
 *   the file system. `mockEditor` uses `relaxed = true` so unstubbed write calls are no-ops.
 * - The `@Before` method stubs [mockApi.getLocations] to return an empty list by default,
 *   preventing autocomplete network calls from interfering with connection-search tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    /**
     * Installs a [kotlinx.coroutines.test.StandardTestDispatcher] as [kotlinx.coroutines.Dispatchers.Main]
     * before each test and restores the original dispatcher afterward.
     * See [MainDispatcherRule] for why this is necessary.
     */
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mockApi: TransportApi = mockk()
    private val mockApplication: Application = mockk()
    private val mockPrefs: SharedPreferences = mockk()
    private val mockEditor: SharedPreferences.Editor = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { mockApplication.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getString("last_from", "") } returns ""
        every { mockPrefs.getString("last_to", "") } returns ""
        every { mockPrefs.edit() } returns mockEditor
        // Stub getLocations to return empty by default so autocomplete coroutines don't
        // throw UnstubbedCallException and don't pollute unrelated state assertions.
        coEvery { mockApi.getLocations(any(), any()) } returns LocationsResponse(emptyList())
    }

    /** Constructs a [SearchViewModel] with the shared mocks. */
    private fun buildVm() = SearchViewModel(mockApplication, mockApi)

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `loads last saved queries from SharedPreferences on init`() {
        // Override the default empty stubs to simulate a previously searched route.
        every { mockPrefs.getString("last_from", "") } returns "Zürich HB"
        every { mockPrefs.getString("last_to", "") } returns "Bern"

        val vm = buildVm()

        assertEquals("Zürich HB", vm.uiState.value.fromQuery)
        assertEquals("Bern", vm.uiState.value.toQuery)
    }

    @Test
    fun `initial state has empty suggestions and no connections`() {
        val vm = buildVm()
        assertTrue(vm.uiState.value.fromSuggestions.isEmpty())
        assertTrue(vm.uiState.value.toSuggestions.isEmpty())
        assertTrue(vm.uiState.value.connections.isEmpty())
        assertFalse(vm.uiState.value.isLoadingConnections)
        assertNull(vm.uiState.value.connectionError)
    }

    // -------------------------------------------------------------------------
    // Query field changes
    // -------------------------------------------------------------------------

    @Test
    fun `onFromQueryChange updates fromQuery in state`() {
        val vm = buildVm()
        vm.onFromQueryChange("Zür")
        assertEquals("Zür", vm.uiState.value.fromQuery)
    }

    @Test
    fun `onFromQueryChange clears suggestions when query drops below 2 chars`() {
        // A query shorter than 2 characters is below the autocomplete minimum —
        // any stale suggestions from a previous longer query must be dismissed.
        val vm = buildVm()
        vm.onFromQueryChange("Z")
        assertTrue(vm.uiState.value.fromSuggestions.isEmpty())
    }

    @Test
    fun `onToQueryChange updates toQuery in state`() {
        val vm = buildVm()
        vm.onToQueryChange("Bern")
        assertEquals("Bern", vm.uiState.value.toQuery)
    }

    // -------------------------------------------------------------------------
    // Station selection from autocomplete dropdown
    // -------------------------------------------------------------------------

    @Test
    fun `selecting a from-station sets query to station name and clears suggestions`() {
        val vm = buildVm()
        val station = Station("008503000", "Zürich HB", null)
        vm.onFromStationSelected(station)

        assertEquals("Zürich HB", vm.uiState.value.fromQuery)
        assertTrue(vm.uiState.value.fromSuggestions.isEmpty())
    }

    @Test
    fun `selecting a to-station sets query to station name and clears suggestions`() {
        val vm = buildVm()
        val station = Station("008507000", "Bern", null)
        vm.onToStationSelected(station)

        assertEquals("Bern", vm.uiState.value.toQuery)
        assertTrue(vm.uiState.value.toSuggestions.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Autocomplete pipeline (debounce + flatMapLatest)
    // -------------------------------------------------------------------------

    @Test
    fun `autocomplete fetches after 300ms debounce for 2+ char query`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val results = listOf(Station("1", "Zürich HB", null), Station("2", "Zürich Oerlikon", null))
            coEvery { mockApi.getLocations("Zü", any()) } returns LocationsResponse(results)

            val vm = buildVm()
            vm.onFromQueryChange("Zü")
            // Advance 400 ms — past the 300 ms debounce window — to allow the API call to fire.
            advanceTimeBy(400)

            assertEquals(results, vm.uiState.value.fromSuggestions)
        }

    @Test
    fun `rapid typing only triggers one fetch for the final query`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // flatMapLatest should cancel the previous inner flow the moment a new query arrives,
            // so only the last stable value (after the debounce) produces a network call.
            coEvery { mockApi.getLocations(any(), any()) } returns LocationsResponse(emptyList())

            val vm = buildVm()
            vm.onFromQueryChange("Z")   // below threshold — no fetch
            vm.onFromQueryChange("Zü")  // 2+ chars — debounce starts
            vm.onFromQueryChange("Zür") // resets debounce — only this should reach the API
            advanceTimeBy(400)

            // Exactly one API call for the final stabilised query.
            coVerify(exactly = 1) { mockApi.getLocations(any(), any()) }
        }

    @Test
    fun `single character query does not trigger a fetch at all`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // The filter { it.length >= 2 } operator in the pipeline must prevent this call.
            val vm = buildVm()
            vm.onFromQueryChange("Z")
            advanceUntilIdle()

            coVerify(exactly = 0) { mockApi.getLocations(any(), any()) }
        }

    // -------------------------------------------------------------------------
    // Connection search
    // -------------------------------------------------------------------------

    @Test
    fun `searchConnections does nothing when from is blank`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Guard: should not set isLoadingConnections or call the API.
            val vm = buildVm()
            vm.onToQueryChange("Bern")
            var called = false
            vm.searchConnections { called = true }
            advanceUntilIdle()

            assertFalse(called)
            assertFalse(vm.uiState.value.isLoadingConnections)
            assertTrue(vm.uiState.value.connections.isEmpty())
        }

    @Test
    fun `searchConnections does nothing when to is blank`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = buildVm()
            vm.onFromQueryChange("Zürich HB")
            var called = false
            vm.searchConnections { called = true }
            advanceUntilIdle()

            assertFalse(called)
            assertTrue(vm.uiState.value.connections.isEmpty())
        }

    @Test
    fun `searchConnections on success populates connections and invokes callback`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val connections = listOf(fakeConnection("Zürich HB", "Bern"))
            coEvery { mockApi.getConnections("Zürich HB", "Bern", 5) } returns
                ConnectionsResponse(connections)

            val vm = buildVm()
            vm.onFromQueryChange("Zürich HB")
            vm.onToQueryChange("Bern")

            var navigated = false
            vm.searchConnections { navigated = true }
            advanceUntilIdle()

            assertTrue(navigated)
            assertEquals(1, vm.uiState.value.connections.size)
            assertFalse(vm.uiState.value.isLoadingConnections)
            assertNull(vm.uiState.value.connectionError)
        }

    @Test
    fun `searchConnections on network failure sets error and clears loading flag`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { mockApi.getConnections(any(), any(), any()) } throws
                RuntimeException("Network unavailable")

            val vm = buildVm()
            vm.onFromQueryChange("Zürich HB")
            vm.onToQueryChange("Bern")
            vm.searchConnections {}
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoadingConnections)
            assertEquals("Network unavailable", vm.uiState.value.connectionError)
            assertTrue(vm.uiState.value.connections.isEmpty())
        }

    @Test
    fun `searchConnections saves from and to to SharedPreferences on success`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { mockApi.getConnections(any(), any(), any()) } returns
                ConnectionsResponse(emptyList())

            val vm = buildVm()
            vm.onFromQueryChange("Zürich HB")
            vm.onToQueryChange("Bern")
            vm.searchConnections {}
            advanceUntilIdle()

            // The successful pair must be persisted so the next app launch can pre-fill the fields.
            verify { mockEditor.putString("last_from", "Zürich HB") }
            verify { mockEditor.putString("last_to", "Bern") }
            verify { mockEditor.apply() }
        }

    @Test
    fun `searchConnections clears suggestions before navigating`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Simulate a scenario where autocomplete has populated suggestions and the user
            // then taps Search — the dropdowns must be gone when the results screen appears.
            val stations = listOf(Station("1", "Zürich HB", null))
            coEvery { mockApi.getLocations("Zürich HB", any()) } returns LocationsResponse(stations)
            coEvery { mockApi.getConnections(any(), any(), any()) } returns
                ConnectionsResponse(emptyList())

            val vm = buildVm()
            vm.onFromQueryChange("Zürich HB")
            advanceTimeBy(400) // let the autocomplete pipeline populate fromSuggestions
            vm.onToQueryChange("Bern")
            vm.searchConnections {}
            advanceUntilIdle()

            assertTrue(vm.uiState.value.fromSuggestions.isEmpty())
            assertTrue(vm.uiState.value.toSuggestions.isEmpty())
        }

    // -------------------------------------------------------------------------
    // Test data helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal [Connection] object suitable for stubbing [TransportApi.getConnections].
     * Fields not relevant to the test under way are set to plausible fixed values.
     */
    private fun fakeConnection(fromName: String, toName: String) = Connection(
        from = Stop(
            station = Station("1", fromName, null),
            arrival = null,
            departure = "2024-01-15T10:00:00+0100",
            delay = 0,
            platform = "7"
        ),
        to = Stop(
            station = Station("2", toName, null),
            arrival = "2024-01-15T10:57:00+0100",
            departure = null,
            delay = null,
            platform = "8"
        ),
        duration = "00d00:57:00",
        transfers = 0,
        products = listOf("IC"),
        sections = emptyList()
    )
}
