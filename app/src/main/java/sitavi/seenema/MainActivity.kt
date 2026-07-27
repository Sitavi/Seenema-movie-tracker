package sitavi.seenema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovieTrackerTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(vm, nav) }
        composable(
            "title/{type}/{id}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            )
        ) { entry ->
            TitleScreen(
                vm, nav,
                type = entry.arguments?.getString("type") ?: "movie",
                id = entry.arguments?.getString("id") ?: "",
            )
        }
        composable(
            "person/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            PersonScreen(vm, nav, id = entry.arguments?.getString("id") ?: "")
        }
        composable(
            "team/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            TeamScreen(nav, id = entry.arguments?.getString("id") ?: "")
        }
        composable("about") { AboutScreen(nav) }
    }
}
