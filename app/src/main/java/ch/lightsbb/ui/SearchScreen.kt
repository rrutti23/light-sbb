package ch.lightsbb.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.lightsbb.api.Station
import ch.lightsbb.viewmodel.SearchViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val Gray = Color(0xFF888888)
private val DimGray = Color(0xFF444444)
private val SubtleGray = Color(0xFF222222)
private val SurfaceGray = Color(0xFF0D0D0D)

@Composable
fun SearchScreen(viewModel: SearchViewModel, onNavigateToResults: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var isDeparture by remember { mutableStateOf(true) }

    val dateLabel = date.format(DateTimeFormatter.ofPattern("d MMM yyyy")).uppercase()
    val timeLabel = time.format(DateTimeFormatter.ofPattern("HH:mm"))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(44.dp))

            // Title — underlined like the Light Phone GPS Mode header
            Text(
                text = "SBB LIGHT",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                modifier = Modifier.drawBottomLine(Color.White, 6.dp)
            )

            Spacer(modifier = Modifier.height(52.dp))

            // From field with swap button
            StationField(
                label = "From:",
                query = uiState.fromQuery,
                suggestions = uiState.fromSuggestions,
                onQueryChange = viewModel::onFromQueryChange,
                onStationSelected = viewModel::onFromStationSelected,
                imeAction = ImeAction.Next,
                trailing = {
                    val from = uiState.fromQuery
                    val to = uiState.toQuery
                    Text(
                        text = "⇅",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = SpaceGrotesk,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.onFromQueryChange(to)
                                viewModel.onToQueryChange(from)
                            }
                    )
                }
            )

            Spacer(modifier = Modifier.height(36.dp))

            // To field
            StationField(
                label = "To:",
                query = uiState.toQuery,
                suggestions = uiState.toSuggestions,
                onQueryChange = viewModel::onToQueryChange,
                onStationSelected = viewModel::onToStationSelected,
                imeAction = ImeAction.Search,
                onSearch = { viewModel.searchConnections(onNavigateToResults) }
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Thin section divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(DimGray)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Date row
            InfoRow(
                label = "Date",
                value = dateLabel,
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            date = LocalDate.of(year, month + 1, day)
                        },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth
                    ).show()
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Time row
            InfoRow(
                label = "Time",
                value = timeLabel,
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            time = LocalTime.of(hour, minute)
                        },
                        time.hour,
                        time.minute,
                        true
                    ).show()
                }
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Departure / Arrival slider toggle
            DepartureArrivalToggle(
                isDeparture = isDeparture,
                onToggle = { isDeparture = it }
            )
        }

        // Error — bottom left
        uiState.connectionError?.let { error ->
            Text(
                text = error,
                color = Gray,
                fontSize = 12.sp,
                fontFamily = SpaceGrotesk,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 40.dp)
            )
        }

        // Search action — bottom right, underlined like the Light Phone START label
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 40.dp)
        ) {
            if (uiState.isLoadingConnections) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 1.5.dp
                )
            } else {
                Text(
                    text = "SEARCH",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 4.sp,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.searchConnections(onNavigateToResults) }
                        .drawBottomLine(Color.White, 5.dp)
                )
            }
        }
    }
}

// —— Small helpers ——

private fun Modifier.drawBottomLine(color: Color, offsetY: Dp = 4.dp): Modifier =
    drawBehind {
        val y = size.height + offsetY.toPx()
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }

@Composable
private fun InfoRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = label,
            color = Gray,
            fontSize = 11.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp,
            modifier = Modifier.width(56.dp)
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 22.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Light
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(DimGray)
    )
}

@Composable
private fun DepartureArrivalToggle(isDeparture: Boolean, onToggle: (Boolean) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val halfWidth = maxWidth / 2
        val indicatorOffset by animateDpAsState(
            targetValue = if (isDeparture) 0.dp else halfWidth,
            animationSpec = tween(durationMillis = 220),
            label = "dep_arr"
        )

        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "DEPARTURE",
                    color = if (isDeparture) Color.White else DimGray,
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onToggle(true) }
                )
                Text(
                    text = "ARRIVAL",
                    color = if (!isDeparture) Color.White else DimGray,
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onToggle(false) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DimGray)
            ) {
                // Active indicator slides between left and right half
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(halfWidth)
                        .height(1.dp)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun StationField(
    label: String,
    query: String,
    suggestions: List<Station>,
    onQueryChange: (String) -> Unit,
    onStationSelected: (Station) -> Unit,
    imeAction: ImeAction = ImeAction.Default,
    onSearch: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column {
        Text(
            text = label,
            color = Gray,
            fontSize = 11.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 30.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Light
                ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch?.invoke() },
                    onNext = {}
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "—",
                                color = DimGray,
                                fontSize = 30.sp,
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Light
                            )
                        }
                        innerTextField()
                    }
                }
            )
            trailing?.invoke()
        }

        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(DimGray)
        )

        // Autocomplete suggestions
        if (suggestions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceGray)
            ) {
                suggestions.forEach { station ->
                    Text(
                        text = station.name ?: "",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStationSelected(station) }
                            .padding(vertical = 14.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(SubtleGray)
                    )
                }
            }
        }
    }
}
