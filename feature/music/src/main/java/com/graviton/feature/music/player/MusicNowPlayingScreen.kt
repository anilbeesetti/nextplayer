package com.graviton.feature.music.player

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.music.artwork.MediaArtwork
import com.graviton.feature.music.lyrics.LyricsDocument
import com.graviton.feature.music.rememberMusicPlaybackSnapshot
import com.graviton.feature.music.rememberMusicSession
import com.graviton.feature.player.service.getAudioSessionId
import com.graviton.feature.player.service.getSkipSilenceEnabled
import com.graviton.feature.player.service.setSkipSilenceEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MusicNowPlayingRoute(
    onClose: () -> Unit,
    viewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val connection = rememberMusicSession()
    val controller = connection.controller
    val snapshot = rememberMusicPlaybackSnapshot(controller)
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val remainingSleep by viewModel.remainingSleepMs.collectAsStateWithLifecycle()
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
        lyrics = lyrics,
        remainingSleepMs = remainingSleep,
        onClose = onClose,
        onSeek = { controller?.seekTo(it) },
        onTogglePlay = { if (controller?.isPlaying == true) controller.pause() else controller?.play() },
        onNext = { controller?.seekToNextMediaItem() },
        onPrevious = { controller?.seekToPreviousMediaItem() },
        onSleep = viewModel::startSleepTimer,
        onCancelSleep = viewModel::cancelSleepTimer,
        onPauseFromTimer = { controller?.pause() },
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
    lyrics: LyricsDocument,
    remainingSleepMs: Long,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSleep: (Int) -> Unit,
    onCancelSleep: () -> Unit,
    onPauseFromTimer: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var sliderPosition by remember { mutableFloatStateOf(positionMs.toFloat()) }
    var draggingSlider by remember { mutableStateOf(false) }
    LaunchedEffect(positionMs, draggingSlider) {
        if (!draggingSlider) sliderPosition = positionMs.toFloat()
    }
    LaunchedEffect(remainingSleepMs) {
        if (remainingSleepMs in 1..400) onPauseFromTimer()
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
                .weight(1f)
                .pointerInput(controller) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                dragAccum > 80f -> onPrevious()
                                dragAccum < -80f -> onNext()
                            }
                            dragAccum = 0f
                        },
                        onHorizontalDrag = { _, amount -> dragAccum += amount },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            MediaArtwork(
                artworkUri = artworkUri,
                mediaUri = mediaUri,
                artworkData = artworkData,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp)),
                corner = 28.dp,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            text = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" • "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

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
            TextButton(onClick = { showQueue = true }) { Icon(NextIcons.QueueMusic, null); Spacer(Modifier.size(6.dp)); Text("Queue") }
            TextButton(onClick = { showLyrics = true }) { Icon(NextIcons.Lyrics, null); Spacer(Modifier.size(6.dp)); Text("Lyrics") }
            TextButton(onClick = { showSleep = true }) { Icon(NextIcons.Timer, null); Spacer(Modifier.size(6.dp)); Text(if (remainingSleepMs > 0) formatClock(remainingSleepMs) else "Sleep") }
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

    if (showQueue && controller != null) {
        QueueSheet(controller = controller, onDismiss = { showQueue = false })
    }
    if (showLyrics) {
        LyricsSheet(lyrics = lyrics, positionMs = positionMs, onDismiss = { showLyrics = false })
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(controller: MediaController, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val items = remember(controller.mediaItemCount, controller.currentMediaItemIndex) {
        (0 until controller.mediaItemCount).map { index ->
            val item = controller.getMediaItemAt(index)
            Triple(index, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty())
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Text("Queue", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn {
            itemsIndexed(items) { _, (index, title, artist) ->
                val active = index == controller.currentMediaItemIndex
                TextButton(
                    onClick = { controller.seekToDefaultPosition(index); controller.play() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(title.ifBlank { "Item ${index + 1}" }, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        if (artist.isNotBlank()) Text(artist, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSheet(lyrics: LyricsDocument, positionMs: Long, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val active = lyrics.lineAt(positionMs)
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem(active)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Text("Lyrics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        when {
            lyrics.isSynced -> LazyColumn(state = listState, modifier = Modifier.height(360.dp).padding(horizontal = 20.dp)) {
                itemsIndexed(lyrics.lines) { index, line ->
                    Text(
                        text = line.text,
                        fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            !lyrics.unsynced.isNullOrBlank() -> Text(lyrics.unsynced, modifier = Modifier.padding(20.dp))
            else -> Text("No lyrics found for this track.", modifier = Modifier.padding(20.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
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

private fun formatClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val seconds = total % 60
    val minutes = (total / 60) % 60
    val hours = total / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
