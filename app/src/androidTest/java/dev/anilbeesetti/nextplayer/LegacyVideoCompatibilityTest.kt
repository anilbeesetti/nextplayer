package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.extensions.ffmpegExtraction
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode as PlayerDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegMediaSourceFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyVideoCompatibilityTest {
    @Test
    fun legacyFormatsHaveMetadataThumbnailsAndSeekableSoftwareAudioVideo() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val supplied = InstrumentationRegistry.getArguments().getString("compatibilityVideo")
        val assets = if (supplied != null) {
            listOf(File(supplied).name)
        } else {
            listOf(
                "legacy-mpeg1.mpg",
                "legacy-xvid.avi",
                "legacy-wmv2.wmv",
                "legacy-dvd.vob.mpeg",
            )
        }
        for (asset in assets) {
            CompatibilityPickerTest().withRecording(asset) { uri ->
                val info = checkNotNull(
                    MediaInfoBuilder().from(
                        context,
                        uri,
                    ).build(),
                )
                try {
                    assertTrue(
                        "$asset duration",
                        info.duration > 3_000,
                    )
                    assertTrue(
                        "$asset dimensions",
                        checkNotNull(info.videoStream).frameWidth > 0,
                    )
                } finally {
                    info.release()
                }
                val loader = ImageLoader.Builder(context).components {
                    add(VideoThumbnailDecoder.Factory { ThumbnailStrategy.FirstFrame })
                }.build()
                try {
                    for (strategy in listOf(
                        ThumbnailStrategy.FirstFrame,
                        ThumbnailStrategy.FrameAtPercentage(),
                        ThumbnailStrategy.Hybrid(),
                    )) {
                        val result = loader.execute(
                            ImageRequest.Builder(context).data(uri).size(
                                160,
                                120,
                            )
                                .decoderFactory(VideoThumbnailDecoder.Factory { strategy })
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .diskCachePolicy(CachePolicy.DISABLED).build(),
                        )
                        assertTrue(
                            "$asset $strategy: $result",
                            result is SuccessResult,
                        )
                    }
                } finally {
                    loader.shutdown()
                }
                playAndSeek(
                    uri,
                    asset,
                )
                if (supplied != null) {
                    playAndSeek(
                        Uri.fromFile(File(supplied)),
                        asset,
                    )
                }
            }
        }
    }

    @Test
    fun serviceRecoversAndSwitchesDecodersWithoutLosingAudioOrPosition() = runBlocking {
        CompatibilityPickerTest().withRecording(
            "legacy-wmv2.wmv",
            supplied = null,
        ) { uri ->
            playThroughService(
                uri,
                selectSoftware = false,
            )
        }
        CompatibilityPickerTest().withRecording(
            "compatibility-opus.mkv",
            supplied = null,
        ) { uri ->
            playThroughService(
                uri,
                selectSoftware = true,
            )
        }
        CompatibilityPickerTest().withRecording("legacy-dvd.vob.mpeg") { uri ->
            playThroughService(
                uri,
                selectSoftware = true,
            )
        }
    }

    @Test
    fun autoPreservesDvdPcmAudio() = runBlocking {
        CompatibilityPickerTest().withRecording("legacy-dvd.vob.mpeg") { uri ->
            playThroughService(
                uri,
                selectSoftware = false,
                expectedAudioMime = "audio/x-pcm-dvd",
            )
        }
    }

    @Test
    fun programStreamPreservesLayerOneAudio() = runBlocking {
        CompatibilityPickerTest().withRecording(
            "legacy-layer1.mpg",
            supplied = null,
        ) { uri ->
            playAndSeek(
                uri,
                "MPEG Layer I",
            )
            playThroughService(
                uri,
                selectSoftware = false,
                expectedAudioMime = "audio/mpeg-L1",
            )
        }
    }

    @Test
    fun hwPlusRendersSystemVideoAndRecoversAudio() = runBlocking {
        CompatibilityPickerTest().withRecording("legacy-xvid.avi") { uri ->
            playAndSeek(
                uri,
                "HW+ Xvid",
                videoMode = DecoderMode.SYSTEM,
            )
            playThroughService(
                uri,
                selectSoftware = false,
                initialVideoMode = PlayerDecoderMode.HW_PLUS,
            )
        }
    }

    private fun playThroughService(
        uri: Uri,
        selectSoftware: Boolean,
        expectFfmpegExtraction: Boolean = true,
        initialVideoMode: PlayerDecoderMode = PlayerDecoderMode.AUTO,
        expectedAudioMime: String? = null,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
        lateinit var future: ListenableFuture<MediaController>
        onMain {
            future = MediaController.Builder(
                context,
                SessionToken(
                    context,
                    ComponentName(
                        context,
                        PlayerService::class.java,
                    ),
                ),
            ).buildAsync()
        }
        val player = future.get(
            10,
            TimeUnit.SECONDS,
        )
        var observed = ""
        fun await(condition: () -> Boolean) {
            var done = false
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (!done && SystemClock.elapsedRealtime() < deadline) {
                onMain {
                    done = condition()
                    observed =
                        "state=${player.playbackState}, position=${player.currentPosition}, " +
                        "error=${player.playerError}, " +
                        "extras=${player.sessionExtras}, " +
                        "requested=${player.currentMediaItem?.mediaMetadata?.videoDecoderMode}, " +
                        "extraction=${player.currentMediaItem?.mediaMetadata?.ffmpegExtraction}, " +
                        "playing=${player.playWhenReady}, audio=" +
                        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                            .map { it.getTrackFormat(0).sampleMimeType }
                }
                if (!done) SystemClock.sleep(50)
            }
            assertTrue(
                observed,
                done,
            )
        }
        try {
            onMain {
                var item = MediaItem.Builder().setUri(uri).setMediaId(uri.toString()).build()
                    .copy(videoDecoderMode = initialVideoMode)
                if (selectSoftware) {
                    item = item.copy(videoDecoderMode = PlayerDecoderMode.HARDWARE)
                }
                player.setMediaItem(item)
                player.prepare()
                if (!selectSoftware) player.play()
            }
            if (selectSoftware) {
                lateinit var commandResult: ListenableFuture<SessionResult>
                var targetPosition = 0L
                await {
                    player.currentMediaItem?.mediaId == uri.toString() &&
                        (player.playbackState == Player.STATE_READY || player.playerError != null)
                }
                onMain { player.pause() }
                await {
                    !player.playWhenReady &&
                        (player.playbackState == Player.STATE_READY || player.playerError != null)
                }
                onMain {
                    targetPosition = if (!expectFfmpegExtraction && player.playerError == null &&
                        player.isCurrentMediaItemSeekable &&
                        player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    ) {
                        player.seekTo(700)
                        700L
                    } else {
                        player.currentPosition
                    }
                }
                // Let Media3 flush its queued pause/seek before the immediate custom command.
                await {
                    !player.playWhenReady && player.currentPosition == targetPosition &&
                        (player.playbackState == Player.STATE_READY || player.playerError != null)
                }
                onMain {
                    commandResult = player.sendCustomCommand(
                        CustomCommands.SET_VIDEO_DECODER_MODE.sessionCommand,
                        Bundle().apply {
                            putString(
                                CustomCommands.VIDEO_DECODER_MODE_KEY,
                                "FFMPEG",
                            )
                        },
                    )
                }
                assertEquals(
                    SessionResult.RESULT_SUCCESS,
                    commandResult.get(
                        10,
                        TimeUnit.SECONDS,
                    ).resultCode,
                )
                await {
                    player.sessionExtras.getString(CustomCommands.VIDEO_DECODER_MODE_KEY) ==
                        PlayerDecoderMode.FFMPEG.name &&
                        player.currentMediaItem?.mediaMetadata?.ffmpegExtraction ==
                        expectFfmpegExtraction &&
                        player.playbackState == Player.STATE_READY &&
                        player.currentPosition == targetPosition &&
                        !player.playWhenReady
                }
                onMain { player.play() }
            }
            await {
                player.playerError == null && player.playbackState == Player.STATE_READY &&
                    player.currentPosition >= 1_000 &&
                    (
                        expectedAudioMime == null || player.currentTracks.groups.any {
                            it.type == C.TRACK_TYPE_AUDIO && it.isSelected &&
                                it.getTrackFormat(0).sampleMimeType == expectedAudioMime
                        }
                        ) &&
                    listOf(
                        C.TRACK_TYPE_VIDEO,
                        C.TRACK_TYPE_AUDIO,
                    ).all { type ->
                        player.currentTracks.groups.any { it.type == type && it.isSelected }
                    }
            }
            if (initialVideoMode == PlayerDecoderMode.HW_PLUS) {
                onMain {
                    assertEquals(
                        PlayerDecoderMode.HW_PLUS.name,
                        player.sessionExtras.getString(CustomCommands.VIDEO_DECODER_MODE_KEY),
                    )
                }
            }
        } finally {
            onMain {
                player.stop()
                player.clearMediaItems()
                player.release()
            }
            context.stopService(
                Intent(
                    context,
                    PlayerService::class.java,
                ),
            )
        }
    }

    private fun playAndSeek(uri: Uri, name: String, videoMode: DecoderMode = DecoderMode.FFMPEG) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val error = AtomicReference<PlaybackException>()
        val audioEvents = AtomicInteger()
        lateinit var player: ExoPlayer
        lateinit var texture: SurfaceTexture
        lateinit var surface: Surface
        val decoders = DecoderManager(
            videoMode,
            DecoderMode.FFMPEG,
        )
        fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
        onMain {
            texture = SurfaceTexture(false)
            surface = Surface(texture)
            player = ExoPlayer.Builder(context)
                .setRenderersFactory(NextRenderersFactory(context).setDecoderManager(decoders))
                .build()
            decoders.attach(player)
            player.volume = 0f
            player.setVideoSurface(surface)
            player.addListener(object : Player.Listener {
                override fun onPlayerError(exception: PlaybackException) {
                    error.set(exception)
                }
            })
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioPositionAdvancing(
                    eventTime: AnalyticsListener.EventTime,
                    playoutStartSystemTimeMs: Long,
                ) {
                    audioEvents.incrementAndGet()
                }
            })
            player.setMediaSource(
                FfmpegMediaSourceFactory(DefaultDataSource.Factory(context))
                    .createMediaSource(MediaItem.fromUri(uri)),
            )
            player.prepare()
            player.play()
        }
        try {
            val supplied = InstrumentationRegistry.getArguments().getString("compatibilityVideo")
            for (position in listOf(
                0L,
                if (supplied == null) 1_500L else 120_000L,
                200L,
            )) {
                var videoBefore = 0
                val audioBefore = audioEvents.get()
                onMain {
                    if (position != 0L) {
                        player.pause()
                        player.videoDecoderCounters?.let {
                            it.ensureUpdated()
                            videoBefore = it.renderedOutputBufferCount
                        }
                        player.seekTo(position)
                        player.play()
                    }
                }
                var rendered = false
                val deadline = SystemClock.elapsedRealtime() + 20_000
                while (!rendered && error.get() == null &&
                    SystemClock.elapsedRealtime() < deadline
                ) {
                    onMain {
                        val video = player.videoDecoderCounters
                        val audio = player.audioDecoderCounters
                        video?.ensureUpdated()
                        audio?.ensureUpdated()
                        rendered = player.playbackState == Player.STATE_READY &&
                            player.currentPosition >= position + 100 &&
                            (video?.renderedOutputBufferCount ?: 0) > videoBefore &&
                            (audio?.renderedOutputBufferCount ?: 0) > 0 &&
                            audioEvents.get() > audioBefore &&
                            player.currentTracks.groups.any {
                                it.type == C.TRACK_TYPE_AUDIO &&
                                    it.isSelected
                            }
                    }
                    if (!rendered) SystemClock.sleep(50)
                }
                assertTrue(
                    "$name at $position: ${error.get()?.stackTraceToString()}",
                    rendered,
                )
                if (videoMode == DecoderMode.SYSTEM) {
                    assertTrue(
                        "Expected Android system video decoder: ${decoders.activeVideoMode}",
                        decoders.activeVideoMode in
                            listOf(
                                DecoderMode.HARDWARE,
                                DecoderMode.SOFTWARE,
                            ),
                    )
                } else {
                    assertEquals(
                        videoMode,
                        decoders.activeVideoMode,
                    )
                }
                android.util.Log.i(
                    "CompatibilityPlayback",
                    "$name/$position: ${decoders.activeVideoMode}/${decoders.activeAudioMode}",
                )
                assertEquals(
                    DecoderMode.FFMPEG,
                    decoders.activeAudioMode,
                )
            }
        } finally {
            onMain {
                decoders.detach()
                player.release()
                surface.release()
                texture.release()
            }
        }
    }
}
