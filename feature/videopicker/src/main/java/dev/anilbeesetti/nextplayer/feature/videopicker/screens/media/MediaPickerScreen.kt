package dev.anilbeesetti.nextplayer.feature.videopicker.screens.media

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import dev.anilbeesetti.nextplayer.core.model.AppPrefs
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.preview.DevicePreviews
import dev.anilbeesetti.nextplayer.core.ui.preview.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.FoldersState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.FoldersListFromState
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.QuickSettingsDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.TextIconToggleButton
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideosListFromState

const val CIRCULAR_PROGRESS_INDICATOR_TEST_TAG = "circularProgressIndicator"

@Composable
fun MediaPickerScreen(
    onSettingsClick: () -> Unit,
    onVideoItemClick: (uri: Uri) -> Unit,
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
        onSettingsClick = onSettingsClick,
        onVideoItemClick = onVideoItemClick,
        onFolderClick = onFolderClick,
        updatePreferences = viewModel::updateMenu
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerScreen(
    videosState: VideosState,
    foldersState: FoldersState,
    preferences: AppPrefs,
    onVideoItemClick: (uri: Uri) -> Unit = {},
    onFolderClick: (folderPath: String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    updatePreferences: (SortBy, SortOrder, Boolean) -> Unit = { _, _, _ -> }
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (preferences.groupVideosByFolder) {
                FoldersListFromState(foldersState = foldersState, onFolderClick = onFolderClick)
            } else {
                VideosListFromState(videosState = videosState, onVideoClick = onVideoItemClick)
            }
            if (showMenu) {
                QuickSettingsDialog(
                    preferences = preferences,
                    onDismiss = { showMenu = false },
                    updatePreferences = updatePreferences
                )
            }
        }
    }
}

@DevicePreviews
@Composable
fun MediaPickerScreenPreview(
    @PreviewParameter(VideoPickerPreviewParameterProvider::class)
    videos: List<Video>
) {
    BoxWithConstraints {
        NextPlayerTheme {
            Surface {
                MediaPickerScreen(
                    videosState = VideosState.Success(
                        data = videos
                    ),
                    foldersState = FoldersState.Loading,
                    preferences = AppPrefs.default().copy(groupVideosByFolder = false),
                    onVideoItemClick = {},
                    onFolderClick = {}
                )
            }
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
                preferences = AppPrefs.default(),
                onVideoItemClick = {},
                onFolderClick = {}
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
                preferences = AppPrefs.default(),
                onVideoItemClick = {},
                onFolderClick = {}
            )
        }
    }
}