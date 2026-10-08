package dev.anilbeesetti.nextplayer.core.media.network.clients

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import java.io.IOException
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

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
            assertTrue("The failure must come from the network: ${result.exceptionOrNull()}", result.exceptionOrNull() is IOException)
            assertFalse(client.isConnected())
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `server root connects and lists shares as folders`() = runBlocking {
        val server = TestServer()
        val shares = listOf(
            NetworkFile(name = "Media", path = "Media", isDirectory = true),
            NetworkFile(name = "Other", path = "Other", isDirectory = true),
        )
        val client = server.client("/") { shares }

        assertTrue(client.connect().isSuccess)
        assertTrue(client.isConnected())
        assertEquals(shares, client.listFiles(client.rootPath).getOrThrow())
        verify(server.session, never()).connectShare("")
        client.disconnect()
        verify(server.session).close()
        verify(server.connection).close()
        verify(server.smbClient).close()
        assertFalse(client.isConnected())
    }

    @Test
    fun `failed share enumeration fails connection test and releases resources`() = runBlocking {
        val server = TestServer()
        val error = IOException("Share enumeration denied")
        val client = server.client("/") { throw error }

        assertEquals(error, client.connect().exceptionOrNull())
        assertFalse(client.isConnected())
        verify(server.session).close()
        verify(server.connection).close()
        verify(server.smbClient).close()
    }

    @Test
    fun `server root browses nested folders with share-qualified file paths`() = runBlocking {
        val server = TestServer()
        val file = mock(FileIdBothDirectoryInformation::class.java)
        `when`(file.fileName).thenReturn("clip.mp4")
        `when`(file.endOfFile).thenReturn(123L)
        `when`(server.share.list("folder\\nested")).thenReturn(listOf(file))
        val client = server.client("/")
        assertTrue(client.connect().isSuccess)

        val files = client.listFiles("Media/folder/nested").getOrThrow()

        assertEquals(
            listOf(NetworkFile(name = "clip.mp4", path = "Media/folder/nested/clip.mp4", isDirectory = false, size = 123L)),
            files,
        )
        verify(server.session).connectShare("Media")
        verify(server.share).list("folder\\nested")
        client.disconnect()
    }

    @Test
    fun `server root reads size and stream from the selected share at the requested offset`() = runBlocking {
        val server = TestServer()
        val file = mock(com.hierynomus.smbj.share.File::class.java, RETURNS_DEEP_STUBS)
        `when`(file.fileInformation.standardInformation.endOfFile).thenReturn(123L)
        `when`(server.share.openFile(anyString(), any(), any(), any(), any(), any())).thenReturn(file)
        `when`(file.read(any(ByteArray::class.java), eq(4L), anyInt(), anyInt())).thenAnswer { call ->
            val buffer = call.getArgument<ByteArray>(0)
            buffer[0] = 4
            buffer[1] = 5
            2
        }
        val client = server.client("/")
        assertTrue(client.connect().isSuccess)

        assertEquals(123L, client.fileSize("Media/folder/clip.mp4"))
        client.openStream("Media/folder/clip.mp4", offset = 4L).use { stream ->
            assertEquals(4, stream.read())
            assertEquals(5, stream.read())
        }

        verify(server.session, times(2)).connectShare("Media")
        verify(server.share, times(2)).openFile(eq("folder\\clip.mp4"), any(), any(), any(), any(), any())
        verify(file, times(2)).close()
        client.disconnect()
    }

    @Test
    fun `named share retains share-relative browsing`() = runBlocking {
        val server = TestServer()
        val client = server.client("/Media/") { error("Named shares must not enumerate the server") }

        assertTrue(client.connect().isSuccess)
        assertEquals(emptyList<NetworkFile>(), client.listFiles("folder").getOrThrow())

        verify(server.session, times(2)).connectShare("Media")
        verify(server.share).list("")
        verify(server.share).list("folder")
        client.disconnect()
    }

    @Test
    fun `blank and nested share names still fail local validation`() = runBlocking {
        for (path in listOf("", "Media/folder")) {
            val server = TestServer()
            val client = server.client(path)

            assertTrue(client.connect().exceptionOrNull() is IllegalArgumentException)
            verify(server.smbClient, never()).connect(anyString(), anyInt())
        }
    }

    private class TestServer {
        val smbClient: SMBClient = mock(SMBClient::class.java)
        val connection: Connection = mock(Connection::class.java)
        val session: Session = mock(Session::class.java)
        val share: DiskShare = mock(DiskShare::class.java)

        init {
            `when`(smbClient.connect("server", 445)).thenReturn(connection)
            `when`(connection.authenticate(any())).thenReturn(session)
            `when`(connection.isConnected).thenReturn(true)
            `when`(session.connectShare("Media")).thenReturn(share)
        }

        fun client(path: String, listShares: (Session) -> List<NetworkFile> = { emptyList() }) = SmbClient(
            connection = NetworkConnection(name = "Test", protocol = NetworkProtocol.SMB, host = "server", path = path),
            createClient = { smbClient },
            listShares = listShares,
        )
    }
}
