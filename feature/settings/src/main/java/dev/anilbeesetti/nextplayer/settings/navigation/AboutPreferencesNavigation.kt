package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.settings.screens.about.AboutPreferencesScreen

const val ABOUT_PREFERENCES_NAVIGATION_ROUTE = "about_preferences_route"

fun NavController.navigateToAboutPreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(ABOUT_PREFERENCES_NAVIGATION_ROUTE, navOptions)
}

fun NavGraphBuilder.aboutPreferencesScreen(onNavigateUp: () -> Unit) {
    animatedComposable(route = ABOUT_PREFERENCES_NAVIGATION_ROUTE) {
        AboutPreferencesScreen(
            onNavigateUp = onNavigateUp
        )
    }
}
