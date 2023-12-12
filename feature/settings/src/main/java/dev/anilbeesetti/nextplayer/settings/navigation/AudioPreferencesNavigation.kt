package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.settings.screens.audio.AudioPreferencesScreen

const val AUDIO_PREFERENCES_NAVIGATION_ROUTE = "audio_preferences_route"

fun NavController.navigateToAudioPreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(AUDIO_PREFERENCES_NAVIGATION_ROUTE, navOptions)
}

fun NavGraphBuilder.audioPreferencesScreen(onNavigateUp: () -> Unit) {
    animatedComposable(route = AUDIO_PREFERENCES_NAVIGATION_ROUTE) {
        AudioPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
