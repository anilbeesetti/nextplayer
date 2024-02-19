package dev.anilbeesetti.nextplayer.feature.videopicker.screens.media

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.preview.DevicePreviews
import dev.anilbeesetti.nextplayer.core.ui.preview.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.FoldersView
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.QuickSettingsDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.TextIconToggleButton
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideosView
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.FoldersState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState

const val CIRCULAR_PROGRESS_INDICATOR_TEST_TAG = "circularProgressIndicator"

@Composable
fun MediaPickerRoute(
    onSettingsClick: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    viewModel: MediaPickerViewModel = hiltViewModel()
) {
    val videosState by viewModel.videosState.collectAsStateWithLifecycle()
    val foldersState by viewModel.foldersState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    MediaPickerScreen(
        videosState = videosState,
        foldersState = foldersState,
        preferences = preferences,
        onPlayVideo = onPlayVideo,
        onFolderClick = onFolderClick,
        onSettingsClick = onSettingsClick,
        updatePreferences = viewModel::updateMenu,
        onDeleteVideoClick = { viewModel.deleteVideos(listOf(it)) },
        onDeleteFolderClick = { viewModel.deleteFolders(listOf(it)) },
        onAddToSync = viewModel::addToMediaInfoSynchronizer,
        onRenameVideoClick = viewModel::renameVideo
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerScreen(
    videosState: VideosState,
    foldersState: FoldersState,
    preferences: ApplicationPreferences,
    onPlayVideo: (uri: Uri) -> Unit = {},
    onFolderClick: (folderPath: String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    updatePreferences: (ApplicationPreferences) -> Unit = {},
    onDeleteVideoClick: (String) -> Unit,
    onRenameVideoClick: (Uri, String) -> Unit = { _, _ -> },
    onDeleteFolderClick: (String) -> Unit,
    onAddToSync: (Uri) -> Unit = {}
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Column {
        NextCenterAlignedTopAppBar(
            title = stringResource(id = R.string.app_name),
            navigationIcon = {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = NextIcons.Settings,
                        contentDescription = stringResource(id = R.string.settings)
                    )
                }
            },
            actions = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = NextIcons.DashBoard,
                        contentDescription = stringResource(id = R.string.menu)
                    )
                }
            }
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (preferences.groupVideosByFolder) {
                FoldersView(
                    foldersState = foldersState,
                    preferences = preferences,
                    onFolderClick = onFolderClick,
                    onDeleteFolderClick = onDeleteFolderClick
                )
            } else {
                VideosView(
                    videosState = videosState,
                    onVideoClick = onPlayVideo,
                    preferences = preferences,
                    onDeleteVideoClick = onDeleteVideoClick,
                    onVideoLoaded = onAddToSync,
                    onRenameVideoClick = onRenameVideoClick
                )
            }
        }
    }

    if (showMenu) {
        QuickSettingsDialog(
            applicationPreferences = preferences,
            onDismiss = { showMenu = false },
            updatePreferences = updatePreferences
        )
    }
}

@DevicePreviews
@Composable
fun MediaPickerScreenPreview(
    @PreviewParameter(VideoPickerPreviewParameterProvider::class)
    videos: List<Video>
) {
    NextPlayerTheme {
        Surface {
            MediaPickerScreen(
                videosState = VideosState.Success(
                    data = videos
                ),
                foldersState = FoldersState.Loading,
                preferences = ApplicationPreferences().copy(groupVideosByFolder = false),
                onPlayVideo = {},
                onFolderClick = {},
                onDeleteVideoClick = {},
                onDeleteFolderClick = {}
            )
        }
    }
}

@Preview
@Composable
fun ButtonPreview() {
    Surface {
        TextIconToggleButton(
            text = "Title",
            icon = NextIcons.Title,
            onClick = {}
        )
    }
}

@DayNightPreview
@Composable
fun MediaPickerNoVideosFoundPreview() {
    NextPlayerTheme {
        Surface {
            MediaPickerScreen(
                videosState = VideosState.Loading,
                foldersState = FoldersState.Success(
                    data = emptyList()
                ),
                preferences = ApplicationPreferences(),
                onPlayVideo = {},
                onFolderClick = {},
                onDeleteVideoClick = {},
                onDeleteFolderClick = {}
            )
        }
    }
}

@DayNightPreview
@Composable
fun MediaPickerLoadingPreview() {
    NextPlayerTheme {
        Surface {
            MediaPickerScreen(
                videosState = VideosState.Loading,
                foldersState = FoldersState.Loading,
                preferences = ApplicationPreferences(),
                onPlayVideo = {},
                onFolderClick = {},
                onDeleteVideoClick = {},
                onDeleteFolderClick = {}
            )
        }
    }
}
