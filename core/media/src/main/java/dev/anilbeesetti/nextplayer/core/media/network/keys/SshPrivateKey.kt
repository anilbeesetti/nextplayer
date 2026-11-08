package dev.anilbeesetti.nextplayer.core.media.network.keys

import dev.anilbeesetti.nextplayer.core.media.network.sftp.SftpSecurityProvider
import java.io.File
import net.schmizz.sshj.SSHClient

fun isValidSshPrivateKey(file: File, passphrase: String): Boolean = try {
    SftpSecurityProvider.install()
    SSHClient().use { client ->
        val key = client.loadKeys(
            file.absolutePath,
            passphrase.toCharArray(),
        )
        // SSHJ loads key material lazily; force decoding before replacing an existing key.
        key.private != null && key.public != null
    }
} catch (_: Exception) {
    false
}
