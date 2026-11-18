package dev.anilbeesetti.nextplayer.feature.player.service

import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderTrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DecoderRecoveryManagerTest {
    private val video = DecoderTrackType.VIDEO
    private val audio = DecoderTrackType.AUDIO

    @Test
    fun systemAudioFallsBackDirectlyToFfmpeg() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(
            audio,
            DecoderMode.HARDWARE,
        )
        assertNull(
            manager.onDecoderFailure(
                audio,
                DecoderMode.HARDWARE,
            ),
        )
        assertEquals(
            DecoderRetry(
                audio,
                DecoderMode.FFMPEG,
            ),
            manager.confirmFallback(),
        )
    }

    @Test
    fun automaticFailureRetriesFfmpegOnce() {
        val manager = DecoderRecoveryManager()
        assertEquals(
            DecoderRetry(
                trackType = video,
                DecoderMode.FFMPEG,
            ),
            manager.onDecoderFailure(
                trackType = video,
                DecoderMode.AUTO,
            ),
        )
        assertEquals(
            DecoderRecoveryStatus.RECOVERING,
            manager.state.status,
        )
        assertNull(
            manager.onDecoderFailure(
                trackType = video,
                DecoderMode.AUTO,
            ),
        )
        manager.onDecoderInitialized(trackType = video)
        assertEquals(
            DecoderRecoveryStatus.NONE,
            manager.state.status,
        )
        assertNull(
            manager.onDecoderFailure(
                trackType = video,
                DecoderMode.FFMPEG,
            ),
        )
        assertEquals(
            DecoderRecoveryStatus.FAILED,
            manager.state.status,
        )
    }

    @Test
    fun explicitSystemRequiresConfirmationThenTriesFfmpegDirectly() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(
            trackType = video,
            DecoderMode.HARDWARE,
        )
        assertNull(
            manager.onDecoderFailure(
                trackType = video,
                DecoderMode.HARDWARE,
            ),
        )
        assertEquals(
            DecoderRecoveryStatus.AWAITING_CONFIRMATION,
            manager.state.status,
        )
        assertEquals(
            DecoderMode.HARDWARE,
            manager.state.unsupportedMode,
        )
        assertEquals(
            DecoderRetry(
                trackType = video,
                DecoderMode.FFMPEG,
            ),
            manager.confirmFallback(),
        )
        assertNull(manager.confirmFallback())
        assertNull(
            manager.onDecoderFailure(
                trackType = video,
                DecoderMode.FFMPEG,
            ),
        )
        assertEquals(
            DecoderRecoveryStatus.FAILED,
            manager.state.status,
        )
    }

    @Test
    fun explicitFfmpegReturnsToAutomaticWithoutLooping() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(
            trackType = audio,
            DecoderMode.FFMPEG,
        )
        assertNull(
            manager.onDecoderFailure(
                trackType = audio,
                DecoderMode.FFMPEG,
            ),
        )
        assertEquals(
            DecoderRetry(
                trackType = audio,
                DecoderMode.AUTO,
            ),
            manager.confirmFallback(),
        )
        assertNull(
            manager.onDecoderFailure(
                trackType = audio,
                DecoderMode.AUTO,
            ),
        )
        assertEquals(
            DecoderRecoveryStatus.FAILED,
            manager.state.status,
        )
    }

    @Test
    fun userCanStartAnotherAttemptAfterFailure() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(
            trackType = video,
            DecoderMode.HW_PLUS,
        )
        manager.onDecoderFailure(
            trackType = video,
            DecoderMode.HW_PLUS,
        )
        manager.confirmFallback()
        manager.onDecoderFailure(
            trackType = video,
            DecoderMode.FFMPEG,
        )
        manager.onUserSelection(
            trackType = video,
            DecoderMode.HW_PLUS,
        )
        assertEquals(
            DecoderRecoveryStatus.NONE,
            manager.state.status,
        )
        manager.onDecoderFailure(
            trackType = video,
            DecoderMode.HW_PLUS,
        )
        assertEquals(
            DecoderRetry(
                trackType = video,
                DecoderMode.FFMPEG,
            ),
            manager.confirmFallback(),
        )
    }

    @Test
    fun tracksHaveIndependentAttemptsAndInitialization() {
        val manager = DecoderRecoveryManager()
        assertNotNull(
            manager.onDecoderFailure(
                trackType = video,
                DecoderMode.AUTO,
            ),
        )
        manager.onDecoderInitialized(trackType = audio)
        assertEquals(
            DecoderRecoveryStatus.RECOVERING,
            manager.state.status,
        )
        assertEquals(
            video,
            manager.state.trackType,
        )
        assertEquals(
            DecoderRetry(
                trackType = audio,
                DecoderMode.FFMPEG,
            ),
            manager.onDecoderFailure(
                trackType = audio,
                DecoderMode.AUTO,
            ),
        )
    }

    @Test
    fun audioRecoveryDoesNotHidePendingVideoConfirmation() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(
            trackType = video,
            DecoderMode.HARDWARE,
        )
        manager.onDecoderFailure(
            trackType = video,
            DecoderMode.HARDWARE,
        )
        manager.onDecoderFailure(
            trackType = audio,
            DecoderMode.AUTO,
        )
        manager.onDecoderInitialized(trackType = audio)
        assertEquals(
            DecoderRecoveryStatus.AWAITING_CONFIRMATION,
            manager.state.status,
        )
        assertEquals(
            video,
            manager.state.trackType,
        )
        assertEquals(
            DecoderRetry(
                trackType = video,
                DecoderMode.FFMPEG,
            ),
            manager.confirmFallback(),
        )
    }

    @Test
    fun nonDecoderErrorClearsRecovery() {
        val manager = DecoderRecoveryManager()
        manager.onDecoderFailure(
            trackType = video,
            DecoderMode.AUTO,
        )
        manager.onNonDecoderError()
        assertEquals(
            DecoderRecoveryStatus.NONE,
            manager.state.status,
        )
        assertNull(manager.confirmFallback())
    }
}
