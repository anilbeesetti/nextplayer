package dev.anilbeesetti.nextplayer.feature.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.DecoderPriority
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.feature.player.extensions.MediaState
import dev.anilbeesetti.nextplayer.feature.player.extensions.getCurrentMediaItemData
import dev.anilbeesetti.nextplayer.feature.player.extensions.switchTrack
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val END_POSITION_OFFSET = 5L

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerService : MediaSessionService() {
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaSession? = null

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var mediaRepository: MediaRepository

    private val playerPreferences: PlayerPreferences
        get() = runBlocking { preferencesRepository.playerPreferences.first() }

    private var currentMediaItem: MediaItem? = null
    private var currentVideoState: VideoState? = null
    private var currentMediaState: MediaState? = null
    private var areTracksRestored: Boolean = false

    init {
        serviceScope.launch {
            while (true) {
                updateCurrentMediaState()
                delay(1000)
            }
        }
    }

    private val playbackStateListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveCurrentMediaState()
            currentMediaItem = mediaItem
            areTracksRestored = false
            if (mediaItem != null) {
                serviceScope.launch {
                    currentVideoState = mediaRepository.getVideoState(mediaItem.mediaId)
                    withContext(Dispatchers.Main.immediate) {
                        if (playerPreferences.resume == Resume.YES) {
                            currentVideoState?.position?.let { mediaSession?.player?.seekTo(it) }
                        }
                    }
                }
            }
            super.onMediaItemTransition(mediaItem, reason)
        }

        override fun onRenderedFirstFrame() {
            super.onRenderedFirstFrame()
            currentVideoState?.let { state ->
                mediaSession?.player?.switchTrack(C.TRACK_TYPE_AUDIO, state.audioTrackIndex)
                mediaSession?.player?.switchTrack(C.TRACK_TYPE_TEXT, state.subtitleTrackIndex)
                state.playbackSpeed?.let { mediaSession?.player?.setPlaybackSpeed(it) }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            updateCurrentMediaState()
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
        }
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            saveCurrentMediaState()
            super.onDisconnected(session, controller)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onCreate() {
        super.onCreate()
        val renderersFactory = NextRenderersFactory(applicationContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                when (playerPreferences.decoderPriority) {
                    DecoderPriority.DEVICE_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    DecoderPriority.PREFER_DEVICE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    DecoderPriority.PREFER_APP -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                },
            )

        val trackSelector = DefaultTrackSelector(applicationContext).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(playerPreferences.preferredAudioLanguage)
                    .setPreferredTextLanguage(playerPreferences.preferredSubtitleLanguage),
            )
        }

        val player = ExoPlayer.Builder(applicationContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                playerPreferences.requireAudioFocus,
            )
            .setHandleAudioBecomingNoisy(playerPreferences.pauseOnHeadsetDisconnect)
            .build()
            .also {
                it.addListener(playbackStateListener)
            }

        try {
            mediaSession = MediaSession.Builder(this, player).apply {
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@PlayerService,
                        0,
                        Intent(this@PlayerService, PlayerActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setCallback(mediaSessionCallback)
            }.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        println("PlayerService: onTaskRemoved")
        val player = mediaSession?.player!!
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        println("PlayerService: onDestroy")
        super.onDestroy()
        serviceScope.cancel()
        saveCurrentMediaState()
        mediaSession?.run {
            player.removeListener(playbackStateListener)
            player.release()
            release()
            mediaSession = null
        }
    }

    private fun updateCurrentMediaState() {
        val player = mediaSession?.player ?: return
        if (player.currentMediaItem == null) return
        if (player.currentMediaItem?.mediaId != currentMediaItem?.mediaId) return
        currentMediaState = player.getCurrentMediaItemData()
    }

    private fun saveCurrentMediaState() {
        currentMediaState?.let { data ->
            mediaRepository.saveMediumState(
                uri = data.uri,
                position = data.position.takeIf { it < data.duration - END_POSITION_OFFSET } ?: C.TIME_UNSET,
                audioTrackIndex = data.audioTrackIndex,
                subtitleTrackIndex = data.subtitleTrackIndex,
                playbackSpeed = data.playbackSpeed,
            )
            currentMediaState = null
        }
    }
}
