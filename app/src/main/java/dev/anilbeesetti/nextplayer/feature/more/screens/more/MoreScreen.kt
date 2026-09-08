package dev.anilbeesetti.nextplayer.feature.more.screens.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.components.BindTopLevelFab
import dev.anilbeesetti.nextplayer.core.ui.components.LocalNavigationBottomPadding
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabKey
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoGridItem

@Composable
fun MoreScreen(
    onHistoryClick: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onTrashClick: () -> Unit,
    onVaultClick: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val output = remember(onHistoryClick, onPlayVideo, onSettingsClick, onTrashClick, onVaultClick) {
        MoreViewModel.Output(
            openHistory = onHistoryClick,
            playVideo = onPlayVideo,
            openSettings = onSettingsClick,
            openTrash = onTrashClick,
            openVault = onVaultClick,
        )
    }

    MoreScreenContent(
        uiState = uiState,
        onAction = { viewModel.onAction(it, output) },
    )
}

@Composable
internal fun MoreScreenContent(
    uiState: MoreUiState,
    onAction: (MoreAction) -> Unit,
) {
    BindTopLevelFab(TopLevelFabKey.MORE, NextIcons.Settings) { onAction(MoreAction.OpenSettings) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onAction(MoreAction.PlayVideo(it.toString())) }
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(R.string.more),
                fontWeight = FontWeight.Bold,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding.copy(bottom = 0.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
                    .padding(top = 8.dp, bottom = scaffoldPadding.calculateBottomPadding() + LocalNavigationBottomPadding.current + 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(MoreAction.OpenVault) },
                    ) {
                        Icon(
                            imageVector = NextIcons.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(R.string.vault))
                    }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = { filePicker.launch("video/*") },
                    ) {
                        Icon(
                            imageVector = NextIcons.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(R.string.pick_file))
                    }
                    if (MediaOperationsService.supportsTrash()) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onAction(MoreAction.OpenTrash) },
                        ) {
                            Icon(
                                imageVector = NextIcons.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = stringResource(R.string.trash))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                HistorySection(
                    history = uiState.history.result.orEmpty().take(10),
                    preferences = uiState.preferences,
                    onMoreClick = { onAction(MoreAction.OpenHistory) },
                    onVideoClick = { onAction(MoreAction.PlayVideo(it.uriString)) },
                )
            }
        }
    }
}

@Composable
private fun HistorySection(
    history: List<Video>,
    preferences: ApplicationPreferences,
    onMoreClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
) {
    if (history.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            IconButton(onClick = onMoreClick) {
                Icon(imageVector = NextIcons.ArrowForward, contentDescription = stringResource(R.string.history))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items(history, key = { it.uriString }) { video ->
                VideoGridItem(
                    video = video,
                    isRecentlyPlayedVideo = false,
                    textStyle = MaterialTheme.typography.bodySmall,
                    preferences = preferences,
                    modifier = Modifier.width(140.dp),
                    onClick = { onVideoClick(video) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun MoreScreenContentPreview() {
    MoreScreenContent(uiState = MoreUiState(), onAction = {})
}
