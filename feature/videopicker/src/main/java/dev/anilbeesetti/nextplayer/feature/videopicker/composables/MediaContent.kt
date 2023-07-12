package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.FoldersState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.CIRCULAR_PROGRESS_INDICATOR_TEST_TAG

@Composable
fun MediaLazyList(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 10.dp),
        modifier = modifier
            .fillMaxSize(),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

@Composable
fun CenterCircularProgressBar() {
    CircularProgressIndicator(
        modifier = Modifier
            .testTag(CIRCULAR_PROGRESS_INDICATOR_TEST_TAG)
    )
}

@Composable
fun NoVideosFound() {
    Text(
        text = stringResource(id = R.string.no_videos_found),
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
fun VideosListFromState(
    videosState: VideosState,
    onVideoClick: (Uri) -> Unit,
    onVideoLongClick: (Video) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    when (videosState) {
        VideosState.Loading -> CenterCircularProgressBar()
        is VideosState.Success -> if (videosState.data.isEmpty()) {
            NoVideosFound()
        } else {
            MediaLazyList {
                items(videosState.data, key = { it.path }) {
                    VideoItem(
                        video = it,
                        onClick = { onVideoClick(Uri.parse(it.uriString)) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onVideoLongClick(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FoldersListFromState(
    foldersState: FoldersState,
    onFolderClick: (folderPath: String) -> Unit
) {
    when (foldersState) {
        FoldersState.Loading -> CenterCircularProgressBar()
        is FoldersState.Success -> if (foldersState.data.isEmpty()) {
            NoVideosFound()
        } else {
            MediaLazyList {
                items(foldersState.data, key = { it.path }) {
                    FolderItem(
                        directory = it,
                        onClick = { onFolderClick(it.path) }
                    )
                }
            }
        }
    }
}
