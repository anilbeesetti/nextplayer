package dev.anilbeesetti.nextplayer.core.media.network.sftp

import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPublicKeySpec
import javax.crypto.KeyAgreement
import net.schmizz.sshj.common.Buffer.PlainBuffer
import net.schmizz.sshj.common.KeyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class SftpSecurityProviderInstrumentedTest {

    @Test
    fun sshjDecodesP256HostKeyWithInstalledProvider() {
        SftpSecurityProvider.install()
        val hostKey = PlainBuffer()
            .putString("nistp256")
            .putBytes(P256_GENERATOR)

        val publicKey = KeyType.ECDSA256.readPubKeyFromBuffer(hostKey) as ECPublicKey

        assertEquals(256, publicKey.params.curve.field.fieldSize)
        assertEquals(P256_GENERATOR_X, publicKey.w.affineX)
        assertEquals(P256_GENERATOR_Y, publicKey.w.affineY)
    }

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

    private companion object {
        val P256_GENERATOR_X = "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296".toBigInteger(16)
        val P256_GENERATOR_Y = "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5".toBigInteger(16)
        val P256_GENERATOR = (
            "04" +
                P256_GENERATOR_X.toString(16).padStart(64, '0') +
                P256_GENERATOR_Y.toString(16).padStart(64, '0')
            ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
