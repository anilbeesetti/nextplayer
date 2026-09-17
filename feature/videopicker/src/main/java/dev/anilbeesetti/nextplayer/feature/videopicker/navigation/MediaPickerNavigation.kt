package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.compose.runtime.SideEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker.MediaPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker.MediaPickerViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data class MediaPickerRoute(
    val folderId: String? = null,
) : NavKey

fun NavBackStack<NavKey>.navigateToMediaPickerScreen(folderId: String) {
    add(MediaPickerRoute(folderId))
}

fun EntryProviderScope<NavKey>.mediaPickerEntry(
    onNavigateUp: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onPlayVideos: (uris: List<Uri>) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onVaultClick: () -> Unit,
) {
    entry<MediaPickerRoute> { key ->
        val output = MediaPickerViewModel.Output(
            navigateUp = onNavigateUp,
            playVideo = onPlayVideo,
            playVideos = onPlayVideos,
            openFolder = onFolderClick,
            openSettings = onSettingsClick,
            openSearch = onSearchClick,
            openVault = onVaultClick,
        )
        val viewModel = koinViewModel<MediaPickerViewModel>(
            parameters = {
                parametersOf(
                    MediaPickerViewModel.Input(folderId = key.folderId),
                    output,
                )
            },
        )
        SideEffect { viewModel.output = output }
        MediaPickerScreen(viewModel = viewModel)
    }
}
