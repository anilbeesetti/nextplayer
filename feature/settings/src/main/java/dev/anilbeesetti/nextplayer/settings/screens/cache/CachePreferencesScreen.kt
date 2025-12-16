package dev.anilbeesetti.nextplayer.settings.screens.cache

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.common.cache.StreamCacheStorage
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.StreamCacheClearPolicy
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialogWithDoneCancelAndResetButtons
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.RadioTextButton
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.settings.composables.OptionsDialog
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSubtitle
import dev.anilbeesetti.nextplayer.settings.extensions.name
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachePreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: CachePreferencesViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var cacheSizeText by remember { mutableStateOf(Utils.formatFileSize(0L)) }
    var isClearingCache by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheSizeText = withContext(Dispatchers.IO) {
            Utils.formatFileSize(StreamCacheStorage.sizeBytes(context))
        }
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.cache),
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
            PreferenceSubtitle(text = stringResource(id = R.string.stream_cache_clear_policy))
            CacheClearPolicySetting(
                currentPolicy = preferences.streamCacheClearPolicy,
                onClick = { viewModel.showDialog(CachePreferenceDialog.CacheClearPolicyDialog) },
            )

            ClearCacheSetting(
                cacheSizeText = cacheSizeText,
                isClearingCache = isClearingCache,
                onClick = {
                    if (isClearingCache) return@ClearCacheSetting
                    isClearingCache = true
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            StreamCacheStorage.clear(context)
                        }
                        cacheSizeText = withContext(Dispatchers.IO) {
                            Utils.formatFileSize(StreamCacheStorage.sizeBytes(context))
                        }
                        isClearingCache = false
                    }
                },
            )

            PreferenceSubtitle(text = stringResource(id = R.string.buffering))
            BufferSettingsPreference(
                preferences = preferences,
                onClick = { viewModel.showDialog(CachePreferenceDialog.BufferSettingsDialog) },
            )
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                CachePreferenceDialog.CacheClearPolicyDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.stream_cache_clear_policy),
                        onDismissClick = viewModel::hideDialog,
                    ) {
                        items(StreamCacheClearPolicy.entries.toTypedArray()) { policy ->
                            RadioTextButton(
                                text = policy.name(),
                                selected = (policy == preferences.streamCacheClearPolicy),
                                onClick = {
                                    viewModel.updateStreamCacheClearPolicy(policy)
                                    viewModel.hideDialog()
                                },
                            )
                        }
                    }
                }

                CachePreferenceDialog.BufferSettingsDialog -> {
                    BufferSettingsDialog(
                        preferences = preferences,
                        onDoneClick = { minMs, maxMs, startMs, rebufferMs, chunkBytes, concurrency ->
                            viewModel.updateBufferSettings(
                                minBufferMs = minMs,
                                maxBufferMs = maxMs,
                                bufferForPlaybackMs = startMs,
                                bufferForPlaybackAfterRebufferMs = rebufferMs,
                                rangeStreamChunkSizeBytes = chunkBytes,
                                segmentConcurrentDownloads = concurrency,
                            )
                            viewModel.hideDialog()
                        },
                        onDismissClick = viewModel::hideDialog,
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheClearPolicySetting(
    currentPolicy: StreamCacheClearPolicy,
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(R.string.stream_cache_clear_policy),
        description = currentPolicy.name(),
        icon = NextIcons.Settings,
        onClick = onClick,
    )
}

@Composable
private fun ClearCacheSetting(
    cacheSizeText: String,
    isClearingCache: Boolean,
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(R.string.clear_cache),
        description = cacheSizeText,
        icon = NextIcons.Delete,
        onClick = onClick,
        enabled = !isClearingCache,
    )
}

@Composable
private fun BufferSettingsPreference(
    preferences: PlayerPreferences,
    onClick: () -> Unit,
) {
    val minSec = preferences.minBufferMs / 1000
    val maxSec = preferences.maxBufferMs / 1000
    val chunkKb = (preferences.rangeStreamChunkSizeBytes / 1024L).toInt()
    val concurrency = preferences.segmentConcurrentDownloads.coerceAtLeast(1)
    ClickablePreferenceItem(
        title = stringResource(R.string.buffer_settings),
        description = "${minSec}s–${maxSec}s, ${chunkKb}KB, x$concurrency",
        icon = NextIcons.Settings,
        onClick = onClick,
    )
}

