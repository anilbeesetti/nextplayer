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
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.preview.DevicePreviews
import dev.anilbeesetti.nextplayer.core.ui.preview.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MenuDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.TextIconToggleButton
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.FolderContent
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideosContent

const val CIRCULAR_PROGRESS_INDICATOR_TEST_TAG = "circularProgressIndicator"

@Composable
fun MediaPickerScreen(
    onSettingsClick: () -> Unit,
    onVideoItemClick: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    viewModel: MediaPickerViewModel = hiltViewModel()
) {
    val videosState by viewModel.videoItems.collectAsStateWithLifecycle()
    val folderState by viewModel.folderItems.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    MediaPickerScreen(
        videosState = videosState,
        folderState = folderState,
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
    folderState: FolderState,
    preferences: AppPreferences,
    onVideoItemClick: (uri: Uri) -> Unit = {},
    onFolderClick: (folderPath: String) -> Unit,
    onSettingsClick: () -> Unit = {},
    updatePreferences: (SortBy, SortOrder) -> Unit = { _, _ -> }
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
                FolderContent(
                    folderState = folderState,
                    onFolderClick = onFolderClick
                )
            } else {
                VideosContent(
                    videosState = videosState,
                    onVideoItemClick = onVideoItemClick
                )
            }
            if (showMenu) {
                MenuDialog(
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
                        videos = videos
                    ),
                    folderState = FolderState.Success(
                        folders = emptyList()
                    ),
                    preferences = AppPreferences(),
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
                videosState = VideosState.Success(
                    videos = emptyList()
                ),
                folderState = FolderState.Success(
                    folders = emptyList()
                ),
                preferences = AppPreferences(),
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
                folderState = FolderState.Loading,
                preferences = AppPreferences(),
                onVideoItemClick = {},
                onFolderClick = {}
            )
        }
    }
}
