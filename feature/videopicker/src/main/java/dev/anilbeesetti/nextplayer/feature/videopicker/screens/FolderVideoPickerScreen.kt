package dev.anilbeesetti.nextplayer.feature.videopicker.screens

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderVideoPickerScreen(
    viewModel: FolderVideoPickerViewModel = hiltViewModel(),
    onVideoItemClick: (uri: Uri) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val videosState by viewModel.videoItems.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = viewModel.folderPath,
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
            VideosContent(videosState = videosState, onVideoItemClick = onVideoItemClick)
        }
    }
}

