package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.videopicker.MediaState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.CIRCULAR_PROGRESS_INDICATOR_TEST_TAG

@Composable
fun MediaContent(
    state: MediaState,
    onMediaClick: (data: String) -> Unit
) {
    when (state) {
        is MediaState.Loading -> {
            CircularProgressIndicator(
                modifier = Modifier.testTag(CIRCULAR_PROGRESS_INDICATOR_TEST_TAG)
            )
        }

        is MediaState.Success<*> -> {
            if (state.data.isEmpty()) {
                Column {
                    Text(
                        text = stringResource(id = R.string.no_videos_found),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    items(state.data) { data ->
                        when (data) {
                            is Folder -> FolderItem(
                                folder = data,
                                onClick = { onMediaClick(data.path) }
                            )

                            is Video -> VideoItem(
                                video = data,
                                onClick = { onMediaClick(data.uriString) }
                            )
                        }
                    }
                }
            }
        }
    }
}
