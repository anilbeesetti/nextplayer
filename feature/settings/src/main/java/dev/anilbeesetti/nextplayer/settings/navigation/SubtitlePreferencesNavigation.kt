package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.compose.runtime.SideEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.subtitle.SubtitlePreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.subtitle.SubtitlePreferencesViewModel
import kotlinx.serialization.Serializable

@Serializable
object SubtitlePreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToSubtitlePreferences() {
    add(SubtitlePreferencesRoute)
}

fun EntryProviderScope<NavKey>.subtitlePreferencesEntry(onNavigateUp: () -> Unit) {
    entry<SubtitlePreferencesRoute> {
        val output = SubtitlePreferencesViewModel.Output(
            navigateUp = onNavigateUp,
        )
        val viewModel = hiltViewModel<SubtitlePreferencesViewModel, SubtitlePreferencesViewModel.Factory>(
            creationCallback = { factory -> factory.create(output = output) },
        )
        SideEffect { viewModel.output = output }
        SubtitlePreferencesScreen(viewModel = viewModel)
    }
}
