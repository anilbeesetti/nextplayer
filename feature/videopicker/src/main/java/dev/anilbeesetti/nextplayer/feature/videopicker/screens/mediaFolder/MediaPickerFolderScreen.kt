package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediaFolder

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.remotesubs.service.Subtitle
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideosView
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState

@Composable
fun MediaPickerFolderRoute(
    viewModel: MediaPickerFolderViewModel = hiltViewModel(),
    onVideoClick: (uri: Uri) -> Unit,
    onNavigateUp: () -> Unit,
) {
    // The app experiences jank when videosState updates before the initial render finishes.
    // By adding Lifecycle.State.RESUMED, we ensure that we wait until the first render completes.
    val videosState by viewModel.videos.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MediaPickerFolderScreen(
        folderName = viewModel.folderName,
        videosState = videosState,
        preferences = preferences,
        uiState = uiState,
        onPlayVideo = onVideoClick,
        onNavigateUp = onNavigateUp,
        onDeleteVideoClick = { viewModel.deleteVideos(listOf(it)) },
        onAddToSync = viewModel::addToMediaInfoSynchronizer,
        onRenameVideoClick = viewModel::renameVideo,
        onGetSubtitlesOnline = viewModel::getSubtitlesOnline,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerFolderScreen(
    folderName: String,
    videosState: VideosState,
    preferences: ApplicationPreferences,
    uiState: MediaPicketFolderUiState,
    onNavigateUp: () -> Unit,
    onPlayVideo: (Uri) -> Unit,
    onDeleteVideoClick: (String) -> Unit,
    onRenameVideoClick: (Uri, String) -> Unit = { _, _ -> },
    onAddToSync: (Uri) -> Unit,
    onGetSubtitlesOnline: (Video) -> Unit,
) {
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
                .padding(it),
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
        }
    }

    uiState.dialog?.let { dialog ->
        when (dialog) {
            is MediaPickerFolderScreenDialog.LoadingDialog -> {
                NextDialog(
                    onDismissRequest = {},
                    title = {},
                    content = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Text(
                                text = stringResource(id = dialog.messageRes ?: R.string.loading),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    },
                    confirmButton = {},
                )
            }

            is MediaPickerFolderScreenDialog.ErrorDialog -> {
                NextDialog(
                    onDismissRequest = {},
                    title = {
                        Text(text = stringResource(id = R.string.error))
                    },
                    content = {
                        Text(
                            text = dialog.message ?: stringResource(id = R.string.unknown_error_try_again),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = dialog.onDismiss) {
                            Text(text = stringResource(id = R.string.ok))
                        }
                    },
                )
            }

            is MediaPickerFolderScreenDialog.GetSubtitlesOnlineDialog -> {
                GetSubtitlesOnlineDialogComponent(
                    video = dialog.video,
                    onDismissRequest = dialog.onDismiss,
                    onConfirm = dialog.onConfirm,
                )
            }

            is MediaPickerFolderScreenDialog.SubtitleResultsDialog -> {
                SubtitleResultDialogComponent(
                    data = dialog.results,
                    onDismissRequest = dialog.onDismiss,
                    onSubtitleSelected = dialog.onSubtitleSelected,
                )
            }
        }
    }
}

@Composable
fun GetSubtitlesOnlineDialogComponent(
    modifier: Modifier = Modifier,
    video: Video,
    onDismissRequest: () -> Unit,
    onConfirm: (searchText: String?, language: String) -> Unit,
) {
    var searchText by remember { mutableStateOf(video.displayName) }
    var language by remember { mutableStateOf("en") }

    NextDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Get subtitles online",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        content = {
            Column {
                Text(text = "Search subtitle from opensubtitles.com")
                Text(text = "Language: English")
                Spacer(modifier = Modifier.size(16.dp))
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(text = "Search") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(searchText, language) }) {
                Text(text = stringResource(id = R.string.okay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
    )
}

@Composable
fun SubtitleResultDialogComponent(
    modifier: Modifier = Modifier,
    data: List<Subtitle>,
    onDismissRequest: () -> Unit,
    onSubtitleSelected: (Subtitle) -> Unit,
) {
    var selectedData: Subtitle? by remember { mutableStateOf(null) }

    NextDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Subtitles", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        content = {
            LazyColumn(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(data) { subtitle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = selectedData == subtitle,
                                onValueChange = { selectedData = subtitle },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RadioButton(
                            selected = selectedData == subtitle,
                            onClick = null,
                        )
                        Column {
                            Text(
                                text = subtitle.name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = buildString {
                                    append(subtitle.languageName)
                                    if (subtitle.rating != null) {
                                        append(", ${subtitle.rating}")
                                    }
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedData?.let { onSubtitleSelected(it) } },
                enabled = selectedData != null,
            ) {
                Text(text = stringResource(id = R.string.download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
    )
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
            uiState = MediaPicketFolderUiState(
                dialog = MediaPickerFolderScreenDialog.GetSubtitlesOnlineDialog(
                    video = Video.sample,
                    onDismiss = {},
                    onConfirm = { _, _ -> },
                ),
            ),
            onNavigateUp = {},
            onPlayVideo = {},
            onDeleteVideoClick = {},
            onAddToSync = {},
            onGetSubtitlesOnline = {},
        )
    }
}
