package dev.anilbeesetti.nextplayer.feature.videopicker

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.navigation.compose.hiltViewModel
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import dev.anilbeesetti.nextplayer.core.ui.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.DevicePreviews
import dev.anilbeesetti.nextplayer.core.ui.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MenuDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.TextIconToggleButton
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoItemsPickerView

const val CIRCULAR_PROGRESS_INDICATOR_TEST_TAG = "circularProgressIndicator"

@Composable
fun VideoPickerScreen(
    viewModel: VideoPickerViewModel = hiltViewModel(),
    showMenu: Boolean,
    showMenuDialog: (Boolean) -> Unit = {},
    onVideoItemClick: (uri: Uri) -> Unit
) {
    val videosState by viewModel.videoItems.collectAsState()
    val preferences by viewModel.preferences.collectAsState()

    VideoPickerScreen(
        videosState = videosState,
        showDialog = showMenu,
        preferences = preferences,
        onVideoItemClick = onVideoItemClick,
        showMenuDialog = showMenuDialog,
        updatePreferences = viewModel::updateMenu
    )
}

@Composable
internal fun VideoPickerScreen(
    videosState: VideosState,
    showDialog: Boolean = false,
    preferences: AppPreferences,
    onVideoItemClick: (uri: Uri) -> Unit = {},
    showMenuDialog: (Boolean) -> Unit = {},
    updatePreferences: (SortBy, SortOrder) -> Unit = { _, _ -> }
) {
    Box(
        modifier = Modifier
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
        if (showDialog) {
            MenuDialog(
                preferences = preferences,
                showMenuDialog = showMenuDialog,
                update = updatePreferences
            )
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
                    onVideoItemClick = {},
                    showMenuDialog = {}
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
                onVideoItemClick = {},
                showMenuDialog = {}
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
                onVideoItemClick = {},
                showMenuDialog = {}
            )
        }
    }
}
