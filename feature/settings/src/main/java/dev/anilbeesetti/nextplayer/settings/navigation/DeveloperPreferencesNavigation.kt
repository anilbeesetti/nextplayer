package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.compose.runtime.SideEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.core.common.BuildConfig
import dev.anilbeesetti.nextplayer.settings.screens.developer.DeveloperPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.developer.DeveloperPreferencesViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
object DeveloperPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToDeveloperPreferences() {
    if (BuildConfig.DEBUG) add(DeveloperPreferencesRoute)
}

fun EntryProviderScope<NavKey>.developerPreferencesEntry(onNavigateUp: () -> Unit) {
    if (!BuildConfig.DEBUG) return
    entry<DeveloperPreferencesRoute> {
        val output = DeveloperPreferencesViewModel.Output(navigateUp = onNavigateUp)
        val viewModel = koinViewModel<DeveloperPreferencesViewModel>(
            parameters = { parametersOf(output) },
        )
        SideEffect { viewModel.output = output }
        DeveloperPreferencesScreen(viewModel)
    }
}
