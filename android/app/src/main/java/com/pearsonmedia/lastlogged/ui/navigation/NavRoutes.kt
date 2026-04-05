package com.pearsonmedia.lastlogged.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Onboarding : NavRoutes("onboarding")
    data object Home : NavRoutes("home")
    data object Settings : NavRoutes("settings")
    data object Auth : NavRoutes("auth")
    data object Paywall : NavRoutes("paywall")
    data object AddTracker : NavRoutes("add_tracker")
    data object EditTracker : NavRoutes("edit_tracker/{trackerId}") {
        fun createRoute(trackerId: String) = "edit_tracker/$trackerId"
    }
    data object TrackerDetail : NavRoutes("tracker_detail/{trackerId}") {
        fun createRoute(trackerId: String) = "tracker_detail/$trackerId"
    }
}
