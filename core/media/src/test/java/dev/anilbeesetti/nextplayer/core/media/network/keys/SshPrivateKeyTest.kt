package dev.anilbeesetti.nextplayer.core.media.network.keys

import java.io.File
import java.security.KeyPairGenerator
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshPrivateKeyTest {
    @Test
    fun acceptsPrivateKeysThroughTheSameLoaderUsedForSftp() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encoded = Base64.getMimeEncoder(
            64,
            "\n".toByteArray(),
        ).encodeToString(pair.private.encoded)
        val file = File.createTempFile(
            "settings-test-",
            ".key",
        )
        try {
            file.writeText("-----BEGIN PRIVATE KEY-----\n$encoded\n-----END PRIVATE KEY-----\n")
            assertTrue(
                isValidSshPrivateKey(
                    file,
                    "",
                ),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsCorruptKeyMaterialBeforeItCanReplaceAUsableKey() {
        val file = File.createTempFile(
            "settings-test-",
            ".key",
        )
        try {
            file.writeText("not a key")
            assertFalse(
                isValidSshPrivateKey(
                    file,
                    "",
                ),
            )
        } finally {
            file.delete()
        }
    }
}
