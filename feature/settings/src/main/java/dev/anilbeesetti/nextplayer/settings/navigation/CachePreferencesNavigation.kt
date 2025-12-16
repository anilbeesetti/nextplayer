package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import dev.anilbeesetti.nextplayer.core.ui.designsystem.animatedComposable
import dev.anilbeesetti.nextplayer.settings.screens.cache.CachePreferencesScreen

const val cachePreferencesNavigationRoute = "cache_preferences_route"

fun NavController.navigateToCachePreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(cachePreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.cachePreferencesScreen(onNavigateUp: () -> Unit) {
    animatedComposable(route = cachePreferencesNavigationRoute) {
        CachePreferencesScreen(
            onNavigateUp = onNavigateUp,
        )
    }
}
