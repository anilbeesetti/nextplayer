package dev.anilbeesetti.nextplayer.core.media.network.sftp

import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Provider
import java.security.Security
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPublicKeySpec
import javax.crypto.KeyAgreement
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SftpSecurityProviderTest {

    @Test
    fun `install leaves provider registered as BC unchanged`() {
        val existingBouncyCastle = Security.getProvider("BC")

        SftpSecurityProvider.install()

        assertSame(existingBouncyCastle, Security.getProvider("BC"))
    }

    @Test
    fun `installed provider supplies SSHJ algorithms`() {
        SftpSecurityProvider.install()

        assertNotNull(KeyPairGenerator.getInstance("X25519", SftpSecurityProvider.NAME))
        assertNotNull(KeyAgreement.getInstance("X25519", SftpSecurityProvider.NAME))
        assertNotNull(KeyFactory.getInstance("ECDSA", SftpSecurityProvider.NAME))
        assertNotNull(AlgorithmParameters.getInstance("EC", SftpSecurityProvider.NAME))
        assertNotNull(KeyFactory.getInstance("Ed25519", SftpSecurityProvider.NAME))
        assertNotNull(Signature.getInstance("Ed25519", SftpSecurityProvider.NAME))
    }

    @Test
    fun `installed provider constructs P256 host public key`() {
        SftpSecurityProvider.install()
        val parameters = AlgorithmParameters.getInstance("EC", SftpSecurityProvider.NAME).apply {
            init(ECGenParameterSpec("secp256r1"))
        }
        val parameterSpec = parameters.getParameterSpec(ECParameterSpec::class.java)

        val publicKey = KeyFactory.getInstance("ECDSA", SftpSecurityProvider.NAME).generatePublic(
            ECPublicKeySpec(parameterSpec.generator, parameterSpec),
        )

        assertNotNull(publicKey)
    }

    @Test
    fun `install rejects an unrelated provider using the application provider name`() {
        val installedProvider = Security.getProvider(SftpSecurityProvider.NAME)
        Security.removeProvider(SftpSecurityProvider.NAME)
        Security.addProvider(UnrelatedProvider())

        try {
            assertThrows(IllegalStateException::class.java) {
                SftpSecurityProvider.install()
            }
        } finally {
            Security.removeProvider(SftpSecurityProvider.NAME)
            if (installedProvider != null) {
                Security.addProvider(installedProvider)
            }
            SftpSecurityProvider.install()
        }
    }

    @Suppress("DEPRECATION")
    private class UnrelatedProvider : Provider(SftpSecurityProvider.NAME, 1.0, "Unrelated provider")
}
