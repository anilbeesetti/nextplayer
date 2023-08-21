package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NavigationAnimations
import dev.anilbeesetti.nextplayer.settings.screens.appearance.AppearancePreferencesScreen

const val appearancePreferencesNavigationRoute = "appearance_preferences_route"

fun NavController.navigateToAppearancePreferences(navOptions: NavOptions? = null) {
    this.navigate(appearancePreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.appearancePreferencesScreen(onNavigateUp: () -> Unit) {
    composable(
        route = appearancePreferencesNavigationRoute,
        enterTransition = { NavigationAnimations.slideEnter },
        popExitTransition = { NavigationAnimations.slideExit },
        popEnterTransition = null
    ) {
        AppearancePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
