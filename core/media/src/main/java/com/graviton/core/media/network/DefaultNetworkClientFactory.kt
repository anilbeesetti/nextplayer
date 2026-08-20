package com.graviton.core.media.network

import com.graviton.core.media.network.clients.FtpClient
import com.graviton.core.media.network.clients.SftpClient
import com.graviton.core.media.network.clients.SmbClient
import com.graviton.core.media.network.clients.WebDavClient
import com.graviton.core.media.network.keys.SshKeyStore
import com.graviton.core.model.NetworkConnection
import com.graviton.core.model.NetworkProtocol
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
