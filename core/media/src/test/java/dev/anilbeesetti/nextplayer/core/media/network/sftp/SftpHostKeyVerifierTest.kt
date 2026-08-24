package dev.anilbeesetti.nextplayer.core.media.network.sftp

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
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

    @Test
    fun `fingerprint matches fixed OpenSSH SHA256 vector`() {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(Base64.Default.decode(PUBLIC_KEY_BASE64)),
        )

        val confirmation = assertThrows(HostKeyConfirmationRequired::class.java) {
            SftpHostKeyVerifier("").verify("host", 22, publicKey)
        }

        assertEquals(OPENSSH_SHA256_FINGERPRINT, confirmation.fingerprint)
        assertTrue(SftpHostKeyVerifier(OPENSSH_SHA256_FINGERPRINT).verify("host", 22, publicKey))
    }

    private fun confirmationFor(keyPair: KeyPair) = assertThrows(HostKeyConfirmationRequired::class.java) {
        SftpHostKeyVerifier("").verify("host", 22, keyPair.public)
    }

    private fun generateRsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }

    private companion object {
        const val OPENSSH_SHA256_FINGERPRINT = "SHA256:nduAt4u4wQIOter2qOpMVAC6ST0oCGD8OJXXlbTbv20"
        const val PUBLIC_KEY_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArdxG0/VhPoiyEqopufeKFv2QpCXokNUUFMXLdxU" +
                "HypfgT3png4eEPypSRbK0FZbsGej6Cw+LfeWyCwqcD3SxH3YPmEkCpGBjAqEtTXmeu29fjVvrUWPc91h9" +
                "h+xYgn6ichrMXv1YP4Tmw/pITJKWCCPvW3bfHcSEN9Ghtsjg0Vv6U+sT/I7Bad5XRUhNLLAzg24RW5Oa" +
                "3eJN38wNWdFmm8A7YFfFJoLB+EMkqMCvaYz4C13ppK23DhyrWWhpLODYjXuXswknYampee2Pn351Eb0Mx" +
                "k6LalIA9BP34bDOaOpXramcunh+f3zgHSpggg4VM8dsUzD7uJX7PdRtEwXd7wIDAQAB"
    }
}
