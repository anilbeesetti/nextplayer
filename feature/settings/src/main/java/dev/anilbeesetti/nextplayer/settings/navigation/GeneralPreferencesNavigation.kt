package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.settings.screens.general.GeneralPreferencesScreen

const val generalPreferencesNavigationRoute = "general_preferences_route"

fun NavController.navigateToGeneralPreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(generalPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.generalPreferencesScreen(onNavigateUp: () -> Unit) {
    composable(route = generalPreferencesNavigationRoute) {
        GeneralPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
