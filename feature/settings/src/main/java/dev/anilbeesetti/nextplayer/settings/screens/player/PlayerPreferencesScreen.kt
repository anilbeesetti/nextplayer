package dev.anilbeesetti.nextplayer.settings.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.round
import dev.anilbeesetti.nextplayer.core.model.ControlButtonsPosition
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.FastSeek
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialogWithDoneAndCancelButtons
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitch
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitchWithDivider
import dev.anilbeesetti.nextplayer.core.ui.components.RadioTextButton
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.settings.composables.OptionsDialog
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSubtitle
import dev.anilbeesetti.nextplayer.settings.extensions.name

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: PlayerPreferencesViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.player_name),
                scrollBehavior = scrollBehaviour,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
                    ) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(state = rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            PreferenceSubtitle(text = stringResource(id = R.string.interface_name))
            SeekGestureSetting(
                isChecked = preferences.useSeekControls,
                onClick = viewModel::toggleUseSeekControls,
            )
            SwipeGestureSetting(
                isChecked = preferences.useSwipeControls,
                onClick = viewModel::toggleUseSwipeControls,
            )
            ZoomGestureSetting(
                isChecked = preferences.useZoomControls,
                onClick = viewModel::toggleUseZoomControls,
            )
            DoubleTapGestureSetting(
                isChecked = (preferences.doubleTapGesture != DoubleTapGesture.NONE),
                onChecked = viewModel::toggleDoubleTapGesture,
                onClick = { viewModel.showDialog(PlayerPreferenceDialog.DoubleTapDialog) },
            )
            LongPressGesture(
                isChecked = preferences.useLongPressControls,
                onChecked = viewModel::toggleUseLongPressControls,
                playbackSpeed = preferences.longPressControlsSpeed,
                onClick = { viewModel.showDialog(PlayerPreferenceDialog.LongPressControlsSpeedDialog) },
            )
            SeekIncrementPreference(
                currentValue = preferences.seekIncrement,
                onClick = { viewModel.showDialog(PlayerPreferenceDialog.SeekIncrementDialog) },
            )
            ControllerTimeoutPreference(
                currentValue = preferences.controllerAutoHideTimeout,
                onClick = { viewModel.showDialog(PlayerPreferenceDialog.ControllerTimeoutDialog) },
            )
            ControlButtonsPositionSetting(
                currentControlButtonPosition = preferences.controlButtonsPosition,
                onClick = {
                    viewModel.showDialog(PlayerPreferenceDialog.ControlButtonsDialog)
                },
            )
            PreferenceSubtitle(text = stringResource(id = R.string.playback))
            ResumeSetting(
                onClick = { viewModel.showDialog(PlayerPreferenceDialog.ResumeDialog) },
            )
            DefaultPlaybackSpeedSetting(
                currentDefaultPlaybackSpeed = preferences.defaultPlaybackSpeed,
                onClick = { viewModel.showDialog(PlayerPreferenceDialog.PlaybackSpeedDialog) },
            )
            AutoplaySetting(
                isChecked = preferences.autoplay,
                onClick = viewModel::toggleAutoplay,
            )
            PipSetting(
                isChecked = preferences.autoPip,
                onClick = viewModel::toggleAutoPip,
            )
            BackgroundPlaybackSetting(
                isChecked = preferences.autoBackgroundPlay,
                onClick = viewModel::toggleAutoBackgroundPlay,
            )
            RememberBrightnessSetting(
                isChecked = preferences.rememberPlayerBrightness,
                onClick = viewModel::toggleRememberBrightnessLevel,
            )
            RememberSelectionsSetting(
                isChecked = preferences.rememberSelections,
                onClick = viewModel::toggleRememberSelections,
            )
            FastSeekSetting(
                isChecked = (preferences.fastSeek != FastSeek.DISABLE),
                onChecked = viewModel::toggleFastSeek,
                onClick = { viewModel.showDialog(PlayerPreferenceDialog.FastSeekDialog) },
            )
            ScreenOrientationSetting(
                currentOrientationPreference = preferences.playerScreenOrientation,
                onClick = {
                    viewModel.showDialog(PlayerPreferenceDialog.PlayerScreenOrientationDialog)
                },
            )
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

                PlayerPreferenceDialog.FastSeekDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.fast_seek),
                        onDismissClick = viewModel::hideDialog,
                    ) {
                        items(FastSeek.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = (it == preferences.fastSeek),
                                onClick = {
                                    viewModel.updateFastSeek(it)
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

                PlayerPreferenceDialog.PlaybackSpeedDialog -> {
                    var defaultPlaybackSpeed by remember {
                        mutableFloatStateOf(preferences.defaultPlaybackSpeed)
                    }

                    NextDialogWithDoneAndCancelButtons(
                        title = stringResource(R.string.default_playback_speed),
                        onDoneClick = {
                            viewModel.updateDefaultPlaybackSpeed(defaultPlaybackSpeed)
                            viewModel.hideDialog()
                        },
                        onDismissClick = viewModel::hideDialog,
                        content = {
                            Text(
                                text = "$defaultPlaybackSpeed",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Slider(
                                value = defaultPlaybackSpeed,
                                onValueChange = { defaultPlaybackSpeed = it.round(1) },
                                valueRange = 0.2f..4.0f,
                            )
                        },
                    )
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
                PlayerPreferenceDialog.ControllerTimeoutDialog -> {
                    var controllerAutoHideSec by remember {
                        mutableIntStateOf(preferences.controllerAutoHideTimeout)
                    }

                    NextDialogWithDoneAndCancelButtons(
                        title = stringResource(R.string.controller_timeout),
                        onDoneClick = {
                            viewModel.updateControlAutoHideTimeout(controllerAutoHideSec)
                            viewModel.hideDialog()
                        },
                        onDismissClick = viewModel::hideDialog,
                        content = {
                            Text(
                                text = stringResource(R.string.seconds, controllerAutoHideSec),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Slider(
                                value = controllerAutoHideSec.toFloat(),
                                onValueChange = { controllerAutoHideSec = it.toInt() },
                                valueRange = 1.0f..60.0f,
                            )
                        },
                    )
                }

                PlayerPreferenceDialog.SeekIncrementDialog -> {
                    var seekIncrement by remember {
                        mutableIntStateOf(preferences.seekIncrement)
                    }

                    NextDialogWithDoneAndCancelButtons(
                        title = stringResource(R.string.seek_increment),
                        onDoneClick = {
                            viewModel.updateSeekIncrement(seekIncrement)
                            viewModel.hideDialog()
                        },
                        onDismissClick = viewModel::hideDialog,
                        content = {
                            Text(
                                text = stringResource(R.string.seconds, seekIncrement),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Slider(
                                value = seekIncrement.toFloat(),
                                onValueChange = { seekIncrement = it.toInt() },
                                valueRange = 1.0f..60.0f,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SeekGestureSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.seek_gesture),
        description = stringResource(id = R.string.seek_gesture_description),
        icon = NextIcons.SwipeHorizontal,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun SwipeGestureSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.swipe_gesture),
        description = stringResource(id = R.string.swipe_gesture_description),
        icon = NextIcons.SwipeVertical,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun ZoomGestureSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.zoom_gesture),
        description = stringResource(id = R.string.zoom_gesture_description),
        icon = NextIcons.Pinch,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun DoubleTapGestureSetting(
    isChecked: Boolean,
    onChecked: () -> Unit,
    onClick: () -> Unit,
) {
    PreferenceSwitchWithDivider(
        title = stringResource(id = R.string.double_tap),
        description = stringResource(id = R.string.double_tap_description),
        isChecked = isChecked,
        onChecked = onChecked,
        icon = NextIcons.DoubleTap,
        onClick = onClick,
    )
}

@Composable
fun LongPressGesture(
    isChecked: Boolean,
    onChecked: () -> Unit,
    playbackSpeed: Float,
    onClick: () -> Unit,
) {
    PreferenceSwitchWithDivider(
        title = stringResource(id = R.string.long_press_gesture),
        description = stringResource(id = R.string.long_press_gesture_desc, playbackSpeed),
        isChecked = isChecked,
        onChecked = onChecked,
        icon = NextIcons.Tap,
        onClick = onClick,
    )
}

@Composable
fun SeekIncrementPreference(
    currentValue: Int,
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(R.string.seek_increment),
        description = stringResource(R.string.seconds, currentValue),
        icon = NextIcons.Replay,
        onClick = onClick,
    )
}

@Composable
fun ControllerTimeoutPreference(
    currentValue: Int,
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(R.string.controller_timeout),
        description = stringResource(R.string.seconds, currentValue),
        icon = NextIcons.Timer,
        onClick = onClick,
    )
}

@Composable
fun ResumeSetting(
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.resume),
        description = stringResource(id = R.string.resume_description),
        icon = NextIcons.Resume,
        onClick = onClick,
    )
}

@Composable
fun DefaultPlaybackSpeedSetting(
    currentDefaultPlaybackSpeed: Float,
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.default_playback_speed),
        description = currentDefaultPlaybackSpeed.toString(),
        icon = NextIcons.Speed,
        onClick = onClick,
    )
}

@Composable
fun AutoplaySetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.autoplay_settings),
        description = stringResource(
            id = R.string.autoplay_settings_description,
        ),
        icon = NextIcons.Player,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun PipSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.pip_settings),
        description = stringResource(
            id = R.string.pip_settings_description,
        ),
        icon = NextIcons.Pip,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun BackgroundPlaybackSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.background_play),
        description = stringResource(
            id = R.string.background_play_description,
        ),
        icon = NextIcons.Headset,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun RememberBrightnessSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.remember_brightness_level),
        description = stringResource(
            id = R.string.remember_brightness_level_description,
        ),
        icon = NextIcons.Brightness,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun RememberSelectionsSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.remember_selections),
        description = stringResource(id = R.string.remember_selections_description),
        icon = NextIcons.Selection,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun FastSeekSetting(
    isChecked: Boolean,
    onChecked: () -> Unit,
    onClick: () -> Unit,
) {
    PreferenceSwitchWithDivider(
        title = stringResource(id = R.string.fast_seek),
        description = stringResource(id = R.string.fast_seek_description),
        isChecked = isChecked,
        onChecked = onChecked,
        icon = NextIcons.Fast,
        onClick = onClick,
    )
}

@Composable
fun ScreenOrientationSetting(
    currentOrientationPreference: ScreenOrientation,
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.player_screen_orientation),
        description = currentOrientationPreference.name(),
        icon = NextIcons.Rotation,
        onClick = onClick,
    )
}

@Composable
fun ControlButtonsPositionSetting(
    currentControlButtonPosition: ControlButtonsPosition,
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.control_buttons_alignment),
        description = currentControlButtonPosition.name(),
        icon = NextIcons.ButtonsPosition,
        onClick = onClick,
    )
}
