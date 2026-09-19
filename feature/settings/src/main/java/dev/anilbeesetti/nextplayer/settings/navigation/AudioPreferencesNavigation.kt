package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.compose.runtime.SideEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.audio.AudioPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.audio.AudioPreferencesViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
object AudioPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToAudioPreferences() {
    add(AudioPreferencesRoute)
}

fun EntryProviderScope<NavKey>.audioPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<AudioPreferencesRoute> {
        val output = AudioPreferencesViewModel.Output(
            navigateUp = onNavigateUp,
        )
        val viewModel = koinViewModel<AudioPreferencesViewModel>(
            parameters = { parametersOf(output) },
        )
        SideEffect { viewModel.output = output }
        AudioPreferencesScreen(viewModel = viewModel)
    }
}
