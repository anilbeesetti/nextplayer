package com.graviton.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.graviton.settings.screens.music.MusicPreferencesScreen
import kotlinx.serialization.Serializable

@Serializable
object MusicPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToMusicPreferences() {
    add(MusicPreferencesRoute)
}

fun EntryProviderScope<NavKey>.musicPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<MusicPreferencesRoute> {
        MusicPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
