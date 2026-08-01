package dev.anilbeesetti.nextplayer.core.media.network

import dev.anilbeesetti.nextplayer.core.media.network.clients.FtpClient
import dev.anilbeesetti.nextplayer.core.media.network.clients.SftpClient
import dev.anilbeesetti.nextplayer.core.media.network.clients.SmbClient
import dev.anilbeesetti.nextplayer.core.media.network.clients.WebDavClient
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultNetworkClientFactory @Inject constructor(
    private val sshKeyStore: SshKeyStore,
) : NetworkClientFactory {
    override fun create(connection: NetworkConnection): NetworkClient = when (connection.protocol) {
        NetworkProtocol.SMB -> SmbClient(connection)
        NetworkProtocol.FTP -> FtpClient(connection)
        NetworkProtocol.SFTP -> SftpClient(connection, sshKeyStore)
        NetworkProtocol.WEBDAV -> WebDavClient(connection)
    }
}
