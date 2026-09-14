package dev.anilbeesetti.nextplayer.feature.network.screens.browse

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.NetworkUri
import dev.anilbeesetti.nextplayer.core.media.network.sftp.HostKeyMismatch
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.feature.network.MainDispatcherRule
import java.io.InputStream
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
                viewModel.state.value.error,
            )
        }

    @Test
    fun `ordinary connection error keeps its message without fingerprint details`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(
                connectResult = Result.failure(IllegalStateException("Server unavailable")),
            )

            advanceUntilIdle()

            assertEquals("Server unavailable", viewModel.state.value.error?.message)
            assertNull(viewModel.state.value.error?.hostKeyMismatch)
        }

    @Test
    fun `playing a video queues only folder videos in display order and preserves the selected item`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val conn = connection().copy(protocol = NetworkProtocol.WEBDAV)
            val first = NetworkFile("a #1.mp4", "Series/a #1.mp4", false)
            val second = NetworkFile("B.mp4", "Series/B.mp4", false)
            val folder = NetworkFile("Subfolder", "Series/Subfolder", true)
            val requests = mutableListOf<Pair<List<Uri>, Uri>>()
            val viewModel = viewModel(
                conn = conn,
                files = listOf(second, folder, NetworkFile("Notes.txt", "Series/Notes.txt", false), first),
                onPlayVideos = { uris, startUri -> requests.add(uris to startUri) },
            )
            advanceUntilIdle()

            viewModel.onAction(NetworkBrowseAction.PlayVideo(second))
            viewModel.onAction(NetworkBrowseAction.PlayAll)
            viewModel.onAction(NetworkBrowseAction.PlayVideo(folder))
            viewModel.onAction(NetworkBrowseAction.PlayVideo(NetworkFile("Missing.mp4", "Missing.mp4", false)))

            val queue = listOf(first, second).map { NetworkUri.build(conn, it.path) }
            assertEquals(listOf(queue to queue[1], queue to queue[0]), requests)
        }

    @Test
    fun `play all does nothing when the folder has no videos`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel(
            files = listOf(NetworkFile("Subfolder", "/Subfolder", true)),
            onPlayVideos = { _, _ -> error("An empty folder must not start playback") },
        )
        advanceUntilIdle()

        viewModel.onAction(NetworkBrowseAction.PlayAll)
    }

    @Test
    fun `history tracks progress and descendants without mixing connections or sibling folders`() =
        runTest(mainDispatcherRule.testDispatcher) {
            NetworkProtocol.entries.forEach { protocol ->
                val conn = connection().copy(protocol = protocol)
                val path = if (protocol == NetworkProtocol.FTP || protocol == NetworkProtocol.SFTP) "/Series" else "Series"
                val mediaRepository = FakeMediaRepository()
                val video = Video.sample.copy(
                    uriString = NetworkUri.build(conn, "$path/Season #1/Episode 1.mp4").toString(),
                    lastPlayedAt = Date(100),
                    playbackPosition = 250,
                    duration = 1_000,
                )
                mediaRepository.videos.addAll(
                    listOf(
                        video,
                        video.copy(uriString = NetworkUri.build(conn.copy(id = 8), "$path/Other.mp4").toString(), lastPlayedAt = Date(200)),
                        video.copy(uriString = NetworkUri.build(conn.copy(host = "other.example"), "$path/Other.mp4").toString(), lastPlayedAt = Date(300)),
                        video.copy(uriString = NetworkUri.build(conn, "${path}2/Other.mp4").toString(), lastPlayedAt = Date(400)),
                        video.copy(uriString = "content://media/external/video/media/1", lastPlayedAt = Date(500)),
                    ),
                )
                val viewModel = viewModel(conn = conn, path = path, mediaRepository = mediaRepository, connectionDelayMillis = 1_000)
                advanceUntilIdle()

                assertEquals(setOf("$path/Season #1/Episode 1.mp4"), viewModel.state.value.playbackHistory.keys)
                assertEquals("$path/Season #1/Episode 1.mp4", viewModel.state.value.recentlyPlayedPath)
                assertEquals(0.25f, viewModel.state.value.playbackHistory.values.single().playedPercentage)

                val finished = video.copy(
                    uriString = NetworkUri.build(conn, "$path/Episode 2.mp4").toString(),
                    lastPlayedAt = Date(600),
                    playbackPosition = -1,
                )
                mediaRepository.videos.add(finished)
                mediaRepository.notifyMediaChanged()
                advanceUntilIdle()
                viewModel.onAction(NetworkBrowseAction.Retry)
                advanceUntilIdle()

                assertEquals("$path/Episode 2.mp4", viewModel.state.value.recentlyPlayedPath)
                assertEquals(1f, viewModel.state.value.playbackHistory["$path/Episode 2.mp4"]?.playedPercentage)

                mediaRepository.videos.clear()
                mediaRepository.notifyMediaChanged()
                advanceUntilIdle()
                assertNull(viewModel.state.value.recentlyPlayedPath)
                assertTrue(viewModel.state.value.playbackHistory.isEmpty())
            }
        }

    @Test
    fun `display preferences update without reloading the folder`() = runTest(mainDispatcherRule.testDispatcher) {
        val preferences = FakePreferencesRepository()
        preferences.updateApplicationPreferences { it.copy(markLastPlayedMedia = false, showPlayedProgress = false) }
        val viewModel = viewModel(preferencesRepository = preferences)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.preferences.markLastPlayedMedia)
        assertFalse(viewModel.state.value.preferences.showPlayedProgress)

        preferences.updateApplicationPreferences { it.copy(markLastPlayedMedia = true, showPlayedProgress = true) }
        advanceUntilIdle()

        assertTrue(viewModel.state.value.preferences.markLastPlayedMedia)
        assertTrue(viewModel.state.value.preferences.showPlayedProgress)
    }

    private fun viewModel(
        connectResult: Result<Unit> = Result.success(Unit),
        conn: NetworkConnection = connection(),
        path: String? = null,
        files: List<NetworkFile> = emptyList(),
        mediaRepository: FakeMediaRepository = FakeMediaRepository(),
        preferencesRepository: FakePreferencesRepository = FakePreferencesRepository(),
        onPlayVideos: (List<Uri>, Uri) -> Unit = { _, _ -> },
        connectionDelayMillis: Long = 0,
    ): NetworkBrowseViewModel {
        val client = FakeNetworkClient(connectResult, files)
        val factory = NetworkClientFactory { client }
        return NetworkBrowseViewModel(
            input = NetworkBrowseViewModel.Input(connectionId = conn.id, path = path),
            output = NetworkBrowseViewModel.Output(navigateUp = {}, playVideos = onPlayVideos, openFolder = { _, _ -> }),
            repository = FakeRepository(conn, connectionDelayMillis),
            clientFactory = factory,
            mediaRepository = mediaRepository,
            preferencesRepository = preferencesRepository,
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
    private val connectionDelayMillis: Long,
) : NetworkConnectionRepository {
    override fun getConnections(): Flow<List<NetworkConnection>> = flowOf(listOf(connection))

    override suspend fun getConnection(id: Long): NetworkConnection {
        delay(connectionDelayMillis)
        return connection
    }

    override suspend fun upsert(connection: NetworkConnection): Long = error("Not used")

    override suspend fun delete(id: Long) = error("Not used")
}

private class FakeNetworkClient(
    private val connectResult: Result<Unit>,
    private val files: List<NetworkFile>,
) : NetworkClient {
    override val rootPath: String = "/"

    override suspend fun connect(): Result<Unit> = connectResult

    override suspend fun disconnect() = Unit

    override fun isConnected(): Boolean = false

    override suspend fun listFiles(path: String): Result<List<NetworkFile>> = Result.success(files)

    override suspend fun fileSize(path: String): Long = error("Not used")

    override suspend fun openStream(path: String, offset: Long): InputStream = error("Not used")
}
