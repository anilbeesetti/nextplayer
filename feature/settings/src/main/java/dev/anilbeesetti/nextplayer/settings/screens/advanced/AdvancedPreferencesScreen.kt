package dev.anilbeesetti.nextplayer.settings.screens.advanced

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialogWithDoneAndCancelButtons
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: AdvancedPreferencesViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.advanced),
                scrollBehavior = scrollBehaviour,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    ) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(state = rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        ) {
            PreferenceSubtitle(text = stringResource(id = R.string.advanced))
            MinBufferPreference(
                currentValue = preferences.minBufferMs,
                onClick = { viewModel.showDialog(AdvancedPreferenceDialog.MinBufferDialog) }
            )
            MaxBufferPreference(
                currentValue = preferences.maxBufferMs,
                onClick = { viewModel.showDialog(AdvancedPreferenceDialog.MaxBufferDialog) }
            )
            BufferForPlaybackPreference(
                currentValue = preferences.bufferForPlaybackMs,
                onClick = { viewModel.showDialog(AdvancedPreferenceDialog.BufferForPlaybackDialog) }
            )
            BufferForPlaybackAfterRebufferPreference(
                currentValue = preferences.bufferForPlaybackAfterRebuffer,
                onClick = { viewModel.showDialog(AdvancedPreferenceDialog.BufferForPlaybackAfterRebufferDialog) }
            )
            HttpUserAgentPreference(
                currentValue = preferences.httpUserAgent,
                onClick = { viewModel.showDialog(AdvancedPreferenceDialog.HttpUserAgentDialog) }
            )
            HttpHeadersPreference(
                currentValue = preferences.httpHeaders,
                onClick = { viewModel.showDialog(AdvancedPreferenceDialog.HttpHeadersDialog) }
            )
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                AdvancedPreferenceDialog.MinBufferDialog -> {
                    SeekPreferenceDialog(
                        titleRes = R.string.min_buffer_title,
                        descriptionRes = R.string.min_buffer_description,
                        valueFormattedStringRes = R.string.milliseconds,
                        initialState = preferences.minBufferMs.toFloat(),
                        defaultState = PlayerPreferences.DEFAULT_MIN_BUFFER_MS.toFloat(),
                        range = 0F..120_000F,
                        onDoneClick = {
                            viewModel.updateMinBufferMs(it.toInt())
                            viewModel.hideDialog()
                        },
                        onDismissClick = viewModel::hideDialog
                    )
                }

                AdvancedPreferenceDialog.MaxBufferDialog -> {
                    SeekPreferenceDialog(
                        titleRes = R.string.max_buffer_title,
                        descriptionRes = R.string.max_buffer_description,
                        valueFormattedStringRes = R.string.milliseconds,
                        initialState = preferences.maxBufferMs.toFloat(),
                        defaultState = PlayerPreferences.DEFAULT_MAX_BUFFER_MS.toFloat(),
                        range = 0F..120_000F,
                        onDoneClick = {
                            viewModel.updateMaxBufferMs(it.toInt())
                            viewModel.hideDialog()
                        },
                        onDismissClick = viewModel::hideDialog
                    )
                }

                AdvancedPreferenceDialog.BufferForPlaybackDialog -> {
                    SeekPreferenceDialog(
                        titleRes = R.string.buffer_for_playback_title,
                        descriptionRes = R.string.buffer_for_playback_description,
                        valueFormattedStringRes = R.string.milliseconds,
                        initialState = preferences.bufferForPlaybackMs.toFloat(),
                        defaultState = PlayerPreferences.DEFAULT_BUFFER_FOR_PLAYBACK_MS.toFloat(),
                        range = 0F..60_000F,
                        onDoneClick = {
                            viewModel.updateBufferForPlaybackMs(it.toInt())
                            viewModel.hideDialog()
                        },
                        onDismissClick = viewModel::hideDialog
                    )
                }

                AdvancedPreferenceDialog.BufferForPlaybackAfterRebufferDialog -> {
                    SeekPreferenceDialog(
                        titleRes = R.string.buffer_for_playback_after_rebuffer_title,
                        descriptionRes = R.string.buffer_for_playback_after_rebuffer_description,
                        valueFormattedStringRes = R.string.milliseconds,
                        initialState = preferences.bufferForPlaybackAfterRebuffer.toFloat(),
                        defaultState = PlayerPreferences.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS.toFloat(),
                        range = 0F..60_000F,
                        onDoneClick = {
                            viewModel.updateBufferForPlaybackAfterRebufferMs(it.toInt())
                            viewModel.hideDialog()
                        },
                        onDismissClick = viewModel::hideDialog
                    )
                }

                AdvancedPreferenceDialog.HttpUserAgentDialog -> {
                    StringDialog(
                        titleRes = R.string.http_user_agent_title,
                        placeholderRes = R.string.http_user_agent_placeholder,
                        initialValue = preferences.httpUserAgent ?: "",
                        validator = { true },
                        onDone = {
                            viewModel.updateHttpUserAgent(it.ifBlank { null })
                            viewModel.hideDialog()
                        },
                        onDismiss = viewModel::hideDialog
                    )
                }

                AdvancedPreferenceDialog.HttpHeadersDialog -> {
                    StringDialog(
                        titleRes = R.string.http_headers_title,
                        placeholderRes = R.string.http_headers_placeholder,
                        initialValue = httpHeadersAsKeyValueString(preferences.httpHeaders),
                        validator = {
                            parseHttpHeadersString(it) != null
                        },
                        onDone = {
                            parseHttpHeadersString(it)?.let { headers ->
                                viewModel.updateHttpHeaders(headers)
                            }
                            viewModel.hideDialog()
                        },
                        onDismiss = viewModel::hideDialog
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekPreferenceDialog(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    @StringRes valueFormattedStringRes: Int,
    initialState: Float,
    defaultState: Float,
    range: ClosedFloatingPointRange<Float>,
    onDoneClick: (value: Float) -> Unit,
    onDismissClick: () -> Unit
) {
    var seekIncrement by remember { mutableFloatStateOf(initialState) }

    NextDialogWithDoneAndCancelButtons(
        title = stringResource(titleRes),
        onDoneClick = { onDoneClick(seekIncrement) },
        onDismissClick = onDismissClick,
        content = {
            Text(
                text = stringResource(descriptionRes),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(valueFormattedStringRes, seekIncrement.toInt()),
                modifier = Modifier
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = seekIncrement,
                onValueChange = { seekIncrement = it },
                valueRange = range,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { seekIncrement = defaultState },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Text(text = stringResource(id = R.string.reset_to_default))
                }
            }
        }
    )
}

@Composable
private fun StringDialog(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int? = null,
    @StringRes placeholderRes: Int,
    initialValue: String,
    validator: (String) -> Boolean,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    var string by rememberSaveable { mutableStateOf(initialValue) }
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        content = {
            descriptionRes?.let {
                Text(text = stringResource(it))
                Spacer(modifier = Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = string,
                onValueChange = { string = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = stringResource(placeholderRes)) }
            )
        },
        confirmButton = {
            DoneButton(
                enabled = validator(string),
                onClick = { onDone(string) }
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) }
    )
}

@Composable
private fun MinBufferPreference(
    currentValue: Int,
    onClick: () -> Unit
) {
    ClickablePreferenceItem(
        title = stringResource(R.string.min_buffer_title),
        description = stringResource(R.string.milliseconds, currentValue),
        icon = NextIcons.Buffer,
        onClick = onClick
    )
}

@Composable
private fun MaxBufferPreference(
    currentValue: Int,
    onClick: () -> Unit
) {
    ClickablePreferenceItem(
        title = stringResource(R.string.max_buffer_title),
        description = stringResource(R.string.milliseconds, currentValue),
        icon = NextIcons.Buffer,
        onClick = onClick
    )
}

@Composable
private fun BufferForPlaybackPreference(
    currentValue: Int,
    onClick: () -> Unit
) {
    ClickablePreferenceItem(
        title = stringResource(R.string.buffer_for_playback_title),
        description = stringResource(R.string.milliseconds, currentValue),
        icon = NextIcons.Buffer,
        onClick = onClick
    )
}

@Composable
private fun BufferForPlaybackAfterRebufferPreference(
    currentValue: Int,
    onClick: () -> Unit
) {
    ClickablePreferenceItem(
        title = stringResource(R.string.buffer_for_playback_after_rebuffer_title),
        description = stringResource(R.string.milliseconds, currentValue),
        icon = NextIcons.Buffer,
        onClick = onClick
    )
}

@Composable
private fun HttpUserAgentPreference(
    currentValue: String?,
    onClick: () -> Unit
) {
    ClickablePreferenceItem(
        title = stringResource(R.string.http_user_agent_title),
        description = currentValue ?: stringResource(R.string.empty),
        icon = NextIcons.HttpUserAgent,
        onClick = onClick
    )
}

@Composable
private fun HttpHeadersPreference(
    currentValue: Map<String, String>,
    onClick: () -> Unit
) {
    ClickablePreferenceItem(
        title = stringResource(R.string.http_headers_title),
        description = if (currentValue.isNotEmpty()) httpHeadersAsKeyValueString(currentValue) else stringResource(R.string.empty),
        icon = NextIcons.HttpUserAgent,
        onClick = onClick
    )
}

private fun httpHeadersAsKeyValueString(values: Map<String, String>): String {
    return values.entries.joinToString(
        separator = ",",
        transform = { (key, value) ->
            "$key=$value"
        }
    )
}

private fun parseHttpHeadersString(string: String): Map<String, String>? {
    if (string.isBlank()) return emptyMap()
    return string.split(',').associate { part ->
        val parts = part.split('=')
        if (parts.size != 2) return null
        val key = parts[0]
        val value = parts[1]
        if (key.isBlank()) return null
        key to value
    }
}
