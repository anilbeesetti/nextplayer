package dev.anilbeesetti.nextplayer.settings.screens.audio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitch
import dev.anilbeesetti.nextplayer.core.ui.components.RadioTextButton
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.settings.composables.OptionsDialog
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSubtitle
import dev.anilbeesetti.nextplayer.settings.utils.LocalesHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: AudioPreferencesViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val languages = remember { listOf(Pair("None", "")) + LocalesHelper.getAvailableLocales() }

    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.audio),
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
            PreferenceSubtitle(text = stringResource(id = R.string.playback))
            PreferredAudioLanguageSetting(
                currentLanguage = LocalesHelper.getLocaleDisplayLanguage(preferences.preferredAudioLanguage),
                onClick = { viewModel.showDialog(AudioPreferenceDialog.AudioLanguageDialog) },
            )
            VolumeBoost(
                isChecked = preferences.shouldUseVolumeBoost,
                onClick = viewModel::toggleShouldUseVolumeBoost,
            )
            RequireAudioFocusSetting(
                isChecked = preferences.requireAudioFocus,
                onClick = viewModel::toggleRequireAudioFocus,
            )
            PauseOnHeadsetDisconnectSetting(
                isChecked = preferences.pauseOnHeadsetDisconnect,
                onClick = viewModel::togglePauseOnHeadsetDisconnect,
            )
            ShowSystemVolumePanelSetting(
                isChecked = preferences.showSystemVolumePanel,
                onClick = viewModel::toggleShowSystemVolumePanel,
            )
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                AudioPreferenceDialog.AudioLanguageDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.preferred_audio_lang),
                        onDismissClick = viewModel::hideDialog,
                    ) {
                        items(languages) {
                            RadioTextButton(
                                text = it.first,
                                selected = it.second == preferences.preferredAudioLanguage,
                                onClick = {
                                    viewModel.updateAudioLanguage(it.second)
                                    viewModel.hideDialog()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreferredAudioLanguageSetting(
    currentLanguage: String,
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.preferred_audio_lang),
        description = currentLanguage.takeIf { it.isNotBlank() } ?: stringResource(
            id = R.string.preferred_audio_lang_description,
        ),
        icon = NextIcons.Language,
        onClick = onClick,
    )
}

@Composable
fun VolumeBoost(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(R.string.volume_boost),
        description = stringResource(R.string.volume_boost_desc),
        icon = NextIcons.VolumeUp,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun RequireAudioFocusSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(R.string.require_audio_focus),
        description = stringResource(R.string.require_audio_focus_desc),
        icon = NextIcons.Focus,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun PauseOnHeadsetDisconnectSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.pause_on_headset_disconnect),
        description = stringResource(id = R.string.pause_on_headset_disconnect_desc),
        icon = NextIcons.HeadsetOff,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun ShowSystemVolumePanelSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.system_volume_panel),
        description = stringResource(id = R.string.system_volume_panel_desc),
        icon = NextIcons.Headset,
        isChecked = isChecked,
        onClick = onClick,
    )
}
