package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.settings.screens.player.PlayerPreferencesScreen

const val PLAYER_PREFERENCES_NAVIGATION_ROUTE = "player_preferences_route"

fun NavController.navigateToPlayerPreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(PLAYER_PREFERENCES_NAVIGATION_ROUTE, navOptions)
}

fun NavGraphBuilder.playerPreferencesScreen(onNavigateUp: () -> Unit) {
    animatedComposable(route = PLAYER_PREFERENCES_NAVIGATION_ROUTE) {
        PlayerPreferencesScreen(
            onNavigateUp = onNavigateUp
        )
    }
}
