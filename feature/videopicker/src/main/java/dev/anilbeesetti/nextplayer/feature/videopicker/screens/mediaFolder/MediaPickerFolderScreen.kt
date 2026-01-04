package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediaFolder

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.CenterCircularProgressBar
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaView

@Composable
fun MediaPickerFolderRoute(
    viewModel: MediaPickerFolderViewModel = hiltViewModel(),
    onVideoClick: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MediaPickerFolderScreen(
        uiState = uiState,
        onPlayVideo = onVideoClick,
        onNavigateUp = onNavigateUp,
        onFolderClick = onFolderClick,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaPickerFolderScreen(
    uiState: MediaPickerFolderUiState,
    onNavigateUp: () -> Unit,
    onPlayVideo: (Uri) -> Unit,
    onFolderClick: (String) -> Unit = {},
    onEvent: (MediaPickerFolderUiEvent) -> Unit = {},
) {
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = uiState.folderName,
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.preferences.showFloatingPlayButton) return@Scaffold
            FloatingActionButton(
                onClick = {
                    val folder = (uiState.mediaDataState as? DataState.Success)?.value ?: return@FloatingActionButton
                    val videoToPlay = folder.recentlyPlayedVideo ?: folder.firstVideo ?: return@FloatingActionButton
                    onPlayVideo(videoToPlay.uriString.toUri())
                },
            ) {
                Icon(
                    imageVector = NextIcons.Play,
                    contentDescription = null,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { scaffoldPadding ->
        when (uiState.mediaDataState) {
            is DataState.Error -> {
            }

            is DataState.Loading -> {
                CenterCircularProgressBar(modifier = Modifier.padding(scaffoldPadding))
            }

            is DataState.Success -> {
                PullToRefreshBox(
                    modifier = Modifier.padding(top = scaffoldPadding.calculateTopPadding()),
                    isRefreshing = uiState.refreshing,
                    onRefresh = { onEvent(MediaPickerFolderUiEvent.Refresh) },
                ) {
                    MediaView(
                        rootFolder = uiState.mediaDataState.value,
                        preferences = uiState.preferences,
                        onFolderClick = onFolderClick,
                        onVideoClick = onPlayVideo,
                        contentPadding = scaffoldPadding.copy(top = 0.dp),
                        onDeleteFolderClick = { onEvent(MediaPickerFolderUiEvent.DeleteFolders(listOf(it))) },
                        onDeleteVideoClick = { onEvent(MediaPickerFolderUiEvent.DeleteVideos(listOf(it))) },
                        onVideoLoaded = { onEvent(MediaPickerFolderUiEvent.AddToSync(it)) },
                        onRenameVideoClick = { uri, to -> onEvent(MediaPickerFolderUiEvent.RenameVideo(uri, to)) },
                    )
                }
            }
        }
    }
}
