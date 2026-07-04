package dev.anilbeesetti.nextplayer.core.media.network

import dev.anilbeesetti.nextplayer.core.media.network.clients.FtpClient
import dev.anilbeesetti.nextplayer.core.media.network.clients.SmbClient
import dev.anilbeesetti.nextplayer.core.media.network.clients.WebDavClient
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol

object NetworkClientFactory {
    fun create(connection: NetworkConnection): NetworkClient = when (connection.protocol) {
        NetworkProtocol.SMB -> SmbClient(connection)
        NetworkProtocol.FTP -> FtpClient(connection)
        NetworkProtocol.WEBDAV -> WebDavClient(connection)
    }
}
