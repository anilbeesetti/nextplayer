package dev.anilbeesetti.nextplayer.core.media.network.sftp

import java.io.IOException
import java.security.PublicKey
import kotlin.io.encoding.Base64
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier

class SftpHostKeyVerifier(
    private val expectedFingerprint: String,
) : HostKeyVerifier {

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val presentedFingerprint = sha256Fingerprint(key)
        if (expectedFingerprint.isBlank()) {
            throw HostKeyConfirmationRequired(
                host = hostname,
                port = port,
                algorithm = key.algorithm,
                fingerprint = presentedFingerprint,
            )
        }
        if (expectedFingerprint == presentedFingerprint) return true
        throw HostKeyMismatch(
            expectedFingerprint = expectedFingerprint,
            presentedFingerprint = presentedFingerprint,
        )
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()

    private fun sha256Fingerprint(key: PublicKey): String {
        val encodedKey = Buffer.PlainBuffer().apply { putPublicKey(key) }.compactData
        val digest = SecurityUtils.getMessageDigest(SHA_256).digest(encodedKey)
        val encodedDigest = Base64.Default.withPadding(Base64.PaddingOption.ABSENT).encode(digest)
        return "SHA256:$encodedDigest"
    }

    private companion object {
        const val SHA_256 = "SHA-256"
    }
}

class HostKeyConfirmationRequired(
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprint: String,
) : IOException("Confirm SSH host key $fingerprint for $host:$port")

class HostKeyMismatch(
    val expectedFingerprint: String,
    val presentedFingerprint: String,
) : IOException("SSH host key changed")
