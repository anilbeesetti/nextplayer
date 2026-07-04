package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.audio.AudioPreferencesScreen
import kotlinx.serialization.Serializable

@Serializable
object AudioPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToAudioPreferences() {
    add(AudioPreferencesRoute)
}

fun EntryProviderScope<NavKey>.audioPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<AudioPreferencesRoute> {
        AudioPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
