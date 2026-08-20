package com.graviton.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.graviton.settings.screens.gesture.GesturePreferencesScreen
import kotlinx.serialization.Serializable

@Serializable
object GesturePreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToGesturePreferences() {
    add(GesturePreferencesRoute)
}

fun EntryProviderScope<NavKey>.gesturePreferencesEntry(onNavigateUp: () -> Unit) {
    entry<GesturePreferencesRoute> {
        GesturePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
