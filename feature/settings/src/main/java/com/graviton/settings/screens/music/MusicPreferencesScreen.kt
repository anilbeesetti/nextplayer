package com.graviton.settings.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.graviton.core.model.MusicBackgroundStyle
import com.graviton.core.model.NowPlayingStyle
import com.graviton.core.ui.R
import com.graviton.core.ui.components.ClickablePreferenceItem
import com.graviton.core.ui.components.ListSectionTitle
import com.graviton.core.ui.components.NextTopAppBar
import com.graviton.core.ui.components.PreferenceSwitch
import com.graviton.core.ui.components.RadioTextButton
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.settings.composables.OptionsDialog
import com.graviton.settings.utils.rememberTvListFocusRequester
import com.graviton.settings.utils.tvFocusDown
import com.graviton.settings.utils.tvListFocus

@Composable
fun MusicPreferencesScreen(onNavigateUp: () -> Unit, viewModel: MusicPreferencesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MusicPreferencesContent(uiState, viewModel::onEvent, onNavigateUp)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MusicPreferencesContent(
    uiState: MusicPreferencesUiState,
    onEvent: (MusicPreferencesEvent) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val listFocusRequester = rememberTvListFocusRequester()
    var styleDialog by remember { mutableStateOf(false) }
    var backgroundDialog by remember { mutableStateOf(false) }
    val preferences = uiState.preferences
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(R.string.music_settings),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp, modifier = Modifier.tvFocusDown(listFocusRequester)) {
                        Icon(NextIcons.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tvListFocus(listFocusRequester)
                .padding(innerPadding).padding(horizontal = 16.dp),
        ) {
            ListSectionTitle("Now Playing")
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ClickablePreferenceItem(
                    title = "Now Playing style",
                    description = preferences.musicNowPlayingStyle.displayName(),
                    icon = NextIcons.Audio,
                    onClick = { styleDialog = true },
                    isFirstItem = true,
                )
                ClickablePreferenceItem(
                    title = "Background",
                    description = preferences.musicBackgroundStyle.displayName(),
                    icon = NextIcons.Image,
                    onClick = { backgroundDialog = true },
                )
                MusicValueSlider("Artwork corners", "${preferences.musicArtworkCornerRadius.toInt()} dp", preferences.musicArtworkCornerRadius, 0f..48f) {
                    onEvent(MusicPreferencesEvent.SetArtworkRadius(it))
                }
                MusicValueSlider("Artwork size", "${preferences.musicArtworkSizePercent}%", preferences.musicArtworkSizePercent.toFloat(), 70f..100f) {
                    onEvent(MusicPreferencesEvent.SetArtworkSize(it.toInt()))
                }
                MusicValueSlider("Blur intensity", "${preferences.musicBlurIntensity.toInt()} dp", preferences.musicBlurIntensity, 0f..48f) {
                    onEvent(MusicPreferencesEvent.SetBlur(it))
                }
                PreferenceSwitch(title = "Dynamic artwork background", description = "Derive player visuals from artwork", icon = NextIcons.Image, isChecked = preferences.musicDynamicArtworkBackground, onClick = { onEvent(MusicPreferencesEvent.ToggleDynamicBackground) })
                PreferenceSwitch(title = "Show metadata", description = "Artist and album", icon = NextIcons.Info, isChecked = preferences.musicShowMetadata, onClick = { onEvent(MusicPreferencesEvent.ToggleMetadata) })
                PreferenceSwitch(title = "Show codec information", description = "Only when a real format is available", icon = NextIcons.Info, isChecked = preferences.musicShowCodecInfo, onClick = { onEvent(MusicPreferencesEvent.ToggleCodec) })
                PreferenceSwitch(title = "Player animations", description = "Animated artwork and state transitions", icon = NextIcons.Play, isChecked = preferences.musicAnimationsEnabled, onClick = { onEvent(MusicPreferencesEvent.ToggleAnimations) }, isLastItem = true)
            }

            ListSectionTitle("Controls and gestures")
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                PreferenceSwitch(title = "Artwork gestures", description = "Swipe for previous or next", icon = NextIcons.SwipeHorizontal, isChecked = preferences.musicGestureControls, onClick = { onEvent(MusicPreferencesEvent.ToggleGestures) }, isFirstItem = true)
                PreferenceSwitch(title = "Lyrics button", icon = NextIcons.Lyrics, isChecked = preferences.musicShowLyricsButton, onClick = { onEvent(MusicPreferencesEvent.ToggleLyricsButton) })
                PreferenceSwitch(title = "Queue button", icon = NextIcons.QueueMusic, isChecked = preferences.musicShowQueueButton, onClick = { onEvent(MusicPreferencesEvent.ToggleQueueButton) })
                PreferenceSwitch(title = "Sleep timer button", icon = NextIcons.Timer, isChecked = preferences.musicShowSleepTimerButton, onClick = { onEvent(MusicPreferencesEvent.ToggleSleepButton) }, isLastItem = true)
            }

            ListSectionTitle(stringResource(R.string.playback))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                PreferenceSwitch(title = stringResource(R.string.show_lyrics), description = stringResource(R.string.show_lyrics_desc), icon = NextIcons.Lyrics, isChecked = preferences.musicShowLyrics, onClick = { onEvent(MusicPreferencesEvent.ToggleShowLyrics) }, isFirstItem = true)
                PreferenceSwitch(title = "Gapless playback", description = "Keep one player and pre-buffer the queue", icon = NextIcons.Audio, isChecked = preferences.musicGaplessPlayback, onClick = { onEvent(MusicPreferencesEvent.ToggleGapless) })
                PreferenceSwitch(title = stringResource(R.string.remember_shuffle), description = stringResource(R.string.remember_shuffle_desc), icon = NextIcons.Shuffle, isChecked = preferences.musicRememberShuffle, onClick = { onEvent(MusicPreferencesEvent.ToggleRememberShuffle) }, isLastItem = true)
            }
            ListSectionTitle(stringResource(R.string.recently_played))
            ClickablePreferenceItem(title = stringResource(R.string.clear_music_history), description = stringResource(R.string.clear_music_history_desc), icon = NextIcons.History, onClick = { onEvent(MusicPreferencesEvent.ClearHistory) }, isFirstItem = true, isLastItem = true)
        }
    }

    if (styleDialog) OptionsDialog("Now Playing style", { styleDialog = false }) {
        items(NowPlayingStyle.entries) { style ->
            RadioTextButton(style.displayName(), style == preferences.musicNowPlayingStyle) {
                onEvent(MusicPreferencesEvent.SetStyle(style)); styleDialog = false
            }
        }
    }
    if (backgroundDialog) OptionsDialog("Player background", { backgroundDialog = false }) {
        items(MusicBackgroundStyle.entries) { style ->
            RadioTextButton(style.displayName(), style == preferences.musicBackgroundStyle) {
                onEvent(MusicPreferencesEvent.SetBackground(style)); backgroundDialog = false
            }
        }
    }
}

@Composable
private fun MusicValueSlider(title: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(valueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value, onValue, valueRange = range)
        }
    }
}

private fun NowPlayingStyle.displayName() = name.lowercase().replaceFirstChar(Char::uppercase)
private fun MusicBackgroundStyle.displayName() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
