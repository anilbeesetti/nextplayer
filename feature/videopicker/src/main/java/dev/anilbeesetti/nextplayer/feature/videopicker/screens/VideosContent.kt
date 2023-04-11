package dev.anilbeesetti.nextplayer.feature.videopicker.screens

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoItemsPickerView

@Composable
fun VideosContent(
    videosState: VideosState,
    onVideoItemClick: (uri: Uri) -> Unit,
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
}