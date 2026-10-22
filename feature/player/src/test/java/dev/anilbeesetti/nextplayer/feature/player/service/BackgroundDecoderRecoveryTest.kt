package dev.anilbeesetti.nextplayer.feature.player.service

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import dev.anilbeesetti.nextplayer.feature.player.extensions.setVideoPlaybackEnabled
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderTrackType
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BackgroundDecoderRecoveryTest {
    @Test
    fun unsupportedVideoDoesNotInterruptBackgroundAudio() = withService { player, service, recovery ->
        player.setVideoPlaybackEnabled(false)
        service.checkUnsupportedTrack(DecoderTrackType.VIDEO)
        assertEquals(DecoderRecoveryStatus.NONE, recovery.state.status)

        // Returning to the video player must still detect a genuinely unsupported video track.
        player.setVideoPlaybackEnabled(true)
        service.checkUnsupportedTrack(DecoderTrackType.VIDEO)
        assertEquals(DecoderRecoveryStatus.AWAITING_CONFIRMATION, recovery.state.status)
        assertEquals(DecoderTrackType.VIDEO, recovery.state.trackType)
    }

    @Test
    fun unsupportedAudioStillTriggersRecoveryDuringBackgroundPlayback() = withService { player, service, recovery ->
        player.setVideoPlaybackEnabled(false)
        service.checkUnsupportedTrack(DecoderTrackType.AUDIO)
        assertEquals(DecoderRecoveryStatus.AWAITING_CONFIRMATION, recovery.state.status)
        assertEquals(DecoderTrackType.AUDIO, recovery.state.trackType)
    }

    private fun withService(check: (ExoPlayer, PlayerService, DecoderRecoveryManager) -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        val trackSelector = DefaultTrackSelector(context)
        val player = ExoPlayer.Builder(context).setTrackSelector(trackSelector).build()
        val session = MediaSession.Builder(context, player).build()
        val service = PlayerService()
        service.setField("mediaSession", session)
        service.setField("trackSelector", trackSelector)
        service.setField("decoderManager", DecoderManager(DecoderMode.SOFTWARE, DecoderMode.SOFTWARE))
        val recovery = PlayerService::class.java.getDeclaredField("decoderRecoveryManager")
            .apply { isAccessible = true }.get(service) as DecoderRecoveryManager
        DecoderTrackType.entries.forEach { recovery.onUserSelection(it, DecoderMode.SOFTWARE) }
        try {
            check(player, service, recovery)
        } finally {
            session.release()
            player.release()
        }
    }

    private fun PlayerService.checkUnsupportedTrack(trackType: DecoderTrackType) {
        val mimeType = if (trackType == DecoderTrackType.VIDEO) MimeTypes.VIDEO_H264 else MimeTypes.AUDIO_AAC
        val tracks = Tracks(
            listOf(
                Tracks.Group(
                    TrackGroup(Format.Builder().setSampleMimeType(mimeType).build()),
                    false,
                    intArrayOf(C.FORMAT_UNSUPPORTED_SUBTYPE),
                    booleanArrayOf(false),
                ),
            ),
        )
        PlayerService::class.java.getDeclaredMethod("handleUnsupportedTrack", Tracks::class.java, DecoderTrackType::class.java)
            .apply { isAccessible = true }.invoke(this, tracks, trackType)
    }

    private fun PlayerService.setField(name: String, value: Any) {
        PlayerService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
    }
}
