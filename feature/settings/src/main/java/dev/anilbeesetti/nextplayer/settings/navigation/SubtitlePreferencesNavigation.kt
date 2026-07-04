package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.subtitle.SubtitlePreferencesScreen
import kotlinx.serialization.Serializable

@Serializable
object SubtitlePreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToSubtitlePreferences() {
    add(SubtitlePreferencesRoute)
}

fun EntryProviderScope<NavKey>.subtitlePreferencesEntry(onNavigateUp: () -> Unit) {
    entry<SubtitlePreferencesRoute> {
        SubtitlePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
