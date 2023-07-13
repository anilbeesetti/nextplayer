package dev.anilbeesetti.nextplayer.settings.screens.subtitle

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.Font
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitch
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
    val charsetResource = stringArrayResource(id = R.array.charsets_list)

    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.subtitle),
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
            subtitleTextEncodingPreference(
                currentEncoding = charsetResource.first { it.contains(preferences.subtitleTextEncoding) },
                onClick = { viewModel.showDialog(SubtitlePreferenceDialog.SubtitleEncodingDialog) }
            )
            item { PreferenceSubtitle(text = stringResource(id = R.string.appearance_name)) }
            subtitleFontPreference(
                currentFont = preferences.subtitleFont,
                onClick = { viewModel.showDialog(SubtitlePreferenceDialog.SubtitleFontDialog) }
            )
            subtitleTextBoldPreference(
                isChecked = preferences.subtitleTextBold,
                onClick = viewModel::toggleSubtitleTextBold
            )
            subtitleTextSizePreference(
                currentSize = preferences.subtitleTextSize,
                onClick = { viewModel.showDialog(SubtitlePreferenceDialog.SubtitleSizeDialog) }
            )
            subtitleBackgroundPreference(
                isChecked = preferences.subtitleBackground,
                onClick = viewModel::toggleSubtitleBackground
            )
            subtitleEmbeddedStylesPreference(
                isChecked = preferences.applyEmbeddedStyles,
                onClick = viewModel::toggleApplyEmbeddedStyles
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

            SubtitlePreferenceDialog.SubtitleSizeDialog -> {
                var size by remember { mutableStateOf(preferences.subtitleTextSize) }

                NextDialog(
                    onDismissRequest = viewModel::hideDialog,
                    title = { Text(text = stringResource(id = R.string.subtitle_text_size)) },
                    content = {
                        Text(
                            text = size.toString(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            textAlign = TextAlign.Center,
                            fontSize = size.sp,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Slider(
                            value = size.toFloat(),
                            onValueChange = { size = it.toInt() },
                            valueRange = 15f..60f
                        )
                    },
                    confirmButton = {
                        DoneButton(onClick = {
                            viewModel.updateSubtitleFontSize(size)
                            viewModel.hideDialog()
                        })
                    },
                    dismissButton = { CancelButton(onClick = viewModel::hideDialog) }
                )
            }

            SubtitlePreferenceDialog.SubtitleEncodingDialog -> {
                OptionsDialog(
                    text = stringResource(id = R.string.subtitle_text_encoding),
                    onDismissClick = viewModel::hideDialog
                ) {
                    items(charsetResource) {
                        val currentCharset = it.substringAfterLast("(", "").removeSuffix(")")
                        RadioTextButton(
                            text = it,
                            selected = currentCharset == preferences.subtitleTextEncoding,
                            onClick = {
                                viewModel.updateSubtitleEncoding(currentCharset)
                                viewModel.hideDialog()
                            }
                        )
                    }
                }
            }

            SubtitlePreferenceDialog.None -> Unit
        }
    }
}

fun LazyListScope.preferredSubtitleLanguageSetting(
    currentLanguage: String,
    onClick: () -> Unit
) = item {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.preferred_subtitle_lang),
        description = currentLanguage.takeIf { it.isNotBlank() } ?: stringResource(
            id = R.string.preferred_subtitle_lang_description
        ),
        icon = NextIcons.Language,
        onClick = onClick
    )
}

fun LazyListScope.subtitleTextEncodingPreference(
    currentEncoding: String,
    onClick: () -> Unit
) = item {
    ClickablePreferenceItem(
        title = stringResource(R.string.subtitle_text_encoding),
        description = currentEncoding,
        icon = NextIcons.Caption,
        onClick = onClick
    )
}

fun LazyListScope.subtitleFontPreference(
    currentFont: Font,
    onClick: () -> Unit
) = item {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.subtitle_font),
        description = currentFont.name(),
        icon = NextIcons.Font,
        onClick = onClick
    )
}

fun LazyListScope.subtitleTextBoldPreference(
    isChecked: Boolean,
    onClick: () -> Unit
) = item {
    PreferenceSwitch(
        title = stringResource(id = R.string.subtitle_text_bold),
        description = stringResource(id = R.string.subtitle_text_bold_desc),
        icon = NextIcons.Bold,
        isChecked = isChecked,
        onClick = onClick
    )
}

fun LazyListScope.subtitleTextSizePreference(
    currentSize: Int,
    onClick: () -> Unit
) = item {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.subtitle_text_size),
        description = currentSize.toString(),
        icon = NextIcons.FontSize,
        onClick = onClick
    )
}

fun LazyListScope.subtitleBackgroundPreference(
    isChecked: Boolean,
    onClick: () -> Unit
) = item {
    PreferenceSwitch(
        title = stringResource(id = R.string.subtitle_background),
        description = stringResource(id = R.string.subtitle_background_desc),
        icon = NextIcons.Background,
        isChecked = isChecked,
        onClick = onClick
    )
}

fun LazyListScope.subtitleEmbeddedStylesPreference(
    isChecked: Boolean,
    onClick: () -> Unit
) = item {
    PreferenceSwitch(
        title = stringResource(R.string.embedded_styles),
        description = stringResource(R.string.embedded_styles_desc),
        icon = NextIcons.Style,
        isChecked = isChecked,
        onClick = onClick
    )
}
