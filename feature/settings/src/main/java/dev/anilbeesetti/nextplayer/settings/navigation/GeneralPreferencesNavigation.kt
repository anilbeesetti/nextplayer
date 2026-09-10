package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.general.GeneralPreferencesRoute
import dev.anilbeesetti.nextplayer.settings.screens.general.GeneralPreferencesViewModel
import kotlinx.serialization.Serializable

@Serializable
object GeneralPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToGeneralPreferences() {
    add(GeneralPreferencesRoute)
}

fun EntryProviderScope<NavKey>.generalPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<GeneralPreferencesRoute> {
        GeneralPreferencesRoute(
            output = GeneralPreferencesViewModel.Output(
                navigateUp = onNavigateUp,
            ),
        )
    }
}
