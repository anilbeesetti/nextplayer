package dev.anilbeesetti.nextplayer.feature.videopicker

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoItemsPickerView

@Composable
fun VideoPickerScreen(
    viewModel: VideoPickerViewModel = hiltViewModel(),
    onVideoItemClick: (uri: Uri) -> Unit
) {
    val uiState by viewModel.videoPickerUiState.collectAsState()

    VideoPickerScreen(
        uiState = uiState,
        onVideoItemClick = onVideoItemClick,
        onResumeEvent = viewModel::scanMedia
    )
}

@Composable
internal fun VideoPickerScreen(
    uiState: VideoPickerUiState,
    onVideoItemClick: (uri: Uri) -> Unit,
    onResumeEvent: () -> Unit
) {

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResumeEvent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    VideoItemsPickerView(
        videoItems = uiState.videoItems,
        onVideoItemClick = onVideoItemClick
    )
}
