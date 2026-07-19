package dev.anilbeesetti.nextplayer.feature.network.screens.list

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.media.network.keys.StagedSshKey
import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `deleting SSH key connection looks up then deletes row before key`() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeNetworkConnectionRepository(
            connection = connection(privateKeyFileName = "living-room.key"),
            events = events,
        )
        val keyStore = FakeSshKeyStore(events)

        deleteConnectionAndCleanup(7, repository, keyStore)

        assertEquals(
            listOf("repository.get:7", "repository.delete:7", "key.delete:living-room.key"),
            events,
        )
    }

    @Test
    fun `missing connection stops after lookup`() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeNetworkConnectionRepository(connection = null, events = events)
        val keyStore = FakeSshKeyStore(events)

        deleteConnectionAndCleanup(7, repository, keyStore)

        assertEquals(listOf("repository.get:7"), events)
    }

    @Test
    fun `lookup failure stops before repository deletion`() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("Room lookup failed")
        val repository = FakeNetworkConnectionRepository(
            connection = null,
            events = events,
            lookupFailure = failure,
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { deleteConnectionAndCleanup(7, repository, FakeSshKeyStore(events)) }
        }

        assertEquals(failure.message, thrown.message)
        assertSame(failure, thrown.cause ?: thrown)
        assertEquals(listOf("repository.get:7"), events)
    }

    @Test
    fun `repository failure retains SSH key`() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("Room delete failed")
        val repository = FakeNetworkConnectionRepository(
            connection = connection(privateKeyFileName = "living-room.key"),
            events = events,
            deleteFailure = failure,
        )
        val keyStore = FakeSshKeyStore(events)

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { deleteConnectionAndCleanup(7, repository, keyStore) }
        }

        assertEquals(failure.message, thrown.message)
        assertSame(failure, thrown.cause ?: thrown)
        assertEquals(listOf("repository.get:7", "repository.delete:7"), events)
    }

    @Test
    fun `password connection with stale key filename does not delete key`() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeNetworkConnectionRepository(
            connection = connection(
                authentication = NetworkAuthentication.PASSWORD,
                privateKeyFileName = "stale.key",
            ),
            events = events,
        )

        deleteConnectionAndCleanup(7, repository, FakeSshKeyStore(events))

        assertEquals(listOf("repository.get:7", "repository.delete:7"), events)
    }

    @Test
    fun `SSH key connection without key filename does not delete key`() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeNetworkConnectionRepository(
            connection = connection(privateKeyFileName = ""),
            events = events,
        )

        deleteConnectionAndCleanup(7, repository, FakeSshKeyStore(events))

        assertEquals(listOf("repository.get:7", "repository.delete:7"), events)
    }

    @Test
    fun `key deletion failure restores original connection`() {
        val events = mutableListOf<String>()
        val original = connection(privateKeyFileName = "living-room.key")
        val keyFailure = IllegalStateException("Key delete failed")
        val repository = FakeNetworkConnectionRepository(connection = original, events = events)
        val keyStore = FakeSshKeyStore(events, deleteFailure = keyFailure)

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { deleteConnectionAndCleanup(7, repository, keyStore) }
        }

        assertEquals(keyFailure.message, thrown.message)
        assertSame(keyFailure, thrown.cause ?: thrown)
        assertEquals(
            listOf(
                "repository.get:7",
                "repository.delete:7",
                "key.delete:living-room.key",
                "repository.upsert:7",
            ),
            events,
        )
        assertEquals(listOf(original), repository.upsertedConnections)
    }

    @Test
    fun `rollback failure is suppressed onto key deletion failure`() {
        val events = mutableListOf<String>()
        val keyFailure = IllegalStateException("Key delete failed")
        val rollbackFailure = IllegalArgumentException("Room rollback failed")
        val repository = FakeNetworkConnectionRepository(
            connection = connection(privateKeyFileName = "living-room.key"),
            events = events,
            upsertFailure = rollbackFailure,
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                deleteConnectionAndCleanup(
                    7,
                    repository,
                    FakeSshKeyStore(events, deleteFailure = keyFailure),
                )
            }
        }

        val originalKeyFailure = thrown.cause ?: thrown
        assertSame(keyFailure, originalKeyFailure)
        assertEquals(listOf(rollbackFailure), originalKeyFailure.suppressed.toList())
        assertEquals("repository.upsert:7", events.last())
    }

    @Test
    fun `cancellation after repository deletion cannot skip key cleanup`() = runTest {
        val events = mutableListOf<String>()
        val rowDeleted = CompletableDeferred<Unit>()
        val allowDeleteToReturn = CompletableDeferred<Unit>()
        val repository = FakeNetworkConnectionRepository(
            connection = connection(privateKeyFileName = "living-room.key"),
            events = events,
            afterDelete = {
                rowDeleted.complete(Unit)
                allowDeleteToReturn.await()
            },
        )
        val keyStore = FakeSshKeyStore(events, beforeDelete = { yield() })

        val deletion = launch {
            deleteConnectionAndCleanup(7, repository, keyStore)
        }
        rowDeleted.await()
        deletion.cancel()
        allowDeleteToReturn.complete(Unit)
        deletion.join()

        assertTrue(deletion.isCancelled)
        assertEquals(
            listOf("repository.get:7", "repository.delete:7", "key.delete:living-room.key"),
            events,
        )
    }

    @Test
    fun `public deletion path handles lookup delete and cleanup failures`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val lookupEvents = mutableListOf<String>()
            val deleteEvents = mutableListOf<String>()
            val cleanupEvents = mutableListOf<String>()
            NetworkViewModel(
                repository = FakeNetworkConnectionRepository(
                    connection = null,
                    events = lookupEvents,
                    lookupFailure = IllegalStateException("lookup"),
                ),
                sshKeyStore = FakeSshKeyStore(lookupEvents),
            ).deleteConnection(1)

            NetworkViewModel(
                repository = FakeNetworkConnectionRepository(
                    connection = connection(privateKeyFileName = "delete.key"),
                    events = deleteEvents,
                    deleteFailure = IllegalStateException("delete"),
                ),
                sshKeyStore = FakeSshKeyStore(deleteEvents),
            ).deleteConnection(2)

            NetworkViewModel(
                repository = FakeNetworkConnectionRepository(
                    connection = connection(privateKeyFileName = "cleanup.key"),
                    events = cleanupEvents,
                ),
                sshKeyStore = FakeSshKeyStore(
                    cleanupEvents,
                    deleteFailure = IllegalStateException("cleanup"),
                ),
            ).deleteConnection(3)

            advanceUntilIdle()

            assertEquals(listOf("repository.get:1"), lookupEvents)
            assertEquals(listOf("repository.get:2", "repository.delete:2"), deleteEvents)
            assertEquals("repository.upsert:7", cleanupEvents.last())
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
    private val lookupFailure: Throwable? = null,
    private val deleteFailure: Throwable? = null,
    private val upsertFailure: Throwable? = null,
    private val afterDelete: suspend () -> Unit = {},
) : NetworkConnectionRepository {
    val upsertedConnections = mutableListOf<NetworkConnection>()

    override fun getConnections(): Flow<List<NetworkConnection>> = flowOf(emptyList())

    override suspend fun getConnection(id: Long): NetworkConnection? {
        events += "repository.get:$id"
        lookupFailure?.let { throw it }
        return connection
    }

    override suspend fun upsert(connection: NetworkConnection): Long {
        events += "repository.upsert:${connection.id}"
        upsertedConnections += connection
        upsertFailure?.let { throw it }
        return connection.id
    }

    override suspend fun delete(id: Long) {
        events += "repository.delete:$id"
        deleteFailure?.let { throw it }
        afterDelete()
    }
}

private class FakeSshKeyStore(
    private val events: MutableList<String>,
    private val deleteFailure: Throwable? = null,
    private val beforeDelete: suspend () -> Unit = {},
) : SshKeyStore {
    override suspend fun stage(uri: Uri): StagedSshKey = error("Not used")

    override fun resolve(fileName: String): File = error("Not used")

    override suspend fun commit(fileName: String): String = error("Not used")

    override suspend fun delete(fileName: String) {
        beforeDelete()
        events += "key.delete:$fileName"
        deleteFailure?.let { throw it }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
