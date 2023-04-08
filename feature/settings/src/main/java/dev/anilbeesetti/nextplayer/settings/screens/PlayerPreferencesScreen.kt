package dev.anilbeesetti.nextplayer.settings.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.datastore.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.datastore.Resume
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.RadioTextButton
import dev.anilbeesetti.nextplayer.feature.settings.R
import dev.anilbeesetti.nextplayer.settings.composables.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.settings.composables.OptionsDialog
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSwitch
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSwitchWithDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: PlayerPreferencesViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.player_name),
                scrollBehavior = scrollBehaviour,
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = innerPadding,
                modifier = Modifier
                    .fillMaxSize()
            ) {
                item {
                    ClickablePreferenceItem(
                        title = stringResource(id = R.string.resume),
                        description = stringResource(id = R.string.resume_description),
                        onClick = {
                            viewModel.onEvent(
                                PlayerPreferencesEvent.ShowDialog(Dialog.ResumeDialog)
                            )
                        }
                    )
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.remember_brightness_level),
                        description = stringResource(
                            id = R.string.remember_brightness_level_description
                        ),
                        isChecked = preferences.rememberPlayerBrightness,
                        onClick = viewModel::toggleRememberBrightnessLevel
                    )
                }
                item {
                    PreferenceSwitchWithDivider(
                        title = stringResource(id = R.string.double_tap),
                        description = stringResource(id = R.string.double_tap_description),
                        isChecked = (preferences.doubleTapGesture != DoubleTapGesture.NONE),
                        onChecked = viewModel::toggleDoubleTapGesture,
                        onClick = {
                            viewModel.onEvent(
                                PlayerPreferencesEvent.ShowDialog(Dialog.DoubleTapDialog)
                            )
                        }
                    )
                }
            }
            when (uiState.showDialog) {
                Dialog.ResumeDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.resume),
                        onDismissClick = {
                            viewModel.onEvent(PlayerPreferencesEvent.ShowDialog(Dialog.None))
                        }
                    ) {
                        Resume.values().forEach {
                            RadioTextButton(
                                text = it.value,
                                selected = (it == preferences.resume),
                                onClick = {
                                    viewModel.updatePlaybackResume(it)
                                    viewModel.onEvent(
                                        PlayerPreferencesEvent.ShowDialog(Dialog.None)
                                    )
                                }
                            )
                        }
                    }
                }
                Dialog.DoubleTapDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.double_tap),
                        onDismissClick = {
                            viewModel.onEvent(PlayerPreferencesEvent.ShowDialog(Dialog.None))
                        }
                    ) {
                        DoubleTapGesture.values().forEach {
                            RadioTextButton(
                                text = it.value,
                                selected = (it == preferences.doubleTapGesture),
                                onClick = {
                                    viewModel.updateDoubleTapGesture(it)
                                    viewModel.onEvent(
                                        PlayerPreferencesEvent.ShowDialog(Dialog.None)
                                    )
                                }
                            )
                        }
                    }
                }
                Dialog.None -> { /* Do nothing */ }
            }
        }
    }
}
