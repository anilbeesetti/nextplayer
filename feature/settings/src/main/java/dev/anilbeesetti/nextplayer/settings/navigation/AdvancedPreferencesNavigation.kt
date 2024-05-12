package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.settings.screens.advanced.AdvancedPreferencesScreen

const val advancedPreferencesNavigationRoute = "advanced_preferences_route"

fun NavController.navigateToAdvancedPreferences(navOptions: NavOptions? = androidx.navigation.navOptions { launchSingleTop = true }) {
    this.navigate(advancedPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.advancedPreferencesScreen(onNavigateUp: () -> Unit) {
    animatedComposable(route = advancedPreferencesNavigationRoute) {
        AdvancedPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
