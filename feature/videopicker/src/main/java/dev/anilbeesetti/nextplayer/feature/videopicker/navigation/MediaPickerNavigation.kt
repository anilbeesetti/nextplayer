package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker.MediaPickerRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker.MediaPickerViewModel
import kotlinx.serialization.Serializable

class FolderArgs(val folderId: String?)

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
        MediaPickerRoute(
            viewModel = hiltViewModel<MediaPickerViewModel, MediaPickerViewModel.Factory>(
                creationCallback = { factory -> factory.create(FolderArgs(key.folderId)) },
            ),
            onPlayVideo = onPlayVideo,
            onPlayVideos = onPlayVideos,
            onNavigateUp = onNavigateUp,
            onFolderClick = onFolderClick,
            onSettingsClick = onSettingsClick,
            onSearchClick = onSearchClick,
            onVaultClick = onVaultClick,
        )
    }
}
