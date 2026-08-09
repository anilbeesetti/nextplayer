package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.database.dao.NetworkConnectionDao
import dev.anilbeesetti.nextplayer.core.database.entities.NetworkConnectionEntity
import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalNetworkConnectionRepositoryTest {
    private val dao = FakeNetworkConnectionDao()
    private val repository = LocalNetworkConnectionRepository(dao)

    @Test
    fun `upsert and get connection preserves authentication settings`() = runTest {
        val connection = NetworkConnection(
            name = "SFTP",
            protocol = NetworkProtocol.SFTP,
            host = "10.0.2.2",
            username = "alice",
            authentication = NetworkAuthentication.SSH_KEY,
            privateKeyFileName = "123.key",
            privateKeyPassphrase = "passphrase",
            hostKeyFingerprint = "SHA256:abc",
        )

        assertEquals(1L, repository.upsert(connection))
        assertEquals(connection.copy(id = 1), repository.getConnection(1))
    }

    @Test
    fun `unknown stored authentication falls back to password`() = runTest {
        dao.seed(
            NetworkConnectionEntity(
                id = 7,
                name = "Legacy",
                protocol = NetworkProtocol.SFTP.name,
                host = "10.0.2.2",
                port = 22,
                authentication = "UNKNOWN",
            ),
        )

        assertEquals(
            NetworkAuthentication.PASSWORD,
            repository.getConnection(7)?.authentication,
        )
    }

    private class FakeNetworkConnectionDao : NetworkConnectionDao {
        private val connections = MutableStateFlow<List<NetworkConnectionEntity>>(emptyList())

        fun seed(connection: NetworkConnectionEntity) {
            connections.value = connections.value + connection
        }

        override suspend fun upsert(connection: NetworkConnectionEntity): Long {
            val id = connection.id.takeIf { it != 0L } ?: 1L
            val savedConnection = connection.copy(id = id)
            connections.value = connections.value.filterNot { it.id == id } + savedConnection
            return id
        }

        override fun getAll(): Flow<List<NetworkConnectionEntity>> = connections

        override suspend fun getById(id: Long): NetworkConnectionEntity? =
            connections.value.firstOrNull { it.id == id }

        override suspend fun deleteById(id: Long) {
            connections.value = connections.value.filterNot { it.id == id }
        }
    }
}
