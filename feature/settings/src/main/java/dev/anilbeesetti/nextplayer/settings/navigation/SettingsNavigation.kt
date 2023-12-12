package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.settings.Setting
import dev.anilbeesetti.nextplayer.settings.SettingsScreen

const val SETTINGS_PREFERENCES_NAVIGATION_ROUTE = "settings_route"

fun NavController.navigateToSettings(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(SETTINGS_PREFERENCES_NAVIGATION_ROUTE, navOptions)
}

fun NavGraphBuilder.settingsScreen(onNavigateUp: () -> Unit, onItemClick: (Setting) -> Unit) {
    animatedComposable(route = SETTINGS_PREFERENCES_NAVIGATION_ROUTE) {
        SettingsScreen(onNavigateUp = onNavigateUp, onItemClick = onItemClick)
    }
}
