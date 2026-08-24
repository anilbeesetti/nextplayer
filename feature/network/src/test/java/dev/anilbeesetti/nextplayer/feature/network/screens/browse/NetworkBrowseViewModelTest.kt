package dev.anilbeesetti.nextplayer.feature.network.screens.browse

import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.proxy.NetworkStreamingProxy
import dev.anilbeesetti.nextplayer.core.media.network.sftp.HostKeyMismatch
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import dev.anilbeesetti.nextplayer.feature.network.MainDispatcherRule
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkBrowseViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `wrapped host key mismatch retains trusted and presented fingerprints`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val mismatch = HostKeyMismatch(
                expectedFingerprint = "SHA256:trusted",
                presentedFingerprint = "SHA256:presented",
            )
            val viewModel = viewModel(
                connectResult = Result.failure(IllegalStateException("SSH failed", mismatch)),
            )

            advanceUntilIdle()

            assertEquals(
                NetworkBrowseError(
                    message = "SSH failed",
                    hostKeyMismatch = NetworkBrowseHostKeyMismatch(
                        trustedFingerprint = "SHA256:trusted",
                        presentedFingerprint = "SHA256:presented",
                    ),
                ),
                viewModel.uiState.value.error,
            )
        }

    @Test
    fun `ordinary connection error keeps its message without fingerprint details`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(
                connectResult = Result.failure(IllegalStateException("Server unavailable")),
            )

            advanceUntilIdle()

            assertEquals("Server unavailable", viewModel.uiState.value.error?.message)
            assertNull(viewModel.uiState.value.error?.hostKeyMismatch)
        }

    private fun viewModel(connectResult: Result<Unit>): NetworkBrowseViewModel {
        val client = FakeNetworkClient(connectResult)
        val factory = NetworkClientFactory { client }
        return NetworkBrowseViewModel(
            connectionId = 7,
            path = null,
            repository = FakeRepository(connection()),
            streamingProxy = NetworkStreamingProxy(factory),
            clientFactory = factory,
        )
    }

    private fun connection() = NetworkConnection(
        id = 7,
        name = "Media server",
        protocol = NetworkProtocol.SFTP,
        host = "sftp.example",
        username = "media",
        hostKeyFingerprint = "SHA256:trusted",
    )
}

private class FakeRepository(
    private val connection: NetworkConnection,
) : NetworkConnectionRepository {
    override fun getConnections(): Flow<List<NetworkConnection>> = flowOf(listOf(connection))

    override suspend fun getConnection(id: Long): NetworkConnection = connection

    override suspend fun upsert(connection: NetworkConnection): Long = error("Not used")

    override suspend fun delete(id: Long) = error("Not used")
}

private class FakeNetworkClient(
    private val connectResult: Result<Unit>,
) : NetworkClient {
    override val rootPath: String = "/"

    override suspend fun connect(): Result<Unit> = connectResult

    override suspend fun disconnect() = Unit

    override fun isConnected(): Boolean = false

    override suspend fun listFiles(path: String): Result<List<NetworkFile>> = error("Not used")

    override suspend fun fileSize(path: String): Long = error("Not used")

    override suspend fun openStream(path: String, offset: Long): InputStream = error("Not used")
}
