package ch.lightsbb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ch.lightsbb.ui.ResultsScreen
import ch.lightsbb.ui.SearchScreen
import ch.lightsbb.viewmodel.SearchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White,
                    primary = Color.White,
                    onPrimary = Color.Black
                )
            ) {
                val navController = rememberNavController()
                val viewModel: SearchViewModel = viewModel()

                NavHost(
                    navController = navController,
                    startDestination = "search",
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .safeDrawingPadding()
                ) {
                    composable("search") {
                        SearchScreen(
                            viewModel = viewModel,
                            onNavigateToResults = { navController.navigate("results") }
                        )
                    }
                    composable("results") {
                        ResultsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
