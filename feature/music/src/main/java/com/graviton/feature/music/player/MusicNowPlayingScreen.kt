package com.graviton.feature.music.player

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.graviton.core.model.ApplicationPreferences
import com.graviton.core.model.MusicBackgroundStyle
import com.graviton.core.model.NowPlayingStyle
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.music.artwork.MediaArtwork
import com.graviton.feature.music.lyrics.LyricsDocument
import com.graviton.feature.music.rememberMusicPlaybackSnapshot
import com.graviton.feature.music.rememberMusicSession
import com.graviton.feature.player.service.getAudioSessionId
import com.graviton.feature.player.service.cancelSleepTimer
import com.graviton.feature.player.service.getSkipSilenceEnabled
import com.graviton.feature.player.service.getSleepTimerRemainingMs
import com.graviton.feature.player.service.setSkipSilenceEnabled
import com.graviton.feature.player.service.startSleepTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun MusicNowPlayingRoute(
    onClose: () -> Unit,
    viewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val connection = rememberMusicSession()
    val controller = connection.controller
    val snapshot = rememberMusicPlaybackSnapshot(controller)
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var remainingSleep by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(controller) {
        while (controller != null) {
            remainingSleep = runCatching { controller.getSleepTimerRemainingMs() }.getOrDefault(0L)
            delay(1_000)
        }
    }
    LaunchedEffect(snapshot?.mediaId) {
        viewModel.loadLyrics(snapshot?.mediaId, snapshot?.title)
    }
    LaunchedEffect(snapshot) {
        while (snapshot != null) {
            snapshot.refresh()
            delay(400)
        }
    }
    MusicNowPlayingScreen(
        controller = controller,
        title = snapshot?.title.orEmpty().ifBlank { "Not playing" },
        artist = snapshot?.artist.orEmpty(),
        album = snapshot?.album.orEmpty(),
        artworkUri = snapshot?.artworkUri?.toString(),
        mediaUri = snapshot?.mediaId,
        artworkData = snapshot?.artworkData,
        isPlaying = snapshot?.isPlaying == true,
        positionMs = snapshot?.positionMs ?: 0L,
        durationMs = snapshot?.durationMs ?: 0L,
        audioFormat = snapshot?.audioFormat,
        lyrics = lyrics,
        remainingSleepMs = remainingSleep,
        preferences = preferences,
        onClose = onClose,
        onSeek = { controller?.seekTo(it) },
        onTogglePlay = { if (controller?.isPlaying == true) controller.pause() else controller?.play() },
        onNext = { controller?.seekToNextMediaItem() },
        onPrevious = { controller?.seekToPreviousMediaItem() },
        onSleep = { minutes ->
            scope.launch { controller?.startSleepTimer(minutes * 60_000L, fadeMs = 10_000L) }
        },
        onCancelSleep = { scope.launch { controller?.cancelSleepTimer() } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicNowPlayingScreen(
    controller: MediaController?,
    title: String,
    artist: String,
    album: String,
    artworkUri: String?,
    mediaUri: String?,
    artworkData: ByteArray?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    audioFormat: androidx.media3.common.Format?,
    lyrics: LyricsDocument,
    remainingSleepMs: Long,
    preferences: ApplicationPreferences,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSleep: (Int) -> Unit,
    onCancelSleep: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAudioInfo by remember { mutableStateOf(false) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var sliderPosition by remember { mutableFloatStateOf(positionMs.toFloat()) }
    var draggingSlider by remember { mutableStateOf(false) }
    LaunchedEffect(positionMs, draggingSlider) {
        if (!draggingSlider) sliderPosition = positionMs.toFloat()
    }
    val style = remember(preferences.musicNowPlayingStyle) { PlayerStyleTokens.forStyle(preferences.musicNowPlayingStyle) }
    val useArtworkBackground = preferences.musicDynamicArtworkBackground &&
        preferences.musicBackgroundStyle in setOf(MusicBackgroundStyle.ARTWORK, MusicBackgroundStyle.BLURRED_ARTWORK)

    Box(
        Modifier
            .fillMaxSize()
            .background(if (preferences.musicBackgroundStyle == MusicBackgroundStyle.BLACK) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        if (useArtworkBackground) {
            MediaArtwork(
                artworkUri = artworkUri,
                mediaUri = mediaUri,
                artworkData = artworkData,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (preferences.musicBackgroundStyle == MusicBackgroundStyle.BLURRED_ARTWORK) Modifier.blur(preferences.musicBlurIntensity.dp) else Modifier)
                    .alpha(0.2f),
                corner = 0.dp,
            )
        }
        Column(
            modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(NextIcons.ArrowBack, contentDescription = "Close player")
            }
            Text(
                text = "Now playing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(NextIcons.Settings, contentDescription = "Music settings")
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(style.artworkWeight)
                .then(
                    if (preferences.musicGestureControls) {
                        Modifier.pointerInput(controller, preferences.musicSeekGestureSensitivity) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val threshold = 80f / preferences.musicSeekGestureSensitivity.coerceIn(0.5f, 2f)
                                    when {
                                        dragAccum > threshold -> onPrevious()
                                        dragAccum < -threshold -> onNext()
                                    }
                                    dragAccum = 0f
                                },
                                onHorizontalDrag = { _, amount -> dragAccum += amount },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            MediaArtwork(
                artworkUri = artworkUri,
                mediaUri = mediaUri,
                artworkData = artworkData,
                modifier = Modifier
                    .fillMaxWidth((preferences.musicArtworkSizePercent / 100f).coerceIn(0.7f, 1f) * style.artworkScale)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(preferences.musicArtworkCornerRadius.dp)),
                corner = preferences.musicArtworkCornerRadius.dp,
            )
        }

        Spacer(Modifier.height(style.metadataSpacing))
        Text(
            title,
            style = if (preferences.musicNowPlayingStyle == NowPlayingStyle.EXPRESSIVE) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (preferences.musicShowMetadata) {
            Text(
                text = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (preferences.musicShowNextTrack && controller != null && controller.hasNextMediaItem()) {
            val nextTitle = controller.getMediaItemAt(controller.nextMediaItemIndex).mediaMetadata.title?.toString()
            if (!nextTitle.isNullOrBlank()) Text("Next • $nextTitle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (preferences.musicShowCodecInfo) {
            audioFormat?.technicalSummary()?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Slider(
            value = sliderPosition.coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()),
            onValueChange = {
                draggingSlider = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                draggingSlider = false
                onSeek(sliderPosition.toLong())
            },
            valueRange = 0f..(durationMs.coerceAtLeast(1).toFloat()),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(positionMs), style = MaterialTheme.typography.labelMedium)
            Text(formatClock(durationMs), style = MaterialTheme.typography.labelMedium)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = {
                controller?.shuffleModeEnabled = !(controller?.shuffleModeEnabled ?: false)
            }) {
                Icon(
                    NextIcons.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (controller?.shuffleModeEnabled == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onPrevious) { Icon(NextIcons.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp)) }
            FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(72.dp), shape = CircleShape) {
                Icon(
                    imageVector = if (isPlaying) NextIcons.Pause else NextIcons.Play,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = onNext) { Icon(NextIcons.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp)) }
            IconButton(onClick = {
                val next = when (controller?.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                controller?.repeatMode = next
            }) {
                Icon(
                    imageVector = if (controller?.repeatMode == Player.REPEAT_MODE_ONE) NextIcons.RepeatOne else NextIcons.Repeat,
                    contentDescription = "Repeat",
                    tint = if ((controller?.repeatMode ?: Player.REPEAT_MODE_OFF) != Player.REPEAT_MODE_OFF) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (preferences.musicShowQueueButton) TextButton(onClick = { showQueue = true }) { Icon(NextIcons.QueueMusic, null); Spacer(Modifier.size(6.dp)); Text("Queue") }
            if (preferences.musicShowLyricsButton) TextButton(onClick = { showLyrics = true }) { Icon(NextIcons.Lyrics, null); Spacer(Modifier.size(6.dp)); Text("Lyrics") }
            if (preferences.musicShowSleepTimerButton) TextButton(onClick = { showSleep = true }) { Icon(NextIcons.Timer, null); Spacer(Modifier.size(6.dp)); Text(if (remainingSleepMs > 0) formatClock(remainingSleepMs) else "Sleep") }
            TextButton(onClick = { showAudioInfo = true }) { Icon(NextIcons.Info, null); Spacer(Modifier.size(6.dp)); Text("Info") }
            TextButton(onClick = {
                scope.launch {
                    val sessionId = runCatching { controller?.getAudioSessionId() }.getOrNull()
                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        sessionId?.takeIf { it > 0 }?.let { putExtra(AudioEffect.EXTRA_AUDIO_SESSION, it) }
                    }
                    runCatching { context.startActivity(intent) }.onFailure {
                        Toast.makeText(context, "No equalizer is installed", Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Icon(NextIcons.Equalizer, null); Spacer(Modifier.size(6.dp)); Text("EQ") }
        }
    }
    }

    if (showQueue && controller != null) {
        QueueSheet(controller = controller, onDismiss = { showQueue = false })
    }
    if (showLyrics) {
        LyricsSheet(lyrics = lyrics, positionMs = positionMs, onSeek = onSeek, onDismiss = { showLyrics = false })
    }
    if (showSleep) {
        SleepDialog(
            remainingMs = remainingSleepMs,
            onMinutes = { onSleep(it); showSleep = false },
            onCancel = { onCancelSleep(); showSleep = false },
            onDismiss = { showSleep = false },
        )
    }
    if (showSettings && controller != null) {
        MusicSettingsSheet(controller = controller, onDismiss = { showSettings = false })
    }
    if (showAudioInfo) {
        AudioInformationSheet(audioFormat, durationMs, onDismiss = { showAudioInfo = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(controller: MediaController, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queue = remember(controller) {
        (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it) }.toMutableStateList()
    }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index in queue.indices && to.index in queue.indices) {
            val moved = queue.removeAt(from.index)
            queue.add(to.index, moved)
            controller.moveMediaItem(from.index, to.index)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { controller.clearMediaItems(); queue.clear(); onDismiss() }) { Text("Clear") }
        }
        LazyColumn(state = listState) {
            itemsIndexed(queue, key = { _, item -> item.mediaId }) { index, item ->
                ReorderableItem(reorderState, key = item.mediaId) {
                    val active = item.mediaId == controller.currentMediaItem?.mediaId
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                            Icon(NextIcons.Reorder, contentDescription = "Drag to reorder")
                        }
                        TextButton(
                            onClick = { controller.seekToDefaultPosition(index); controller.play() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(item.mediaMetadata.title?.toString().orEmpty().ifBlank { "Item ${index + 1}" }, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                                item.mediaMetadata.artist?.toString()?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        IconButton(onClick = { controller.removeMediaItem(index); queue.removeAt(index) }) {
                            Icon(NextIcons.Delete, contentDescription = "Remove from queue")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSheet(lyrics: LyricsDocument, positionMs: Long, onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val active = lyrics.lineAt(positionMs)
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem(active)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text("Lyrics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        when {
            lyrics.isSynced -> LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
                itemsIndexed(lyrics.lines) { index, line ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .pointerInput(line.timeMs) {
                                detectTapGestures { onSeek(line.timeMs) }
                            }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                    ) {
                        KaraokeLyricLine(line = line, positionMs = positionMs, active = index == active)
                        line.translation?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            !lyrics.unsynced.isNullOrBlank() -> Text(lyrics.unsynced, modifier = Modifier.padding(20.dp))
            else -> Text("No lyrics found for this track.", modifier = Modifier.padding(20.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KaraokeLyricLine(
    line: com.graviton.feature.music.lyrics.LyricLine,
    positionMs: Long,
    active: Boolean,
) {
    val highlighted = MaterialTheme.colorScheme.primary
    val pending = MaterialTheme.colorScheme.onSurfaceVariant
    val text = remember(line, positionMs, active, highlighted, pending) {
        if (!active || line.words.isEmpty()) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = if (active) highlighted else pending)) { append(line.text) }
            }
        } else {
            buildAnnotatedString {
                line.words.forEach { word ->
                    val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
                    val progress = ((positionMs - word.startMs).toFloat() / duration).coerceIn(0f, 1f)
                    val split = (word.text.length * progress).toInt().coerceIn(0, word.text.length)
                    withStyle(SpanStyle(color = highlighted, fontWeight = FontWeight.Bold)) {
                        append(word.text.substring(0, split))
                    }
                    withStyle(SpanStyle(color = pending)) { append(word.text.substring(split)) }
                }
            }
        }
    }
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SleepDialog(
    remainingMs: Long,
    onMinutes: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                if (remainingMs > 0) Text("Remaining ${formatClock(remainingMs)}")
                listOf(5, 15, 30, 45, 60).forEach { minutes ->
                    TextButton(onClick = { onMinutes(minutes) }, modifier = Modifier.fillMaxWidth()) {
                        Text("$minutes minutes")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("Cancel timer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioInformationSheet(
    format: androidx.media3.common.Format?,
    durationMs: Long,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Audio information", style = MaterialTheme.typography.titleLarge)
            AudioInfoRow("Codec", format?.codecs ?: format?.sampleMimeType)
            AudioInfoRow("Bitrate", format?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" })
            AudioInfoRow("Sample rate", format?.sampleRate?.takeIf { it > 0 }?.let { "${it / 1000f} kHz" })
            AudioInfoRow("Channels", format?.channelCount?.takeIf { it > 0 }?.toString())
            AudioInfoRow("Duration", formatClock(durationMs).takeIf { durationMs > 0 })
            AudioInfoRow("Decoder", "Media3 / ${format?.sampleMimeType ?: "unknown"}")
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AudioInfoRow(label: String, value: String?) {
    if (value != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun androidx.media3.common.Format.technicalSummary(): String? {
    val values = buildList {
        (codecs ?: sampleMimeType)?.substringAfterLast('.')?.uppercase()?.let(::add)
        bitrate.takeIf { it > 0 }?.let { add("${it / 1000} kbps") }
        sampleRate.takeIf { it > 0 }?.let { add("${it / 1000f} kHz") }
    }
    return values.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicSettingsSheet(controller: MediaController, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var skipSilence by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(controller.playbackParameters.speed) }
    LaunchedEffect(controller) {
        skipSilence = runCatching { controller.getSkipSilenceEnabled() }.getOrDefault(false)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Playback", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = {
                skipSilence = !skipSilence
                scope.launch { controller.setSkipSilenceEnabled(skipSilence) }
            }) {
                Text(if (skipSilence) "Skip silence on" else "Skip silence off")
            }
            Text("Speed ${"%.2f".format(speed)}×")
            Slider(
                value = speed,
                onValueChange = { speed = it },
                onValueChangeFinished = { controller.setPlaybackSpeed(speed) },
                valueRange = 0.5f..2f,
            )
        }
    }
}

private data class PlayerStyleTokens(
    val artworkScale: Float,
    val artworkWeight: Float,
    val metadataSpacing: androidx.compose.ui.unit.Dp,
) {
    companion object {
        fun forStyle(style: NowPlayingStyle): PlayerStyleTokens = when (style) {
            NowPlayingStyle.CLASSIC -> PlayerStyleTokens(1f, 1f, 20.dp)
            NowPlayingStyle.EXPRESSIVE -> PlayerStyleTokens(0.96f, 1.08f, 24.dp)
            NowPlayingStyle.BLUR -> PlayerStyleTokens(0.94f, 1f, 20.dp)
            NowPlayingStyle.M3 -> PlayerStyleTokens(0.9f, 0.95f, 16.dp)
            NowPlayingStyle.PLAIN -> PlayerStyleTokens(0.82f, 0.85f, 12.dp)
            NowPlayingStyle.PEEK -> PlayerStyleTokens(0.74f, 0.68f, 10.dp)
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val seconds = total % 60
    val minutes = (total / 60) % 60
    val hours = total / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
