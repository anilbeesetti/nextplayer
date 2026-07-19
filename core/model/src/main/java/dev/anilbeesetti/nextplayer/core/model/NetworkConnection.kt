package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable

enum class NetworkProtocol(val defaultPort: Int) {
    SMB(445),
    FTP(21),
    SFTP(22),
    WEBDAV(80),
}

enum class NetworkAuthentication {
    PASSWORD,
    SSH_KEY,
}

/**
 * A saved connection to a network location (SMB share, FTP, SFTP, or WebDAV server).
 *
 * [path] is the protocol-specific root: the share name for SMB, or the base directory for FTP,
 * SFTP, and WebDAV. Imported SSH keys remain in app-private storage and are referenced by their
 * generated [privateKeyFileName].
 */
data class NetworkConnection(
    val id: Long = 0,
    val name: String,
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int? = null,
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val useHttps: Boolean = false,
    val authentication: NetworkAuthentication = NetworkAuthentication.PASSWORD,
    val privateKeyFileName: String = "",
    val privateKeyPassphrase: String = "",
    val hostKeyFingerprint: String = "",
) : Serializable {

    val effectivePort: Int get() = port ?: protocol.defaultPort

    val isAnonymous: Boolean get() = username.isBlank()

    companion object {
        val sample = NetworkConnection(
            id = 1,
            name = "Home NAS",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.10",
            path = "Media",
            username = "guest",
        )
    }
}
