package dev.anilbeesetti.nextplayer.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import dev.anilbeesetti.nextplayer.settings.Setting
import dev.anilbeesetti.nextplayer.settings.navigation.aboutPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.appearancePreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.decoderPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.folderPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.mediaLibraryPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAboutPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAppearancePreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToDecoderPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToFolderPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToMediaLibraryPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToPlayerPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSubtitlePreferences
import dev.anilbeesetti.nextplayer.settings.navigation.playerPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.settingsNavigationRoute
import dev.anilbeesetti.nextplayer.settings.navigation.settingsScreen
import dev.anilbeesetti.nextplayer.settings.navigation.subtitlePreferencesScreen

const val SETTINGS_ROUTE = "settings_nav_route"

fun NavGraphBuilder.settingsNavGraph(
    navController: NavHostController
) {
    navigation(
        startDestination = settingsNavigationRoute,
        route = SETTINGS_ROUTE
    ) {
        settingsScreen(
            onNavigateUp = navController::popBackStack,
            onItemClick = { setting ->
                when (setting) {
                    Setting.APPEARANCE -> navController.navigateToAppearancePreferences()
                    Setting.MEDIA_LIBRARY -> navController.navigateToMediaLibraryPreferencesScreen()
                    Setting.PLAYER -> navController.navigateToPlayerPreferences()
                    Setting.DECODER -> navController.navigateToDecoderPreferences()
                    Setting.SUBTITLE -> navController.navigateToSubtitlePreferences()
                    Setting.ABOUT -> navController.navigateToAboutPreferences()
                }
            }
        )
        appearancePreferencesScreen(
            onNavigateUp = navController::popBackStack
        )
        mediaLibraryPreferencesScreen(
            onNavigateUp = navController::popBackStack,
            onFolderSettingClick = navController::navigateToFolderPreferencesScreen
        )
        folderPreferencesScreen(
            onNavigateUp = navController::popBackStack
        )
        playerPreferencesScreen(
            onNavigateUp = navController::popBackStack
        )
        decoderPreferencesScreen(
            onNavigateUp = navController::popBackStack
        )
        subtitlePreferencesScreen(
            onNavigateUp = navController::popBackStack
        )
        aboutPreferencesScreen(
            onNavigateUp = navController::popBackStack
        )
    }
}
