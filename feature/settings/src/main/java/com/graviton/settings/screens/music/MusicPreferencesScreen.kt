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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * Music settings, grouped as PLAYBACK / APPEARANCE / CONTROLS / FEATURES / AUDIO.
 *
 * Every switch here writes a preference that is actually read somewhere: nothing is exposed that
 * the player or library would ignore.
 */
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
                    FilledTonalIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.tvFocusDown(listFocusRequester),
                    ) {
                        Icon(NextIcons.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .tvListFocus(listFocusRequester)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            ListSectionTitle(text = stringResource(R.string.playback))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                PreferenceSwitch(
                    title = stringResource(R.string.remember_shuffle),
                    description = stringResource(R.string.remember_shuffle_desc),
                    icon = NextIcons.Shuffle,
                    isChecked = preferences.musicRememberShuffle,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleRememberShuffle) },
                    isFirstItem = true,
                )
                ClickablePreferenceItem(
                    title = stringResource(R.string.clear_music_history),
                    description = stringResource(R.string.clear_music_history_desc),
                    icon = NextIcons.History,
                    onClick = { onEvent(MusicPreferencesEvent.ClearHistory) },
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(R.string.music_section_appearance))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ClickablePreferenceItem(
                    title = stringResource(R.string.now_playing_style),
                    description = preferences.musicNowPlayingStyle.displayName(),
                    icon = NextIcons.Audio,
                    onClick = { styleDialog = true },
                    isFirstItem = true,
                )
                ClickablePreferenceItem(
                    title = stringResource(R.string.background_style),
                    description = preferences.musicBackgroundStyle.displayName(),
                    icon = NextIcons.Image,
                    onClick = { backgroundDialog = true },
                )
                MusicValueSlider(
                    title = stringResource(R.string.artwork_corners),
                    valueLabel = stringResource(
                        R.string.value_dp,
                        preferences.musicArtworkCornerRadius.toInt(),
                    ),
                    value = preferences.musicArtworkCornerRadius,
                    range = 0f..48f,
                ) { onEvent(MusicPreferencesEvent.SetArtworkRadius(it)) }
                MusicValueSlider(
                    title = stringResource(R.string.artwork_size),
                    valueLabel = stringResource(R.string.value_percent, preferences.musicArtworkSizePercent),
                    value = preferences.musicArtworkSizePercent.toFloat(),
                    range = 70f..100f,
                ) { onEvent(MusicPreferencesEvent.SetArtworkSize(it.toInt())) }
                PreferenceSwitch(
                    title = stringResource(R.string.dynamic_artwork_background),
                    description = stringResource(R.string.dynamic_artwork_background_desc),
                    icon = NextIcons.Image,
                    isChecked = preferences.musicDynamicArtworkBackground,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleDynamicBackground) },
                )
                // The blur slider only affects the blurred-artwork background, so it is hidden
                // rather than shown as a control that does nothing.
                if (preferences.musicBackgroundStyle == MusicBackgroundStyle.BLURRED_ARTWORK) {
                    MusicValueSlider(
                        title = stringResource(R.string.blur_intensity),
                        valueLabel = stringResource(
                            R.string.value_dp,
                            preferences.musicBlurIntensity.toInt(),
                        ),
                        value = preferences.musicBlurIntensity,
                        range = 0f..48f,
                    ) { onEvent(MusicPreferencesEvent.SetBlur(it)) }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.show_metadata),
                    description = stringResource(R.string.show_metadata_desc),
                    icon = NextIcons.Info,
                    isChecked = preferences.musicShowMetadata,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleMetadata) },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.show_next_track),
                    description = stringResource(R.string.show_next_track_desc),
                    icon = NextIcons.PlayNext,
                    isChecked = preferences.musicShowNextTrack,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleNextTrack) },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.show_codec_info),
                    description = stringResource(R.string.show_codec_info_desc),
                    icon = NextIcons.Info,
                    isChecked = preferences.musicShowCodecInfo,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleCodec) },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.music_animations),
                    description = stringResource(R.string.music_animations_desc),
                    icon = NextIcons.Play,
                    isChecked = preferences.musicAnimationsEnabled,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleAnimations) },
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(R.string.music_section_controls))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                PreferenceSwitch(
                    title = stringResource(R.string.artwork_gestures),
                    description = stringResource(R.string.artwork_gestures_desc),
                    icon = NextIcons.SwipeHorizontal,
                    isChecked = preferences.musicGestureControls,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleGestures) },
                    isFirstItem = true,
                )
                if (preferences.musicGestureControls) {
                    MusicValueSlider(
                        title = stringResource(R.string.seek_sensitivity),
                        valueLabel = "%.1f×".format(preferences.musicSeekGestureSensitivity),
                        value = preferences.musicSeekGestureSensitivity,
                        range = 0.5f..2f,
                    ) { onEvent(MusicPreferencesEvent.SetSeekSensitivity(it)) }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.show_lyrics_button),
                    icon = NextIcons.Lyrics,
                    isChecked = preferences.musicShowLyricsButton,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleLyricsButton) },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.show_queue_button),
                    icon = NextIcons.QueueMusic,
                    isChecked = preferences.musicShowQueueButton,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleQueueButton) },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.show_sleep_timer_button),
                    icon = NextIcons.Timer,
                    isChecked = preferences.musicShowSleepTimerButton,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleSleepButton) },
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(R.string.music_section_features))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                PreferenceSwitch(
                    title = stringResource(R.string.show_lyrics),
                    description = stringResource(R.string.show_lyrics_desc),
                    icon = NextIcons.Lyrics,
                    isChecked = preferences.musicShowLyrics,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleShowLyrics) },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(R.string.music_section_audio))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                PreferenceSwitch(
                    title = stringResource(R.string.replay_gain),
                    description = stringResource(R.string.replay_gain_desc),
                    icon = NextIcons.Audio,
                    isChecked = preferences.musicReplayGainEnabled,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleReplayGain) },
                    isFirstItem = true,
                )
                if (preferences.musicReplayGainEnabled) {
                    MusicValueSlider(
                        title = stringResource(R.string.replay_gain_preamp),
                        valueLabel = stringResource(
                            R.string.value_db,
                            "%.1f".format(preferences.musicReplayGainPreampDb),
                        ),
                        value = preferences.musicReplayGainPreampDb,
                        range = -15f..15f,
                    ) { onEvent(MusicPreferencesEvent.SetReplayGainPreamp(it)) }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.equalizer),
                    description = stringResource(R.string.equalizer_bands_desc),
                    icon = NextIcons.Equalizer,
                    isChecked = preferences.musicEqualizerEnabled,
                    onClick = { onEvent(MusicPreferencesEvent.ToggleEqualizer) },
                    isLastItem = !preferences.musicEqualizerEnabled,
                )
                if (preferences.musicEqualizerEnabled) {
                    EqualizerFrequencies.forEachIndexed { index, frequency ->
                        val gain = preferences.musicEqualizerGainsDb.getOrElse(index) { 0f }
                        MusicValueSlider(
                            title = formatFrequency(frequency),
                            valueLabel = stringResource(R.string.value_db, "%.1f".format(gain)),
                            value = gain,
                            range = -15f..15f,
                        ) { onEvent(MusicPreferencesEvent.SetEqualizerBand(index, it)) }
                    }
                    ClickablePreferenceItem(
                        title = stringResource(R.string.reset_equalizer),
                        description = stringResource(R.string.reset_equalizer_desc),
                        icon = NextIcons.History,
                        onClick = { onEvent(MusicPreferencesEvent.ResetEqualizer) },
                        isLastItem = true,
                    )
                }
            }
        }
    }

    if (styleDialog) {
        OptionsDialog(
            text = stringResource(R.string.now_playing_style),
            onDismissClick = { styleDialog = false },
        ) {
            items(NowPlayingStyle.entries) { style ->
                RadioTextButton(
                    text = style.displayName(),
                    selected = style == preferences.musicNowPlayingStyle,
                    onClick = {
                        onEvent(MusicPreferencesEvent.SetStyle(style))
                        styleDialog = false
                    },
                )
            }
        }
    }
    if (backgroundDialog) {
        OptionsDialog(
            text = stringResource(R.string.background_style),
            onDismissClick = { backgroundDialog = false },
        ) {
            items(MusicBackgroundStyle.entries) { style ->
                RadioTextButton(
                    text = style.displayName(),
                    selected = style == preferences.musicBackgroundStyle,
                    onClick = {
                        onEvent(MusicPreferencesEvent.SetBackground(style))
                        backgroundDialog = false
                    },
                )
            }
        }
    }
}

/**
 * Title, current value and slider as a single preference row.
 *
 * The value label duplicates what the slider announces, so it is hidden from TalkBack to avoid
 * reading the same number twice.
 */
@Composable
private fun MusicValueSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Slider(
                value = value,
                onValueChange = onValue,
                valueRange = range,
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
    }
}

private val EqualizerFrequencies = intArrayOf(
    25, 40, 63, 100, 160, 250, 400, 630, 1_000, 1_600, 2_500, 4_000, 6_300, 10_000, 16_000,
)

@Composable
private fun formatFrequency(value: Int): String = if (value >= 1_000) {
    stringResource(R.string.frequency_khz, "%.1f".format(value / 1_000f))
} else {
    stringResource(R.string.frequency_hz, value)
}

private fun NowPlayingStyle.displayName() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun MusicBackgroundStyle.displayName() =
    name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
