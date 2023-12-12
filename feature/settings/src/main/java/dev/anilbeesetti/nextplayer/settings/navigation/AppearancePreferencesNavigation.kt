package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.settings.screens.appearance.AppearancePreferencesScreen

const val APPEARANCE_PREFERENCES_NAVIGATION_ROUTE = "appearance_preferences_route"

fun NavController.navigateToAppearancePreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(APPEARANCE_PREFERENCES_NAVIGATION_ROUTE, navOptions)
}

fun NavGraphBuilder.appearancePreferencesScreen(onNavigateUp: () -> Unit) {
    animatedComposable(route = APPEARANCE_PREFERENCES_NAVIGATION_ROUTE) {
        AppearancePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
