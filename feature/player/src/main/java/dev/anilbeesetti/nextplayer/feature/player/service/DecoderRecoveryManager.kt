package dev.anilbeesetti.nextplayer.feature.player.service

import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryState
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderTrackType

/** Keeps NextPlayer's decoder fallback policy separate from nextlib's decoder switching. */
internal class DecoderRecoveryManager {

    private val videoRecovery = TrackRecovery()
    private val audioRecovery = TrackRecovery()

    // Terminal errors and confirmation take precedence over background recovery.
    val state: DecoderRecoveryState
        get() = listOf(
            videoRecovery.state,
            audioRecovery.state,
        ).maxBy { it.status }

    fun onMediaItemChanged(audioMode: DecoderMode = DecoderMode.AUTO) {
        videoRecovery.reset()
        onUserSelection(
            DecoderTrackType.AUDIO,
            mode = audioMode,
        )
    }

    fun onVideoReady(mode: DecoderMode) {
        if (videoRecovery.pendingRetry != null) return
        videoRecovery.lastWorkingMode = mode.videoBackend
        onDecoderInitialized(DecoderTrackType.VIDEO)
    }

    fun onUserSelection(
        trackType: DecoderTrackType,
        mode: DecoderMode,
        effectiveMode: DecoderMode = mode,
    ) {
        recoveryFor(trackType = trackType).apply {
            if (trackType == DecoderTrackType.VIDEO) {
                state = DecoderRecoveryState()
                failedModes.remove(effectiveMode.videoBackend)
                pendingRetry = null
            } else {
                reset()
            }
            requiresConfirmation = mode != DecoderMode.AUTO
        }
    }

    fun onDecoderFailure(
        trackType: DecoderTrackType,
        mode: DecoderMode,
        canTryHwPlus: Boolean = false,
        requestedMode: DecoderMode = mode,
        retrySoftwareReader: Boolean = false,
        showConfirmation: Boolean = true,
    ): DecoderRetry? {
        val recovery = recoveryFor(trackType)
        val failedMode = if (trackType == DecoderTrackType.VIDEO) mode.videoBackend else mode
        if (!retrySoftwareReader && !recovery.failedModes.add(failedMode)) return null

        val candidates = when (trackType) {
            DecoderTrackType.VIDEO -> listOfNotNull(
                DecoderMode.FFMPEG.takeIf { retrySoftwareReader },
                recovery.lastWorkingMode?.takeUnless { it == DecoderMode.HW_PLUS && !canTryHwPlus },
                when (failedMode) {
                    DecoderMode.HARDWARE -> DecoderMode.HW_PLUS.takeIf { canTryHwPlus }
                    DecoderMode.HW_PLUS, DecoderMode.FFMPEG -> DecoderMode.HARDWARE
                    DecoderMode.AUTO -> null
                },
                DecoderMode.FFMPEG.takeUnless { failedMode == DecoderMode.FFMPEG },
            )

            DecoderTrackType.AUDIO -> if (mode == DecoderMode.FFMPEG) {
                listOf(DecoderMode.AUTO.takeIf { recovery.requiresConfirmation })
            } else {
                listOf(DecoderMode.FFMPEG)
            }.filterNotNull()
        }
        val fallbackMode = candidates.firstOrNull { it !in recovery.failedModes }
        if (fallbackMode == null) {
            recovery.pendingRetry = null
            recovery.state = DecoderRecoveryState(
                DecoderRecoveryStatus.FAILED,
                trackType,
            )
            return null
        }

        val retry = DecoderRetry(
            trackType,
            fallbackMode,
        )
        recovery.pendingRetry = retry
        if (recovery.requiresConfirmation && showConfirmation && !retrySoftwareReader) {
            recovery.state = DecoderRecoveryState(
                status = DecoderRecoveryStatus.AWAITING_CONFIRMATION,
                trackType = trackType,
                unsupportedMode = requestedMode,
            )
            return null
        }
        return startRetry(retry)
    }

    fun confirmFallback(): DecoderRetry? {
        if (state.status != DecoderRecoveryStatus.AWAITING_CONFIRMATION) return null
        val trackType = state.trackType ?: return null
        return recoveryFor(trackType).pendingRetry?.let(::startRetry)
    }

    fun takeRetry(retry: DecoderRetry): Boolean {
        val recovery = recoveryFor(retry.trackType)
        if (recovery.pendingRetry !== retry ||
            recovery.state.status != DecoderRecoveryStatus.RECOVERING
        ) {
            return false
        }
        recovery.pendingRetry = null
        return true
    }

    private fun startRetry(retry: DecoderRetry): DecoderRetry {
        val recovery = recoveryFor(retry.trackType)
        recovery.requiresConfirmation = false
        recovery.state = DecoderRecoveryState(
            DecoderRecoveryStatus.RECOVERING,
            retry.trackType,
        )
        return retry
    }

    fun onDecoderInitialized(trackType: DecoderTrackType) {
        val recovery = recoveryFor(trackType = trackType)
        if (recovery.pendingRetry == null &&
            recovery.state.status == DecoderRecoveryStatus.RECOVERING
        ) {
            recovery.state = DecoderRecoveryState()
        }
    }

    fun onNonDecoderError() {
        videoRecovery.state = DecoderRecoveryState()
        audioRecovery.state = DecoderRecoveryState()
    }

    private fun recoveryFor(trackType: DecoderTrackType): TrackRecovery = when (trackType) {
        DecoderTrackType.VIDEO -> videoRecovery
        DecoderTrackType.AUDIO -> audioRecovery
    }
}

internal data class DecoderRetry(val trackType: DecoderTrackType, val mode: DecoderMode)

private class TrackRecovery {
    var state = DecoderRecoveryState()
    var requiresConfirmation = false
    val failedModes = mutableSetOf<DecoderMode>()
    var lastWorkingMode: DecoderMode? = null
    var pendingRetry: DecoderRetry? = null

    fun reset() {
        state = DecoderRecoveryState()
        requiresConfirmation = false
        failedModes.clear()
        lastWorkingMode = null
        pendingRetry = null
    }
}

private val DecoderMode.videoBackend: DecoderMode
    get() = if (this == DecoderMode.AUTO) DecoderMode.HARDWARE else this
