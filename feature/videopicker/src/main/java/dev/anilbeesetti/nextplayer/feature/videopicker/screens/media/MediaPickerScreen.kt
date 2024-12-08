@file:OptIn(ExperimentalPermissionsApi::class)

package dev.anilbeesetti.nextplayer.feature.videopicker.screens.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.composables.PermissionMissingView
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.preview.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaView
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.QuickSettingsDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.TextIconToggleButton
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.MediaState

const val CIRCULAR_PROGRESS_INDICATOR_TEST_TAG = "circularProgressIndicator"

@Composable
fun MediaPickerRoute(
    onSettingsClick: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    viewModel: MediaPickerViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mediaState by viewModel.mediaState.collectAsStateWithLifecycle()

    val permissionState = rememberPermissionState(permission = storagePermission)

    MediaPickerScreen(
        mediaState = mediaState,
        preferences = preferences,
        isRefreshing = uiState.refreshing,
        permissionState = permissionState,
        onPlayVideo = onPlayVideo,
        onFolderClick = onFolderClick,
        onSettingsClick = onSettingsClick,
        updatePreferences = viewModel::updateMenu,
        onDeleteVideoClick = { viewModel.deleteVideos(listOf(it)) },
        onDeleteFolderClick = { viewModel.deleteFolders(listOf(it)) },
        onAddToSync = viewModel::addToMediaInfoSynchronizer,
        onRenameVideoClick = viewModel::renameVideo,
        onRefreshClicked = viewModel::onRefreshClicked,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerScreen(
    mediaState: MediaState,
    preferences: ApplicationPreferences,
    isRefreshing: Boolean = false,
    permissionState: PermissionState = GrantedPermissionState,
    onPlayVideo: (uri: Uri) -> Unit = {},
    onFolderClick: (folderPath: String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    updatePreferences: (ApplicationPreferences) -> Unit = {},
    onDeleteVideoClick: (String) -> Unit,
    onRenameVideoClick: (Uri, String) -> Unit = { _, _ -> },
    onDeleteFolderClick: (Folder) -> Unit,
    onAddToSync: (Uri) -> Unit = {},
    onRefreshClicked: () -> Unit = {},
) {
    var showQuickSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    val selectVideoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { it?.let(onPlayVideo) },
    )

    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        topBar = {
            NextCenterAlignedTopAppBar(
                title = stringResource(id = R.string.app_name),
                navigationIcon = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = NextIcons.Settings,
                            contentDescription = stringResource(id = R.string.settings),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showQuickSettingsDialog = true }) {
                        Icon(
                            imageVector = NextIcons.DashBoard,
                            contentDescription = stringResource(id = R.string.menu),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!preferences.showFloatingPlayButton) return@Scaffold
            if (!permissionState.status.isGranted) return@Scaffold
            FloatingActionButton(
                onClick = {
                    val state = mediaState as? MediaState.Success
                    val videoToPlay = state?.data?.recentlyPlayedVideo ?: state?.data?.firstVideo
                    if (videoToPlay != null) {
                        onPlayVideo(Uri.parse(videoToPlay.uriString))
                    }
                },
            ) {
                Icon(
                    imageVector = NextIcons.Play,
                    contentDescription = null,
                )
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            modifier = Modifier.padding(paddingValues),
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = onRefreshClicked,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        ShortcutChipButton(
                            text = stringResource(id = R.string.open_local_video),
                            icon = NextIcons.FileOpen,
                            onClick = { selectVideoFileLauncher.launch("video/*") },
                        )
                    }
                    item {
                        ShortcutChipButton(
                            text = stringResource(id = R.string.open_network_stream),
                            icon = NextIcons.Link,
                            onClick = { showUrlDialog = true },
                        )
                    }
                }
                PermissionMissingView(
                    isGranted = permissionState.status.isGranted,
                    showRationale = permissionState.status.shouldShowRationale,
                    permission = permissionState.permission,
                    launchPermissionRequest = { permissionState.launchPermissionRequest() },
                ) {
                    MediaView(
                        isLoading = mediaState is MediaState.Loading,
                        rootFolder = (mediaState as? MediaState.Success)?.data,
                        preferences = preferences,
                        onFolderClick = onFolderClick,
                        onDeleteFolderClick = onDeleteFolderClick,
                        onVideoClick = onPlayVideo,
                        onRenameVideoClick = onRenameVideoClick,
                        onDeleteVideoClick = onDeleteVideoClick,
                        onVideoLoaded = onAddToSync,
                    )
                }
            }
        }
    }

    if (showQuickSettingsDialog) {
        QuickSettingsDialog(
            applicationPreferences = preferences,
            onDismiss = { showQuickSettingsDialog = false },
            updatePreferences = updatePreferences,
        )
    }

    if (showUrlDialog) {
        NetworkUrlDialog(
            onDismiss = { showUrlDialog = false },
            onDone = { onPlayVideo(Uri.parse(it)) },
        )
    }
}

@Composable
fun ShortcutChipButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .background(color = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),

    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NetworkUrlDialog(
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
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
                placeholder = { Text(text = stringResource(R.string.example_url)) },
            )
        },
        confirmButton = {
            DoneButton(
                enabled = url.isNotBlank(),
                onClick = { onDone(url) },
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@Preview
@Composable
fun MediaPickerScreenPreview(
    @PreviewParameter(VideoPickerPreviewParameterProvider::class)
    videos: List<Video>,
) {
    NextPlayerTheme {
        Surface {
            MediaPickerScreen(
                mediaState = MediaState.Loading,
                preferences = ApplicationPreferences(),
                onPlayVideo = {},
                onFolderClick = {},
                onDeleteVideoClick = {},
                onDeleteFolderClick = {},
            )
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
            onClick = {},
        )
    }
}

@DayNightPreview
@Composable
fun MediaPickerNoVideosFoundPreview() {
    NextPlayerTheme {
        Surface {
            MediaPickerScreen(
                mediaState = MediaState.Loading,
                preferences = ApplicationPreferences(),
                onPlayVideo = {},
                onFolderClick = {},
                onDeleteVideoClick = {},
                onDeleteFolderClick = {},
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
                mediaState = MediaState.Loading,
                preferences = ApplicationPreferences(),
                onPlayVideo = {},
                onFolderClick = {},
                onDeleteVideoClick = {},
                onDeleteFolderClick = {},
            )
        }
    }
}

@ExperimentalPermissionsApi
private val GrantedPermissionState = object : PermissionState {
    override val permission: String
        get() = ""
    override val status: PermissionStatus
        get() = PermissionStatus.Granted

    override fun launchPermissionRequest() {}
}
