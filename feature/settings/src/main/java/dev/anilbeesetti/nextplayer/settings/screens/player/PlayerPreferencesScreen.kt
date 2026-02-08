package dev.anilbeesetti.nextplayer.settings.screens.player

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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.round
import dev.anilbeesetti.nextplayer.core.model.ControlButtonsPosition
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.ListSectionTitle
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialogWithDoneAndCancelButtons
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSlider
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitch
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitchWithDivider
import dev.anilbeesetti.nextplayer.core.ui.components.RadioTextButton
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.settings.composables.OptionsDialog
import dev.anilbeesetti.nextplayer.settings.extensions.name

@Composable
fun PlayerPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: PlayerPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlayerPreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerPreferencesContent(
    uiState: PlayerPreferencesUiState,
    onEvent: (PlayerPreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.player_name),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
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
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            ListSectionTitle(text = stringResource(id = R.string.gestures))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    title = stringResource(id = R.string.seek_gesture),
                    description = stringResource(id = R.string.seek_gesture_description),
                    icon = NextIcons.SwipeHorizontal,
                    isChecked = uiState.preferences.useSeekControls,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleUseSeekControls) },
                    isFirstItem = true
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.brightness_gesture),
                    description = stringResource(id = R.string.brightness_gesture_description),
                    icon = NextIcons.SwipeVertical,
                    isChecked = uiState.preferences.enableBrightnessSwipeGesture,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleEnableBrightnessSwipeGesture) },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.volume_gesture),
                    description = stringResource(id = R.string.volume_gesture_description),
                    icon = NextIcons.SwipeVertical,
                    isChecked = uiState.preferences.enableVolumeSwipeGesture,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleEnableVolumeSwipeGesture) },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.zoom_gesture),
                    description = stringResource(id = R.string.zoom_gesture_description),
                    icon = NextIcons.Pinch,
                    isChecked = uiState.preferences.useZoomControls,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleUseZoomControls) },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.pan_gesture),
                    description = stringResource(id = R.string.pan_gesture_description),
                    icon = NextIcons.Pan,
                    enabled = uiState.preferences.useZoomControls,
                    isChecked = uiState.preferences.enablePanGesture,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleEnablePanGesture) },
                )
                PreferenceSwitchWithDivider(
                    title = stringResource(id = R.string.double_tap),
                    description = stringResource(id = R.string.double_tap_description),
                    icon = NextIcons.DoubleTap,
                    isChecked = (uiState.preferences.doubleTapGesture != DoubleTapGesture.NONE),
                    onChecked = { onEvent(PlayerPreferencesUiEvent.ToggleDoubleTapGesture) },
                    onClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.DoubleTapDialog)) },
                )
                PreferenceSwitchWithDivider(
                    title = stringResource(id = R.string.long_press_gesture),
                    description = stringResource(id = R.string.long_press_gesture_desc, uiState.preferences.longPressControlsSpeed),
                    icon = NextIcons.Tap,
                    isChecked = uiState.preferences.useLongPressControls,
                    onChecked = { onEvent(PlayerPreferencesUiEvent.ToggleUseLongPressControls) },
                    onClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.LongPressControlsSpeedDialog)) },
                )
                PreferenceSlider(
                    title = stringResource(R.string.seek_increment),
                    description = stringResource(R.string.seconds, uiState.preferences.seekIncrement),
                    icon = NextIcons.Replay,
                    value = uiState.preferences.seekIncrement.toFloat(),
                    valueRange = 1.0f..60.0f,
                    onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateSeekIncrement(it.toInt())) },
                    trailingContent = {
                        FilledIconButton(onClick = { onEvent(PlayerPreferencesUiEvent.UpdateSeekIncrement(PlayerPreferences.DEFAULT_SEEK_INCREMENT)) }) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_seek_increment),
                            )
                        }
                    },
                )
                PreferenceSlider(
                    title = stringResource(R.string.seek_sensitivity),
                    description = uiState.preferences.seekSensitivity.toString(),
                    icon = NextIcons.FastForward,
                    value = uiState.preferences.seekSensitivity,
                    valueRange = 0.1f..2.0f,
                    onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateSeekSensitivity(it)) },
                    isLastItem = true,
                    trailingContent = {
                        FilledIconButton(onClick = { onEvent(PlayerPreferencesUiEvent.UpdateSeekSensitivity(PlayerPreferences.DEFAULT_SEEK_SENSITIVITY)) }) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_seek_sentivity),
                            )
                        }
                    },
                )
            }
            ListSectionTitle(text = stringResource(id = R.string.interface_name))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSlider(
                    title = stringResource(R.string.controller_timeout),
                    description = stringResource(R.string.seconds, uiState.preferences.controllerAutoHideTimeout),
                    icon = NextIcons.Timer,
                    value = uiState.preferences.controllerAutoHideTimeout.toFloat(),
                    valueRange = 1.0f..60.0f,
                    onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateControlAutoHideTimeout(it.toInt())) },
                    isFirstItem = true,
                    trailingContent = {
                        FilledIconButton(onClick = { onEvent(PlayerPreferencesUiEvent.UpdateControlAutoHideTimeout(PlayerPreferences.DEFAULT_CONTROLLER_AUTO_HIDE_TIMEOUT)) }) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_controller_timeout),
                            )
                        }
                    },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.hide_player_buttons_background),
                    description = stringResource(id = R.string.hide_player_buttons_background_description),
                    icon = NextIcons.HideSource,
                    isChecked = uiState.preferences.hidePlayerButtonsBackground,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleHidePlayerButtonsBackground) },
                    isLastItem = true
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.playback))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.resume),
                    description = stringResource(id = R.string.resume_description),
                    icon = NextIcons.Resume,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.ResumeDialog)) },
                    isFirstItem = true,
                )
                PreferenceSlider(
                    title = stringResource(id = R.string.default_playback_speed),
                    description = uiState.preferences.defaultPlaybackSpeed.toString(),
                    icon = NextIcons.Speed,
                    value = uiState.preferences.defaultPlaybackSpeed,
                    valueRange = 0.2f..4.0f,
                    onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed(it)) },
                    trailingContent = {
                        FilledIconButton(onClick = { onEvent(PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed(1f)) }) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_default_playback_speed),
                            )
                        }
                    },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.autoplay_settings),
                    description = stringResource(
                        id = R.string.autoplay_settings_description,
                    ),
                    icon = NextIcons.Player,
                    isChecked = uiState.preferences.autoplay,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleAutoplay) },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.pip_settings),
                    description = stringResource(
                        id = R.string.pip_settings_description,
                    ),
                    icon = NextIcons.Pip,
                    isChecked = uiState.preferences.autoPip,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleAutoPip) },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.background_play),
                    description = stringResource(
                        id = R.string.background_play_description,
                    ),
                    icon = NextIcons.Headset,
                    isChecked = uiState.preferences.autoBackgroundPlay,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleAutoBackgroundPlay) },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.remember_brightness_level),
                    description = stringResource(
                        id = R.string.remember_brightness_level_description,
                    ),
                    icon = NextIcons.Brightness,
                    isChecked = uiState.preferences.rememberPlayerBrightness,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleRememberBrightnessLevel) },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.remember_selections),
                    description = stringResource(id = R.string.remember_selections_description),
                    icon = NextIcons.Selection,
                    isChecked = uiState.preferences.rememberSelections,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleRememberSelections) },
                )
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.player_screen_orientation),
                    description = uiState.preferences.playerScreenOrientation.name(),
                    icon = NextIcons.Rotation,
                    onClick = {
                        onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.PlayerScreenOrientationDialog))
                    },
                    isLastItem = true
                )
            }
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                PlayerPreferenceDialog.ResumeDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.resume),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(Resume.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = (it == uiState.preferences.resume),
                                onClick = {
                                    onEvent(PlayerPreferencesUiEvent.UpdatePlaybackResume(it))
                                    onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                PlayerPreferenceDialog.DoubleTapDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.double_tap),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(DoubleTapGesture.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = (it == uiState.preferences.doubleTapGesture),
                                onClick = {
                                    onEvent(PlayerPreferencesUiEvent.UpdateDoubleTapGesture(it))
                                    onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                PlayerPreferenceDialog.PlayerScreenOrientationDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.player_screen_orientation),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(ScreenOrientation.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = it == uiState.preferences.playerScreenOrientation,
                                onClick = {
                                    onEvent(PlayerPreferencesUiEvent.UpdatePreferredPlayerOrientation(it))
                                    onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                PlayerPreferenceDialog.ControlButtonsDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.control_buttons_alignment),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(ControlButtonsPosition.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = it == uiState.preferences.controlButtonsPosition,
                                onClick = {
                                    onEvent(PlayerPreferencesUiEvent.UpdatePreferredControlButtonsPosition(it))
                                    onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                PlayerPreferenceDialog.LongPressControlsSpeedDialog -> {
                    var longPressControlsSpeed by remember {
                        mutableFloatStateOf(uiState.preferences.longPressControlsSpeed)
                    }

                    NextDialogWithDoneAndCancelButtons(
                        title = stringResource(R.string.long_press_gesture),
                        onDoneClick = {
                            onEvent(PlayerPreferencesUiEvent.UpdateLongPressControlsSpeed(longPressControlsSpeed))
                            onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
                        },
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                        content = {
                            Text(
                                text = "$longPressControlsSpeed",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Slider(
                                value = longPressControlsSpeed,
                                onValueChange = { longPressControlsSpeed = it.round(1) },
                                valueRange = 0.2f..4.0f,
                            )
                        },
                    )
                }
            }
        }
    }
}

@DayNightPreview
@Composable
private fun PlayerPreferencesScreenPreview() {
    NextPlayerTheme {
        PlayerPreferencesContent(
            uiState = PlayerPreferencesUiState(),
            onEvent = {},
        )
    }
}
