package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.edit

@Singleton
class LocalVaultPinRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : VaultPinRepository {

    private val preferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun hasPinSet(): Boolean = withContext(Dispatchers.IO) {
        preferences.contains(KEY_PIN_HASH)
    }

    override suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        val salt = generateSalt()
        preferences.edit {
            putString(KEY_PIN_HASH, hashPin(pin, salt))
            putString(KEY_SALT, salt)
        }
    }

    override suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val storedHash = preferences.getString(KEY_PIN_HASH, null) ?: return@withContext false
        val salt = preferences.getString(KEY_SALT, null) ?: return@withContext false
        hashPin(pin, salt) == storedHash
    }

    override suspend fun hasShownHideConfirmation(): Boolean = withContext(Dispatchers.IO) {
        preferences.getBoolean(KEY_HIDE_CONFIRMATION_SHOWN, false)
    }

    override suspend fun setHideConfirmationShown() = withContext(Dispatchers.IO) {
        preferences.edit {
            putBoolean(KEY_HIDE_CONFIRMATION_SHOWN, true)
        }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS_NAME = "vault_security_prefs"
        private const val KEY_PIN_HASH = "vault_pin_hash"
        private const val KEY_SALT = "vault_pin_salt"
        private const val KEY_HIDE_CONFIRMATION_SHOWN = "vault_hide_confirmation_shown"
    }
}