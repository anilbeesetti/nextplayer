package dev.anilbeesetti.nextplayer.feature.network.screens.list

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.media.network.keys.StagedSshKey
import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkViewModelTest {
    @Test
    fun `deleting SSH key connection deletes repository row before key`() = runBlocking {
        val events = mutableListOf<String>()
        val repository = FakeNetworkConnectionRepository(
            connection = connection(privateKeyFileName = "living-room.key"),
            events = events,
        )
        val keyStore = FakeSshKeyStore(events)

        deleteConnectionAndCleanup(7, repository, keyStore)

        assertEquals(listOf("repository.delete:7", "key.delete:living-room.key"), events)
    }

    @Test
    fun `deleting password connection does not delete a key`() = runBlocking {
        val events = mutableListOf<String>()
        val repository = FakeNetworkConnectionRepository(
            connection = connection(
                authentication = NetworkAuthentication.PASSWORD,
                privateKeyFileName = "",
            ),
            events = events,
        )
        val keyStore = FakeSshKeyStore(events)

        deleteConnectionAndCleanup(7, repository, keyStore)

        assertEquals(listOf("repository.delete:7"), events)
        assertTrue(keyStore.deletedFileNames.isEmpty())
    }

    @Test
    fun `repository failure retains SSH key`() {
        val events = mutableListOf<String>()
        val repository = FakeNetworkConnectionRepository(
            connection = connection(privateKeyFileName = "living-room.key"),
            events = events,
            deleteFailure = IllegalStateException("Room delete failed"),
        )
        val keyStore = FakeSshKeyStore(events)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { deleteConnectionAndCleanup(7, repository, keyStore) }
        }

        assertEquals(listOf("repository.delete:7"), events)
        assertTrue(keyStore.deletedFileNames.isEmpty())
    }

    private fun connection(
        authentication: NetworkAuthentication = NetworkAuthentication.SSH_KEY,
        privateKeyFileName: String,
    ) = NetworkConnection(
        id = 7,
        name = "Living room",
        protocol = NetworkProtocol.SFTP,
        host = "192.168.1.7",
        authentication = authentication,
        privateKeyFileName = privateKeyFileName,
    )
}

private class FakeNetworkConnectionRepository(
    private val connection: NetworkConnection?,
    private val events: MutableList<String>,
    private val deleteFailure: Throwable? = null,
) : NetworkConnectionRepository {
    override fun getConnections(): Flow<List<NetworkConnection>> = flowOf(emptyList())

    override suspend fun getConnection(id: Long): NetworkConnection? = connection

    override suspend fun upsert(connection: NetworkConnection): Long = error("Not used")

    override suspend fun delete(id: Long) {
        events += "repository.delete:$id"
        deleteFailure?.let { throw it }
    }
}

private class FakeSshKeyStore(
    private val events: MutableList<String>,
) : SshKeyStore {
    val deletedFileNames = mutableListOf<String>()

    override suspend fun stage(uri: Uri): StagedSshKey = error("Not used")

    override fun resolve(fileName: String): File = error("Not used")

    override suspend fun commit(fileName: String): String = error("Not used")

    override suspend fun delete(fileName: String) {
        deletedFileNames += fileName
        events += "key.delete:$fileName"
    }
}
