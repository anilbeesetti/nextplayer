package com.graviton.feature.videopicker.navigation

import android.net.Uri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.graviton.feature.videopicker.screens.mediapicker.MediaPickerRoute
import com.graviton.feature.videopicker.screens.mediapicker.MediaPickerViewModel
import kotlinx.serialization.Serializable

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
    onPlayVideos: (uris: List<Uri>, startUri: Uri?) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onVaultClick: () -> Unit,
) {
    entry<MediaPickerRoute> { key ->
        MediaPickerRoute(
            viewModel = hiltViewModel<MediaPickerViewModel, MediaPickerViewModel.Factory>(
                creationCallback = { factory ->
                    factory.create(
                        input = MediaPickerViewModel.Input(
                            folderId = key.folderId,
                        ),
                        output = MediaPickerViewModel.Output(
                            navigateUp = onNavigateUp,
                            playVideo = onPlayVideo,
                            playVideos = onPlayVideos,
                            openFolder = onFolderClick,
                            openSettings = onSettingsClick,
                            openSearch = onSearchClick,
                            openVault = onVaultClick,
                        ),
                    )
                },
            ),
        )
    }
}
