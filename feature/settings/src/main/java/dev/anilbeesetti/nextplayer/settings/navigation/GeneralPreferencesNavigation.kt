package dev.anilbeesetti.nextplayer.settings.navigation

import android.widget.Toast
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.general.GeneralPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.general.GeneralPreferencesViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
object GeneralPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToGeneralPreferences() {
    add(element = GeneralPreferencesRoute)
}

fun EntryProviderScope<NavKey>.generalPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<GeneralPreferencesRoute> {
        val context = LocalContext.current
        val output = GeneralPreferencesViewModel.Output(
            navigateUp = onNavigateUp,
            showMessage = {
                Toast.makeText(
                    context,
                    it,
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
        val viewModel = koinViewModel<GeneralPreferencesViewModel>(
            parameters = { parametersOf(output) },
        )
        SideEffect { viewModel.output = output }
        GeneralPreferencesScreen(viewModel = viewModel)
    }
}
