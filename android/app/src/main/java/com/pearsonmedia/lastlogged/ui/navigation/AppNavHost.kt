package com.pearsonmedia.lastlogged.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pearsonmedia.lastlogged.ui.auth.AuthScreen
import com.pearsonmedia.lastlogged.ui.components.PaywallScreen
import com.pearsonmedia.lastlogged.ui.detail.TrackerDetailScreen
import com.pearsonmedia.lastlogged.ui.home.AddTrackerScreen
import com.pearsonmedia.lastlogged.ui.home.HomeScreen
import com.pearsonmedia.lastlogged.ui.onboarding.OnboardingScreen
import com.pearsonmedia.lastlogged.ui.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = NavRoutes.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(NavRoutes.Paywall.route) {
                        popUpTo(NavRoutes.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Paywall.route) {
            PaywallScreen(
                isHardPaywall = false,
                onDismiss = {
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.Paywall.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Home.route) {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onNavigateToAddTracker = { navController.navigate(NavRoutes.AddTracker.route) },
                onNavigateToDetail = { trackerId ->
                    navController.navigate(NavRoutes.TrackerDetail.createRoute(trackerId))
                },
                onNavigateToEditTracker = { trackerId ->
                    navController.navigate(NavRoutes.EditTracker.createRoute(trackerId))
                }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAuth = { navController.navigate(NavRoutes.Auth.route) }
            )
        }

        composable(NavRoutes.Auth.route) {
            AuthScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.AddTracker.route) {
            AddTrackerScreen(
                onNavigateBack = { navController.popBackStack() },
                editingTrackerId = null
            )
        }

        composable(
            route = NavRoutes.EditTracker.route,
            arguments = listOf(navArgument("trackerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val trackerId = backStackEntry.arguments?.getString("trackerId") ?: return@composable
            AddTrackerScreen(
                onNavigateBack = { navController.popBackStack() },
                editingTrackerId = trackerId
            )
        }

        composable(
            route = NavRoutes.TrackerDetail.route,
            arguments = listOf(navArgument("trackerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val trackerId = backStackEntry.arguments?.getString("trackerId") ?: return@composable
            TrackerDetailScreen(
                trackerId = trackerId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = {
                    navController.navigate(NavRoutes.EditTracker.createRoute(trackerId))
                }
            )
        }
    }
}
