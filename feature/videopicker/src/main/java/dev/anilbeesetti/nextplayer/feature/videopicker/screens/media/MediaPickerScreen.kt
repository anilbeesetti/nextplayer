package dev.anilbeesetti.nextplayer.feature.videopicker.screens.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.api.AndroidPermissionState
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.preview.DevicePreviews
import dev.anilbeesetti.nextplayer.core.ui.preview.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.core.ui.views.PermissionView
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.FoldersListFromState
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.NetworkUrlDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.QuickSettingsDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.TextIconToggleButton
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideosListFromState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.FoldersState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState

const val CIRCULAR_PROGRESS_INDICATOR_TEST_TAG = "circularProgressIndicator"

@Composable
fun MediaPickerRoute(
    permissionState: AndroidPermissionState,
    onSettingsClick: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    viewModel: MediaPickerViewModel = hiltViewModel()
) {
    val videosState by viewModel.videosState.collectAsStateWithLifecycle()
    val foldersState by viewModel.foldersState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val deleteIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = {}
    )

    MediaPickerScreen(
        videosState = videosState,
        foldersState = foldersState,
        preferences = preferences,
        permissionState = permissionState,
        onPlayVideo = onPlayVideo,
        onFolderClick = onFolderClick,
        onSettingsClick = onSettingsClick,
        updatePreferences = viewModel::updateMenu,
        onDeleteVideoClick = { viewModel.deleteVideos(listOf(it), deleteIntentSenderLauncher) }
    ) { viewModel.deleteFolders(listOf(it), deleteIntentSenderLauncher) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerScreen(
    videosState: VideosState,
    foldersState: FoldersState,
    preferences: ApplicationPreferences,
    permissionState: AndroidPermissionState = AndroidPermissionState(),
    onPlayVideo: (uri: Uri) -> Unit = {},
    onFolderClick: (folderPath: String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    updatePreferences: (ApplicationPreferences) -> Unit = {},
    onDeleteVideoClick: (String) -> Unit,
    onDeleteFolderClick: (String) -> Unit
) {
    val items = listOf(
        BottomNavigationItem(
            title = stringResource(id = R.string.folders),
            selectedIcon = NextIcons.Folder,
            unSelectedIcon = NextIcons.FolderOutline,
            bottomNavigation = BottomNavigation.FOLDERS
        ),
        BottomNavigationItem(
            title = stringResource(id = R.string.videos),
            selectedIcon = NextIcons.Video,
            unSelectedIcon = NextIcons.VideoOutline,
            bottomNavigation = BottomNavigation.VIDEOS
        )
    )

    var showMenu by rememberSaveable { mutableStateOf(false) }
    var selectedBottomNavigation by rememberSaveable { mutableStateOf(items.first().bottomNavigation) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    val selectVideoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { it?.let(onPlayVideo) }
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
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = selectedBottomNavigation == item.bottomNavigation,
                        onClick = { selectedBottomNavigation = item.bottomNavigation },
                        icon = {
                            Icon(
                                imageVector = if (selectedBottomNavigation == item.bottomNavigation) item.selectedIcon else item.unSelectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = { Text(text = item.title) }
                    )
                }
            }
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
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
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (permissionState.isGranted) {
                when (selectedBottomNavigation) {
                    BottomNavigation.FOLDERS -> FoldersListFromState(
                        foldersState = foldersState,
                        preferences = preferences,
                        onFolderClick = onFolderClick,
                        onDeleteFolderClick = onDeleteFolderClick
                    )

                    BottomNavigation.VIDEOS -> VideosListFromState(
                        videosState = videosState,
                        onVideoClick = onPlayVideo,
                        preferences = preferences,
                        onDeleteVideoClick = onDeleteVideoClick
                    )
                }
            } else {
                PermissionView(permissionState = permissionState)
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

    if (showUrlDialog) {
        NetworkUrlDialog(
            onDismiss = { showUrlDialog = false },
            onDone = { onPlayVideo(Uri.parse(it)) }
        )
    }
}

data class BottomNavigationItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unSelectedIcon: ImageVector,
    val bottomNavigation: BottomNavigation
)

enum class BottomNavigation {
    FOLDERS, VIDEOS
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
                    onDeleteVideoClick = {}
                ) {}
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
                onDeleteVideoClick = {}
            ) {}
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
                onDeleteVideoClick = {}
            ) {}
        }
    }
}
