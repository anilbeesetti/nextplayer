package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NavigationAnimations
import dev.anilbeesetti.nextplayer.settings.screens.subtitle.SubtitlePreferencesScreen

const val subtitlePreferencesNavigationRoute = "subtitle_preferences_route"

fun NavController.navigateToSubtitlePreferences(navOptions: NavOptions? = null) {
    this.navigate(subtitlePreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.subtitlePreferencesScreen(onNavigateUp: () -> Unit) {
    composable(
        route = subtitlePreferencesNavigationRoute,
        enterTransition = { NavigationAnimations.slideEnter },
        popExitTransition = { NavigationAnimations.slideExit },
        popEnterTransition = null
    ) {
        SubtitlePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
