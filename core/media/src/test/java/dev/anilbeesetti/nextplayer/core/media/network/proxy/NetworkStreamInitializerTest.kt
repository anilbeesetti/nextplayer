package dev.anilbeesetti.nextplayer.core.media.network.proxy

import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkStreamInitializerTest {

    @Test
    fun `concurrent initial requests connect and fetch file size once`() = runTest {
        val client = BlockingConnectClient()
        val initializer = NetworkStreamInitializer(client, "/videos/movie.mp4")

        val results = listOf(
            async {
                initializer.initialize().also { client.openStream("/videos/movie.mp4") }
            },
            async {
                initializer.initialize().also { client.openStream("/videos/movie.mp4") }
            },
        )
        client.connectStarted.await()
        client.allowConnect.complete(Unit)

        assertEquals(listOf(1234L, 1234L), results.awaitAll())
        assertEquals(1, client.connectCalls.get())
        assertEquals(1, client.fileSizeCalls.get())
        assertEquals(2, client.openStreamCalls.get())
    }

    private class BlockingConnectClient : NetworkClient {
        override val rootPath = "/"
        val connectStarted = CompletableDeferred<Unit>()
        val allowConnect = CompletableDeferred<Unit>()
        val connectCalls = AtomicInteger()
        val fileSizeCalls = AtomicInteger()
        val openStreamCalls = AtomicInteger()

        @Volatile
        private var connected = false

        override suspend fun connect(): Result<Unit> {
            connectCalls.incrementAndGet()
            connectStarted.complete(Unit)
            allowConnect.await()
            connected = true
            return Result.success(Unit)
        }

        override suspend fun disconnect() = Unit

        override fun isConnected(): Boolean = connected

        override suspend fun listFiles(path: String): Result<List<NetworkFile>> =
            error("Not used")

        override suspend fun fileSize(path: String): Long {
            fileSizeCalls.incrementAndGet()
            return 1234L
        }

        override suspend fun openStream(path: String, offset: Long): InputStream {
            check(connected)
            check(fileSizeCalls.get() == 1)
            openStreamCalls.incrementAndGet()
            return ByteArrayInputStream(byteArrayOf())
        }
    }
}
