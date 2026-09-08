package dev.anilbeesetti.nextplayer.feature.more.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

@Composable
fun HistoryScreen(
    onNavigateUp: () -> Unit,
    onPlayVideo: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    HistoryScreenContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onPlayVideo = onPlayVideo,
        onClearHistory = viewModel::clearHistory,
    )
}

@Composable
internal fun HistoryScreenContent(
    uiState: HistoryUiState,
    onNavigateUp: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    var showClearConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(R.string.history),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.tvFocusRing(),
                    ) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    TextButton(
                        enabled = uiState.history.result.orEmpty().isNotEmpty(),
                        onClick = { showClearConfirmation = true },
                        modifier = Modifier.tvFocusRing(),
                    ) {
                        Text(stringResource(R.string.clear_all))
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
            when (val history = uiState.history) {
                is DataState.Loading -> CenterCircularProgressBar()
                is DataState.Error -> Text(
                    text = history.value.message.orEmpty(),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                is DataState.Success -> LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(history.value, key = { _, video -> video.uriString }) { index, video ->
                        VideoListItem(
                            video = video,
                            isRecentlyPlayedVideo = false,
                            preferences = uiState.preferences,
                            isFirstItem = index == 0,
                            isLastItem = index == history.value.lastIndex,
                            onClick = { onPlayVideo(video.uriString) },
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        NextDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.clear_history)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearConfirmation = false
                    },
                ) {
                    Text(stringResource(R.string.clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            content = { Text(stringResource(R.string.clear_history_confirmation)) },
        )
    }
}
