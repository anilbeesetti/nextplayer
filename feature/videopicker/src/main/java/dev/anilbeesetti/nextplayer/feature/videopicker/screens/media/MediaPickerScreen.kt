package dev.anilbeesetti.nextplayer.feature.videopicker.screens.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.preview.DevicePreviews
import dev.anilbeesetti.nextplayer.core.ui.preview.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.FoldersListFromState
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.QuickSettingsDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.TextIconToggleButton
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideosListFromState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.FoldersState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState

const val CIRCULAR_PROGRESS_INDICATOR_TEST_TAG = "circularProgressIndicator"

@Composable
fun MediaPickerRoute(
    onSettingsClick: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    viewModel: MediaPickerViewModel = hiltViewModel()
) {
    val videosState by viewModel.videosState.collectAsStateWithLifecycle()
    val foldersState by viewModel.foldersState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    MediaPickerScreen(
        videosState = videosState,
        foldersState = foldersState,
        preferences = preferences,
        onSettingsClick = onSettingsClick,
        onPlayVideo = onPlayVideo,
        onFolderClick = onFolderClick,
        updatePreferences = viewModel::updateMenu
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerScreen(
    videosState: VideosState,
    foldersState: FoldersState,
    preferences: ApplicationPreferences,
    onPlayVideo: (uri: Uri) -> Unit = {},
    onFolderClick: (folderPath: String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    updatePreferences: (SortBy, SortOrder, Boolean) -> Unit = { _, _, _ -> }
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }

    val selectVideoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let(onPlayVideo)
        }
    )

    Scaffold(
        topBar = {
            NextCenterAlignedTopAppBar(
                title = stringResource(id = R.string.app_name),
                navigationIcon = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = NextIcons.Settings,
                            contentDescription = stringResource(id = R.string.settings)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = NextIcons.DashBoard,
                            contentDescription = stringResource(id = R.string.menu)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = { selectVideoFileLauncher.launch("video/*") }
                ) {
                    Icon(
                        imageVector = NextIcons.FileOpen,
                        contentDescription = stringResource(id = R.string.play_file)
                    )
                }
                FloatingActionButton(
                    onClick = { showUrlDialog = true }
                ) {
                    Icon(
                        imageVector = NextIcons.Link,
                        contentDescription = stringResource(id = R.string.play_url)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (preferences.groupVideosByFolder) {
                FoldersListFromState(foldersState = foldersState, onFolderClick = onFolderClick)
            } else {
                VideosListFromState(videosState = videosState, onVideoClick = onPlayVideo)
            }
        }
        if (showMenu) {
            QuickSettingsDialog(
                preferences = preferences,
                onDismiss = { showMenu = false },
                updatePreferences = updatePreferences
            )
        }
        if (showUrlDialog) {
            NetworkStreamDialog(
                onDismiss = { showUrlDialog = false },
                onDone = {
                    showUrlDialog = false
                    if (it.isNotBlank()) onPlayVideo(Uri.parse(it))
                }
            )
        }
    }
}

@Composable
fun NetworkStreamDialog(
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    var url by rememberSaveable { mutableStateOf("") }
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_stream)) },
        content = {
            Text(text = stringResource(R.string.enter_a_network_url))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = stringResource(R.string.example_url)) }
            )
        },
        confirmButton = {
            DoneButton(
                onClick = { onDone(url) }
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) }
    )
}

@DevicePreviews
@Composable
fun MediaPickerScreenPreview(
    @PreviewParameter(VideoPickerPreviewParameterProvider::class)
    videos: List<Video>
) {
    BoxWithConstraints {
        NextPlayerTheme {
            Surface {
                MediaPickerScreen(
                    videosState = VideosState.Success(
                        data = videos
                    ),
                    foldersState = FoldersState.Loading,
                    preferences = ApplicationPreferences().copy(groupVideosByFolder = false),
                    onPlayVideo = {},
                    onFolderClick = {}
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
            icon = NextIcons.Title,
            onClick = {}
        )
    }
}

@DayNightPreview
@Composable
fun MediaPickerNoVideosFoundPreview() {
    NextPlayerTheme {
        Surface {
            MediaPickerScreen(
                videosState = VideosState.Loading,
                foldersState = FoldersState.Success(
                    data = emptyList()
                ),
                preferences = ApplicationPreferences(),
                onPlayVideo = {},
                onFolderClick = {}
            )
        }
    }
}

@DayNightPreview
@Composable
fun MediaPickerLoadingPreview() {
    NextPlayerTheme {
        Surface {
            MediaPickerScreen(
                videosState = VideosState.Loading,
                foldersState = FoldersState.Loading,
                preferences = ApplicationPreferences(),
                onPlayVideo = {},
                onFolderClick = {}
            )
        }
    }
}
