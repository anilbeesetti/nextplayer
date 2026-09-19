package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.compose.runtime.SideEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.appearance.AppearancePreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.appearance.AppearancePreferencesViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
object AppearancePreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToAppearancePreferences() {
    add(AppearancePreferencesRoute)
}

fun EntryProviderScope<NavKey>.appearancePreferencesEntry(onNavigateUp: () -> Unit) {
    entry<AppearancePreferencesRoute> {
        val output = AppearancePreferencesViewModel.Output(
            navigateUp = onNavigateUp,
        )
        val viewModel = koinViewModel<AppearancePreferencesViewModel>(
            parameters = { parametersOf(output) },
        )
        SideEffect { viewModel.output = output }
        AppearancePreferencesScreen(viewModel = viewModel)
    }
}
