package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.appearance.AppearancePreferencesScreen
import kotlinx.serialization.Serializable

@Serializable
object AppearancePreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToAppearancePreferences() {
    add(AppearancePreferencesRoute)
}

fun EntryProviderScope<NavKey>.appearancePreferencesEntry(onNavigateUp: () -> Unit) {
    entry<AppearancePreferencesRoute> {
        AppearancePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
