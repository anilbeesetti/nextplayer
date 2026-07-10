package dev.anilbeesetti.nextplayer.feature.player.service

import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecoderRecoveryManagerTest {

    @Test
    fun defaultHwPlusFailure_retriesSwSilently() {
        val manager = DecoderRecoveryManager()

        assertEquals(
            DecoderRecoveryAction.Retry(DecoderMode.SW),
            manager.onDecoderError(DecoderMode.HW_PLUS),
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
            manager.onDecoderError(DecoderMode.HW),
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
        manager.onDecoderError(DecoderMode.SW)

        assertEquals(DecoderMode.HW_PLUS, manager.confirmFallback())
    }

    @Test
    fun fallbackFailure_exposesPlayerError() {
        val manager = DecoderRecoveryManager()
        manager.onDecoderError(DecoderMode.HW_PLUS)

        assertEquals(
            DecoderRecoveryAction.ShowPlayerError,
            manager.onDecoderError(DecoderMode.SW),
        )
        assertEquals(DecoderRecoveryStatus.NONE, manager.state.status)
    }

    @Test
    fun newMediaItem_restoresSilentDefaultRecovery() {
        val manager = DecoderRecoveryManager()
        manager.onUserSelection()
        manager.onNewMediaItem()

        assertEquals(
            DecoderRecoveryAction.Retry(DecoderMode.SW),
            manager.onDecoderError(DecoderMode.HW_PLUS),
        )
    }
}