@Composable
private fun BufferSettingsDialog(
    preferences: PlayerPreferences,
    onDoneClick: (Int, Int, Int, Int, Long, Int) -> Unit,
    onDismissClick: () -> Unit,
) {
    val defaults = remember { PlayerPreferences() }

    var minBufferSecText by remember { mutableStateOf((preferences.minBufferMs / 1000).toString()) }
    var maxBufferSecText by remember { mutableStateOf((preferences.maxBufferMs / 1000).toString()) }
    var startBufferMsText by remember { mutableStateOf(preferences.bufferForPlaybackMs.toString()) }
    var rebufferMsText by remember { mutableStateOf(preferences.bufferForPlaybackAfterRebufferMs.toString()) }
    var rangeChunkSizeKbText by remember { mutableStateOf((preferences.rangeStreamChunkSizeBytes / 1024L).toString()) }
    var segmentConcurrentDownloadsText by remember { mutableStateOf(preferences.segmentConcurrentDownloads.toString()) }

    NextDialogWithDoneCancelAndResetButtons(
        title = stringResource(R.string.buffer_settings),
        onDoneClick = {
            val minBufferSec = minBufferSecText.toIntOrNull() ?: (preferences.minBufferMs / 1000)
            val maxBufferSec = maxBufferSecText.toIntOrNull() ?: (preferences.maxBufferMs / 1000)
            val startBufferMs = startBufferMsText.toIntOrNull() ?: preferences.bufferForPlaybackMs
            val rebufferMs = rebufferMsText.toIntOrNull() ?: preferences.bufferForPlaybackAfterRebufferMs
            val rangeChunkKb = rangeChunkSizeKbText.toLongOrNull() ?: (preferences.rangeStreamChunkSizeBytes / 1024L)
            val concurrentDownloads = segmentConcurrentDownloadsText.toIntOrNull() ?: preferences.segmentConcurrentDownloads

            val normalizedMinSec = minBufferSec.coerceAtLeast(1)
            val normalizedMaxSec = max(maxBufferSec, normalizedMinSec)

            onDoneClick(
                normalizedMinSec * 1000,
                normalizedMaxSec * 1000,
                startBufferMs.coerceAtLeast(0),
                rebufferMs.coerceAtLeast(0),
                rangeChunkKb.coerceAtLeast(1L) * 1024L,
                concurrentDownloads.coerceAtLeast(1),
            )
        },
        onResetClick = {
            minBufferSecText = (defaults.minBufferMs / 1000).toString()
            maxBufferSecText = (defaults.maxBufferMs / 1000).toString()
            startBufferMsText = defaults.bufferForPlaybackMs.toString()
            rebufferMsText = defaults.bufferForPlaybackAfterRebufferMs.toString()
            rangeChunkSizeKbText = (defaults.rangeStreamChunkSizeBytes / 1024L).toString()
            segmentConcurrentDownloadsText = defaults.segmentConcurrentDownloads.toString()
        },
        onDismissClick = onDismissClick,
        content = {
            NumberInputRow(
                title = stringResource(R.string.min_buffer),
                unit = "s",
                value = minBufferSecText,
                onValueChange = { minBufferSecText = it },
            )
            NumberInputRow(
                title = stringResource(R.string.max_buffer),
                unit = "s",
                value = maxBufferSecText,
                onValueChange = { maxBufferSecText = it },
            )
            NumberInputRow(
                title = stringResource(R.string.buffer_for_playback),
                unit = "ms",
                value = startBufferMsText,
                onValueChange = { startBufferMsText = it },
            )
            NumberInputRow(
                title = stringResource(R.string.buffer_for_playback_after_rebuffer),
                unit = "ms",
                value = rebufferMsText,
                onValueChange = { rebufferMsText = it },
            )
            NumberInputRow(
                title = stringResource(R.string.range_stream_chunk_size),
                unit = "KB",
                value = rangeChunkSizeKbText,
                onValueChange = { rangeChunkSizeKbText = it },
            )
            NumberInputRow(
                title = stringResource(R.string.segment_concurrent_downloads),
                unit = "",
                value = segmentConcurrentDownloadsText,
                onValueChange = { segmentConcurrentDownloadsText = it },
            )
        },
    )
}

@Composable
private fun NumberInputRow(
    title: String,
    unit: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = if (unit.isBlank()) title else "$title ($unit)"
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { new ->
                if (new.isEmpty() || new.all { it.isDigit() }) {
                    onValueChange(new)
                }
            },
            modifier = Modifier.width(120.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
