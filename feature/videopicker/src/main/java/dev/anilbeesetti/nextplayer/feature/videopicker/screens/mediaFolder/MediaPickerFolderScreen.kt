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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideosListFromState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import java.io.File

@Composable
fun MediaPickerFolderRoute(
    viewModel: MediaPickerFolderViewModel = hiltViewModel(),
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
        onVideoClick = onVideoClick,
        onNavigateUp = onNavigateUp,
        onDeleteVideoClick = { viewModel.deleteVideos(listOf(it), deleteIntentSenderLauncher) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerFolderScreen(
    folderPath: String,
    videosState: VideosState,
    preferences: ApplicationPreferences,
    onNavigateUp: () -> Unit,
    onVideoClick: (Uri) -> Unit,
    onDeleteVideoClick: (String) -> Unit
) {
    Column {
        NextTopAppBar(
            title = File(folderPath).prettyName,
            navigationIcon = {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = NextIcons.ArrowBack,
                        contentDescription = stringResource(id = R.string.navigate_up)
                    )
                }
            }
        )
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            VideosListFromState(
                videosState = videosState,
                preferences = preferences,
                onVideoClick = onVideoClick,
                onDeleteVideoClick = onDeleteVideoClick,
                showVideoInGrid = preferences.showMediaInGrid
            )
        }
    }
}
