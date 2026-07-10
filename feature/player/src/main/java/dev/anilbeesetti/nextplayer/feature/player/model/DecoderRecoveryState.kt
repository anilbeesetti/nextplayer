package dev.anilbeesetti.nextplayer.feature.player.model

/** Player-service recovery state exposed to the controller while handling a decoder error. */
enum class DecoderRecoveryStatus {
    NONE,
    RECOVERING,
    AWAITING_CONFIRMATION,
}

/** Identifies whether decoder recovery is automatic or waiting for user confirmation. */
data class DecoderRecoveryState(
    val status: DecoderRecoveryStatus = DecoderRecoveryStatus.NONE,
    val unsupportedMode: DecoderMode? = null,
)
