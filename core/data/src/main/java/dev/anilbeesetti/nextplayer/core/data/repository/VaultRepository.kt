package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.model.VaultPreferences
import kotlinx.coroutines.flow.StateFlow

interface VaultRepository {
    /** Emits current vault preferences (PIN hash, etc.). */
    val vaultPreferences: StateFlow<VaultPreferences>

    /**
     * Saves a SHA-256 hashed PIN. Pass null to clear the PIN.
     */
    suspend fun setPin(hashedPin: String?)

    /** Returns true if the provided hash matches the stored PIN hash. */
    suspend fun verifyPin(hashedPin: String): Boolean
}
