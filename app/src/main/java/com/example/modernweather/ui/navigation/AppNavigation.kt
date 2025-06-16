package com.example.modernweather.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.modernweather.ui.screens.LocationsScreen
import com.example.modernweather.ui.screens.SettingsScreen
import com.example.modernweather.ui.screens.WeatherDetailScreen
import com.example.modernweather.ui.viewmodel.WeatherViewModel

sealed class Screen(val route: String) {
    data object Locations : Screen("locations")
    data object WeatherDetail : Screen("weatherDetail/{locationId}") {
        fun createRoute(locationId: String) = "weatherDetail/$locationId"
    }
    data object Settings : Screen("settings")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val weatherViewModel: WeatherViewModel = viewModel(
        factory = WeatherViewModel.Factory
    )

    NavHost(navController = navController, startDestination = Screen.Locations.route) {
        composable(Screen.Locations.route) {
            LocationsScreen(
                viewModel = weatherViewModel,
                onLocationClick = { locationId ->
                    navController.navigate(Screen.WeatherDetail.createRoute(locationId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(
            route = Screen.WeatherDetail.route,
            arguments = listOf(navArgument("locationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getString("locationId")
            requireNotNull(locationId) { "Location ID is required" }

            WeatherDetailScreen(
                locationId = locationId,
                viewModel = weatherViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = weatherViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

