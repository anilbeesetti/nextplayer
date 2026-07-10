package dev.anilbeesetti.nextplayer.feature.player.service

import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryState
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus

/** Decides whether a decoder error is retried silently, confirmed by the user, or exposed. */
internal class DecoderRecoveryManager {

    private var userSelectedMode = false
    private var fallbackAttempted = false

    var state: DecoderRecoveryState = DecoderRecoveryState()
        private set

    fun onNewMediaItem() {
        userSelectedMode = false
        fallbackAttempted = false
        state = DecoderRecoveryState()
    }

    fun onUserSelection() {
        userSelectedMode = true
        fallbackAttempted = false
        state = DecoderRecoveryState()
    }

    fun onDecoderError(mode: DecoderMode): DecoderRecoveryAction {
        if (fallbackAttempted) {
            state = DecoderRecoveryState()
            return DecoderRecoveryAction.ShowPlayerError
        }

        if (userSelectedMode) {
            state = DecoderRecoveryState(
                status = DecoderRecoveryStatus.AWAITING_CONFIRMATION,
                unsupportedMode = mode,
            )
            return DecoderRecoveryAction.AwaitUserConfirmation
        }

        fallbackAttempted = true
        state = DecoderRecoveryState(status = DecoderRecoveryStatus.RECOVERING)
        return DecoderRecoveryAction.Retry(mode.fallbackMode())
    }

    fun confirmFallback(): DecoderMode? {
        val unsupportedMode = state.unsupportedMode ?: return null
        fallbackAttempted = true
        state = DecoderRecoveryState(status = DecoderRecoveryStatus.RECOVERING)
        return unsupportedMode.fallbackMode()
    }

    fun onPlayerReady() {
        state = DecoderRecoveryState()
    }

    fun onNonDecoderError() {
        state = DecoderRecoveryState()
    }
}

internal sealed interface DecoderRecoveryAction {
    data class Retry(val mode: DecoderMode) : DecoderRecoveryAction

    data object AwaitUserConfirmation : DecoderRecoveryAction

    data object ShowPlayerError : DecoderRecoveryAction
}

private fun DecoderMode.fallbackMode(): DecoderMode {
    return when (this) {
        DecoderMode.HW_PLUS,
        DecoderMode.HW,
        -> DecoderMode.SW

        DecoderMode.SW -> DecoderMode.HW_PLUS
    }
}
