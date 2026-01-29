package com.example.eventshoppingplanner.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.eventshoppingplanner.presentation.screens.eventlist.EventListScreen
import com.example.eventshoppingplanner.presentation.screens.map.MapScreen
import com.example.eventshoppingplanner.presentation.screens.settings.SettingsScreen
import com.example.eventshoppingplanner.presentation.screens.shoppinglist.ShoppingListScreen

sealed class Screen(val route: String) {
    data object EventList : Screen("event_list")
    data object ShoppingList : Screen("shopping_list/{eventId}") {
        fun createRoute(eventId: String) = "shopping_list/$eventId"
    }
    data object Map : Screen("map/{eventId}") {
        fun createRoute(eventId: String) = "map/$eventId"
    }
    data object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.EventList.route
    ) {
        composable(Screen.EventList.route) {
            EventListScreen(
                onNavigateToShoppingList = { eventId ->
                    navController.navigate(Screen.ShoppingList.createRoute(eventId))
                },
                onNavigateToImport = {
                    // インポート画面への遷移（現在は未実装なので何もしない）
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.ShoppingList.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            ShoppingListScreen(
                eventId = eventId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMap = {
                    navController.navigate(Screen.Map.createRoute(eventId))
                }
            )
        }

        composable(
            route = Screen.Map.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            MapScreen(
                eventId = eventId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}