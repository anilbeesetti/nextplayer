package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.compose.runtime.SideEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.player.PlayerPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.player.PlayerPreferencesViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
object PlayerPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToPlayerPreferences() {
    add(PlayerPreferencesRoute)
}

fun EntryProviderScope<NavKey>.playerPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<PlayerPreferencesRoute> {
        val output = PlayerPreferencesViewModel.Output(
            navigateUp = onNavigateUp,
        )
        val viewModel = koinViewModel<PlayerPreferencesViewModel>(
            parameters = { parametersOf(output) },
        )
        SideEffect { viewModel.output = output }
        PlayerPreferencesScreen(viewModel = viewModel)
    }
}
