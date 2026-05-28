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

/**
 * The sole Activity in the app. All UI is implemented with Jetpack Compose.
 *
 * ## Responsibilities
 *
 * **Theme** — applies a black-and-white [MaterialTheme] so that child composables
 * can use Material3 tokens (shapes, typography, component defaults) without receiving
 * any unwanted colour tints. Only the subset of [darkColorScheme] tokens that are
 * actually used by Material3 components in this app are overridden; the rest remain
 * at their default dark-scheme values and are not visible in the UI.
 *
 * **Inset handling** — [safeDrawingPadding] is applied once at the [NavHost] level so
 * that both screens automatically respect the status bar and gesture navigation bar
 * on the Light Phone III without each screen having to handle window insets individually.
 * This is preferred over the legacy `WindowCompat.setDecorFitsSystemWindows(window, false)`
 * + per-screen padding approach.
 *
 * **Navigation** — a two-destination [NavHost] with string route keys:
 * - `"search"` → [SearchScreen] (start destination)
 * - `"results"` → [ResultsScreen]
 *
 * The back stack is managed by [androidx.navigation.NavController]; pressing the system
 * back button from the results screen automatically returns to the search screen.
 *
 * **ViewModel sharing** — a single [SearchViewModel] instance is created with the
 * Activity scope (via `viewModel()` at the NavHost level, not inside individual
 * `composable { }` blocks). Both screens receive the same instance, which means search
 * results are instantly available on the results screen without serialising them through
 * the navigation arguments.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Minimal dark colour scheme — black background, white content, no accents.
            // Only the tokens actually referenced by Material3 components in this app are
            // set; unset tokens default to the Material3 dark-scheme values but are not
            // rendered anywhere in the current UI.
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White,
                    primary = Color.White,   // used by OutlinedTextField focused border
                    onPrimary = Color.Black  // used by filled Button text
                )
            ) {
                val navController = rememberNavController()

                // viewModel() here (above the NavHost) gives both destinations the same
                // Activity-scoped instance. If it were called inside each composable { }
                // block, each screen would get its own isolated ViewModel with no shared state.
                val viewModel: SearchViewModel = viewModel()

                NavHost(
                    navController = navController,
                    startDestination = "search",
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        // safeDrawingPadding accounts for status bar, notch, and gesture
                        // navigation bar. Applied once here so neither screen needs to
                        // repeat it.
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
