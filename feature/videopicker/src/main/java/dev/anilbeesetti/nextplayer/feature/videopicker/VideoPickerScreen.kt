package dev.anilbeesetti.nextplayer.feature.videopicker

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import dev.anilbeesetti.nextplayer.core.ui.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.DevicePreviews
import dev.anilbeesetti.nextplayer.core.ui.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.components.NextMainTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MenuDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.TextIconToggleButton
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoItemsPickerView

const val CIRCULAR_PROGRESS_INDICATOR_TEST_TAG = "circularProgressIndicator"

@Composable
fun VideoPickerScreen(
    viewModel: VideoPickerViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit,
    onVideoItemClick: (uri: Uri) -> Unit
) {
    val videosState by viewModel.videoItems.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    VideoPickerScreen(
        videosState = videosState,
        preferences = preferences,
        onSettingsClick = onSettingsClick,
        onVideoItemClick = onVideoItemClick,
        updatePreferences = viewModel::updateMenu
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPickerScreen(
    videosState: VideosState,
    preferences: AppPreferences,
    onVideoItemClick: (uri: Uri) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    updatePreferences: (SortBy, SortOrder) -> Unit = { _, _ -> }
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NextMainTopAppBar(
                titleRes = R.string.app_name,
                navigationIcon = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(id = R.string.settings)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Dashboard,
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
            when (videosState) {
                is VideosState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag(CIRCULAR_PROGRESS_INDICATOR_TEST_TAG)
                    )
                }
                is VideosState.Success -> {
                    if (videosState.videos.isEmpty()) {
                        Column {
                            Text(
                                text = stringResource(id = R.string.no_videos_found),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    } else {
                        VideoItemsPickerView(
                            videos = videosState.videos,
                            onVideoItemClick = onVideoItemClick
                        )
                    }
                }
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
fun VideoPickerScreenPreview(
    @PreviewParameter(VideoPickerPreviewParameterProvider::class)
    videos: List<Video>
) {
    BoxWithConstraints {
        NextPlayerTheme {
            Surface {
                VideoPickerScreen(
                    videosState = VideosState.Success(
                        videos = videos
                    ),
                    preferences = AppPreferences(),
                    onVideoItemClick = {}
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
            icon = Icons.Filled.Title,
            onClick = {}
        )
    }
}

@DayNightPreview
@Composable
fun VideoPickerNoVideosFoundPreview() {
    NextPlayerTheme {
        Surface {
            VideoPickerScreen(
                videosState = VideosState.Success(
                    videos = emptyList()
                ),
                preferences = AppPreferences(),
                onVideoItemClick = {}
            )
        }
    }
}

@DayNightPreview
@Composable
fun VideoPickerLoadingPreview() {
    NextPlayerTheme {
        Surface {
            VideoPickerScreen(
                videosState = VideosState.Loading,
                preferences = AppPreferences(),
                onVideoItemClick = {}
            )
        }
    }
}
