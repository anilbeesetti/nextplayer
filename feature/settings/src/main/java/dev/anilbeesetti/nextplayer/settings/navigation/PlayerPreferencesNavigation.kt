package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.settings.screens.player.PlayerPreferencesScreen

const val playerPreferencesNavigationRoute = "player_preferences_route"

fun NavController.navigateToPlayerPreferences(navOptions: NavOptions? = null) {
    this.navigate(playerPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.playerPreferencesScreen(onNavigateUp: () -> Unit) {
    animatedComposable(route = playerPreferencesNavigationRoute) {
        PlayerPreferencesScreen(
            onNavigateUp = onNavigateUp
        )
    }
}
