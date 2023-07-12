package dev.anilbeesetti.nextplayer.feature.videopicker.screens.media

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.deleteFile
import dev.anilbeesetti.nextplayer.core.common.extensions.showToast
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
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
import kotlinx.coroutines.launch

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
    val showMediaActionsFor by viewModel.showMediaActionsFor.collectAsStateWithLifecycle()

    MediaPickerScreen(
        videosState = videosState,
        foldersState = foldersState,
        preferences = preferences,
        showMediaActionsFor = showMediaActionsFor,
        onSettingsClick = onSettingsClick,
        onPlayVideo = onPlayVideo,
        onFolderClick = onFolderClick,
        updatePreferences = viewModel::updateMenu,
        onVideoLongClick = viewModel::showMediaActionsFor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerScreen(
    videosState: VideosState,
    foldersState: FoldersState,
    preferences: ApplicationPreferences,
    showMediaActionsFor: Video?,
    onPlayVideo: (uri: Uri) -> Unit = {},
    onFolderClick: (folderPath: String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    updatePreferences: (ApplicationPreferences) -> Unit = {},
    onVideoLongClick: (Video?) -> Unit = {}
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var rename: Video? by remember { mutableStateOf(null) }
    var delete: Video? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState()
    val intentSenderLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            context.showToast("Renamed successfully")
        } else {
            context.showToast("Error: Couldn't rename")
        }
    }

    Column {
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (preferences.groupVideosByFolder) {
                FoldersListFromState(foldersState = foldersState, onFolderClick = onFolderClick)
            } else {
                VideosListFromState(videosState = videosState, onVideoClick = onPlayVideo, onVideoLongClick = onVideoLongClick)
            }
        }
    }

    if (showMenu) {
        QuickSettingsDialog(
            applicationPreferences = preferences,
            onDismiss = { showMenu = false },
            updatePreferences = updatePreferences
        )
    }

    delete?.let {
        NextDialog(
            onDismissRequest = { delete = null },
            title = {
                Text(text = "Delete the following file")
            },
            confirmButton = {
                DoneButton(
                    onClick = {
                        context.deleteFile(Uri.parse(it.uriString), intentSenderLauncher)
                        delete = null
                    }
                )
            },
            dismissButton = {
                CancelButton(onClick = { delete = null })
            },
            content = {
                ListItem(
                    headlineContent = {
                        Text(
                            text = it.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = it.path,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        )
    }

    showMediaActionsFor?.let { video ->
        ModalBottomSheet(
            onDismissRequest = { onVideoLongClick(null) }
        ) {
            Text(
                text = video.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(20.dp))
            ListItem(
                leadingContent = {
                    Icon(imageVector = Icons.Rounded.Delete, contentDescription = null)
                },
                headlineContent = { Text(text = "Delete") },
                modifier = Modifier.clickable {
                    delete = video
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) onVideoLongClick(null)
                    }
                }
            )
            ListItem(
                leadingContent = {
                    Icon(imageVector = Icons.Rounded.Share, contentDescription = null)
                },
                headlineContent = { Text(text = "Share") },
                modifier = Modifier.clickable {
                    val mediaStoreUri = Uri.parse(video.uriString)
                    val intent = Intent.createChooser(Intent().apply {
                        type = "video/*"
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, mediaStoreUri)
                    }, null)
                    context.startActivity(intent)
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) onVideoLongClick(null)
                    }
                }
            )
        }
    }
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
                    onFolderClick = {},
                    showMediaActionsFor = null
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
                onFolderClick = {},
                showMediaActionsFor = null
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
                onFolderClick = {},
                showMediaActionsFor = null
            )
        }
    }
}
