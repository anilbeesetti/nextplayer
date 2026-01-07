package dev.anilbeesetti.nextplayer.settings.screens.subtitle

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.Font
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.ListSectionTitle
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitch
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitchWithDivider
import dev.anilbeesetti.nextplayer.core.ui.components.RadioTextButton
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.settings.composables.OptionsDialog
import dev.anilbeesetti.nextplayer.settings.extensions.name
import dev.anilbeesetti.nextplayer.settings.utils.LocalesHelper
import java.nio.charset.Charset

@Composable
fun SubtitlePreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: SubtitlePreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SubtitlePreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubtitlePreferencesContent(
    uiState: SubtitlePreferencesUiState,
    onEvent: (SubtitlePreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val languages = remember { listOf(Pair("None", "")) + LocalesHelper.getAvailableLocales() }
    val charsetResource = stringArrayResource(id = R.array.charsets_list)
    val context = LocalContext.current

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.subtitle),
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
            ListSectionTitle(text = stringResource(id = R.string.playback))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                val totalRows = 2
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.preferred_subtitle_lang),
                    description = LocalesHelper.getLocaleDisplayLanguage(uiState.preferences.preferredSubtitleLanguage)
                        .takeIf { it.isNotBlank() } ?: stringResource(R.string.preferred_subtitle_lang_description),
                    icon = NextIcons.Language,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(SubtitlePreferenceDialog.SubtitleLanguageDialog)) },
                    index = 0,
                    count = totalRows,
                )
                ClickablePreferenceItem(
                    title = stringResource(R.string.subtitle_text_encoding),
                    description = charsetResource.first { it.contains(uiState.preferences.subtitleTextEncoding) },
                    icon = NextIcons.Subtitle,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(SubtitlePreferenceDialog.SubtitleEncodingDialog)) },
                    index = 1,
                    count = totalRows,
                )
            }
            ListSectionTitle(text = stringResource(id = R.string.appearance_name))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                val totalRows = 6
                PreferenceSwitchWithDivider(
                    title = stringResource(R.string.system_caption_style),
                    description = stringResource(R.string.system_caption_style_desc),
                    icon = NextIcons.Caption,
                    isChecked = uiState.preferences.useSystemCaptionStyle,
                    onChecked = { onEvent(SubtitlePreferencesUiEvent.ToggleUseSystemCaptionStyle) },
                    onClick = { context.startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS)) },
                    index = 0,
                    count = totalRows,
                )
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.subtitle_font),
                    description = uiState.preferences.subtitleFont.name(),
                    icon = NextIcons.Font,
                    enabled = uiState.preferences.useSystemCaptionStyle.not(),
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(SubtitlePreferenceDialog.SubtitleFontDialog)) },
                    index = 1,
                    count = totalRows,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.subtitle_text_bold),
                    description = stringResource(id = R.string.subtitle_text_bold_desc),
                    icon = NextIcons.Bold,
                    enabled = uiState.preferences.useSystemCaptionStyle.not(),
                    isChecked = uiState.preferences.subtitleTextBold,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ToggleSubtitleTextBold) },
                    index = 2,
                    count = totalRows,
                )
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.subtitle_text_size),
                    description = uiState.preferences.subtitleTextSize.toString(),
                    icon = NextIcons.FontSize,
                    enabled = uiState.preferences.useSystemCaptionStyle.not(),
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(SubtitlePreferenceDialog.SubtitleSizeDialog)) },
                    index = 3,
                    count = totalRows,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.subtitle_background),
                    description = stringResource(id = R.string.subtitle_background_desc),
                    icon = NextIcons.Background,
                    enabled = uiState.preferences.useSystemCaptionStyle.not(),
                    isChecked = uiState.preferences.subtitleBackground,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ToggleSubtitleBackground) },
                    index = 4,
                    count = totalRows,
                )
                PreferenceSwitch(
                    title = stringResource(R.string.embedded_styles),
                    description = stringResource(R.string.embedded_styles_desc),
                    icon = NextIcons.Style,
                    isChecked = uiState.preferences.applyEmbeddedStyles,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ToggleApplyEmbeddedStyles) },
                    index = 5,
                    count = totalRows,
                )
            }
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                SubtitlePreferenceDialog.SubtitleLanguageDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.preferred_subtitle_lang),
                        onDismissClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(languages) {
                            RadioTextButton(
                                text = it.first,
                                selected = it.second == uiState.preferences.preferredSubtitleLanguage,
                                onClick = {
                                    onEvent(SubtitlePreferencesUiEvent.UpdateSubtitleLanguage(it.second))
                                    onEvent(SubtitlePreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                SubtitlePreferenceDialog.SubtitleFontDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.subtitle_font),
                        onDismissClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(Font.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = it == uiState.preferences.subtitleFont,
                                onClick = {
                                    onEvent(SubtitlePreferencesUiEvent.UpdateSubtitleFont(it))
                                    onEvent(SubtitlePreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                SubtitlePreferenceDialog.SubtitleSizeDialog -> {
                    var size by remember { mutableIntStateOf(uiState.preferences.subtitleTextSize) }

                    NextDialog(
                        onDismissRequest = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(null)) },
                        title = { Text(text = stringResource(id = R.string.subtitle_text_size)) },
                        content = {
                            Text(
                                text = size.toString(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                textAlign = TextAlign.Center,
                                fontSize = size.sp,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Slider(
                                value = size.toFloat(),
                                onValueChange = { size = it.toInt() },
                                valueRange = 15f..60f,
                            )
                        },
                        confirmButton = {
                            DoneButton(onClick = {
                                onEvent(SubtitlePreferencesUiEvent.UpdateSubtitleFontSize(size))
                                onEvent(SubtitlePreferencesUiEvent.ShowDialog(null))
                            })
                        },
                        dismissButton = { CancelButton(onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(null)) }) },
                    )
                }

                SubtitlePreferenceDialog.SubtitleEncodingDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.subtitle_text_encoding),
                        onDismissClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(charsetResource) {
                            val currentCharset = it.substringAfterLast("(", "").removeSuffix(")")
                            if (currentCharset.isEmpty() || Charset.isSupported(currentCharset)) {
                                RadioTextButton(
                                    text = it,
                                    selected = currentCharset == uiState.preferences.subtitleTextEncoding,
                                    onClick = {
                                        onEvent(SubtitlePreferencesUiEvent.UpdateSubtitleEncoding(currentCharset))
                                        onEvent(SubtitlePreferencesUiEvent.ShowDialog(null))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SubtitlePreferencesScreenPreview() {
    NextPlayerTheme {
        SubtitlePreferencesContent(
            uiState = SubtitlePreferencesUiState(),
            onEvent = {},
            onNavigateUp = {},
        )
    }
}
