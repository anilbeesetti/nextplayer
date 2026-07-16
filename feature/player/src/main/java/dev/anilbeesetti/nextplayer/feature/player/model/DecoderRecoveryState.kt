package dev.anilbeesetti.nextplayer.feature.player.model

/** Player-service recovery state exposed to the controller while handling a decoder failure. */
enum class DecoderRecoveryStatus {
    NONE,
    RECOVERING,
    AWAITING_CONFIRMATION,
    FAILED,
}

/** Identifies whether decoder recovery is automatic, awaiting confirmation, or exhausted. */
data class DecoderRecoveryState(
    val status: DecoderRecoveryStatus = DecoderRecoveryStatus.NONE,
    val unsupportedMode: DecoderMode? = null,
)
