package dev.anilbeesetti.nextplayer.feature.player.service

import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderTrackType
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
    fun softwareRetriesTheReaderBeforeMarkingTheDecoderFailed() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(
            video,
            DecoderMode.FFMPEG,
        )
        val retry = checkNotNull(
            manager.onDecoderFailure(
                video,
                DecoderMode.FFMPEG,
                retrySoftwareReader = true,
            ),
        )
        assertEquals(
            DecoderRetry(
                video,
                DecoderMode.FFMPEG,
            ),
            retry,
        )
        assertTrue(manager.takeRetry(retry))
        assertEquals(
            DecoderRetry(
                video,
                DecoderMode.HARDWARE,
            ),
            manager.onDecoderFailure(
                video,
                DecoderMode.FFMPEG,
            ),
        )
        assertNull(
            manager.onDecoderFailure(
                video,
                DecoderMode.HARDWARE,
            ),
        )
        assertEquals(
            DecoderRecoveryStatus.FAILED,
            manager.state.status,
        )
    }

    @Test
    fun manualHwPlusOnSystemReaderReenablesHardwareAndReportsRequestedMode() {
        val manager = DecoderRecoveryManager()
        assertNotNull(
            manager.onDecoderFailure(
                video,
                DecoderMode.HARDWARE,
            ),
        )
        manager.onUserSelection(
            video,
            DecoderMode.HW_PLUS,
            effectiveMode = DecoderMode.HARDWARE,
        )
        assertNull(
            manager.onDecoderFailure(
                video,
                DecoderMode.HARDWARE,
                requestedMode = DecoderMode.HW_PLUS,
            ),
        )
        assertEquals(
            DecoderRecoveryStatus.AWAITING_CONFIRMATION,
            manager.state.status,
        )
        assertEquals(
            DecoderMode.HW_PLUS,
            manager.state.unsupportedMode,
        )
    }

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
        val retry = checkNotNull(
            manager.onDecoderFailure(
                video,
                DecoderMode.AUTO,
            ),
        )
        assertEquals(
            DecoderRetry(
                video,
                DecoderMode.FFMPEG,
            ),
            retry,
        )
        assertTrue(manager.takeRetry(retry))
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
    fun hardwareTriesHwPlusThenSoftwareWithoutRepeatingHardware() {
        val manager = DecoderRecoveryManager()
        assertEquals(
            DecoderRetry(
                video,
                DecoderMode.HW_PLUS,
            ),
            manager.onDecoderFailure(
                video,
                DecoderMode.AUTO,
                canTryHwPlus = true,
            ),
        )
        assertEquals(
            DecoderRetry(
                video,
                DecoderMode.FFMPEG,
            ),
            manager.onDecoderFailure(
                video,
                DecoderMode.HW_PLUS,
                canTryHwPlus = true,
            ),
        )
        assertNull(
            manager.onDecoderFailure(
                video,
                DecoderMode.FFMPEG,
                canTryHwPlus = true,
            ),
        )
        assertEquals(
            DecoderRecoveryStatus.FAILED,
            manager.state.status,
        )
    }

    @Test
    fun manualSwitchPrefersLastWorkingModeAndOnlyPromptsOnce() {
        val manager = DecoderRecoveryManager()
        manager.onVideoReady(DecoderMode.HW_PLUS)
        manager.onUserSelection(
            video,
            DecoderMode.FFMPEG,
        )
        assertNull(
            manager.onDecoderFailure(
                video,
                DecoderMode.FFMPEG,
                canTryHwPlus = true,
            ),
        )
        assertEquals(
            DecoderRecoveryStatus.AWAITING_CONFIRMATION,
            manager.state.status,
        )
        assertEquals(
            DecoderRetry(
                video,
                DecoderMode.HW_PLUS,
            ),
            manager.confirmFallback(),
        )
        assertEquals(
            DecoderRetry(
                video,
                DecoderMode.HARDWARE,
            ),
            manager.onDecoderFailure(
                video,
                DecoderMode.HW_PLUS,
            ),
        )
        assertNull(manager.confirmFallback())
    }

    @Test
    fun lateFailureAfterManualHwPlusSelectionRetriesHardwareSilently() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(
            video,
            DecoderMode.HW_PLUS,
        )
        val retry = checkNotNull(
            manager.onDecoderFailure(
                video,
                DecoderMode.HW_PLUS,
                showConfirmation = false,
            ),
        )
        assertEquals(
            DecoderRetry(
                video,
                DecoderMode.HARDWARE,
            ),
            retry,
        )
        assertEquals(
            DecoderRecoveryStatus.RECOVERING,
            manager.state.status,
        )
        manager.onMediaItemChanged()
        assertEquals(
            DecoderRecoveryStatus.NONE,
            manager.state.status,
        )
        assertFalse(manager.takeRetry(retry))
    }

    @Test
    fun softwareFailureTriesHardwareWithDefaultSettings() {
        val manager = DecoderRecoveryManager()
        assertEquals(
            DecoderRetry(
                video,
                DecoderMode.HARDWARE,
            ),
            manager.onDecoderFailure(
                video,
                DecoderMode.FFMPEG,
                canTryHwPlus = true,
            ),
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
