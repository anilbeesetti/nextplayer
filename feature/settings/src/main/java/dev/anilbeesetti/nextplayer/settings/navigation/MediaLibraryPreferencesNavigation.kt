package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.MediaLibraryPreferencesScreen

const val mediaLibraryPreferencesNavigationRoute = "media_library_preferences_route"

fun NavController.navigateToMediaPreferencesPreferences(navOptions: NavOptions? = null) {
    this.navigate(mediaLibraryPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.mediaLibraryPreferencesScreen(onNavigateUp: () -> Unit) {
    composable(route = mediaLibraryPreferencesNavigationRoute) {
        MediaLibraryPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
