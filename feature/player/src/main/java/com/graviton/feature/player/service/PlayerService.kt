package com.graviton.feature.player.service

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import androidx.media3.common.Player.DISCONTINUITY_REASON_REMOVE
import androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_UNDEFINED
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.graviton.core.common.extensions.deleteFiles
import com.graviton.core.common.extensions.getFilenameFromUri
import com.graviton.core.common.extensions.getLocalSubtitles
import com.graviton.core.common.extensions.getPath
import com.graviton.core.common.extensions.subtitleCacheDir
import com.graviton.core.data.repository.MediaRepository
import com.graviton.core.data.repository.MusicRepository
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.model.LoopMode
import com.graviton.core.model.PlayerPreferences
import com.graviton.core.model.Resume
import com.graviton.core.model.recordMusicPlay
import com.graviton.core.ui.R as coreUiR
import com.graviton.feature.player.PlayerActivity
import com.graviton.feature.player.R
import com.graviton.feature.player.audio.SessionEqualizer
import com.graviton.feature.player.decoder.DeviceDecoderCapabilities
import com.graviton.feature.player.decoder.PlaybackDiagnostics
import com.graviton.feature.player.decoder.toConfiguration
import com.graviton.feature.player.extensions.addAdditionalSubtitleConfiguration
import com.graviton.feature.player.extensions.audioTrackIndex
import com.graviton.feature.player.extensions.copy
import com.graviton.feature.player.extensions.getManuallySelectedTrackIndex
import com.graviton.feature.player.extensions.playbackSpeed
import com.graviton.feature.player.extensions.positionMs
import com.graviton.feature.player.extensions.setExtras
import com.graviton.feature.player.extensions.setIsScrubbingModeEnabled
import com.graviton.feature.player.extensions.subtitleDelayMilliseconds
import com.graviton.feature.player.extensions.subtitleSpeed
import com.graviton.feature.player.extensions.subtitleTrackIndex
import com.graviton.feature.player.extensions.switchTrack
import com.graviton.feature.player.extensions.uriToSubtitleConfiguration
import com.graviton.feature.player.extensions.videoZoom
import dagger.hilt.android.AndroidEntryPoint
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleDelayMilliseconds
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleSpeed
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerService : MediaLibraryService() {

    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaLibrarySession? = null
    private var artworkLoadJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var sleepTimerDeadlineMs: Long = 0L
    private var sleepTimerOriginalVolume: Float = 1f

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var mediaRepository: MediaRepository

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var imageLoader: ImageLoader

    private val playerPreferences: PlayerPreferences
        get() = preferencesRepository.playerPreferences.value

    private val customCommands = CustomCommands.asSessionCommands()

    private var isMediaItemReady = false
    private var currentQueueIsMusic = false
    private var lastCountedMusicId: String? = null
    private var listeningStartedAtMs: Long = 0L
    private var listeningMediaId: String? = null
    private val knownDurations = mutableMapOf<String, Long>()

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: SessionEqualizer? = null
    private var currentVolumeGain: Int = 0

    private val deviceDecoderCapabilities = DeviceDecoderCapabilities()

    /**
     * Log-only decoder telemetry. Built lazily because it needs the decoder mode, which is read from
     * preferences once the service starts.
     */
    private val playbackDiagnostics: PlaybackDiagnostics by lazy {
        PlaybackDiagnostics(
            capabilities = deviceDecoderCapabilities,
            decoderMode = playerPreferences.decoderMode,
            // Asked per MIME type rather than hardcoded: nextlib's FFmpeg build has no AV1 decoder,
            // so AV1 genuinely has no software fallback while H.264/HEVC/VP8/VP9 do.
            softwareDecoderAvailableFor = { mimeType -> FfmpegLibrary.supportsFormat(mimeType) },
        )
    }

    private val playbackStateListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            super.onTimelineChanged(timeline, reason)
            persistMusicQueue()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            flushListeningTime()
            if (mediaSession?.player?.isPlaying == true) beginListening(mediaItem?.mediaId)
            persistMusicQueue()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            isMediaItemReady = false
            loadArtworkForCurrentMediaItem()
            mediaItem?.mediaMetadata?.let { metadata ->
                mediaSession?.player?.run {
                    setPlaybackSpeed(metadata.playbackSpeed ?: playerPreferences.defaultPlaybackSpeed)
                    playerSpecificSubtitleDelayMilliseconds = metadata.subtitleDelayMilliseconds ?: 0L
                    playerSpecificSubtitleSpeed = metadata.subtitleSpeed ?: 1f
                }

                metadata.positionMs?.takeIf { playerPreferences.resume == Resume.YES }?.let {
                    mediaSession?.player?.seekTo(it)
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            val oldMediaItem = oldPosition.mediaItem ?: return

            when (reason) {
                DISCONTINUITY_REASON_SEEK,
                DISCONTINUITY_REASON_AUTO_TRANSITION,
                -> {
                    if (newPosition.mediaItem == null || oldMediaItem == newPosition.mediaItem) return

                    val updatedPosition = oldPosition.positionMs.takeIf { reason == DISCONTINUITY_REASON_SEEK } ?: C.TIME_UNSET
                    mediaSession?.player?.replaceMediaItem(
                        oldPosition.mediaItemIndex,
                        oldMediaItem.copy(positionMs = updatedPosition),
                    )
                    serviceScope.launch {
                        mediaRepository.updateMediumPosition(
                            uri = oldMediaItem.mediaId,
                            position = updatedPosition,
                        )
                    }
                }

                DISCONTINUITY_REASON_REMOVE -> {
                    serviceScope.launch {
                        val durationMs = oldMediaItem.mediaMetadata.durationMs
                            ?: knownDurations[oldMediaItem.mediaId]
                        val isAtEnd = durationMs != null && oldPosition.positionMs >= durationMs - 1000
                        mediaRepository.updateMediumPosition(
                            uri = oldMediaItem.mediaId,
                            position = if (isAtEnd) C.TIME_UNSET else oldPosition.positionMs,
                        )
                    }
                }

                else -> return
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)
            if (!isMediaItemReady && tracks.groups.isNotEmpty()) {
                isMediaItemReady = true

                if (!playerPreferences.rememberSelections) return
                mediaSession?.player?.mediaMetadata?.audioTrackIndex?.let {
                    mediaSession?.player?.switchTrack(C.TRACK_TYPE_AUDIO, it)
                }
                mediaSession?.player?.mediaMetadata?.subtitleTrackIndex?.let {
                    mediaSession?.player?.switchTrack(C.TRACK_TYPE_TEXT, it)
                }
            }
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            super.onTrackSelectionParametersChanged(parameters)
            val player = mediaSession?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return

            val audioTrackIndex = player.getManuallySelectedTrackIndex(C.TRACK_TYPE_AUDIO)
            val subtitleTrackIndex = player.getManuallySelectedTrackIndex(C.TRACK_TYPE_TEXT)

            if (audioTrackIndex != null) {
                serviceScope.launch {
                    mediaRepository.updateMediumAudioTrack(
                        uri = currentMediaItem.mediaId,
                        audioTrackIndex = audioTrackIndex,
                    )
                }
            }

            if (subtitleTrackIndex != null) {
                serviceScope.launch {
                    mediaRepository.updateMediumSubtitleTrack(
                        uri = currentMediaItem.mediaId,
                        subtitleTrackIndex = subtitleTrackIndex,
                    )
                }
            }

            player.replaceMediaItem(
                player.currentMediaItemIndex,
                currentMediaItem.copy(
                    audioTrackIndex = audioTrackIndex,
                    subtitleTrackIndex = subtitleTrackIndex,
                ),
            )
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            super.onPlaybackParametersChanged(playbackParameters)
            val player = mediaSession?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return
            val playbackSpeed = playbackParameters.speed

            serviceScope.launch {
                mediaRepository.updateMediumPlaybackSpeed(
                    uri = currentMediaItem.mediaId,
                    playbackSpeed = playbackSpeed,
                )
            }
            // Do not replace the currently rendered item just to persist speed. Replacing a
            // current item can tear down its decoder/surface during a seek or 2x gesture.
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                mediaSession?.player?.trackSelectionParameters = TrackSelectionParameters.DEFAULT
                mediaSession?.player?.setPlaybackSpeed(playerPreferences.defaultPlaybackSpeed)
            }

            if (playbackState == Player.STATE_READY) {
                mediaSession?.player?.let {
                    it.currentMediaItem?.mediaId?.let { mediaId ->
                        knownDurations[mediaId] = it.duration.coerceAtLeast(0L)
                    }
                    serviceScope.launch {
                        val mediaId = it.currentMediaItem?.mediaId ?: return@launch
                        mediaRepository.updateMediumLastPlayedTime(
                            uri = mediaId,
                            lastPlayedTime = System.currentTimeMillis(),
                        )
                        val audio = musicRepository.getTrack(mediaId)
                        if (audio != null && lastCountedMusicId != mediaId) {
                            lastCountedMusicId = mediaId
                            preferencesRepository.updateApplicationPreferences { prefs ->
                                prefs.recordMusicPlay(
                                    uri = mediaId,
                                    folderPath = audio.path.substringBeforeLast('/', ""),
                                    countPlay = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            if (isPlaying) beginListening(mediaSession?.player?.currentMediaItem?.mediaId) else flushListeningTime()
            mediaSession?.run {
                serviceScope.launch {
                    mediaRepository.updateMediumPosition(
                        uri = player.currentMediaItem?.mediaId ?: return@launch,
                        position = player.currentPosition,
                    )
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                if (mediaSession?.player?.repeatMode != Player.REPEAT_MODE_OFF) {
                    mediaSession?.player?.seekTo(0)
                    mediaSession?.player?.play()
                    return
                }
                mediaSession?.run {
                    player.clearMediaItems()
                    player.stop()
                }
                stopSelf()
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            super.onRepeatModeChanged(repeatMode)
            serviceScope.launch {
                preferencesRepository.updatePlayerPreferences {
                    it.copy(
                        loopMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> LoopMode.OFF
                            Player.REPEAT_MODE_ONE -> LoopMode.ONE
                            Player.REPEAT_MODE_ALL -> LoopMode.ALL
                            else -> LoopMode.OFF
                        },
                    )
                }
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            super.onAudioSessionIdChanged(audioSessionId)
            val musicPreferences = preferencesRepository.applicationPreferences.value
            if (!playerPreferences.enableVolumeBoost &&
                !musicPreferences.musicReplayGainEnabled &&
                !musicPreferences.musicEqualizerEnabled
            ) return
            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
            try {
                equalizer?.release()
                equalizer = SessionEqualizer.create(audioSessionId)?.also { engine ->
                    val configured = musicPreferences.musicEqualizerGainsDb
                    val gains = if (configured.size == engine.bandCount) configured.toFloatArray() else FloatArray(engine.bandCount)
                    engine.setGains(gains)
                    engine.setEnabled(musicPreferences.musicEqualizerEnabled)
                }
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                val replayGain = if (musicPreferences.musicReplayGainEnabled) {
                    (musicPreferences.musicReplayGainPreampDb * 100f).toInt().coerceAtLeast(0)
                } else {
                    0
                }
                if (currentVolumeGain > 0 || replayGain > 0) {
                    setEnhancerTargetGain(maxOf(currentVolumeGain, replayGain))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                loudnessEnhancer = null
            }
        }

        /**
         * Reaching this callback means every decoder already failed.
         *
         * Decoder-level retries are Media3's job: with decoder fallback enabled, MediaCodecRenderer
         * works through the device's decoders for the format and only surfaces an error once they
         * are all exhausted. Retrying the player again here would just loop on the same failure, so
         * this logs the failure with enough context to tell which decoder mode and stream caused it.
         */
        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            val failedPlayer = mediaSession?.player
            val mediaId = failedPlayer?.currentMediaItem?.mediaId
            Log.e(
                PlaybackDiagnostics.TAG,
                "Playback failed for $mediaId " +
                    "(code=${error.errorCode} ${error.errorCodeName}, " +
                    "decoderMode=${playerPreferences.decoderMode.name})",
                error,
            )
            // A corrupt or deleted song must not strand a background queue. Decoder fallback has
            // already been exhausted at this point, so advance once; never loop the failed item.
            val failedItem = failedPlayer?.currentMediaItem
            val looksLikeMusic = failedItem?.mediaMetadata?.artist != null || failedItem?.mediaMetadata?.albumTitle != null
            if (looksLikeMusic && failedPlayer.hasNextMediaItem()) {
                failedPlayer.seekToNextMediaItem()
                failedPlayer.prepare()
                failedPlayer.play()
            }
        }
    }

    private fun beginListening(mediaId: String?) {
        if (mediaId.isNullOrBlank() || listeningStartedAtMs != 0L) return
        listeningMediaId = mediaId
        listeningStartedAtMs = android.os.SystemClock.elapsedRealtime()
    }

    private fun flushListeningTime() {
        val mediaId = listeningMediaId
        val startedAt = listeningStartedAtMs
        listeningMediaId = null
        listeningStartedAtMs = 0L
        if (mediaId == null || startedAt == 0L) return
        val elapsed = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        if (elapsed < 1_000L) return
        serviceScope.launch {
            if (musicRepository.getTrack(mediaId) == null) return@launch
            preferencesRepository.updateApplicationPreferences { preferences ->
                preferences.copy(
                    musicListeningTimeMs = preferences.musicListeningTimeMs +
                        (mediaId to ((preferences.musicListeningTimeMs[mediaId] ?: 0L) + elapsed)),
                )
            }
        }
    }

    private fun persistMusicQueue() {
        val player = mediaSession?.player ?: return
        val current = player.currentMediaItem
        if (current == null) {
            if (currentQueueIsMusic) {
                currentQueueIsMusic = false
                serviceScope.launch {
                    preferencesRepository.updateApplicationPreferences {
                        it.copy(musicQueueUris = emptyList(), musicQueueIndex = 0, musicQueuePositionMs = 0L)
                    }
                }
            }
            return
        }
        val isMusic = current.mediaMetadata.artist != null || current.mediaMetadata.albumTitle != null
        if (!isMusic) return
        currentQueueIsMusic = true
        val uris = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.filter(String::isNotBlank)
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val position = player.currentPosition.coerceAtLeast(0L)
        serviceScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(musicQueueUris = uris, musicQueueIndex = index, musicQueuePositionMs = position)
            }
        }
    }

    private fun startSleepTimer(durationMs: Long, fadeMs: Long, endOfTrack: Boolean) {
        sleepTimerJob?.cancel()
        val player = mediaSession?.player ?: return
        val actualDuration = if (endOfTrack) {
            (player.duration - player.currentPosition).takeIf { it > 0 } ?: return
        } else {
            durationMs.takeIf { it > 0 } ?: return
        }
        sleepTimerDeadlineMs = android.os.SystemClock.elapsedRealtime() + actualDuration
        sleepTimerOriginalVolume = player.volume
        sleepTimerJob = serviceScope.launch {
            val fadeDuration = fadeMs.coerceIn(0L, actualDuration)
            val waitBeforeFade = actualDuration - fadeDuration
            if (waitBeforeFade > 0) kotlinx.coroutines.delay(waitBeforeFade)
            val originalVolume = player.volume
            if (fadeDuration > 0) {
                val steps = 20
                val stepDelay = (fadeDuration / steps).coerceAtLeast(25L)
                repeat(steps) { step ->
                    player.volume = originalVolume * (1f - (step + 1f) / steps)
                    kotlinx.coroutines.delay(stepDelay)
                }
            }
            player.pause()
            player.volume = originalVolume
            sleepTimerDeadlineMs = 0L
            sleepTimerJob = null
        }
    }

    private fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerDeadlineMs = 0L
        mediaSession?.player?.volume = sleepTimerOriginalVolume
    }

    private fun setEnhancerTargetGain(gain: Int) {
        val enhancer = loudnessEnhancer ?: return

        try {
            enhancer.setTargetGain(gain)
            enhancer.enabled = gain > 0
            currentVolumeGain = enhancer.targetGain.toInt()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mediaSessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val connectionResult = MediaSession.ConnectionResult
                .AcceptedResultBuilder(session, controller)
                .build()
            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands
                    .buildUpon()
                    .addSessionCommands(customCommands)
                    .build(),
                connectionResult.availablePlayerCommands,
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            LibraryResult.ofItem(libraryFolder(LIBRARY_ROOT, "Graviton Music"), params)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future(Dispatchers.IO) {
            val children = when (parentId) {
                LIBRARY_ROOT -> listOf(libraryFolder(LIBRARY_SONGS, "Songs"))
                LIBRARY_SONGS -> musicRepository.observeTracks().first().map { track ->
                    MediaItem.Builder()
                        .setMediaId(track.uriString)
                        .setUri(track.uriString)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.displayTitle)
                                .setArtist(track.displayArtist)
                                .setAlbumTitle(track.displayAlbum)
                                .setArtworkUri(track.artworkUriString?.toUri())
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .build(),
                        )
                        .build()
                }
                else -> emptyList()
            }
            val from = (page * pageSize).coerceAtMost(children.size)
            val to = (from + pageSize).coerceAtMost(children.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(children.subList(from, to)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future(Dispatchers.IO) {
            val track = musicRepository.getTrack(mediaId)
                ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            val item = MediaItem.Builder()
                .setMediaId(track.uriString)
                .setUri(track.uriString)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.displayTitle)
                        .setArtist(track.displayArtist)
                        .setAlbumTitle(track.displayAlbum)
                        .setArtworkUri(track.artworkUriString?.toUri())
                        .setIsPlayable(true)
                        .build(),
                )
                .build()
            LibraryResult.ofItem(item, null)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future(Dispatchers.Default) {
            val updatedMediaItems = updatedMediaItemsWithMetadata(mediaItems)
            return@future MediaSession.MediaItemsWithStartPosition(updatedMediaItems, startIndex, startPositionMs)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future(Dispatchers.Default) {
            val updatedMediaItems = updatedMediaItemsWithMetadata(mediaItems)
            return@future updatedMediaItems.toMutableList()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = serviceScope.future {
            val command = CustomCommands.fromSessionCommand(customCommand)
                ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)

            when (command) {
                CustomCommands.ADD_SUBTITLE_TRACK -> {
                    val subtitleUri = args.getString(CustomCommands.SUBTITLE_TRACK_URI_KEY)?.toUri()
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)

                    val newSubConfiguration = uriToSubtitleConfiguration(
                        uri = subtitleUri,
                        subtitleEncoding = playerPreferences.subtitleTextEncoding,
                    )
                    mediaSession?.player?.let { player ->
                        val currentMediaItem = player.currentMediaItem ?: return@let
                        val textTracks = player.currentTracks.groups.filter {
                            it.type == C.TRACK_TYPE_TEXT && it.isSupported
                        }

                        mediaRepository.updateMediumPosition(
                            uri = currentMediaItem.mediaId,
                            position = player.currentPosition,
                        )
                        mediaRepository.updateMediumSubtitleTrack(
                            uri = currentMediaItem.mediaId,
                            subtitleTrackIndex = textTracks.size,
                        )
                        mediaRepository.addExternalSubtitleToMedium(
                            uri = currentMediaItem.mediaId,
                            subtitleUri = subtitleUri,
                        )
                        player.addAdditionalSubtitleConfiguration(newSubConfiguration)
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.SET_SKIP_SILENCE_ENABLED -> {
                    val enabled = args.getBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY)
                    mediaSession?.player?.playerSpecificSkipSilenceEnabled = enabled
                    mediaSession?.sessionExtras = Bundle().apply {
                        putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, enabled)
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SKIP_SILENCE_ENABLED -> {
                    val enabled = mediaSession?.player?.playerSpecificSkipSilenceEnabled ?: false
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, enabled)
                        },
                    )
                }

                CustomCommands.SET_IS_SCRUBBING_MODE_ENABLED -> {
                    val enabled = args.getBoolean(CustomCommands.IS_SCRUBBING_MODE_ENABLED_KEY)
                    mediaSession?.player?.setIsScrubbingModeEnabled(enabled)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED -> {
                    val isSupported = loudnessEnhancer != null
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putBoolean(CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED_KEY, isSupported)
                        },
                    )
                }

                CustomCommands.SET_LOUDNESS_GAIN -> {
                    val gain = args.getInt(CustomCommands.LOUDNESS_GAIN_KEY, 0)
                    setEnhancerTargetGain(gain)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_LOUDNESS_GAIN -> {
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putInt(CustomCommands.LOUDNESS_GAIN_KEY, currentVolumeGain)
                        },
                    )
                }

                CustomCommands.GET_SUBTITLE_DELAY -> {
                    val subtitleDelay = mediaSession?.player?.playerSpecificSubtitleDelayMilliseconds ?: 0
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putLong(CustomCommands.SUBTITLE_DELAY_KEY, subtitleDelay)
                        },
                    )
                }

                CustomCommands.SET_SUBTITLE_DELAY -> {
                    val subtitleDelay = args.getLong(CustomCommands.SUBTITLE_DELAY_KEY)
                    mediaSession?.player?.playerSpecificSubtitleDelayMilliseconds = subtitleDelay
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SUBTITLE_SPEED -> {
                    val subtitleSpeed = mediaSession?.player?.playerSpecificSubtitleSpeed ?: 0f
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putFloat(CustomCommands.SUBTITLE_SPEED_KEY, subtitleSpeed)
                        },
                    )
                }

                CustomCommands.SET_SUBTITLE_SPEED -> {
                    val subtitleSpeed = args.getFloat(CustomCommands.SUBTITLE_SPEED_KEY)
                    mediaSession?.player?.playerSpecificSubtitleSpeed = subtitleSpeed
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.STOP_PLAYER_SESSION -> {
                    mediaSession?.run {
                        serviceScope.launch {
                            mediaRepository.updateMediumPosition(
                                uri = player.currentMediaItem?.mediaId ?: return@launch,
                                position = player.currentPosition,
                            )
                        }
                    }
                    mediaSession?.run {
                        player.clearMediaItems()
                        player.stop()
                    }
                    stopSelf()
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_AUDIO_SESSION_ID -> {
                    val sessionId = (mediaSession?.player as? ExoPlayer)?.audioSessionId
                        ?: C.AUDIO_SESSION_ID_UNSET
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putInt(CustomCommands.AUDIO_SESSION_ID_KEY, sessionId)
                        },
                    )
                }

                CustomCommands.START_SLEEP_TIMER -> {
                    startSleepTimer(
                        durationMs = args.getLong(CustomCommands.SLEEP_DURATION_MS_KEY),
                        fadeMs = args.getLong(CustomCommands.SLEEP_FADE_MS_KEY),
                        endOfTrack = args.getBoolean(CustomCommands.SLEEP_END_OF_TRACK_KEY),
                    )
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.CANCEL_SLEEP_TIMER -> {
                    cancelSleepTimer()
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SLEEP_TIMER -> {
                    val remaining = (sleepTimerDeadlineMs - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply { putLong(CustomCommands.SLEEP_REMAINING_MS_KEY, remaining) },
                    )
                }

                CustomCommands.GET_PLAYBACK_DIAGNOSTICS -> {
                    val diagnostics = playbackDiagnostics.snapshot
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            diagnostics.videoDecoderName?.let {
                                putString(CustomCommands.VIDEO_DECODER_NAME_KEY, it)
                            }
                            diagnostics.isVideoDecoderHardware?.let {
                                putBoolean(CustomCommands.VIDEO_DECODER_IS_HARDWARE_KEY, it)
                            }
                            putLong(CustomCommands.VIDEO_DECODER_INIT_MS_KEY, diagnostics.videoDecoderInitMs)
                            diagnostics.audioDecoderName?.let {
                                putString(CustomCommands.AUDIO_DECODER_NAME_KEY, it)
                            }
                            putInt(CustomCommands.DROPPED_FRAMES_KEY, diagnostics.droppedFrames)
                            putInt(CustomCommands.DECODER_INITIALISATIONS_KEY, diagnostics.decoderInitialisations)
                        },
                    )
                }
            }
        }
    }

    private fun libraryFolder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build(),
        )
        .build()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession


    override fun onCreate() {
        super.onCreate()
        // Decoder selection is fixed for the lifetime of the service: the renderers factory is read
        // here once, so a decoder-mode change only takes effect the next time PlayerService is
        // created. See DecoderModeConfiguration for what each mode maps to.
        val decoderConfiguration = playerPreferences.decoderMode.toConfiguration()
        val renderersFactory = NextRenderersFactory(applicationContext)
            .setEnableDecoderFallback(decoderConfiguration.enableDecoderFallback)
            .setExtensionRendererMode(decoderConfiguration.extensionRendererMode)
            .setMediaCodecSelector(
                object : androidx.media3.exoplayer.mediacodec.MediaCodecSelector {
                    override fun getDecoderInfos(
                        mimeType: String,
                        requiresSecureDecoder: Boolean,
                        requiresTunnelingDecoder: Boolean
                    ): MutableList<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
                        val defaultInfos = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                        if (playerPreferences.decoderMode == com.graviton.core.model.DecoderMode.HARDWARE || playerPreferences.decoderMode == com.graviton.core.model.DecoderMode.HARDWARE_PLUS) {
                            return defaultInfos.filter { it.hardwareAccelerated }.toMutableList()
                        }
                        return defaultInfos
                    }
                }
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
                it.addAnalyticsListener(playbackDiagnostics)
                it.pauseAtEndOfMediaItems = !playerPreferences.autoplay
                it.repeatMode = when (playerPreferences.loopMode) {
                    LoopMode.OFF -> Player.REPEAT_MODE_OFF
                    LoopMode.ONE -> Player.REPEAT_MODE_ONE
                    LoopMode.ALL -> Player.REPEAT_MODE_ALL
                }
            }

        try {
            mediaSession = MediaLibrarySession.Builder(this, player, mediaSessionCallback).apply {
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@PlayerService,
                        0,
                        Intent(this@PlayerService, PlayerActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setCustomLayout(
                    listOf(
                        CommandButton.Builder(ICON_UNDEFINED)
                            .setCustomIconResId(coreUiR.drawable.ic_close)
                            .setDisplayName(getString(coreUiR.string.stop_player_session))
                            .setSessionCommand(CustomCommands.STOP_PLAYER_SESSION.sessionCommand)
                            .setEnabled(true)
                            .build(),
                    ),
                )
            }.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val saved = preferencesRepository.applicationPreferences.value
        if (saved.musicQueueUris.isNotEmpty() && player.mediaItemCount == 0) {
            serviceScope.launch(Dispatchers.Default) {
                val restored = updatedMediaItemsWithMetadata(
                    saved.musicQueueUris.map { uri -> MediaItem.Builder().setMediaId(uri).setUri(uri).build() },
                )
                withContext(Dispatchers.Main) {
                    if (player.mediaItemCount == 0 && restored.isNotEmpty()) {
                        player.setMediaItems(
                            restored,
                            saved.musicQueueIndex.coerceIn(0, restored.lastIndex),
                            saved.musicQueuePositionMs.coerceAtLeast(0L),
                        )
                        player.prepare()
                    }
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        flushListeningTime()
        artworkLoadJob?.cancel()
        sleepTimerJob?.cancel()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        equalizer?.release()
        equalizer = null
        mediaSession?.run {
            player.clearMediaItems()
            player.stop()
            player.removeListener(playbackStateListener)
            player.release()
            release()
            mediaSession = null
        }
        subtitleCacheDir.deleteFiles()
        serviceScope.cancel()
    }

    private suspend fun updatedMediaItemsWithMetadata(
        mediaItems: List<MediaItem>,
    ): List<MediaItem> = supervisorScope {
        mediaItems.map { mediaItem ->
            async {
                val uri = mediaItem.mediaId.toUri()
                val video = mediaRepository.getVideoByUri(uri = mediaItem.mediaId)
                val audio = if (video == null) musicRepository.getTrack(mediaItem.mediaId) else null
                val videoState = mediaRepository.getVideoState(uri = mediaItem.mediaId)

                val externalSubs = videoState?.externalSubs ?: emptyList()
                val localSubs = (videoState?.path ?: getPath(uri))?.let {
                    File(it).getLocalSubtitles(
                        context = this@PlayerService,
                        excludeSubsList = externalSubs,
                    )
                } ?: emptyList()

                val existingSubConfigurations = mediaItem.localConfiguration?.subtitleConfigurations ?: emptyList()
                val subConfigurations = (localSubs + externalSubs).map { subtitleUri ->
                    uriToSubtitleConfiguration(
                        uri = subtitleUri,
                        subtitleEncoding = playerPreferences.subtitleTextEncoding,
                    )
                }

                // Keep metadata supplied by the caller. Music items carry artist/album artwork
                // here, while video items get the normal Graviton artwork fallback.
                val artworkUri = mediaItem.mediaMetadata.artworkUri
                    ?: audio?.artworkUriString?.toUri()
                    ?: getDefaultArtworkUri()
                val title = mediaItem.mediaMetadata.title
                    ?: video?.nameWithExtension
                    ?: audio?.displayTitle
                    ?: getFilenameFromUri(uri)
                val artist = mediaItem.mediaMetadata.artist ?: audio?.displayArtist
                val albumTitle = mediaItem.mediaMetadata.albumTitle ?: audio?.displayAlbum
                val positionMs = mediaItem.mediaMetadata.positionMs ?: videoState?.position
                val videoScale = mediaItem.mediaMetadata.videoZoom ?: videoState?.videoScale
                val playbackSpeed = mediaItem.mediaMetadata.playbackSpeed ?: videoState?.playbackSpeed
                val audioTrackIndex = mediaItem.mediaMetadata.audioTrackIndex ?: videoState?.audioTrackIndex
                val subtitleTrackIndex = mediaItem.mediaMetadata.subtitleTrackIndex ?: videoState?.subtitleTrackIndex
                val subtitleDelay = mediaItem.mediaMetadata.subtitleDelayMilliseconds ?: videoState?.subtitleDelayMilliseconds
                val subtitleSpeed = mediaItem.mediaMetadata.subtitleSpeed ?: videoState?.subtitleSpeed

                mediaItem.buildUpon().apply {
                    setSubtitleConfigurations(existingSubConfigurations + subConfigurations)
                    setMediaMetadata(
                        MediaMetadata.Builder().apply {
                            setTitle(title)
                            artist?.let { setArtist(it) }
                            albumTitle?.let { setAlbumTitle(it) }
                            setArtworkUri(artworkUri)
                            setExtras(
                                positionMs = positionMs,
                                videoScale = videoScale,
                                playbackSpeed = playbackSpeed,
                                audioTrackIndex = audioTrackIndex,
                                subtitleTrackIndex = subtitleTrackIndex,
                                subtitleDelayMilliseconds = subtitleDelay,
                                subtitleSpeed = subtitleSpeed,
                            )
                        }.build(),
                    )
                }.build()
            }
        }.awaitAll()
    }
    
    private fun getDefaultArtworkUri(): Uri = Uri.Builder().apply {
        val defaultArtwork = R.drawable.artwork_default
        scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        authority(resources.getResourcePackageName(defaultArtwork))
        appendPath(resources.getResourceTypeName(defaultArtwork))
        appendPath(resources.getResourceEntryName(defaultArtwork))
    }.build()

    private fun loadArtworkForCurrentMediaItem() {
        artworkLoadJob?.cancel()
        artworkLoadJob = serviceScope.launch(Dispatchers.Main) {
            val player = mediaSession?.player ?: return@launch
            val currentMediaItem = player.currentMediaItem ?: return@launch
            if (currentMediaItem.mediaMetadata.artworkData != null) return@launch

            val artworkUri = loadArtworkForMediaItem(currentMediaItem) ?: return@launch

            val updatedPlayer = mediaSession?.player ?: return@launch
            val updatedMediaItem = updatedPlayer.currentMediaItem ?: return@launch
            if (updatedMediaItem.mediaId != currentMediaItem.mediaId) return@launch

            updatedPlayer.replaceMediaItem(
                updatedPlayer.currentMediaItemIndex,
                updatedMediaItem.withArtwork(artworkUri),
            )
        }
    }
    private suspend fun loadArtworkForMediaItem(mediaItem: MediaItem): Uri? = withContext(Dispatchers.IO) {
        val candidates = listOfNotNull(
            mediaItem.mediaMetadata.artworkUri,
            mediaItem.localConfiguration?.uri,
            mediaItem.mediaId.toUri(),
        ).distinct()
        candidates.firstNotNullOfOrNull { candidate ->
            runCatching {
                val request = ImageRequest.Builder(this@PlayerService)
                    .data(candidate)
                    .size(512, 512)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val result = imageLoader.execute(request)
                if (result.image == null) return@runCatching null
                val diskCache = imageLoader.diskCache
                val snapshotKey = request.diskCacheKey ?: candidate.toString()
                diskCache?.openSnapshot(snapshotKey)?.use { snapshot ->
                    snapshot.data.toFile().toUri()
                } ?: candidate
            }.getOrNull()
        }
    }
    private fun MediaItem.withArtwork(uri: Uri): MediaItem = buildUpon()
        .setMediaMetadata(
            mediaMetadata.buildUpon()
                .setArtworkUri(uri)
                .build(),
        )
        .build()

    private companion object {
        const val LIBRARY_ROOT = "music/root"
        const val LIBRARY_SONGS = "music/songs"
    }
}

@get:UnstableApi
@set:UnstableApi
private var Player.playerSpecificSkipSilenceEnabled: Boolean
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.skipSilenceEnabled
        else -> false
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.skipSilenceEnabled = value
        }
    }

@get:UnstableApi
@set:UnstableApi
private var Player.playerSpecificSubtitleDelayMilliseconds: Long
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.subtitleDelayMilliseconds
        else -> 0L
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.subtitleDelayMilliseconds = value
        }
    }

@get:UnstableApi
@set:UnstableApi
private var Player.playerSpecificSubtitleSpeed: Float
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.subtitleSpeed
        else -> 0f
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.subtitleSpeed = value
        }
    }
