package dev.anilbeesetti.nextplayer.core.media.network.sftp

import java.security.KeyPair
import java.security.KeyPairGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpHostKeyVerifierTest {

    @Test
    fun `blank fingerprint requires confirmation with presented host key details`() {
        val keyPair = generateRsaKeyPair()

        val confirmation = confirmationFor(keyPair)

        assertEquals("host", confirmation.host)
        assertEquals(22, confirmation.port)
        assertEquals(keyPair.public.algorithm, confirmation.algorithm)
        assertTrue(confirmation.fingerprint.startsWith("SHA256:"))
    }

    @Test
    fun `matching fingerprint accepts host key`() {
        val keyPair = generateRsaKeyPair()
        val fingerprint = confirmationFor(keyPair).fingerprint

        assertTrue(SftpHostKeyVerifier(fingerprint).verify("host", 22, keyPair.public))
    }

    @Test
    fun `changed host key throws mismatch with expected and presented fingerprints`() {
        val expectedKeyPair = generateRsaKeyPair()
        val presentedKeyPair = generateRsaKeyPair()
        val expectedFingerprint = confirmationFor(expectedKeyPair).fingerprint

        val mismatch = assertThrows(HostKeyMismatch::class.java) {
            SftpHostKeyVerifier(expectedFingerprint).verify("host", 22, presentedKeyPair.public)
        }

        assertEquals(expectedFingerprint, mismatch.expectedFingerprint)
        assertTrue(mismatch.presentedFingerprint.startsWith("SHA256:"))
        assertNotEquals(expectedFingerprint, mismatch.presentedFingerprint)
    }

    private fun confirmationFor(keyPair: KeyPair) = assertThrows(HostKeyConfirmationRequired::class.java) {
        SftpHostKeyVerifier("").verify("host", 22, keyPair.public)
    }

    private fun generateRsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }
}
