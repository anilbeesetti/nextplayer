package dev.anilbeesetti.nextplayer.core.media.network

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.media.network.clients.FtpClient
import dev.anilbeesetti.nextplayer.core.media.network.clients.SftpClient
import dev.anilbeesetti.nextplayer.core.media.network.clients.SmbClient
import dev.anilbeesetti.nextplayer.core.media.network.clients.WebDavClient
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.media.network.keys.StagedSshKey
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkClientFactoryTest {

    private val factory = DefaultNetworkClientFactory(FakeSshKeyStore())

    @Test
    fun `factory creates SFTP client`() {
        assertTrue(factory.create(connection(NetworkProtocol.SFTP)) is SftpClient)
    }

    @Test
    fun `factory retains existing protocol client types`() {
        assertTrue(factory.create(connection(NetworkProtocol.SMB)) is SmbClient)
        assertTrue(factory.create(connection(NetworkProtocol.FTP)) is FtpClient)
        assertTrue(factory.create(connection(NetworkProtocol.WEBDAV)) is WebDavClient)
    }

    private fun connection(protocol: NetworkProtocol) = NetworkConnection(
        name = protocol.name,
        protocol = protocol,
        host = "host",
    )

    private class FakeSshKeyStore : SshKeyStore {
        override suspend fun stage(uri: Uri): StagedSshKey = error("Not used")
        override fun resolve(fileName: String): File = error("Not used")
        override suspend fun commit(fileName: String): String = error("Not used")
        override suspend fun delete(fileName: String) = Unit
    }
}
