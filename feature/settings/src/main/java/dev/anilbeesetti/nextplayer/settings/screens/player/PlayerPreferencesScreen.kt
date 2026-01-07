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
import androidx.compose.material3.IconButton
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
import dev.anilbeesetti.nextplayer.settings.composables.OptionsDialog
import dev.anilbeesetti.nextplayer.settings.extensions.name

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: PlayerPreferencesViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                val totalRows = 9
                PreferenceSwitch(
                    title = stringResource(id = R.string.seek_gesture),
                    description = stringResource(id = R.string.seek_gesture_description),
                    icon = NextIcons.SwipeHorizontal,
                    isChecked = preferences.useSeekControls,
                    onClick = viewModel::toggleUseSeekControls,
                    index = 0,
                    count = totalRows,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.brightness_gesture),
                    description = stringResource(id = R.string.brightness_gesture_description),
                    icon = NextIcons.SwipeVertical,
                    isChecked = preferences.enableBrightnessSwipeGesture,
                    onClick = viewModel::toggleEnableBrightnessSwipeGesture,
                    index = 1,
                    count = totalRows,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.volume_gesture),
                    description = stringResource(id = R.string.volume_gesture_description),
                    icon = NextIcons.SwipeVertical,
                    isChecked = preferences.enableVolumeSwipeGesture,
                    onClick = viewModel::toggleEnableVolumeSwipeGesture,
                    index = 2,
                    count = totalRows,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.zoom_gesture),
                    description = stringResource(id = R.string.zoom_gesture_description),
                    icon = NextIcons.Pinch,
                    isChecked = preferences.useZoomControls,
                    onClick = viewModel::toggleUseZoomControls,
                    index = 3,
                    count = totalRows,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.pan_gesture),
                    description = stringResource(id = R.string.pan_gesture_description),
                    icon = NextIcons.Pan,
                    enabled = preferences.useZoomControls,
                    isChecked = preferences.enablePanGesture,
                    onClick = viewModel::toggleEnablePanGesture,
                    index = 4,
                    count = totalRows,
                )
                PreferenceSwitchWithDivider(
                    title = stringResource(id = R.string.double_tap),
                    description = stringResource(id = R.string.double_tap_description),
                    icon = NextIcons.DoubleTap,
                    isChecked = (preferences.doubleTapGesture != DoubleTapGesture.NONE),
                    onChecked = viewModel::toggleDoubleTapGesture,
                    onClick = { viewModel.showDialog(PlayerPreferenceDialog.DoubleTapDialog) },
                    index = 5,
                    count = totalRows,
                )
                PreferenceSwitchWithDivider(
                    title = stringResource(id = R.string.long_press_gesture),
                    description = stringResource(id = R.string.long_press_gesture_desc, preferences.longPressControlsSpeed),
                    icon = NextIcons.Tap,
                    isChecked = preferences.useLongPressControls,
                    onChecked = viewModel::toggleUseLongPressControls,
                    onClick = { viewModel.showDialog(PlayerPreferenceDialog.LongPressControlsSpeedDialog) },
                    index = 6,
                    count = totalRows,
                )
                PreferenceSlider(
                    title = stringResource(R.string.seek_increment),
                    description = stringResource(R.string.seconds, preferences.seekIncrement),
                    icon = NextIcons.Replay,
                    value = preferences.seekIncrement.toFloat(),
                    valueRange = 1.0f..60.0f,
                    onValueChange = { viewModel.updateSeekIncrement(it.toInt()) },
                    index = 7,
                    count = totalRows,
                    trailingContent = {
                        FilledIconButton(onClick = { viewModel.updateSeekIncrement(PlayerPreferences.DEFAULT_SEEK_INCREMENT) }) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_seek_increment),
                            )
                        }
                    }
                )
                PreferenceSlider(
                    title = stringResource(R.string.seek_sensitivity),
                    description = preferences.seekSensitivity.toString(),
                    icon = NextIcons.FastForward,
                    value = preferences.seekSensitivity,
                    valueRange = 0.1f..2.0f,
                    onValueChange = { viewModel.updateSeekSensitivity(it) },
                    index = 8,
                    count = totalRows,
                    trailingContent = {
                        FilledIconButton(onClick = { viewModel.updateSeekIncrement(PlayerPreferences.DEFAULT_SEEK_INCREMENT) }) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_seek_sentivity),
                            )
                        }
                    }
                )
            }
            ListSectionTitle(text = stringResource(id = R.string.interface_name))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                val totalRows = 3
                PreferenceSlider(
                    title = stringResource(R.string.controller_timeout),
                    description = stringResource(R.string.seconds, preferences.controllerAutoHideTimeout),
                    icon = NextIcons.Timer,
                    value = preferences.controllerAutoHideTimeout.toFloat(),
                    valueRange = 1.0f..60.0f,
                    onValueChange = { viewModel.updateControlAutoHideTimeout(it.toInt()) },
                    index = 0,
                    count = totalRows,
                    trailingContent = {
                        FilledIconButton(onClick = { viewModel.updateControlAutoHideTimeout(PlayerPreferences.DEFAULT_CONTROLLER_AUTO_HIDE_TIMEOUT) }) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_controller_timeout),
                            )
                        }
                    }
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.hide_player_buttons_background),
                    description = stringResource(id = R.string.hide_player_buttons_background_description),
                    icon = NextIcons.HideSource,
                    isChecked = preferences.hidePlayerButtonsBackground,
                    onClick = viewModel::toggleHidePlayerButtonsBackground,
                    index = 2,
                    count = totalRows,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.playback))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                val totalRows = 8
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.resume),
                    description = stringResource(id = R.string.resume_description),
                    icon = NextIcons.Resume,
                    onClick = { viewModel.showDialog(PlayerPreferenceDialog.ResumeDialog) },
                    index = 0,
                    count = totalRows,
                )
                PreferenceSlider(
                    title = stringResource(id = R.string.default_playback_speed),
                    description = preferences.defaultPlaybackSpeed.toString(),
                    icon = NextIcons.Speed,
                    value = preferences.defaultPlaybackSpeed,
                    valueRange = 0.2f..4.0f,
                    onValueChange = { viewModel.updateDefaultPlaybackSpeed(it) },
                    index = 1,
                    count = totalRows,
                    trailingContent = {
                        FilledIconButton(onClick = { viewModel.updateDefaultPlaybackSpeed(1f) }) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_default_playback_speed),
                            )
                        }
                    }
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.autoplay_settings),
                    description = stringResource(
                        id = R.string.autoplay_settings_description,
                    ),
                    icon = NextIcons.Player,
                    isChecked = preferences.autoplay,
                    onClick = viewModel::toggleAutoplay,
                    index = 2,
                    count = totalRows,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.pip_settings),
                    description = stringResource(
                        id = R.string.pip_settings_description,
                    ),
                    icon = NextIcons.Pip,
                    isChecked = preferences.autoPip,
                    onClick = viewModel::toggleAutoPip,
                    index = 3,
                    count = totalRows,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.background_play),
                    description = stringResource(
                        id = R.string.background_play_description,
                    ),
                    icon = NextIcons.Headset,
                    isChecked = preferences.autoBackgroundPlay,
                    onClick = viewModel::toggleAutoBackgroundPlay,
                    index = 4,
                    count = totalRows,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.remember_brightness_level),
                    description = stringResource(
                        id = R.string.remember_brightness_level_description,
                    ),
                    icon = NextIcons.Brightness,
                    isChecked = preferences.rememberPlayerBrightness,
                    onClick = viewModel::toggleRememberBrightnessLevel,
                    index = 5,
                    count = totalRows,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.remember_selections),
                    description = stringResource(id = R.string.remember_selections_description),
                    icon = NextIcons.Selection,
                    isChecked = preferences.rememberSelections,
                    onClick = viewModel::toggleRememberSelections,
                    index = 6,
                    count = totalRows,
                )
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.player_screen_orientation),
                    description = preferences.playerScreenOrientation.name(),
                    icon = NextIcons.Rotation,
                    onClick = {
                        viewModel.showDialog(PlayerPreferenceDialog.PlayerScreenOrientationDialog)
                    },
                    index = 7,
                    count = totalRows,
                )
            }
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                PlayerPreferenceDialog.ResumeDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.resume),
                        onDismissClick = viewModel::hideDialog,
                    ) {
                        items(Resume.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = (it == preferences.resume),
                                onClick = {
                                    viewModel.updatePlaybackResume(it)
                                    viewModel.hideDialog()
                                },
                            )
                        }
                    }
                }

                PlayerPreferenceDialog.DoubleTapDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.double_tap),
                        onDismissClick = viewModel::hideDialog,
                    ) {
                        items(DoubleTapGesture.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = (it == preferences.doubleTapGesture),
                                onClick = {
                                    viewModel.updateDoubleTapGesture(it)
                                    viewModel.hideDialog()
                                },
                            )
                        }
                    }
                }

                PlayerPreferenceDialog.PlayerScreenOrientationDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.player_screen_orientation),
                        onDismissClick = viewModel::hideDialog,
                    ) {
                        items(ScreenOrientation.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = it == preferences.playerScreenOrientation,
                                onClick = {
                                    viewModel.updatePreferredPlayerOrientation(it)
                                    viewModel.hideDialog()
                                },
                            )
                        }
                    }
                }

                PlayerPreferenceDialog.ControlButtonsDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.control_buttons_alignment),
                        onDismissClick = viewModel::hideDialog,
                    ) {
                        items(ControlButtonsPosition.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = it == preferences.controlButtonsPosition,
                                onClick = {
                                    viewModel.updatePreferredControlButtonsPosition(it)
                                    viewModel.hideDialog()
                                },
                            )
                        }
                    }
                }

                PlayerPreferenceDialog.LongPressControlsSpeedDialog -> {
                    var longPressControlsSpeed by remember {
                        mutableFloatStateOf(preferences.longPressControlsSpeed)
                    }

                    NextDialogWithDoneAndCancelButtons(
                        title = stringResource(R.string.long_press_gesture),
                        onDoneClick = {
                            viewModel.updateLongPressControlsSpeed(longPressControlsSpeed)
                            viewModel.hideDialog()
                        },
                        onDismissClick = viewModel::hideDialog,
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
