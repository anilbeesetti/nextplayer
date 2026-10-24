package dev.anilbeesetti.nextplayer

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.media.network.keys.StagedSshKey
import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SshKeyStoreInitializerTest {

    @Test
    fun `startup passes only valid SFTP key-auth references to store`() = runBlocking {
        val repository = FakeRepository(
            connections = flowOf(
                value = listOf(
                    connection(fileName = "feed.key"),
                    connection(fileName = "feed.key"),
                    connection(fileName = " dead-beef.key "),
                    connection(fileName = ""),
                    connection(fileName = "../invalid.key"),
                    connection(
                        fileName = "cafe.key",
                        protocol = NetworkProtocol.SMB,
                    ),
                    connection(
                        fileName = "beef.key",
                        authentication = NetworkAuthentication.PASSWORD,
                    ),
                ),
            ),
        )
        val store = FakeSshKeyStore()

        initializeSshKeyStore(
            repository = repository,
            sshKeyStore = store,
        )

        assertEquals(
            setOf(
                "feed.key",
                "dead-beef.key",
            ),
            store.referencedFileNames,
        )
    }

    @Test
    fun `database enumeration failure initializes store without cleanup set`() = runBlocking {
        val repository = FakeRepository(
            connections = flow { throw IOException("database unavailable") },
        )
        val store = FakeSshKeyStore()

        initializeSshKeyStore(
            repository = repository,
            sshKeyStore = store,
        )

        assertEquals(
            null,
            store.referencedFileNames,
        )
    }

    private fun connection(
        fileName: String,
        protocol: NetworkProtocol = NetworkProtocol.SFTP,
        authentication: NetworkAuthentication = NetworkAuthentication.SSH_KEY,
    ) = NetworkConnection(
        name = fileName,
        protocol = protocol,
        host = "host",
        authentication = authentication,
        privateKeyFileName = fileName,
    )
}

private class FakeRepository(private val connections: Flow<List<NetworkConnection>>) :
    NetworkConnectionRepository {
    override fun getConnections(): Flow<List<NetworkConnection>> = connections
    override suspend fun getConnection(id: Long): NetworkConnection? = error(message = "Not used")
    override suspend fun upsert(connection: NetworkConnection): Long = error(message = "Not used")
    override suspend fun delete(id: Long) = error(message = "Not used")
}

private class FakeSshKeyStore : SshKeyStore {
    var referencedFileNames: Set<String>? = emptySet()

    override suspend fun initialize(referencedFileNames: Set<String>?) {
        this.referencedFileNames = referencedFileNames
    }

    override suspend fun stage(uri: Uri): StagedSshKey = error(message = "Not used")
    override fun resolve(fileName: String): File = error(message = "Not used")
    override suspend fun commit(fileName: String): String = error(message = "Not used")
    override suspend fun delete(fileName: String) = error(message = "Not used")
}
