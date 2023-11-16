package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.settings.screens.appearance.AppearancePreferencesScreen

const val appearancePreferencesNavigationRoute = "appearance_preferences_route"

fun NavController.navigateToAppearancePreferences(navOptions: NavOptions? = null) {
    this.navigate(appearancePreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.appearancePreferencesScreen(onNavigateUp: () -> Unit) {
    animatedComposable(route = appearancePreferencesNavigationRoute) {
        AppearancePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
