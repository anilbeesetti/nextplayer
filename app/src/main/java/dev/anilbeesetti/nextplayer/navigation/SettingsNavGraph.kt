package dev.anilbeesetti.nextplayer.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import dev.anilbeesetti.nextplayer.settings.Setting
import dev.anilbeesetti.nextplayer.settings.navigation.aboutPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.appearancePreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.mediaLibraryPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAboutPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAppearancePreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToMediaPreferencesPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToPlayerPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.playerPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.settingsNavigationRoute
import dev.anilbeesetti.nextplayer.settings.navigation.settingsScreen

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
                    Setting.MEDIA_LIBRARY -> navController.navigateToMediaPreferencesPreferences()
                    Setting.PLAYER -> navController.navigateToPlayerPreferences()
                    Setting.ABOUT -> navController.navigateToAboutPreferences()
                }
            }
        )
        appearancePreferencesScreen(
            onNavigateUp = navController::popBackStack
        )
        mediaLibraryPreferencesScreen(
            onNavigateUp = navController::popBackStack
        )
        playerPreferencesScreen(
            onNavigateUp = navController::popBackStack
        )
        aboutPreferencesScreen(
            onNavigateUp = navController::popBackStack
        )
    }
}
