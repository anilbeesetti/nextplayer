package dev.anilbeesetti.nextplayer.core.media.network.sftp

import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPublicKeySpec
import javax.crypto.KeyAgreement
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class SftpSecurityProviderInstrumentedTest {

    @Test
    fun installPreservesAndroidBcAndSuppliesSshjAlgorithms() {
        val androidBouncyCastle = requireNotNull(Security.getProvider("BC"))

        SftpSecurityProvider.install()

        assertSame(androidBouncyCastle, Security.getProvider("BC"))
        assertNotNull(KeyPairGenerator.getInstance("X25519", SftpSecurityProvider.NAME))
        assertNotNull(KeyAgreement.getInstance("X25519", SftpSecurityProvider.NAME))
        assertNotNull(KeyFactory.getInstance("ECDSA", SftpSecurityProvider.NAME))
        assertNotNull(AlgorithmParameters.getInstance("EC", SftpSecurityProvider.NAME))
        assertNotNull(KeyFactory.getInstance("Ed25519", SftpSecurityProvider.NAME))
        assertNotNull(Signature.getInstance("Ed25519", SftpSecurityProvider.NAME))

        val parameters = AlgorithmParameters.getInstance("EC", SftpSecurityProvider.NAME).apply {
            init(ECGenParameterSpec("secp256r1"))
        }
        val parameterSpec = parameters.getParameterSpec(ECParameterSpec::class.java)
        assertNotNull(
            KeyFactory.getInstance("ECDSA", SftpSecurityProvider.NAME).generatePublic(
                ECPublicKeySpec(parameterSpec.generator, parameterSpec),
            ),
        )
    }
}
