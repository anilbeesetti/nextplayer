package dev.anilbeesetti.nextplayer.feature.videopicker.screens.foldervideo

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.videopicker.MediaState
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaContent
import java.io.File

@Composable
fun FolderVideoPickerScreen(
    viewModel: FolderVideoPickerViewModel = hiltViewModel(),
    onVideoItemClick: (uri: Uri) -> Unit,
    onNavigateUp: () -> Unit
) {
    // The app experiences jank when videosState updates before the initial render finishes.
    // By adding Lifecycle.State.RESUMED, we ensure that we wait until the first render completes.
    val videosState by viewModel.videoItems.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.RESUMED
    )

    FolderVideoPickerScreen(
        folderPath = viewModel.folderPath,
        videosState = videosState,
        onVideoItemClick = onVideoItemClick,
        onNavigateUp = onNavigateUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderVideoPickerScreen(
    folderPath: String,
    videosState: MediaState,
    onVideoItemClick: (uri: Uri) -> Unit,
    onNavigateUp: () -> Unit
) {
    Scaffold(
        topBar = {
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
        }
    ) { paddingValues ->
        Box(
            modifier = androidx.compose.ui.Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            MediaContent(
                state = videosState,
                onMediaClick = { onVideoItemClick(Uri.parse(it)) }
            )
        }
    }
}
