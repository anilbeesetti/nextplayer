package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.anilbeesetti.nextplayer.settings.screens.about.AboutPreferencesScreen

const val aboutPreferencesNavigationRoute = "about_preferences_route"

fun NavController.navigateToAboutPreferences(navOptions: NavOptions? = null) {
    this.navigate(aboutPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.aboutPreferencesScreen(onNavigateUp: () -> Unit) {
    composable(route = aboutPreferencesNavigationRoute) {
        AboutPreferencesScreen(
            onNavigateUp = onNavigateUp
        )
    }
}
