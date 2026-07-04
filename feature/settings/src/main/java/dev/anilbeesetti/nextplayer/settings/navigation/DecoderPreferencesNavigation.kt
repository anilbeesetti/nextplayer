package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.decoder.DecoderPreferencesScreen
import kotlinx.serialization.Serializable

@Serializable
object DecoderPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToDecoderPreferences() {
    add(DecoderPreferencesRoute)
}

fun EntryProviderScope<NavKey>.decoderPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<DecoderPreferencesRoute> {
        DecoderPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
