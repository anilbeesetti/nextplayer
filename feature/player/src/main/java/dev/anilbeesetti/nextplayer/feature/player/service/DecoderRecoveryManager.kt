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

    // Terminal errors and confirmation take precedence over background recovery.
    val state: DecoderRecoveryState
        get() = listOf(videoRecovery.state, audioRecovery.state).maxBy { it.status }

    fun onMediaItemChanged(media: DecoderMediaIdentity?): Boolean {
        if (media == currentMedia) return false

        currentMedia = media
        videoRecovery.reset()
        audioRecovery.reset()
        return media != null
    }

    fun onUserSelection(trackType: DecoderTrackType, mode: DecoderMode) {
        recoveryFor(trackType).apply {
            reset()
            requiresConfirmation = mode != DecoderMode.AUTO
        }
    }

    fun onDecoderFailure(
        trackType: DecoderTrackType,
        mode: DecoderMode,
    ): DecoderRetry? {
        val recovery = recoveryFor(trackType)
        if (recovery.failedMode == mode) {
            return null
        }

        recovery.failedMode = mode
        if (recovery.fallbackModes == null) {
            recovery.fallbackModes = ArrayDeque(mode.fallbackModes())
        }
        if (recovery.fallbackModes.isNullOrEmpty()) {
            recovery.state = DecoderRecoveryState(
                status = DecoderRecoveryStatus.FAILED,
                trackType = trackType,
            )
            return null
        }

        if (recovery.requiresConfirmation) {
            recovery.state = DecoderRecoveryState(
                status = DecoderRecoveryStatus.AWAITING_CONFIRMATION,
                trackType = trackType,
                unsupportedMode = mode,
            )
            return null
        }

        val fallbackMode = checkNotNull(recovery.fallbackModes).removeFirst()
        recovery.state = DecoderRecoveryState(
            status = DecoderRecoveryStatus.RECOVERING,
            trackType = trackType,
        )
        return DecoderRetry(trackType, fallbackMode)
    }

    fun confirmFallback(): DecoderRetry? {
        if (state.status != DecoderRecoveryStatus.AWAITING_CONFIRMATION) return null
        val trackType = state.trackType ?: return null
        val recovery = recoveryFor(trackType)
        if (recovery.fallbackModes.isNullOrEmpty()) return null
        val fallbackMode = checkNotNull(recovery.fallbackModes).removeFirst()

        recovery.requiresConfirmation = false
        recovery.state = DecoderRecoveryState(
            status = DecoderRecoveryStatus.RECOVERING,
            trackType = trackType,
        )
        return DecoderRetry(
            trackType = trackType,
            mode = fallbackMode,
        )
    }

    fun onDecoderInitialized(trackType: DecoderTrackType) {
        val recovery = recoveryFor(trackType)
        if (recovery.state.status == DecoderRecoveryStatus.RECOVERING) {
            recovery.state = DecoderRecoveryState()
        }
    }

    fun onNonDecoderError() {
        videoRecovery.state = DecoderRecoveryState()
        audioRecovery.state = DecoderRecoveryState()
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

internal data class DecoderRetry(
    val trackType: DecoderTrackType,
    val mode: DecoderMode,
)

private class TrackRecovery {
    var state = DecoderRecoveryState()
    var requiresConfirmation = false
    var fallbackModes: ArrayDeque<DecoderMode>? = null
    var failedMode: DecoderMode? = null

    fun reset() {
        state = DecoderRecoveryState()
        requiresConfirmation = false
        fallbackModes = null
        failedMode = null
    }
}

private fun DecoderMode.fallbackModes(): List<DecoderMode> {
    return when (this) {
        DecoderMode.AUTO -> listOf(DecoderMode.FFMPEG)
        DecoderMode.HARDWARE -> listOf(DecoderMode.SOFTWARE, DecoderMode.FFMPEG)
        DecoderMode.SOFTWARE -> listOf(DecoderMode.FFMPEG)
        DecoderMode.FFMPEG -> listOf(DecoderMode.AUTO)
    }
}
