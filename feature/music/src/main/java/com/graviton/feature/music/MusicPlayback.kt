package com.graviton.feature.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.graviton.feature.player.service.PlayerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * A connection to the existing Graviton MediaSession. This class never creates an ExoPlayer; the
 * service remains the single owner of playback for music, video, notifications and background use.
 */
@Stable
class MusicSessionConnection(private val context: Context) {
    private var future: ListenableFuture<MediaController>? = null
    private var generation = 0L
    var controller: MediaController? by mutableStateOf(null)
        private set

    suspend fun connect() {
        if (controller != null) return
        val requestGeneration = generation
        val sessionToken = SessionToken(context.applicationContext, ComponentName(context, PlayerService::class.java))
        val currentFuture = future ?: MediaController.Builder(context.applicationContext, sessionToken)
            .buildAsync()
            .also { future = it }
        val resolvedController = currentFuture.await()
        if (requestGeneration == generation) controller = resolvedController
    }

    fun release() {
        generation++
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }
}

@Composable
fun rememberMusicSession(): MusicSessionConnection {
    val context = androidx.compose.ui.platform.LocalContext.current
    val connection = remember { MusicSessionConnection(context) }
    val scope = rememberCoroutineScope()
    LifecycleStartEffect(connection) {
        scope.launch { runCatching { connection.connect() } }
        onStopOrDispose { connection.release() }
    }
    return connection
}

@Stable
class MusicPlaybackSnapshot(private val player: Player) {
    var mediaId: String? by mutableStateOf(null)
        private set
    var title: String by mutableStateOf("")
        private set
    var artist: String by mutableStateOf("")
        private set
    var artworkUri: Uri? by mutableStateOf(null)
        private set
    var artworkData: ByteArray? by mutableStateOf(null)
        private set
    var album: String by mutableStateOf("")
        private set
    var isMusic: Boolean by mutableStateOf(false)
        private set
    var isPlaying: Boolean by mutableStateOf(false)
        private set
    var positionMs: Long by mutableLongStateOf(0L)
        private set
    var durationMs: Long by mutableLongStateOf(0L)
        private set
    var audioFormat: androidx.media3.common.Format? by mutableStateOf(null)
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) refresh()
        }
    }

    init {
        refresh()
        player.addListener(listener)
    }

    fun refresh() {
        val item = player.currentMediaItem
        mediaId = item?.mediaId
        title = item?.mediaMetadata?.title?.toString().orEmpty()
        artist = item?.mediaMetadata?.artist?.toString().orEmpty()
        artworkUri = item?.mediaMetadata?.artworkUri
        artworkData = item?.mediaMetadata?.artworkData
        album = item?.mediaMetadata?.albumTitle?.toString().orEmpty()
        isMusic = item?.mediaMetadata?.artist != null || item?.mediaMetadata?.albumTitle != null
        isPlaying = player.isPlaying
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.coerceAtLeast(0L)
        audioFormat = player.currentTracks.groups
            .firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.mediaTrackGroup
            ?.getFormat(0)
    }

    /** Refreshes only the position — the one value the player does not raise events for. */
    fun refreshPosition() {
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.coerceAtLeast(0L)
    }

    fun release() {
        player.removeListener(listener)
    }
}

/**
 * Observes the session and keeps a Compose-friendly snapshot of it.
 *
 * Everything except the playback position is event-driven. The position is the only value that
 * changes without an event, so it is the only thing polled — and only while playback is actually
 * running, so an idle or paused screen costs nothing.
 */
@Composable
fun rememberMusicPlaybackSnapshot(controller: MediaController?): MusicPlaybackSnapshot? {
    if (controller == null) return null
    val snapshot = remember(controller) { MusicPlaybackSnapshot(controller) }
    DisposableEffect(snapshot) {
        onDispose { snapshot.release() }
    }
    LaunchedEffect(snapshot, snapshot.isPlaying) {
        while (snapshot.isPlaying) {
            snapshot.refreshPosition()
            delay(POSITION_TICK_MS)
        }
    }
    return snapshot
}

private const val POSITION_TICK_MS = 500L

fun MediaController.playTracks(tracks: List<com.graviton.core.model.AudioTrack>, startIndex: Int = 0) {
    if (tracks.isEmpty()) return
    val items = tracks.map { it.toMediaItem() }
    setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
    prepare()
    play()
}

fun MediaController.playNext(track: com.graviton.core.model.AudioTrack) {
    val insertAt = if (mediaItemCount == 0) 0 else currentMediaItemIndex + 1
    addMediaItem(insertAt, track.toMediaItem())
}

fun MediaController.enqueue(track: com.graviton.core.model.AudioTrack) {
    addMediaItem(track.toMediaItem())
}

fun Context.openMusicPlayer(
    @Suppress("UNUSED_PARAMETER") track: com.graviton.core.model.AudioTrack,
    @Suppress("UNUSED_PARAMETER") queue: List<com.graviton.core.model.AudioTrack>,
) {
    startActivity(Intent(this, com.graviton.feature.music.player.MusicPlayerActivity::class.java))
}

private fun com.graviton.core.model.AudioTrack.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(uriString)
    .setMediaId(uriString)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(displayTitle)
            .setArtist(displayArtist)
            .setAlbumTitle(displayAlbum)
            .setArtworkUri(artworkUriString?.let(Uri::parse))
            .build(),
    )
    .build()
