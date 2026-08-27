package dev.anilbeesetti.nextplayer.feature.player.service

import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryState
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderTrackType
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode

/** Keeps NextPlayer's decoder fallback policy separate from nextlib's decoder switching. */
internal class DecoderRecoveryManager {

    private var currentMedia: DecoderMediaIdentity? = null
    private val videoRecovery = TrackRecovery()
    private val audioRecovery = TrackRecovery()

    var state: DecoderRecoveryState = DecoderRecoveryState()
        private set

    fun onMediaItemChanged(media: DecoderMediaIdentity?): Boolean {
        if (media == currentMedia) return false

        currentMedia = media
        videoRecovery.reset()
        audioRecovery.reset()
        state = DecoderRecoveryState()
        return media != null
    }

    fun onUserSelection(trackType: DecoderTrackType, mode: DecoderMode?) {
        recoveryFor(trackType).apply {
            requiresConfirmation = mode != null
            fallbackModes = null
            fallbackIndex = 0
            failureRecorded = false
            failedMode = null
            failureCause = null
        }
        if (state.trackType == trackType) state = DecoderRecoveryState()
    }

    fun onDecoderFailure(
        trackType: DecoderTrackType,
        mode: DecoderMode?,
        cause: DecoderFailureCause,
    ): DecoderRecoveryAction {
        val recovery = recoveryFor(trackType)
        if (recovery.failureRecorded && recovery.failedMode == mode) {
            return DecoderRecoveryAction.Ignore
        }

        recovery.failureRecorded = true
        recovery.failedMode = mode
        recovery.failureCause = cause
        if (recovery.fallbackModes == null) {
            recovery.fallbackModes = mode.fallbackModes()
        }
        if (!recovery.hasFallbackMode()) {
            state = DecoderRecoveryState(
                status = DecoderRecoveryStatus.FAILED,
                trackType = trackType,
            )
            return DecoderRecoveryAction.ShowPlayerError
        }

        val fallbackMode = recovery.nextFallbackMode()

        if (recovery.requiresConfirmation) {
            state = DecoderRecoveryState(
                status = DecoderRecoveryStatus.AWAITING_CONFIRMATION,
                trackType = trackType,
                unsupportedMode = mode,
            )
            return DecoderRecoveryAction.AwaitUserConfirmation
        }

        recovery.consumeFallbackMode()
        state = DecoderRecoveryState(
            status = DecoderRecoveryStatus.RECOVERING,
            trackType = trackType,
        )
        return DecoderRecoveryAction.Retry(
            DecoderRetry(
                trackType = trackType,
                mode = fallbackMode,
                preparePlayer = cause == DecoderFailureCause.PLAYER_ERROR,
            ),
        )
    }

    fun confirmFallback(): DecoderRetry? {
        if (state.status != DecoderRecoveryStatus.AWAITING_CONFIRMATION) return null
        val trackType = state.trackType ?: return null
        val recovery = recoveryFor(trackType)
        if (!recovery.hasFallbackMode()) return null
        val fallbackMode = recovery.consumeFallbackMode()

        recovery.requiresConfirmation = false
        state = DecoderRecoveryState(
            status = DecoderRecoveryStatus.RECOVERING,
            trackType = trackType,
        )
        return DecoderRetry(
            trackType = trackType,
            mode = fallbackMode,
            preparePlayer = recovery.failureCause == DecoderFailureCause.PLAYER_ERROR,
        )
    }

    fun onDecoderInitialized(trackType: DecoderTrackType) {
        if (state.trackType == trackType && state.status == DecoderRecoveryStatus.RECOVERING) {
            state = DecoderRecoveryState()
        }
    }

    fun onNonDecoderError() {
        state = DecoderRecoveryState()
    }

    private fun recoveryFor(trackType: DecoderTrackType): TrackRecovery {
        return when (trackType) {
            DecoderTrackType.VIDEO -> videoRecovery
            DecoderTrackType.AUDIO -> audioRecovery
        }
    }
}

internal data class DecoderMediaIdentity(
    val index: Int,
    val mediaId: String,
    val uri: String?,
)

internal enum class DecoderFailureCause {
    PLAYER_ERROR,
    UNSUPPORTED_TRACK,
}

internal data class DecoderRetry(
    val trackType: DecoderTrackType,
    val mode: DecoderMode?,
    val preparePlayer: Boolean,
)

internal sealed interface DecoderRecoveryAction {
    data class Retry(val retry: DecoderRetry) : DecoderRecoveryAction

    data object AwaitUserConfirmation : DecoderRecoveryAction

    data object ShowPlayerError : DecoderRecoveryAction

    data object Ignore : DecoderRecoveryAction
}

private data class TrackRecovery(
    var requiresConfirmation: Boolean = false,
    var fallbackModes: List<DecoderMode?>? = null,
    var fallbackIndex: Int = 0,
    var failureRecorded: Boolean = false,
    var failedMode: DecoderMode? = null,
    var failureCause: DecoderFailureCause? = null,
) {
    fun hasFallbackMode(): Boolean = fallbackIndex < (fallbackModes?.size ?: 0)

    fun nextFallbackMode(): DecoderMode? = checkNotNull(fallbackModes)[fallbackIndex]

    fun consumeFallbackMode(): DecoderMode? {
        val mode = nextFallbackMode()
        fallbackIndex++
        return mode
    }

    fun reset() {
        requiresConfirmation = false
        fallbackModes = null
        fallbackIndex = 0
        failureRecorded = false
        failedMode = null
        failureCause = null
    }
}

private fun DecoderMode?.fallbackModes(): List<DecoderMode?> {
    return when (this) {
        null -> listOf(DecoderMode.APP_SOFTWARE)
        DecoderMode.HARDWARE -> listOf(DecoderMode.SOFTWARE, DecoderMode.APP_SOFTWARE)
        DecoderMode.SOFTWARE -> listOf(DecoderMode.APP_SOFTWARE)
        DecoderMode.APP_SOFTWARE -> listOf(null)
    }
}
