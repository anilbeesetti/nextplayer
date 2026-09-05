package dev.anilbeesetti.nextplayer.feature.player.service

import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderTrackType
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderRecoveryManagerTest {
    private val video = DecoderTrackType.VIDEO
    private val audio = DecoderTrackType.AUDIO

    @Test
    fun automaticFailureRetriesFfmpegOnce() {
        val manager = DecoderRecoveryManager()
        assertEquals(DecoderRetry(video, DecoderMode.FFMPEG), manager.onDecoderFailure(video, DecoderMode.AUTO))
        assertEquals(DecoderRecoveryStatus.RECOVERING, manager.state.status)
        assertNull(manager.onDecoderFailure(video, DecoderMode.AUTO))
        manager.onDecoderInitialized(video)
        assertEquals(DecoderRecoveryStatus.NONE, manager.state.status)
        assertNull(manager.onDecoderFailure(video, DecoderMode.FFMPEG))
        assertEquals(DecoderRecoveryStatus.FAILED, manager.state.status)
    }

    @Test
    fun explicitHardwareRequiresConfirmationThenTriesBothSoftwareDecoders() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(video, DecoderMode.HARDWARE)
        assertNull(manager.onDecoderFailure(video, DecoderMode.HARDWARE))
        assertEquals(DecoderRecoveryStatus.AWAITING_CONFIRMATION, manager.state.status)
        assertEquals(DecoderMode.HARDWARE, manager.state.unsupportedMode)
        assertEquals(DecoderRetry(video, DecoderMode.SOFTWARE), manager.confirmFallback())
        assertNull(manager.confirmFallback())
        assertEquals(DecoderRetry(video, DecoderMode.FFMPEG), manager.onDecoderFailure(video, DecoderMode.SOFTWARE))
        assertNull(manager.onDecoderFailure(video, DecoderMode.FFMPEG))
        assertEquals(DecoderRecoveryStatus.FAILED, manager.state.status)
    }

    @Test
    fun explicitFfmpegReturnsToAutomaticWithoutLooping() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(audio, DecoderMode.FFMPEG)
        assertNull(manager.onDecoderFailure(audio, DecoderMode.FFMPEG))
        assertEquals(DecoderRetry(audio, DecoderMode.AUTO), manager.confirmFallback())
        assertNull(manager.onDecoderFailure(audio, DecoderMode.AUTO))
        assertEquals(DecoderRecoveryStatus.FAILED, manager.state.status)
    }

    @Test
    fun userCanStartAnotherAttemptAfterFailure() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(video, DecoderMode.SOFTWARE)
        manager.onDecoderFailure(video, DecoderMode.SOFTWARE)
        manager.confirmFallback()
        manager.onDecoderFailure(video, DecoderMode.FFMPEG)
        manager.onUserSelection(video, DecoderMode.SOFTWARE)
        assertEquals(DecoderRecoveryStatus.NONE, manager.state.status)
        manager.onDecoderFailure(video, DecoderMode.SOFTWARE)
        assertEquals(DecoderRetry(video, DecoderMode.FFMPEG), manager.confirmFallback())
    }

    @Test
    fun tracksHaveIndependentAttemptsAndInitialization() {
        val manager = DecoderRecoveryManager()
        assertNotNull(manager.onDecoderFailure(video, DecoderMode.AUTO))
        manager.onDecoderInitialized(audio)
        assertEquals(DecoderRecoveryStatus.RECOVERING, manager.state.status)
        assertEquals(video, manager.state.trackType)
        assertEquals(DecoderRetry(audio, DecoderMode.FFMPEG), manager.onDecoderFailure(audio, DecoderMode.AUTO))
    }

    @Test
    fun audioRecoveryDoesNotHidePendingVideoConfirmation() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(video, DecoderMode.HARDWARE)
        manager.onDecoderFailure(video, DecoderMode.HARDWARE)
        manager.onDecoderFailure(audio, DecoderMode.AUTO)
        manager.onDecoderInitialized(audio)
        assertEquals(DecoderRecoveryStatus.AWAITING_CONFIRMATION, manager.state.status)
        assertEquals(video, manager.state.trackType)
        assertEquals(DecoderRetry(video, DecoderMode.SOFTWARE), manager.confirmFallback())
    }

    @Test
    fun onlyNewMediaIdentityResetsSelections() {
        val manager = DecoderRecoveryManager()
        val media = DecoderMediaIdentity(0, "video", "file:///video.mp4")
        assertTrue(manager.onMediaItemChanged(media))
        manager.onUserSelection(video, DecoderMode.HARDWARE)
        assertFalse(manager.onMediaItemChanged(media.copy()))
        manager.onDecoderFailure(video, DecoderMode.HARDWARE)
        assertEquals(DecoderRecoveryStatus.AWAITING_CONFIRMATION, manager.state.status)

        for (next in listOf(media.copy(index = 1), media.copy(uri = "file:///other.mp4"))) {
            assertTrue(manager.onMediaItemChanged(next))
            assertNotNull(manager.onDecoderFailure(video, DecoderMode.AUTO))
            assertNotNull(manager.onDecoderFailure(audio, DecoderMode.AUTO))
        }
        assertFalse(manager.onMediaItemChanged(null))
        assertTrue(manager.onMediaItemChanged(media))
        assertNotNull(manager.onDecoderFailure(video, DecoderMode.AUTO))
    }

    @Test
    fun nonDecoderErrorClearsRecovery() {
        val manager = DecoderRecoveryManager()
        manager.onDecoderFailure(video, DecoderMode.AUTO)
        manager.onNonDecoderError()
        assertEquals(DecoderRecoveryStatus.NONE, manager.state.status)
        assertNull(manager.confirmFallback())
    }
}
