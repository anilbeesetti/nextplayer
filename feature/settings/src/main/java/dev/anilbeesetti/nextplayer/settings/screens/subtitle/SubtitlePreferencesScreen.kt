package dev.anilbeesetti.nextplayer.settings.screens.subtitle

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitchWithDivider
import dev.anilbeesetti.nextplayer.core.ui.components.RadioTextButton
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.settings.composables.OptionsDialog
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSubtitle
import dev.anilbeesetti.nextplayer.settings.extensions.name
import dev.anilbeesetti.nextplayer.settings.utils.LocalesHelper
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitlePreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: SubtitlePreferencesViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val languages = remember { listOf(Pair("None", "")) + LocalesHelper.getAvailableLocales() }
    val charsetResource = stringArrayResource(id = R.array.charsets_list)
    val context = LocalContext.current

    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.subtitle),
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
            PreferredSubtitleLanguageSetting(
                currentLanguage = LocalesHelper.getLocaleDisplayLanguage(preferences.preferredSubtitleLanguage),
                onClick = { viewModel.showDialog(SubtitlePreferenceDialog.SubtitleLanguageDialog) },
            )
            SubtitleTextEncodingPreference(
                currentEncoding = charsetResource.first { it.contains(preferences.subtitleTextEncoding) },
                onClick = { viewModel.showDialog(SubtitlePreferenceDialog.SubtitleEncodingDialog) },
            )
            PreferenceSubtitle(text = stringResource(id = R.string.appearance_name))
            UseSystemCaptionStyle(
                isChecked = preferences.useSystemCaptionStyle,
                onChecked = viewModel::toggleUseSystemCaptionStyle,
                onClick = { context.startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS)) },
            )
            SubtitleFontPreference(
                currentFont = preferences.subtitleFont,
                onClick = { viewModel.showDialog(SubtitlePreferenceDialog.SubtitleFontDialog) },
                enabled = preferences.useSystemCaptionStyle.not(),
            )
            SubtitleTextBoldPreference(
                isChecked = preferences.subtitleTextBold,
                onClick = viewModel::toggleSubtitleTextBold,
                enabled = preferences.useSystemCaptionStyle.not(),
            )
            SubtitleTextSizePreference(
                currentSize = preferences.subtitleTextSize,
                onClick = { viewModel.showDialog(SubtitlePreferenceDialog.SubtitleSizeDialog) },
                enabled = preferences.useSystemCaptionStyle.not(),
            )
            SubtitleBackgroundPreference(
                isChecked = preferences.subtitleBackground,
                onClick = viewModel::toggleSubtitleBackground,
                enabled = preferences.useSystemCaptionStyle.not(),
            )
            SubtitleEmbeddedStylesPreference(
                isChecked = preferences.applyEmbeddedStyles,
                onClick = viewModel::toggleApplyEmbeddedStyles,
            )
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                SubtitlePreferenceDialog.SubtitleLanguageDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.preferred_subtitle_lang),
                        onDismissClick = viewModel::hideDialog,
                    ) {
                        items(languages) {
                            RadioTextButton(
                                text = it.first,
                                selected = it.second == preferences.preferredSubtitleLanguage,
                                onClick = {
                                    viewModel.updateSubtitleLanguage(it.second)
                                    viewModel.hideDialog()
                                },
                            )
                        }
                    }
                }

                SubtitlePreferenceDialog.SubtitleFontDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.subtitle_font),
                        onDismissClick = viewModel::hideDialog,
                    ) {
                        items(Font.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = it == preferences.subtitleFont,
                                onClick = {
                                    viewModel.updateSubtitleFont(it)
                                    viewModel.hideDialog()
                                },
                            )
                        }
                    }
                }

                SubtitlePreferenceDialog.SubtitleSizeDialog -> {
                    var size by remember { mutableIntStateOf(preferences.subtitleTextSize) }

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
                                viewModel.updateSubtitleFontSize(size)
                                viewModel.hideDialog()
                            })
                        },
                        dismissButton = { CancelButton(onClick = viewModel::hideDialog) },
                    )
                }

                SubtitlePreferenceDialog.SubtitleEncodingDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.subtitle_text_encoding),
                        onDismissClick = viewModel::hideDialog,
                    ) {
                        items(charsetResource) {
                            val currentCharset = it.substringAfterLast("(", "").removeSuffix(")")
                            if (currentCharset.isEmpty() || Charset.isSupported(currentCharset)) {
                                RadioTextButton(
                                    text = it,
                                    selected = currentCharset == preferences.subtitleTextEncoding,
                                    onClick = {
                                        viewModel.updateSubtitleEncoding(currentCharset)
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
}

@Composable
fun PreferredSubtitleLanguageSetting(
    currentLanguage: String,
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.preferred_subtitle_lang),
        description = currentLanguage.takeIf { it.isNotBlank() } ?: stringResource(
            id = R.string.preferred_subtitle_lang_description,
        ),
        icon = NextIcons.Language,
        onClick = onClick,
    )
}

@Composable
fun SubtitleTextEncodingPreference(
    currentEncoding: String,
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(R.string.subtitle_text_encoding),
        description = currentEncoding,
        icon = NextIcons.Subtitle,
        onClick = onClick,
    )
}

@Composable
fun UseSystemCaptionStyle(
    isChecked: Boolean,
    onChecked: () -> Unit,
    onClick: () -> Unit,
) {
    PreferenceSwitchWithDivider(
        title = stringResource(R.string.system_caption_style),
        description = stringResource(R.string.system_caption_style_desc),
        isChecked = isChecked,
        onChecked = onChecked,
        icon = NextIcons.Caption,
        onClick = onClick,
    )
}

@Composable
fun SubtitleFontPreference(
    currentFont: Font,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.subtitle_font),
        description = currentFont.name(),
        icon = NextIcons.Font,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
fun SubtitleTextBoldPreference(
    isChecked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.subtitle_text_bold),
        description = stringResource(id = R.string.subtitle_text_bold_desc),
        icon = NextIcons.Bold,
        isChecked = isChecked,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
fun SubtitleTextSizePreference(
    currentSize: Int,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.subtitle_text_size),
        description = currentSize.toString(),
        icon = NextIcons.FontSize,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
fun SubtitleBackgroundPreference(
    isChecked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.subtitle_background),
        description = stringResource(id = R.string.subtitle_background_desc),
        icon = NextIcons.Background,
        isChecked = isChecked,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
fun SubtitleEmbeddedStylesPreference(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(R.string.embedded_styles),
        description = stringResource(R.string.embedded_styles_desc),
        icon = NextIcons.Style,
        isChecked = isChecked,
        onClick = onClick,
    )
}
