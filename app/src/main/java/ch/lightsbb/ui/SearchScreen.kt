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
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(20.dp))

            StationField(
                label = "TO",
                query = uiState.toQuery,
                suggestions = uiState.toSuggestions,
                onQueryChange = viewModel::onToQueryChange,
                onStationSelected = viewModel::onToStationSelected,
                imeAction = ImeAction.Search,
                onSearch = { viewModel.searchConnections(onNavigateToResults) }
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (uiState.isLoadingConnections) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Button(
                    onClick = { viewModel.searchConnections(onNavigateToResults) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
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

            uiState.connectionError?.let { error ->
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = error, color = Color.Gray, fontSize = 14.sp)
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
    onSearch: (() -> Unit)? = null
) {
    Column {
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
                onNext = {}
            )
        )
        if (suggestions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                }
            }
        }
    }
}
