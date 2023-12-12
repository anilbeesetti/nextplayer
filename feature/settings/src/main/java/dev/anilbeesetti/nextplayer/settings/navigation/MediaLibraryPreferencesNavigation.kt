package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.FolderPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.MediaLibraryPreferencesScreen

const val MEDIA_LIBRARY_PREFERENCES_NAVIGATION_ROUTE = "media_library_preferences_route"
const val FOLDER_PREFERENCES_NAVIGATION_ROUTE = "folder_preferences_route"

fun NavController.navigateToMediaLibraryPreferencesScreen(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(MEDIA_LIBRARY_PREFERENCES_NAVIGATION_ROUTE, navOptions)
}

fun NavController.navigateToFolderPreferencesScreen(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(FOLDER_PREFERENCES_NAVIGATION_ROUTE, navOptions)
}

fun NavGraphBuilder.mediaLibraryPreferencesScreen(
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit
) {
    animatedComposable(route = MEDIA_LIBRARY_PREFERENCES_NAVIGATION_ROUTE) {
        MediaLibraryPreferencesScreen(
            onNavigateUp = onNavigateUp,
            onFolderSettingClick = onFolderSettingClick
        )
    }
}

fun NavGraphBuilder.folderPreferencesScreen(onNavigateUp: () -> Unit) {
    animatedComposable(route = FOLDER_PREFERENCES_NAVIGATION_ROUTE) {
        FolderPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
