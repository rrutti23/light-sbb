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
import ch.lightsbb.viewmodel.SearchViewModel

@Composable
fun ResultsScreen(viewModel: SearchViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No connections found.", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = connection.duration?.let { formatDuration(it) } ?: "",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                val transfers = connection.transfers ?: 0
                Text(
                    text = if (transfers == 0) "Direct" else "$transfers×",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                connection.sections?.forEach { section ->
                    SectionItem(section)
                }
            }
        }
    }
}

@Composable
private fun TimeBlock(label: String, time: String, platform: String?) {
    Column {
        Text(text = label, color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(text = time, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (!platform.isNullOrBlank()) {
            Text(text = "Pl. $platform", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionItem(section: Section) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        if (section.walk != null) {
            Text(
                text = "Walk  ${section.walk.duration ?: "?"} min",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        } else {
            section.journey?.let { j ->
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

@Composable
private fun LegStop(name: String, time: String, platform: String?) {
    Column {
        Text(text = time, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(text = name, color = Color.Gray, fontSize = 13.sp)
        if (!platform.isNullOrBlank()) {
            Text(text = "Pl. $platform", color = Color(0xFF555555), fontSize = 11.sp)
        }
    }
}

// ISO 8601 time strings from the API: "2024-01-15T10:32:00+0100" → "10:32"
private fun formatTime(iso: String): String = try {
    iso.substringAfter("T").take(5)
} catch (_: Exception) {
    iso
}

// API duration format: "00d00:57:00" → "57m" or "1h 03m"
private fun formatDuration(raw: String): String = try {
    val time = raw.substringAfter("d")
    val (h, m) = time.split(":").let { it[0].toInt() to it[1].toInt() }
    if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
} catch (_: Exception) {
    raw
}
