package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.about.AboutPreferencesOutput
import dev.anilbeesetti.nextplayer.settings.screens.about.AboutPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.about.LibrariesOutput
import dev.anilbeesetti.nextplayer.settings.screens.about.LibrariesScreen
import kotlinx.serialization.Serializable

@Serializable
object AboutPreferencesRoute : NavKey

@Serializable
object LibrariesRoute : NavKey

fun NavBackStack<NavKey>.navigateToAboutPreferences() {
    add(AboutPreferencesRoute)
}

fun NavBackStack<NavKey>.navigateToLibraries() {
    add(LibrariesRoute)
}

fun EntryProviderScope<NavKey>.aboutPreferencesEntry(
    onLibrariesClick: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    entry<AboutPreferencesRoute> {
        AboutPreferencesScreen(output = AboutPreferencesOutput(navigateUp = onNavigateUp, openLibraries = onLibrariesClick))
    }
}

fun EntryProviderScope<NavKey>.librariesEntry(onNavigateUp: () -> Unit) {
    entry<LibrariesRoute> {
        LibrariesScreen(output = LibrariesOutput(navigateUp = onNavigateUp))
    }
}
