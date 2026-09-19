package dev.anilbeesetti.nextplayer.settings.navigation

import androidx.compose.runtime.SideEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.FolderPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.FolderPreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.MediaLibraryPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.MediaLibraryPreferencesViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
        val output = MediaLibraryPreferencesViewModel.Output(
            navigateUp = onNavigateUp,
            openFolders = onFolderSettingClick,
            openThumbnails = onThumbnailSettingClick,
        )
        val viewModel = koinViewModel<MediaLibraryPreferencesViewModel>(
            parameters = { parametersOf(output) },
        )
        SideEffect { viewModel.output = output }
        MediaLibraryPreferencesScreen(viewModel = viewModel)
    }
}

fun EntryProviderScope<NavKey>.folderPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<FolderPreferencesRoute> {
        val output = FolderPreferencesViewModel.Output(navigateUp = onNavigateUp)
        val viewModel = koinViewModel<FolderPreferencesViewModel>(
            parameters = { parametersOf(output) },
        )
        SideEffect { viewModel.output = output }
        FolderPreferencesScreen(viewModel = viewModel)
    }
}
