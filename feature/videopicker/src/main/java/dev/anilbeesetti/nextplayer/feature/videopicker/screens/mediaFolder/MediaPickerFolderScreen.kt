package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediaFolder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.DeleteConfirmationDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideosView
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.MediaPickerViewModel
import java.io.File

@Composable
fun MediaPickerFolderRoute(
    viewModel: MediaPickerFolderViewModel = hiltViewModel(),
    mediaPickerViewModel: MediaPickerViewModel = hiltViewModel(),
    onVideoClick: (uri: Uri) -> Unit,
    onNavigateUp: () -> Unit
) {
    // The app experiences jank when videosState updates before the initial render finishes.
    // By adding Lifecycle.State.RESUMED, we ensure that we wait until the first render completes.
    val videosState by viewModel.videos.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val deleteIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = {}
    )

    MediaPickerFolderScreen(
        folderPath = viewModel.folderPath,
        videosState = videosState,
        preferences = preferences,
        viewModel = mediaPickerViewModel,
        onVideoClick = onVideoClick,
        onNavigateUp = onNavigateUp,
        onDeleteVideoClick = { viewModel.deleteVideos(it, deleteIntentSenderLauncher) },
        onAddToSync = viewModel::addToMediaInfoSynchronizer,
        selectedTracks = mediaPickerViewModel.selectedTracks,
        clearSelectedTracks = { mediaPickerViewModel.clearSelectedTracks() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerFolderScreen(
    folderPath: String,
    videosState: VideosState,
    preferences: ApplicationPreferences,
    viewModel: MediaPickerViewModel,
    onNavigateUp: () -> Unit,
    onVideoClick: (Uri) -> Unit,
    onDeleteVideoClick: (List<String>) -> Unit,
    onAddToSync: (Uri) -> Unit,
    selectedTracks: List<Video>,
    clearSelectedTracks: () -> Unit
) {
    var disableMultiSelect by rememberSaveable { mutableStateOf(true) }
    var totalVideosCount by rememberSaveable { mutableIntStateOf(0) }
    var deleteAction by rememberSaveable { mutableStateOf(false) }

    Column {
        NextTopAppBar(
            title = if (disableMultiSelect) {
                File(folderPath).prettyName
            } else {
                stringResource(id = R.string.selected_tracks_count, selectedTracks.size, totalVideosCount)
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (!disableMultiSelect) {
                        disableMultiSelect = true
                        clearSelectedTracks()
                    } else {
                        onNavigateUp()
                    }
                }) {
                    Icon(
                        imageVector = NextIcons.ArrowBack,
                        contentDescription = stringResource(id = R.string.navigate_up)
                    )
                }
            },
            actions = {
                if (!disableMultiSelect && selectedTracks.isNotEmpty()) {
                    IconButton(onClick = {
                        deleteAction = true
                    }) {
                        Icon(
                            imageVector = NextIcons.Delete,
                            contentDescription = stringResource(id = R.string.delete)
                        )
                    }
                }
            }
        )
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            VideosView(
                videosState = videosState,
                preferences = preferences,
                onVideoClick = onVideoClick,
                onDeleteVideoClick = onDeleteVideoClick,
                toggleMultiSelect = {
                    disableMultiSelect = false
                },
                disableMultiSelect = disableMultiSelect,
                totalVideos = {
                    totalVideosCount = it
                },
                onVideoLoaded = onAddToSync,
                viewModel = viewModel
            )
        }
    }
    if (deleteAction) {
        DeleteConfirmationDialog(
            subText = stringResource(id = R.string.delete_file),
            onCancel = { deleteAction = false },
            onConfirm = {
                onDeleteVideoClick(selectedTracks.map { it.uriString })
                deleteAction = false
                clearSelectedTracks()
                disableMultiSelect = true
            },
            fileNames = selectedTracks.map { it.nameWithExtension }
        )
    }
}
