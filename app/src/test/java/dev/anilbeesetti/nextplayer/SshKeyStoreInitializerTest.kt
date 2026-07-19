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
            flowOf(
                listOf(
                    connection("feed.key"),
                    connection("feed.key"),
                    connection(" dead-beef.key "),
                    connection(""),
                    connection("../invalid.key"),
                    connection("cafe.key", protocol = NetworkProtocol.SMB),
                    connection("beef.key", authentication = NetworkAuthentication.PASSWORD),
                ),
            ),
        )
        val store = FakeSshKeyStore()

        initializeSshKeyStore(repository, store)

        assertEquals(setOf("feed.key", "dead-beef.key"), store.referencedFileNames)
    }

    @Test
    fun `database enumeration failure initializes store without cleanup set`() = runBlocking {
        val repository = FakeRepository(
            flow { throw IOException("database unavailable") },
        )
        val store = FakeSshKeyStore()

        initializeSshKeyStore(repository, store)

        assertEquals(null, store.referencedFileNames)
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

private class FakeRepository(
    private val connections: Flow<List<NetworkConnection>>,
) : NetworkConnectionRepository {
    override fun getConnections(): Flow<List<NetworkConnection>> = connections
    override suspend fun getConnection(id: Long): NetworkConnection? = error("Not used")
    override suspend fun upsert(connection: NetworkConnection): Long = error("Not used")
    override suspend fun delete(id: Long) = error("Not used")
}

private class FakeSshKeyStore : SshKeyStore {
    var referencedFileNames: Set<String>? = emptySet()

    override suspend fun initialize(referencedFileNames: Set<String>?) {
        this.referencedFileNames = referencedFileNames
    }

    override suspend fun stage(uri: Uri): StagedSshKey = error("Not used")
    override fun resolve(fileName: String): File = error("Not used")
    override suspend fun commit(fileName: String): String = error("Not used")
    override suspend fun delete(fileName: String) = error("Not used")
}
