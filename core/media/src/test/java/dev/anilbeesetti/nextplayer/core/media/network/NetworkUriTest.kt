package dev.anilbeesetti.nextplayer.core.media.network

import androidx.core.net.toUri
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The playback uri is the media id the player stores playback position and history against, so a
 * client path must survive the round trip byte for byte.
 */
@RunWith(RobolectricTestRunner::class)
class NetworkUriTest {

    @Test
    fun `smb path stays relative to the share`() {
        assertRoundTrip(NetworkProtocol.SMB, "Movies/Season 1/ep.mkv")
    }

    @Test
    fun `smb file at the share root stays relative`() {
        assertRoundTrip(NetworkProtocol.SMB, "ep.mkv")
    }

    @Test
    fun `ftp path keeps its leading slash`() {
        assertRoundTrip(NetworkProtocol.FTP, "/media/Movies/ep.mkv")
    }

    @Test
    fun `sftp path keeps its leading slash`() {
        assertRoundTrip(NetworkProtocol.SFTP, "/home/media/ep.mkv")
    }

    @Test
    fun `webdav path stays relative to the base`() {
        assertRoundTrip(NetworkProtocol.WEBDAV, "Shared/ep.mkv")
    }

    @Test
    fun `paths needing encoding survive the round trip`() {
        assertRoundTrip(NetworkProtocol.SMB, "TV Shows/S01 - pilot #1 & more/ep [1080p].mkv")
    }

    @Test
    fun `uri is stable across calls`() {
        val connection = connection(NetworkProtocol.SMB)
        assertEquals(
            NetworkUri.build(connection, "Movies/ep.mkv"),
            NetworkUri.build(connection, "Movies/ep.mkv"),
        )
    }

    @Test
    fun `connections to the same host stay distinct`() {
        val first = NetworkUri.build(connection(NetworkProtocol.SMB).copy(id = 1), "Movies/ep.mkv")
        val second = NetworkUri.build(connection(NetworkProtocol.SMB).copy(id = 2), "Movies/ep.mkv")
        assertEquals(1L, NetworkUri.connectionIdOf(first))
        assertEquals(2L, NetworkUri.connectionIdOf(second))
    }

    @Test
    fun `port is included only when the connection sets one`() {
        val default = NetworkUri.build(connection(NetworkProtocol.SMB), "ep.mkv")
        val custom = NetworkUri.build(connection(NetworkProtocol.SMB).copy(port = 4455), "ep.mkv")
        assertEquals("192.168.1.10", default.authority)
        assertEquals("192.168.1.10:4455", custom.authority)
    }

    @Test
    fun `last path segment is the file name so the player can title the item`() {
        val uri = NetworkUri.build(connection(NetworkProtocol.SMB), "Movies/Season 1/ep.mkv")
        assertEquals("ep.mkv", uri.lastPathSegment)
    }

    @Test
    fun `network uris are recognised and local ones are not`() {
        assertTrue(NetworkUri.isNetworkUri(NetworkUri.build(connection(NetworkProtocol.SMB), "ep.mkv")))
        assertFalse(NetworkUri.isNetworkUri("content://media/external/video/media/1".toUri()))
        assertFalse(NetworkUri.isNetworkUri("http://example.com/ep.mkv".toUri()))
    }

    @Test
    fun `a uri without a connection id is rejected`() {
        assertNull(NetworkUri.connectionIdOf("smb://192.168.1.10/Movies/ep.mkv".toUri()))
    }

    private fun assertRoundTrip(protocol: NetworkProtocol, filePath: String) {
        val connection = connection(protocol)
        val uri = NetworkUri.build(connection, filePath)

        assertEquals(connection.id, NetworkUri.connectionIdOf(uri))
        assertEquals(filePath, NetworkUri.filePathOf(uri, protocol))
    }

    private fun connection(protocol: NetworkProtocol) = NetworkConnection(
        id = 3,
        name = "NAS",
        protocol = protocol,
        host = "192.168.1.10",
    )
}
