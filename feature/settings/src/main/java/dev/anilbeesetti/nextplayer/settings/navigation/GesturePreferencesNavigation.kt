package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.compose.runtime.SideEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.gesture.GesturePreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.gesture.GesturePreferencesViewModel
import kotlinx.serialization.Serializable

@Serializable
object GesturePreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToGesturePreferences() {
    add(GesturePreferencesRoute)
}

fun EntryProviderScope<NavKey>.gesturePreferencesEntry(onNavigateUp: () -> Unit) {
    entry<GesturePreferencesRoute> {
        val output = GesturePreferencesViewModel.Output(
            navigateUp = onNavigateUp,
        )
        val viewModel = hiltViewModel<GesturePreferencesViewModel, GesturePreferencesViewModel.Factory>(
            creationCallback = { factory -> factory.create(output = output) },
        )
        SideEffect { viewModel.output = output }
        GesturePreferencesScreen(viewModel = viewModel)
    }
}
