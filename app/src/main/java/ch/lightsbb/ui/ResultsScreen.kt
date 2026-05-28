package ch.lightsbb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.lightsbb.api.Connection
import ch.lightsbb.api.Section
import ch.lightsbb.util.formatDuration
import ch.lightsbb.util.formatTime
import ch.lightsbb.viewmodel.SearchViewModel

/**
 * Displays the list of connections returned by the most recent search.
 *
 * The screen is read-only — the only interactions are tapping a connection row to expand it
 * and tapping the back button. The [SearchViewModel] state is not cleared on arrival, so
 * navigating back to [ch.lightsbb.ui.SearchScreen] and then returning here will still show
 * the same results (until the user performs a new search).
 *
 * ## Layout
 * A sticky header row shows the route (from → to) and a back button. Below it, connections
 * are rendered in a [LazyColumn] so the list can scroll when there are 5 results and the
 * device font size is large.
 *
 * ## Empty state
 * If the API returned zero connections (rare but possible for routes with no service at the
 * requested time), a centred "No connections found." message is shown instead of the list.
 *
 * @param viewModel Shared ViewModel providing [ch.lightsbb.viewmodel.SearchUiState.connections]
 *   and the current query strings used in the header.
 * @param onBack Called when the user taps the back button. In [ch.lightsbb.MainActivity] this
 *   calls `navController.popBackStack()`.
 */
@Composable
fun ResultsScreen(viewModel: SearchViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header row: back button on the left, route summary on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "← BACK",
                    color = Color.White,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${uiState.fromQuery}  →  ${uiState.toQuery}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(color = Color(0xFF2A2A2A))

        if (uiState.connections.isEmpty()) {
            // Centre the empty-state message vertically so it is easy to spot.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No connections found.", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Vertical padding keeps the first and last items from touching the divider
                // and the bottom navigation bar respectively.
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.connections) { connection ->
                    ConnectionItem(connection)
                    HorizontalDivider(color = Color(0xFF1E1E1E))
                }
            }
        }
    }
}

/**
 * A single tappable connection row that expands to show per-section details.
 *
 * **Collapsed state** (default): shows departure time, arrival time, total duration,
 * and transfer count in a compact two-column layout.
 *
 * **Expanded state**: additionally renders [SectionItem] for each leg of the journey
 * beneath the summary row, wrapped in [AnimatedVisibility] for a smooth slide-in.
 *
 * The [expanded] flag is stored in local composition memory via [remember] rather than
 * the ViewModel, because it is a pure UI concern that does not need to survive
 * configuration changes or screen transitions.
 *
 * @param connection The connection data to display.
 */
@Composable
private fun ConnectionItem(connection: Connection) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left side: departure and arrival time blocks side by side.
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                TimeBlock(
                    label = "DEP",
                    time = connection.from.departure?.let { formatTime(it) } ?: "–",
                    platform = connection.from.platform
                )
                TimeBlock(
                    label = "ARR",
                    time = connection.to.arrival?.let { formatTime(it) } ?: "–",
                    platform = connection.to.platform
                )
            }

            // Right side: duration and transfer count, right-aligned.
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = connection.duration?.let { formatDuration(it) } ?: "",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                val transfers = connection.transfers ?: 0
                // "Direct" is more informative than "0×" for the common case.
                Text(
                    text = if (transfers == 0) "Direct" else "$transfers×",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }

        // Section breakdown — only visible when the row is tapped.
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                connection.sections?.forEach { section ->
                    SectionItem(section)
                }
            }
        }
    }
}

/**
 * A compact departure/arrival time label with an optional platform number below it.
 *
 * Used in [ConnectionItem] to display the overall departure and arrival times side by side.
 * The large bold time text (26 sp) is the visual focal point of the row — it mirrors the
 * physical departure boards found in Swiss train stations.
 *
 * @param label Short all-caps descriptor shown above the time, e.g. `"DEP"` or `"ARR"`.
 * @param time Formatted time string, e.g. `"10:32"`. Pass `"–"` when unavailable.
 * @param platform Platform identifier to show below the time, e.g. `"7"`. Null or blank hides the line.
 */
@Composable
private fun TimeBlock(label: String, time: String, platform: String?) {
    Column {
        Text(text = label, color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.5.sp)
        // Large bold time mirrors Swiss physical departure boards.
        Text(text = time, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (!platform.isNullOrBlank()) {
            Text(text = "Pl. $platform", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

/**
 * One leg of the expanded connection breakdown.
 *
 * A section is either a **vehicle leg** ([ch.lightsbb.api.Section.journey] non-null) or
 * a **walking transfer** ([ch.lightsbb.api.Section.walk] non-null). The two cases render
 * differently:
 * - Vehicle leg: service line (e.g. `"IC 1  →  Brig"`) followed by departure and arrival stops.
 * - Walking leg: `"Walk  N min"` label followed by the two stops to walk between.
 *
 * For the stop time, departure time takes priority over arrival time within a [Stop] when both
 * are present (which can occur at intermediate interchange stops).
 *
 * @param section The individual leg to render.
 */
@Composable
private fun SectionItem(section: Section) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        if (section.walk != null) {
            // Walking transfer — show duration if provided, fall back to "?" if the API
            // did not include an estimate (occasionally happens for very short transfers).
            Text(
                text = "Walk  ${section.walk.duration ?: "?"} min",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        } else {
            section.journey?.let { j ->
                // Build "IC 1" from category + number, then append the vehicle terminus.
                val line = listOfNotNull(j.category, j.number).joinToString(" ")
                Text(
                    text = if (j.destination != null) "$line  →  ${j.destination}" else line,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            section.departure?.let { dep ->
                LegStop(
                    name = dep.station?.name ?: "–",
                    // Prefer departure time; fall back to arrival (interchange stops may
                    // only carry one of the two fields for this leg).
                    time = (dep.departure ?: dep.arrival)?.let { formatTime(it) } ?: "–",
                    platform = dep.platform
                )
            }
            section.arrival?.let { arr ->
                LegStop(
                    name = arr.station?.name ?: "–",
                    time = (arr.arrival ?: arr.departure)?.let { formatTime(it) } ?: "–",
                    platform = arr.platform
                )
            }
        }
    }
}

/**
 * A single stop within a section leg — time, station name, and optional platform.
 *
 * Used in [SectionItem] to show both the departure stop (left) and arrival stop (right)
 * of each leg in a compact stacked layout.
 *
 * @param name Station display name, e.g. `"Zürich HB"`.
 * @param time Formatted time string, e.g. `"10:32"`.
 * @param platform Platform identifier, or null/blank to omit the platform line.
 */
@Composable
private fun LegStop(name: String, time: String, platform: String?) {
    Column {
        Text(text = time, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(text = name, color = Color.Gray, fontSize = 13.sp)
        if (!platform.isNullOrBlank()) {
            // Platform text is dimmer than the station name — it is supplementary
            // information that most users do not need at a glance.
            Text(text = "Pl. $platform", color = Color(0xFF555555), fontSize = 11.sp)
        }
    }
}
