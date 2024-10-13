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
import dev.anilbeesetti.nextplayer.feature.player.extensions.toSubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.extensions.updateSubtitleConfigurations
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

    private var isMediaItemReady = false
    private var currentMediaItem = MutableStateFlow<MediaItem?>(null)
    private var currentVideoState: VideoState? = null
    private var currentMediaState: MediaState? = null

    init {
        serviceScope.launch {
            while (true) {
                updateCurrentMediaState()
                delay(1000)
            }
        }

        currentMediaItem.onEach { mediaItem ->
            if (mediaItem == null) return@onEach
            mediaRepository.externalSubtitlesFlowForVideo(mediaItem.mediaId).onEach { externalSubtitles ->
                if (externalSubtitles.isNotEmpty()) {
                    val subtitles = externalSubtitles.map { subtitleUri ->
                        subtitleUri.toSubtitleConfiguration(
                            context = this@PlayerService,
                            subtitleEncoding = playerPreferences.subtitleTextEncoding,
                        )
                    }

                    mediaSession?.player?.updateSubtitleConfigurations(subtitles)
                }
            }.launchIn(serviceScope)
        }.launchIn(serviceScope)
    }

    private val playbackStateListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveCurrentMediaState()
            currentMediaItem.update { mediaItem }
            isMediaItemReady = false
            if (mediaItem != null) {
                currentVideoState = runBlocking { mediaRepository.getVideoState(mediaItem.mediaId) }
                if (playerPreferences.resume == Resume.YES) {
                    currentVideoState?.position?.let { mediaSession?.player?.seekTo(it) }
                }
            }
            super.onMediaItemTransition(mediaItem, reason)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            updateCurrentMediaState()
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            when (playbackState) {
                Player.STATE_READY -> {
                    if (!isMediaItemReady) {
                        currentVideoState?.let { state ->
                            mediaSession?.player?.switchTrack(C.TRACK_TYPE_AUDIO, state.audioTrackIndex)
                            mediaSession?.player?.switchTrack(C.TRACK_TYPE_TEXT, state.subtitleTrackIndex)
                            state.playbackSpeed?.let { mediaSession?.player?.setPlaybackSpeed(it) }
                        }
                        isMediaItemReady = true
                    }
                }

                else -> {}
            }
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
        val player = mediaSession?.player!!
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
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
        if (player.currentMediaItem?.mediaId != currentMediaItem.value?.mediaId) return
        currentMediaState = player.getCurrentMediaItemData()
    }

    private fun saveCurrentMediaState() {
        if (!isMediaItemReady) return
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