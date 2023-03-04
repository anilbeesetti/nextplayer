package dev.anilbeesetti.nextplayer.feature.videopicker

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.navigation.compose.hiltViewModel
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.ui.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.DevicePreviews
import dev.anilbeesetti.nextplayer.core.ui.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoItemsPickerView

const val CIRCULAR_PROGRESS_INDICATOR_TEST_TAG = "circularProgressIndicator"

@Composable
fun VideoPickerScreen(
    viewModel: VideoPickerViewModel = hiltViewModel(),
    onVideoItemClick: (uri: Uri) -> Unit
) {
    val uiState by viewModel.videoItems.collectAsState()

    VideoPickerScreen(
        uiState = uiState,
        onVideoItemClick = onVideoItemClick
    )
}

@Composable
internal fun VideoPickerScreen(
    uiState: VideoPickerUiState,
    onVideoItemClick: (uri: Uri) -> Unit
) {


    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is VideoPickerUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(CIRCULAR_PROGRESS_INDICATOR_TEST_TAG)
                )
            }
            is VideoPickerUiState.Success -> {
                if (uiState.videos.isEmpty()) {
                    Column {
                        Text(
                            text = stringResource(id = R.string.no_videos_found),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                } else {
                    VideoItemsPickerView(
                        videos = uiState.videos,
                        onVideoItemClick = onVideoItemClick
                    )
                }
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
                    uiState = VideoPickerUiState.Success(
                        videos = videos
                    ),
                    onVideoItemClick = {}
                )
            }
        }
    }
}

@DayNightPreview
@Composable
fun VideoPickerNoVideosFoundPreview() {
    NextPlayerTheme {
        Surface {
            VideoPickerScreen(
                uiState = VideoPickerUiState.Success(
                    videos = emptyList()
                ),
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
                uiState = VideoPickerUiState.Loading,
                onVideoItemClick = {}
            )
        }
    }
}
