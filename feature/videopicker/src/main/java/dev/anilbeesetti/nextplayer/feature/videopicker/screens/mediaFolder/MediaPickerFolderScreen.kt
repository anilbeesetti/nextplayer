package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediaFolder

import android.net.Uri
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideosView
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.dialogs.ErrorDialogComponent
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.dialogs.GetSubtitlesOnlineDialogComponent
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.dialogs.LoadingDialogComponent
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.dialogs.SubtitleResultDialogComponent
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.MediaCommonDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.MediaCommonUiState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.MediaCommonViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState

@Composable
fun MediaPickerFolderRoute(
    viewModel: MediaPickerFolderViewModel = hiltViewModel(),
    mediaCommonViewModel: MediaCommonViewModel = hiltViewModel(),
    onVideoClick: (uri: Uri) -> Unit,
    onNavigateUp: () -> Unit,
) {
    // The app experiences jank when videosState updates before the initial render finishes.
    // By adding Lifecycle.State.RESUMED, we ensure that we wait until the first render completes.
    val videosState by viewModel.videos.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val commonUiState by mediaCommonViewModel.uiState.collectAsStateWithLifecycle()

    MediaPickerFolderScreen(
        commonUiState = commonUiState,
        folderName = viewModel.folderName,
        videosState = videosState,
        preferences = preferences,
        onPlayVideo = onVideoClick,
        onNavigateUp = onNavigateUp,
        onDeleteVideoClick = { viewModel.deleteVideos(listOf(it)) },
        onAddToSync = viewModel::addToMediaInfoSynchronizer,
        onRenameVideoClick = viewModel::renameVideo,
        onGetSubtitlesOnline = mediaCommonViewModel::getSubtitlesOnline,
        onRefreshClicked = mediaCommonViewModel::onRefreshClicked,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerFolderScreen(
    commonUiState: MediaCommonUiState,
    folderName: String,
    videosState: VideosState,
    preferences: ApplicationPreferences,
    onNavigateUp: () -> Unit,
    onPlayVideo: (Uri) -> Unit,
    onDeleteVideoClick: (String) -> Unit,
    onRenameVideoClick: (Uri, String) -> Unit = { _, _ -> },
    onAddToSync: (Uri) -> Unit,
    onGetSubtitlesOnline: (Video) -> Unit,
    onRefreshClicked: () -> Unit = {},
) {
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            onRefreshClicked()
        }
    }

    LaunchedEffect(commonUiState.isRefreshing) {
        if (commonUiState.isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        topBar = {
            NextTopAppBar(
                title = folderName,
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!preferences.showFloatingPlayButton) return@Scaffold
            FloatingActionButton(
                onClick = {
                    val state = videosState as? VideosState.Success
                    val videoToPlay = state?.recentPlayedVideo ?: state?.firstVideo
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
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .nestedScroll(pullToRefreshState.nestedScrollConnection),
            contentAlignment = Alignment.Center,
        ) {
            VideosView(
                videosState = videosState,
                preferences = preferences,
                onVideoClick = onPlayVideo,
                onDeleteVideoClick = onDeleteVideoClick,
                onVideoLoaded = onAddToSync,
                onRenameVideoClick = onRenameVideoClick,
                onGetSubtitlesOnline = onGetSubtitlesOnline,
            )

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    commonUiState.dialog?.let { dialog ->
        when (dialog) {
            is MediaCommonDialog.Loading -> {
                LoadingDialogComponent(message = dialog.message)
            }

            is MediaCommonDialog.Error -> {
                ErrorDialogComponent(
                    errorMessage = dialog.message,
                    onDismissRequest = dialog.onDismiss,
                )
            }

            is MediaCommonDialog.GetSubtitlesOnline -> {
                GetSubtitlesOnlineDialogComponent(
                    video = dialog.video,
                    onDismissRequest = dialog.onDismiss,
                    onConfirm = dialog.onConfirm,
                )
            }

            is MediaCommonDialog.SubtitleResults -> {
                SubtitleResultDialogComponent(
                    data = dialog.results,
                    onDismissRequest = dialog.onDismiss,
                    onSubtitleSelected = dialog.onSubtitleSelected,
                )
            }
        }
    }
}

@Preview
@Composable
private fun MediaPickerFolderScreenPreview() {
    NextPlayerTheme {
        MediaPickerFolderScreen(
            folderName = "Download",
            videosState = VideosState.Success(
                data = List(10) { Video.sample.copy(path = it.toString()) },
            ),
            preferences = ApplicationPreferences(),
            commonUiState = MediaCommonUiState(),
            onNavigateUp = {},
            onPlayVideo = {},
            onDeleteVideoClick = {},
            onAddToSync = {},
            onGetSubtitlesOnline = {},
        )
    }
}
