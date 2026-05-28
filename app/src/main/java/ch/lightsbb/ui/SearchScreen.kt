package ch.lightsbb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.lightsbb.api.Station
import ch.lightsbb.viewmodel.SearchViewModel

/**
 * Root composable for the search screen — the app's entry point.
 *
 * Displays two [StationField]s (From / To) with live autocomplete dropdowns, a search
 * button, and an optional error message. Both fields are pre-populated from the last
 * successful search via [SearchViewModel], so the user's most common route is ready on launch.
 *
 * ## Design constraints (Light Phone III)
 * - Pure black background (`0xFF000000`) with white text — no colour accents whatsoever.
 * - Generous vertical padding (32 dp top, 48 dp below title) to give the sparse layout
 *   room to breathe on the device's tall narrow display.
 * - The search button is 56 dp tall — the Material3 recommended minimum touch target.
 * - While a search is loading, the button is replaced by a [CircularProgressIndicator] so
 *   the user cannot trigger a second concurrent request.
 *
 * @param viewModel Shared ViewModel providing UI state and event callbacks.
 * @param onNavigateToResults Called after [SearchViewModel.searchConnections] succeeds.
 *   In [ch.lightsbb.MainActivity] this triggers `navController.navigate("results")`.
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel, onNavigateToResults: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // App title — doubles as a visual anchor at the top of the otherwise minimal screen.
            Text(
                text = "SBB Light",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            StationField(
                label = "FROM",
                query = uiState.fromQuery,
                suggestions = uiState.fromSuggestions,
                onQueryChange = viewModel::onFromQueryChange,
                onStationSelected = viewModel::onFromStationSelected,
                // ImeAction.Next moves focus to the "To" field without submitting the form.
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(20.dp))

            StationField(
                label = "TO",
                query = uiState.toQuery,
                suggestions = uiState.toSuggestions,
                onQueryChange = viewModel::onToQueryChange,
                onStationSelected = viewModel::onToStationSelected,
                // ImeAction.Search on the second field lets the user trigger a search
                // directly from the keyboard without reaching for the button.
                imeAction = ImeAction.Search,
                onSearch = { viewModel.searchConnections(onNavigateToResults) }
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (uiState.isLoadingConnections) {
                // Replace the button with a spinner during a pending request so the user
                // cannot fire duplicate searches. The spinner inherits the white colour
                // scheme to stay consistent with the monochrome design.
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                // Inverted colours (white container, black label) to make the primary action
                // visually prominent without introducing any colour accents.
                Button(
                    onClick = { viewModel.searchConnections(onNavigateToResults) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    // extraSmall corners give a squared-off, utilitarian appearance that fits
                    // the Light Phone's typographic design language.
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = "SEARCH",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                }
            }

            // Error surface — rendered inline below the button so the user sees it without
            // any modal interruption. Cleared on the next successful search.
            uiState.connectionError?.let { error ->
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = error, color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

/**
 * A labelled text input with a live autocomplete suggestion list rendered directly beneath it.
 *
 * The suggestion list is a plain [Column] rather than a floating [androidx.compose.material3.DropdownMenu].
 * This is intentional: a floating menu would overlap the "To" field on the narrow Light Phone
 * screen, whereas an inline column pushes the content downward and keeps the layout predictable.
 * The maximum of 5 suggestions (capped in [ch.lightsbb.viewmodel.SearchViewModel]) limits
 * how far the layout is displaced.
 *
 * The text field colours override all Material3 defaults to enforce the black/white scheme:
 * - Container: `0xFF000000` (black) in both focused and unfocused states.
 * - Border: white when focused, `0xFF555555` (dark grey) when unfocused.
 * - Text and cursor: white in all states.
 *
 * @param label All-caps field label shown above the text field (e.g. `"FROM"`, `"TO"`).
 * @param query Current text value of the field.
 * @param suggestions Autocomplete [Station] list to display. Empty list hides the dropdown.
 * @param onQueryChange Invoked on every character change, receives the full new value.
 * @param onStationSelected Invoked when the user taps a suggestion row.
 * @param imeAction Keyboard action button shown for this field ([ImeAction.Next] or [ImeAction.Search]).
 * @param onSearch Optional callback for when the keyboard's Search action is triggered.
 *   Only meaningful when [imeAction] is [ImeAction.Search].
 */
@Composable
private fun StationField(
    label: String,
    query: String,
    suggestions: List<Station>,
    onQueryChange: (String) -> Unit,
    onStationSelected: (Station) -> Unit,
    imeAction: ImeAction = ImeAction.Default,
    onSearch: (() -> Unit)? = null
) {
    Column {
        // Micro-label above the field — small, spaced caps give a clean timetable-board feel.
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            // Font size 20 sp — larger than the Material3 default to create a clear
            // visual hierarchy and improve readability on a high-DPI small screen.
            textStyle = TextStyle(fontSize = 20.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color(0xFF555555),
                focusedContainerColor = Color.Black,
                unfocusedContainerColor = Color.Black
            ),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onSearch = { onSearch?.invoke() },
                onNext = { /* focus moves automatically via the IME */ }
            )
        )

        // Autocomplete dropdown — only rendered when there are results to show.
        if (suggestions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Slightly lighter than pure black so the dropdown is visually distinct
                    // from the page background without introducing any colour.
                    .background(Color(0xFF111111))
            ) {
                suggestions.forEach { station ->
                    Text(
                        text = station.name ?: "",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStationSelected(station) }
                            // 16 dp vertical padding gives each row a 48+ dp touch target.
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                }
            }
        }
    }
}
