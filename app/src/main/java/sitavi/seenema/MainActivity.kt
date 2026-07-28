/*
 * Seenema: track and rate the films and series you have watched.
 * Copyright (C) 2026 Sitavi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

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
