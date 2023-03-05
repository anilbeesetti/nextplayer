package dev.anilbeesetti.nextplayer.feature.videopicker

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
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
    val videosState by viewModel.videoItems.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val preferences by viewModel.preferences.collectAsState()

    VideoPickerScreen(
        videosState = videosState,
        uiState = uiState,
        preferences = preferences,
        onVideoItemClick = onVideoItemClick,
        showMenuDialog = viewModel::showMenuDialog,
        updateSortBy = viewModel::updateSortBy
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun VideoPickerScreen(
    videosState: VideosState,
    uiState: VideoPickerViewState,
    preferences: AppPreferences,
    onVideoItemClick: (uri: Uri) -> Unit = {},
    showMenuDialog: (Boolean) -> Unit = {},
    updateSortBy: (SortBy) -> Unit = {},
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
        if (uiState.showMenuDialog) {
            MenuDialog(
                preferences = preferences,
                showMenuDialog = showMenuDialog,
                updateSortBy = updateSortBy
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MenuDialog(preferences: AppPreferences, showMenuDialog: (Boolean) -> Unit, updateSortBy: (SortBy) -> Unit) {
    Dialog(
        onDismissRequest = { showMenuDialog(false) },
        content = {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .padding(bottom = 20.dp),
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {

                    var sortBy by remember { mutableStateOf(preferences.sortBy) }

                    Column {
                        val textStyle = MaterialTheme.typography.headlineSmall
                        ProvideTextStyle(value = textStyle) {
                            Text(text = "Sort")
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            TextIconToggleButton(
                                text = "Title",
                                icon = Icons.Rounded.Title,
                                isSelected = sortBy == SortBy.TITLE,
                                onClick = { sortBy = SortBy.TITLE },
                            )
                            TextIconToggleButton(
                                text = "Duration",
                                icon = Icons.Rounded.Straighten,
                                isSelected = sortBy == SortBy.DURATION,
                                onClick = { sortBy = SortBy.DURATION },
                            )
                            TextIconToggleButton(
                                text = "Path",
                                icon = Icons.Rounded.LocationOn,
                                isSelected = sortBy == SortBy.PATH,
                                onClick = { sortBy = SortBy.PATH },
                            )
                            TextIconToggleButton(
                                text = "Resolution",
                                icon = Icons.Rounded.HighQuality,
                                isSelected = sortBy == SortBy.RESOLUTION,
                                onClick = { sortBy = SortBy.RESOLUTION },
                            )
                        }

                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showMenuDialog(false) }) {
                            Text(text = "CANCEL")
                        }
                        TextButton(onClick = {
                            showMenuDialog(false)
                            updateSortBy(sortBy)
                        }) {
                            Text(text = "DONE")
                        }
                    }
                }
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        )
    )
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
                    uiState = VideoPickerViewState(),
                    preferences = AppPreferences(),
                    onVideoItemClick = {},
                    showMenuDialog = {}
                )
            }
        }
    }
}

@Composable
fun TextIconToggleButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (Boolean) -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clickable(onClick = { onClick(!isSelected) })
    ) {
        FilledIconToggleButton(checked = isSelected, onCheckedChange = onClick) {
            Icon(imageVector = icon, contentDescription = text)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
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
                uiState = VideoPickerViewState(),
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
                uiState = VideoPickerViewState(),
                preferences = AppPreferences(),
                onVideoItemClick = {},
                showMenuDialog = {}
            )
        }
    }
}
