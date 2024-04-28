package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.settings.screens.network.NetworkPreferencesScreen

const val networkPreferencesNavigationRoute = "network_preferences_route"

fun NavController.navigateToNetworkPreferences(navOptions: NavOptions? = androidx.navigation.navOptions { launchSingleTop = true }) {
    this.navigate(networkPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.networkPreferencesScreen(onNavigateUp: () -> Unit) {
    animatedComposable(route = networkPreferencesNavigationRoute) {
        NetworkPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
