package dev.anilbeesetti.nextplayer.core.media.network.sftp

import java.security.Provider
import java.security.Security
import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider

internal object SftpSecurityProvider {
    const val NAME = "NextPlayerBC"

    @Synchronized
    fun install() {
        val existingProvider = Security.getProvider(NAME)
        check(existingProvider == null || existingProvider is DelegatingBouncyCastleProvider) {
            "Refusing unrelated $NAME security provider"
        }
        if (existingProvider == null) {
            check(Security.addProvider(DelegatingBouncyCastleProvider()) > 0) {
                "Unable to install $NAME security provider"
            }
        }
        SecurityUtils.setSecurityProvider(NAME)
    }

    @Suppress("DEPRECATION")
    private class DelegatingBouncyCastleProvider : Provider(NAME, VERSION, DESCRIPTION) {
        init {
            putAll(BouncyCastleProvider())
        }
    }

    private const val VERSION = 1.0
    private const val DESCRIPTION = "Next Player bundled Bouncy Castle provider"
}
