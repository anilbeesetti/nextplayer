package dev.anilbeesetti.nextplayer.feature.videopicker

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoItemsPickerView

@Composable
fun VideoPickerScreen(
    viewModel: VideoPickerViewModel = hiltViewModel(),
    onVideoItemClick: (uri: Uri) -> Unit
) {
    val videoItems by viewModel.videoItems.collectAsState()

    VideoPickerScreen(
        videoItems = videoItems,
        onVideoItemClick = onVideoItemClick
    )
}

@Composable
internal fun VideoPickerScreen(
    videoItems: List<VideoItem>,
    onVideoItemClick: (uri: Uri) -> Unit,
) {
    VideoItemsPickerView(
        videoItems = videoItems,
        onVideoItemClick = onVideoItemClick
    )
}
