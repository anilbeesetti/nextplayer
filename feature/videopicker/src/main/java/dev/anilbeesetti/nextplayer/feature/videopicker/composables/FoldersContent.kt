package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.CIRCULAR_PROGRESS_INDICATOR_TEST_TAG
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.FolderState


@Composable
fun FolderContent(
    folderState: FolderState,
    onFolderClick: (id: String) -> Unit,
) {
    when (folderState) {
        is FolderState.Loading -> {
            CircularProgressIndicator(
                modifier = Modifier.testTag(CIRCULAR_PROGRESS_INDICATOR_TEST_TAG)
            )
        }
        is FolderState.Success -> {
            if (folderState.folders.isEmpty()) {
                Column {
                    Text(
                        text = stringResource(id = R.string.no_videos_found),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            } else {
                PickerView(list = folderState.folders,) { folder ->
                    FolderView(
                        folder = folder,
                        onClick = { onFolderClick(folder.path) }
                    )
                }
            }
        }
    }
}