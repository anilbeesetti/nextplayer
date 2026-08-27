package dev.anilbeesetti.nextplayer.feature.player.service

import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderTrackType
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderRecoveryManagerTest {

    @Test
    fun defaultAutoVideoFailure_retriesAppSoftwareSilently() {
        val manager = DecoderRecoveryManager()

        assertEquals(
            DecoderRecoveryAction.Retry(
                DecoderRetry(
                    trackType = DecoderTrackType.VIDEO,
                    mode = DecoderMode.APP_SOFTWARE,
                    preparePlayer = true,
                ),
            ),
            manager.onDecoderFailure(
                trackType = DecoderTrackType.VIDEO,
                mode = null,
                cause = DecoderFailureCause.PLAYER_ERROR,
            ),
        )
        assertEquals(DecoderRecoveryStatus.RECOVERING, manager.state.status)
        assertEquals(DecoderTrackType.VIDEO, manager.state.trackType)
        assertNull(manager.state.unsupportedMode)
    }

    @Test
    fun unsupportedTrackRetry_doesNotPreparePlayer() {
        val manager = DecoderRecoveryManager()

        assertEquals(
            DecoderRecoveryAction.Retry(
                DecoderRetry(
                    trackType = DecoderTrackType.AUDIO,
                    mode = DecoderMode.APP_SOFTWARE,
                    preparePlayer = false,
                ),
            ),
            manager.onDecoderFailure(
                trackType = DecoderTrackType.AUDIO,
                mode = null,
                cause = DecoderFailureCause.UNSUPPORTED_TRACK,
            ),
        )
    }

    @Test
    fun explicitHardwareFailure_waitsForConfirmation() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(DecoderTrackType.VIDEO, DecoderMode.HARDWARE)

        assertEquals(
            DecoderRecoveryAction.AwaitUserConfirmation,
            manager.onDecoderFailure(
                trackType = DecoderTrackType.VIDEO,
                mode = DecoderMode.HARDWARE,
                cause = DecoderFailureCause.PLAYER_ERROR,
            ),
        )
        assertEquals(DecoderRecoveryStatus.AWAITING_CONFIRMATION, manager.state.status)
        assertEquals(DecoderTrackType.VIDEO, manager.state.trackType)
        assertEquals(DecoderMode.HARDWARE, manager.state.unsupportedMode)
        assertEquals(
            DecoderRetry(
                trackType = DecoderTrackType.VIDEO,
                mode = DecoderMode.SOFTWARE,
                preparePlayer = true,
            ),
            manager.confirmFallback(),
        )
        assertEquals(
            DecoderRecoveryAction.Retry(
                DecoderRetry(
                    trackType = DecoderTrackType.VIDEO,
                    mode = DecoderMode.APP_SOFTWARE,
                    preparePlayer = false,
                ),
            ),
            manager.onDecoderFailure(
                trackType = DecoderTrackType.VIDEO,
                mode = DecoderMode.SOFTWARE,
                cause = DecoderFailureCause.UNSUPPORTED_TRACK,
            ),
        )
    }

    @Test
    fun explicitAppSoftwareAudioFailure_fallsBackToAuto() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(DecoderTrackType.AUDIO, DecoderMode.APP_SOFTWARE)
        manager.onDecoderFailure(
            trackType = DecoderTrackType.AUDIO,
            mode = DecoderMode.APP_SOFTWARE,
            cause = DecoderFailureCause.UNSUPPORTED_TRACK,
        )

        assertEquals(
            DecoderRetry(
                trackType = DecoderTrackType.AUDIO,
                mode = null,
                preparePlayer = false,
            ),
            manager.confirmFallback(),
        )
    }

    @Test
    fun automaticFailure_remainsAutomatic() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection(DecoderTrackType.VIDEO, null)

        assertTrue(
            manager.onDecoderFailure(
                trackType = DecoderTrackType.VIDEO,
                mode = null,
                cause = DecoderFailureCause.PLAYER_ERROR,
            ) is DecoderRecoveryAction.Retry,
        )
    }

    @Test
    fun fallbackFailure_exposesPlayerError() {
        val manager = DecoderRecoveryManager()
        manager.onDecoderFailure(
            trackType = DecoderTrackType.VIDEO,
            mode = null,
            cause = DecoderFailureCause.PLAYER_ERROR,
        )
        manager.onDecoderInitialized(DecoderTrackType.VIDEO)

        assertEquals(
            DecoderRecoveryAction.ShowPlayerError,
            manager.onDecoderFailure(
                trackType = DecoderTrackType.VIDEO,
                mode = DecoderMode.APP_SOFTWARE,
                cause = DecoderFailureCause.PLAYER_ERROR,
            ),
        )
        assertEquals(DecoderRecoveryStatus.FAILED, manager.state.status)
    }

    @Test
    fun duplicateFailureForSameAttempt_isIgnored() {
        val manager = DecoderRecoveryManager()
        manager.onDecoderFailure(
            trackType = DecoderTrackType.VIDEO,
            mode = null,
            cause = DecoderFailureCause.UNSUPPORTED_TRACK,
        )

        assertEquals(
            DecoderRecoveryAction.Ignore,
            manager.onDecoderFailure(
                trackType = DecoderTrackType.VIDEO,
                mode = null,
                cause = DecoderFailureCause.PLAYER_ERROR,
            ),
        )
    }

    @Test
    fun videoFallback_doesNotConsumeAudioFallback() {
        val manager = DecoderRecoveryManager()
        manager.onDecoderFailure(
            trackType = DecoderTrackType.VIDEO,
            mode = null,
            cause = DecoderFailureCause.PLAYER_ERROR,
        )

        assertTrue(
            manager.onDecoderFailure(
                trackType = DecoderTrackType.AUDIO,
                mode = null,
                cause = DecoderFailureCause.PLAYER_ERROR,
            ) is DecoderRecoveryAction.Retry,
        )
    }

    @Test
    fun audioInitialization_doesNotClearVideoRecovery() {
        val manager = DecoderRecoveryManager()
        manager.onDecoderFailure(
            trackType = DecoderTrackType.VIDEO,
            mode = null,
            cause = DecoderFailureCause.PLAYER_ERROR,
        )

        manager.onDecoderInitialized(DecoderTrackType.AUDIO)

        assertEquals(DecoderRecoveryStatus.RECOVERING, manager.state.status)
        assertEquals(DecoderTrackType.VIDEO, manager.state.trackType)
    }

    @Test
    fun newMediaItem_restoresDefaultRecoveryForBothTracks() {
        val manager = DecoderRecoveryManager()
        assertTrue(manager.onMediaItemChanged(media("video")))
        manager.onUserSelection(DecoderTrackType.VIDEO, DecoderMode.HARDWARE)
        manager.onUserSelection(DecoderTrackType.AUDIO, DecoderMode.SOFTWARE)

        assertTrue(manager.onMediaItemChanged(media("next-video")))
        assertTrue(
            manager.onDecoderFailure(
                trackType = DecoderTrackType.VIDEO,
                mode = null,
                cause = DecoderFailureCause.PLAYER_ERROR,
            ) is DecoderRecoveryAction.Retry,
        )
        assertTrue(
            manager.onDecoderFailure(
                trackType = DecoderTrackType.AUDIO,
                mode = null,
                cause = DecoderFailureCause.PLAYER_ERROR,
            ) is DecoderRecoveryAction.Retry,
        )
    }

    @Test
    fun sameMediaIdentity_doesNotResetExplicitSelection() {
        val manager = DecoderRecoveryManager()
        manager.onMediaItemChanged(media("video"))
        manager.onUserSelection(DecoderTrackType.VIDEO, DecoderMode.HARDWARE)

        assertFalse(manager.onMediaItemChanged(media("video")))
        assertEquals(
            DecoderRecoveryAction.AwaitUserConfirmation,
            manager.onDecoderFailure(
                trackType = DecoderTrackType.VIDEO,
                mode = DecoderMode.HARDWARE,
                cause = DecoderFailureCause.PLAYER_ERROR,
            ),
        )
    }

    @Test
    fun sameMediaIdWithDifferentUri_startsFresh() {
        val manager = DecoderRecoveryManager()
        manager.onMediaItemChanged(media("video", uri = "file:///one.mp4"))
        manager.onUserSelection(DecoderTrackType.VIDEO, DecoderMode.HARDWARE)

        assertTrue(manager.onMediaItemChanged(media("video", uri = "file:///two.mp4")))
        assertTrue(
            manager.onDecoderFailure(
                trackType = DecoderTrackType.VIDEO,
                mode = null,
                cause = DecoderFailureCause.PLAYER_ERROR,
            ) is DecoderRecoveryAction.Retry,
        )
    }

    @Test
    fun clearedMedia_allowsSameMediaToStartFresh() {
        val manager = DecoderRecoveryManager()
        assertTrue(manager.onMediaItemChanged(media("video")))
        manager.onUserSelection(DecoderTrackType.VIDEO, DecoderMode.HARDWARE)

        assertFalse(manager.onMediaItemChanged(null))
        assertTrue(manager.onMediaItemChanged(media("video")))
    }

    private fun media(
        mediaId: String,
        uri: String = "file:///$mediaId.mp4",
    ) = DecoderMediaIdentity(
        index = 0,
        mediaId = mediaId,
        uri = uri,
    )
}
