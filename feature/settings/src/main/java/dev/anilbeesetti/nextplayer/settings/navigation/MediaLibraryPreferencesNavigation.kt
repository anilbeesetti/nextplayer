package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.FolderPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.MediaLibraryPreferencesScreen
import kotlinx.serialization.Serializable

@Serializable
object MediaLibraryPreferencesRoute : NavKey

@Serializable
object FolderPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToMediaLibraryPreferencesScreen() {
    add(MediaLibraryPreferencesRoute)
}

fun NavBackStack<NavKey>.navigateToFolderPreferencesScreen() {
    add(FolderPreferencesRoute)
}

fun EntryProviderScope<NavKey>.mediaLibraryPreferencesEntry(
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit,
    onThumbnailSettingClick: () -> Unit,
) {
    entry<MediaLibraryPreferencesRoute> {
        MediaLibraryPreferencesScreen(
            onNavigateUp = onNavigateUp,
            onFolderSettingClick = onFolderSettingClick,
            onThumbnailSettingClick = onThumbnailSettingClick,
        )
    }
}

fun EntryProviderScope<NavKey>.folderPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<FolderPreferencesRoute> {
        FolderPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
