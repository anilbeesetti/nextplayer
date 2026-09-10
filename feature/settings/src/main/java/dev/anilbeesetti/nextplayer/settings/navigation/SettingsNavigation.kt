package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.Setting
import dev.anilbeesetti.nextplayer.settings.SettingsOutput
import dev.anilbeesetti.nextplayer.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
object SettingsRoute : NavKey

fun NavBackStack<NavKey>.navigateToSettings() {
    add(SettingsRoute)
}

fun EntryProviderScope<NavKey>.settingsEntry(onNavigateUp: () -> Unit, onItemClick: (Setting) -> Unit) {
    entry<SettingsRoute> {
        SettingsRoute(output = SettingsOutput(navigateUp = onNavigateUp, openSetting = onItemClick))
    }
}
