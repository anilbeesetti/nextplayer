package dev.anilbeesetti.nextplayer.feature.more.screens.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.CenterCircularProgressBar
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoListItem
import dev.anilbeesetti.nextplayer.feature.videopicker.state.rememberSelectionManager

@Composable
fun TrashScreen(
    viewModel: TrashViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TrashScreenContent(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
internal fun TrashScreenContent(
    state: TrashUiState,
    onAction: (TrashAction) -> Unit,
) {
    val selectionManager = rememberSelectionManager()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = if (selectionManager.isInSelectionMode) {
                    stringResource(R.string.m_n_selected, selectionManager.selectionItems.size, state.videos.result.orEmpty().size)
                } else {
                    stringResource(R.string.trash)
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = {
                            if (selectionManager.isInSelectionMode) selectionManager.exitSelectionMode() else onAction(TrashAction.NavigateUp)
                        },
                        modifier = Modifier.tvFocusRing(),
                    ) {
                        Icon(
                            imageVector = if (selectionManager.isInSelectionMode) NextIcons.Close else NextIcons.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    if (selectionManager.isInSelectionMode && selectionManager.selectionItems.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                onAction(TrashAction.Restore(selectionManager.selectionItems))
                                selectionManager.exitSelectionMode()
                            },
                            modifier = Modifier.tvFocusRing(),
                        ) { Text(stringResource(R.string.restore)) }
                        TextButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.tvFocusRing(),
                        ) { Text(stringResource(R.string.delete)) }
                    }
                },
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
            when (val videos = state.videos) {
                DataState.Loading -> CenterCircularProgressBar()
                is DataState.Error -> Text(
                    text = videos.value.message.orEmpty(),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                is DataState.Success -> if (videos.value.isEmpty()) {
                    TrashEmptyState()
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(videos.value, key = { _, video -> video.uriString }) { index, video ->
                            VideoListItem(
                                video = video,
                                isRecentlyPlayedVideo = false,
                                preferences = state.preferences,
                                selected = selectionManager.isVideoSelected(video),
                                isFirstItem = index == 0,
                                isLastItem = index == videos.value.lastIndex,
                                onClick = {
                                    if (selectionManager.isInSelectionMode) {
                                        selectionManager.toggleVideoSelection(video)
                                    } else {
                                        onAction(TrashAction.PlayVideo(video.uriString))
                                    }
                                },
                                onLongClick = { selectionManager.toggleVideoSelection(video) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        NextDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_permanently)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(TrashAction.DeletePermanently(selectionManager.selectionItems))
                        selectionManager.exitSelectionMode()
                        showDeleteConfirmation = false
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text(stringResource(R.string.cancel)) }
            },
            content = { Text(stringResource(R.string.delete_items_info)) },
        )
    }
}

@Composable
private fun TrashEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NextIcons.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.trash_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.trash_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
