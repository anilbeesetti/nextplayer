package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.compose.runtime.SideEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.general.GeneralPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.general.GeneralPreferencesViewModel
import kotlinx.serialization.Serializable

@Serializable
object GeneralPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToGeneralPreferences() {
    add(GeneralPreferencesRoute)
}

fun EntryProviderScope<NavKey>.generalPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<GeneralPreferencesRoute> {
        val output = GeneralPreferencesViewModel.Output(
            navigateUp = onNavigateUp,
        )
        val viewModel = hiltViewModel<GeneralPreferencesViewModel, GeneralPreferencesViewModel.Factory>(
            creationCallback = { factory -> factory.create(output = output) },
        )
        SideEffect { viewModel.output = output }
        GeneralPreferencesScreen(viewModel = viewModel)
    }
}
