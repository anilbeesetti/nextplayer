package dev.anilbeesetti.nextplayer.feature.player.service

import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderRecoveryManagerTest {

    @Test
    fun defaultHwPlusFailure_retriesSwSilently() {
        val manager = DecoderRecoveryManager()

        assertEquals(
            DecoderRecoveryAction.Retry(DecoderMode.SW),
            manager.onDecoderFailure(DecoderMode.HW_PLUS),
        )
        assertEquals(DecoderRecoveryStatus.RECOVERING, manager.state.status)
        assertNull(manager.state.unsupportedMode)
    }

    @Test
    fun explicitSelectionFailure_waitsForConfirmation() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection()

        assertEquals(
            DecoderRecoveryAction.AwaitUserConfirmation,
            manager.onDecoderFailure(DecoderMode.HW),
        )
        assertEquals(DecoderRecoveryStatus.AWAITING_CONFIRMATION, manager.state.status)
        assertEquals(DecoderMode.HW, manager.state.unsupportedMode)
        assertEquals(DecoderMode.SW, manager.confirmFallback())
        assertEquals(DecoderRecoveryStatus.RECOVERING, manager.state.status)
    }

    @Test
    fun explicitSwFailure_fallsBackToHwPlusAfterConfirmation() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection()
        manager.onDecoderFailure(DecoderMode.SW)

        assertEquals(DecoderMode.HW_PLUS, manager.confirmFallback())
    }

    @Test
    fun fallbackFailure_exposesPlayerError() {
        val manager = DecoderRecoveryManager()
        manager.onDecoderFailure(DecoderMode.HW_PLUS)
        manager.onDecoderInitialized()

        assertEquals(
            DecoderRecoveryAction.ShowPlayerError,
            manager.onDecoderFailure(DecoderMode.SW),
        )
        assertEquals(DecoderRecoveryStatus.FAILED, manager.state.status)
    }

    @Test
    fun newMediaItem_restoresSilentDefaultRecovery() {
        val manager = DecoderRecoveryManager()
        assertTrue(manager.onMediaItemChanged("video"))
        manager.onUserSelection()
        assertTrue(manager.onMediaItemChanged("next-video"))

        assertEquals(
            DecoderRecoveryAction.Retry(DecoderMode.SW),
            manager.onDecoderFailure(DecoderMode.HW_PLUS),
        )
    }

    @Test
    fun clearedMedia_allowsSameMediaToStartFresh() {
        val manager = DecoderRecoveryManager()
        assertTrue(manager.onMediaItemChanged("video"))
        manager.onUserSelection()

        assertFalse(manager.onMediaItemChanged(null))
        assertTrue(manager.onMediaItemChanged("video"))
        assertEquals(
            DecoderRecoveryAction.Retry(DecoderMode.SW),
            manager.onDecoderFailure(DecoderMode.HW_PLUS),
        )
    }

    @Test
    fun metadataUpdate_doesNotResetExplicitSelection() {
        val manager = DecoderRecoveryManager()
        manager.onMediaItemChanged("video")
        manager.onUserSelection()

        assertFalse(manager.onMediaItemChanged("video"))
        assertEquals(
            DecoderRecoveryAction.AwaitUserConfirmation,
            manager.onDecoderFailure(DecoderMode.HW_PLUS),
        )
    }
}
