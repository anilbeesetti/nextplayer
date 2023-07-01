package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.anilbeesetti.nextplayer.settings.screens.decoder.DecoderPreferencesScreen

const val decoderPreferencesNavigationRoute = "decoder_preferences_route"

fun NavController.navigateToDecoderPreferences(navOptions: NavOptions? = null) {
    this.navigate(decoderPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.decoderPreferencesScreen(onNavigateUp: () -> Unit) {
    composable(route = decoderPreferencesNavigationRoute) {
        DecoderPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
