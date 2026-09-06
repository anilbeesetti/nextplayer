package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalVaultPinRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val repository = LocalVaultPinRepository(context)

    @Test
    fun `biometrics default to disabled for existing vaults`() = runTest {
        context.getSharedPreferences("vault_security_prefs", Context.MODE_PRIVATE).edit {
            putString("vault_pin_hash", "existing hash")
        }

        assertTrue(repository.hasPinSet())
        assertFalse(repository.isBiometricEnabled())
    }

    @Test
    fun `biometric choice persists across repository instances`() = runTest {
        repository.setPin("1234")
        repository.setBiometricEnabled(true)
        val reopened = LocalVaultPinRepository(context)
        assertTrue(reopened.isBiometricEnabled())

        reopened.setBiometricEnabled(false)

        assertFalse(LocalVaultPinRepository(context).isBiometricEnabled())
        assertTrue(repository.verifyPin("1234"))
    }

    @Test
    fun `setting a new PIN requires a new biometric opt in`() = runTest {
        repository.setPin("1234")
        assertFalse(repository.isBiometricEnabled())
        repository.setBiometricEnabled(true)

        repository.setPin("5678")

        assertFalse(repository.isBiometricEnabled())
        assertTrue(repository.verifyPin("5678"))
    }
}
