package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.Serializable

/**
 * Vault security preferences.
 * [pinHash] is the SHA-256 hex digest of the user's PIN, or null when no PIN is set yet.
 */
@Serializable
data class VaultPreferences(
    val pinHash: String? = null,
) {
    val hasPinSet: Boolean get() = pinHash != null
}
