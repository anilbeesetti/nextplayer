package dev.anilbeesetti.nextplayer.core.media.network.clients

import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbClientTest {

    @Test
    fun `server root reaches connection test instead of rejecting the path`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val client = SmbClient(
            NetworkConnection(
                name = "Server root",
                protocol = NetworkProtocol.SMB,
                host = "127.0.0.1",
                port = port,
                path = "/",
            ),
        )

        try {
            val result = client.connect()

            assertTrue("The unavailable server should fail the connection test", result.isFailure)
            assertFalse(
                "SMB server root must not fail local path validation: ${result.exceptionOrNull()}",
                result.exceptionOrNull() is IllegalArgumentException,
            )
            assertFalse(client.isConnected())
        } finally {
            client.disconnect()
        }
    }
}
