package dev.anilbeesetti.nextplayer.settings.screens.subtitle

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
import dev.anilbeesetti.nextplayer.core.model.Font
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.RadioTextButton
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.settings.composables.OptionsDialog
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSubtitle
import dev.anilbeesetti.nextplayer.settings.extensions.name
import dev.anilbeesetti.nextplayer.settings.screens.player.getDisplayTitle
import dev.anilbeesetti.nextplayer.settings.screens.player.getLanguages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitlePreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: SubtitlePreferencesViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val languages = remember { listOf(Pair("None", "")) + getLanguages() }

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
            preferredSubtitleLanguageSetting(
                currentLanguage = getDisplayTitle(preferences.preferredSubtitleLanguage),
                onClick = { viewModel.showDialog(SubtitlePreferenceDialog.SubtitleLanguageDialog) }
            )
            item { PreferenceSubtitle(text = stringResource(id = R.string.appearance_name)) }
            subtitleFontPreference(
                currentFont = preferences.subtitleFont,
                onClick = { viewModel.showDialog(SubtitlePreferenceDialog.SubtitleFontDialog) }
            )
        }

        when (uiState.showDialog) {
            SubtitlePreferenceDialog.SubtitleLanguageDialog -> {
                OptionsDialog(
                    text = stringResource(id = R.string.preferred_subtitle_lang),
                    onDismissClick = viewModel::hideDialog
                ) {
                    items(languages) {
                        RadioTextButton(
                            text = it.first,
                            selected = it.second == preferences.preferredSubtitleLanguage,
                            onClick = {
                                viewModel.updateSubtitleLanguage(it.second)
                                viewModel.hideDialog()
                            }
                        )
                    }
                }
            }

            SubtitlePreferenceDialog.SubtitleFontDialog -> {
                OptionsDialog(
                    text = stringResource(id = R.string.subtitle_font),
                    onDismissClick = viewModel::hideDialog
                ) {
                    items(Font.values()) {
                        RadioTextButton(
                            text = it.name(),
                            selected = it == preferences.subtitleFont,
                            onClick = {
                                viewModel.updateSubtitleFont(it)
                                viewModel.hideDialog()
                            }
                        )
                    }
                }
            }
            SubtitlePreferenceDialog.None -> {}
        }
    }
}


fun LazyListScope.preferredSubtitleLanguageSetting(
    currentLanguage: String,
    onClick: () -> Unit
) {
    item {
        ClickablePreferenceItem(
            title = stringResource(id = R.string.preferred_subtitle_lang),
            description = currentLanguage.takeIf { it.isNotBlank() } ?: stringResource(
                id = R.string.preferred_subtitle_lang_description
            ),
            icon = NextIcons.Subtitle,
            onClick = onClick
        )
    }
}

fun LazyListScope.subtitleFontPreference(
    currentFont: Font,
    onClick: () -> Unit
) {
    item {
        ClickablePreferenceItem(
            title = stringResource(id = R.string.subtitle_font),
            description = currentFont.name(),
            icon = NextIcons.Subtitle,
            onClick = onClick
        )
    }
}