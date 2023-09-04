package dev.anilbeesetti.nextplayer.settings.screens.audio

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
    viewModel: AudioPreferencesViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val languages = remember { listOf(Pair("None", "")) + LocalesHelper.getAvailableLocales() }

    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.audio),
                scrollBehavior = scrollBehaviour,
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item { PreferenceSubtitle(text = stringResource(id = R.string.playback)) }
            preferredAudioLanguageSetting(
                currentLanguage = LocalesHelper.getLocaleDisplayLanguage(preferences.preferredAudioLanguage),
                onClick = { viewModel.showDialog(AudioPreferenceDialog.AudioLanguageDialog) }
            )
            pauseOnHeadsetDisconnectSetting(
                isChecked = preferences.pauseOnHeadsetDisconnect,
                onClick = viewModel::togglePauseOnHeadsetDisconnect
            )
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                AudioPreferenceDialog.AudioLanguageDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.preferred_audio_lang),
                        onDismissClick = viewModel::hideDialog
                    ) {
                        items(languages) {
                            RadioTextButton(
                                text = it.first,
                                selected = it.second == preferences.preferredAudioLanguage,
                                onClick = {
                                    viewModel.updateAudioLanguage(it.second)
                                    viewModel.hideDialog()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.preferredAudioLanguageSetting(
    currentLanguage: String,
    onClick: () -> Unit
) = item {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.preferred_audio_lang),
        description = currentLanguage.takeIf { it.isNotBlank() } ?: stringResource(
            id = R.string.preferred_audio_lang_description
        ),
        icon = NextIcons.Language,
        onClick = onClick
    )
}

fun LazyListScope.pauseOnHeadsetDisconnectSetting(
    isChecked: Boolean,
    onClick: () -> Unit
) = item {
    PreferenceSwitch(
        title = stringResource(id = R.string.pause_on_headset_disconnect),
        description = stringResource(id = R.string.pause_on_headset_disconnect_desc),
        icon = NextIcons.HeadsetOff,
        isChecked = isChecked,
        onClick = onClick
    )
}