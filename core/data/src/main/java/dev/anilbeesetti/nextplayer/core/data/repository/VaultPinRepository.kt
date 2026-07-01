package dev.anilbeesetti.nextplayer.core.data.repository

/**
 * Manages the 4-digit PIN that protects the vault.
 */
interface VaultPinRepository {

    /**
     * Returns `true` if a vault PIN has been set up.
     */
    suspend fun hasPinSet(): Boolean

    /**
     * Hashes and stores [pin] as the vault PIN, replacing any existing one.
     */
    suspend fun setPin(pin: String)

    /**
     * Returns `true` if [pin] matches the currently stored vault PIN.
     */
    suspend fun verifyPin(pin: String): Boolean

    /**
     * Whether the one-time "hide this video?" confirmation dialog has already been shown to
     * the user. After the first hide, subsequent hides should happen immediately without
     * asking for confirmation again.
     */
    suspend fun hasShownHideConfirmation(): Boolean

    /**
     * Marks the one-time hide confirmation as shown so it won't be asked again.
     */
    suspend fun setHideConfirmationShown()
}