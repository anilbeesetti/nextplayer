package com.graviton.settings.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.graviton.core.ui.R
import com.graviton.core.ui.components.ClickablePreferenceItem
import com.graviton.core.ui.components.ListSectionTitle
import com.graviton.core.ui.components.NextTopAppBar
import com.graviton.core.ui.components.PreferenceSwitch
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.settings.utils.rememberTvListFocusRequester
import com.graviton.settings.utils.tvFocusDown
import com.graviton.settings.utils.tvListFocus

@Composable
fun MusicPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: MusicPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MusicPreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MusicPreferencesContent(
    uiState: MusicPreferencesUiState,
    onEvent: (MusicPreferencesEvent) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val listFocusRequester = rememberTvListFocusRequester()
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.music_settings),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp, modifier = Modifier.tvFocusDown(listFocusRequester)) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .tvListFocus(listFocusRequester)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            ListSectionTitle(text = stringResource(id = R.string.playback))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                PreferenceSwitch(
                    title = stringResource(R.string.show_lyrics),
                    description = stringResource(R.string.show_lyrics_desc),
                    icon = NextIcons.Lyrics,
                    isChecked = uiState.preferences.musicShowLyrics,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleShowLyrics) },
                    isFirstItem = true,
                )
                PreferenceSwitch(
                    title = stringResource(R.string.remember_shuffle),
                    description = stringResource(R.string.remember_shuffle_desc),
                    icon = NextIcons.Shuffle,
                    isChecked = uiState.preferences.musicRememberShuffle,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleRememberShuffle) },
                    isLastItem = true,
                )
            }
            ListSectionTitle(text = stringResource(id = R.string.recently_played))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ClickablePreferenceItem(
                    title = stringResource(R.string.clear_music_history),
                    description = stringResource(R.string.clear_music_history_desc),
                    icon = NextIcons.History,
                    onClick = { onEvent(MusicPreferencesEvent.ClearHistory) },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }
        }
    }
}
