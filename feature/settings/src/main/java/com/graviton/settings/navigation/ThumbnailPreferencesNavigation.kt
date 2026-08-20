package com.graviton.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.graviton.settings.screens.thumbnail.ThumbnailPreferencesScreen
import kotlinx.serialization.Serializable

@Serializable
object ThumbnailPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToThumbnailPreferencesScreen() {
    add(ThumbnailPreferencesRoute)
}

fun EntryProviderScope<NavKey>.thumbnailPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<ThumbnailPreferencesRoute> {
        ThumbnailPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
