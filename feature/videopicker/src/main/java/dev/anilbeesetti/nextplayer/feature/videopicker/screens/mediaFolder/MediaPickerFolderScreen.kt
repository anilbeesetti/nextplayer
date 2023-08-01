package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediaFolder

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
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
    val videosState by viewModel.videos.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.RESUMED
    )

    MediaPickerFolderScreen(
        folderPath = viewModel.folderPath,
        videosState = videosState,
        onVideoClick = onVideoClick,
        onNavigateUp = onNavigateUp,
        viewModel = viewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerFolderScreen(
    folderPath: String,
    videosState: VideosState,
    onVideoClick: (uri: Uri) -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: MediaPickerFolderViewModel
) {
    val prefs = viewModel.appPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column {
        NextTopAppBar(title = File(folderPath).prettyName, navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = NextIcons.ArrowBack,
                    contentDescription = stringResource(id = R.string.navigate_up)
                )
            }
        }, actions = {
            IconButton(onClick = {
                if (prefs.value.isShuffleOn) {
                    Toast.makeText(
                        context,
                        R.string.shuffle_disabled,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(context, R.string.shuffle_enabled, Toast.LENGTH_SHORT).show()
                    when (videosState) {
                        is VideosState.Success -> {
                            onVideoClick(
                                Uri.parse(
                                    videosState.data.shuffled().first().uriString
                                )
                            )
                        }

                        else -> {}
                    }
                }
                viewModel.toggleShuffle()
            }) {
                if (prefs.value.isShuffleOn) {
                    Icon(
                        imageVector = NextIcons.ShuffleOn,
                        contentDescription = stringResource(id = R.string.shuffle_enabled),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = NextIcons.Shuffle,
                        contentDescription = stringResource(id = R.string.shuffle_enabled),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        })
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            VideosListFromState(videosState = videosState, onVideoClick = onVideoClick)
        }
    }
}
