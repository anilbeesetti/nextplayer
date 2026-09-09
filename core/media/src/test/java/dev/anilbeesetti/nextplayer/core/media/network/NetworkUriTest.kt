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

    // ----- ad-hoc uris: no saved connection, the uri names its own server -----

    @Test
    fun `ad-hoc ftp uri carries an absolute path and no credentials`() {
        val target = NetworkUri.adHocTargetOf("ftp://192.168.1.10/media/clip.mp4".toUri())!!

        assertEquals(NetworkProtocol.FTP, target.connection.protocol)
        assertEquals("192.168.1.10", target.connection.host)
        assertNull(target.connection.port)
        assertTrue(target.connection.isAnonymous)
        assertEquals("/media/clip.mp4", target.filePath)
    }

    @Test
    fun `ad-hoc uri takes credentials and port from the uri`() {
        val target = NetworkUri.adHocTargetOf("ftp://bob:s3cret@host:2121/clip.mp4".toUri())!!

        assertEquals("bob", target.connection.username)
        assertEquals("s3cret", target.connection.password)
        assertEquals(2121, target.connection.port)
    }

    @Test
    fun `ad-hoc uri with a username but no password stays usable`() {
        val target = NetworkUri.adHocTargetOf("sftp://bob@host/home/bob/clip.mp4".toUri())!!

        assertEquals("bob", target.connection.username)
        assertEquals("", target.connection.password)
        assertEquals("/home/bob/clip.mp4", target.filePath)
    }

    @Test
    fun `ad-hoc smb uri puts the first segment in the share and the rest in the path`() {
        val target = NetworkUri.adHocTargetOf("smb://host/Media/Shows/ep.mkv".toUri())!!

        assertEquals("Media", target.connection.path)
        assertEquals("Shows/ep.mkv", target.filePath)
    }

    @Test
    fun `ad-hoc smb uri naming only a share has no file to play`() {
        assertNull(NetworkUri.adHocTargetOf("smb://host/Media".toUri()))
    }

    @Test
    fun `ad-hoc webdav uri keeps a base-relative path`() {
        val target = NetworkUri.adHocTargetOf("webdav://host/Docs/ep.mp4".toUri())!!

        assertEquals("", target.connection.path)
        assertEquals("Docs/ep.mp4", target.filePath)
        assertFalse(target.connection.useHttps)
    }

    @Test
    fun `webdavs selects https`() {
        val target = NetworkUri.adHocTargetOf("webdavs://host/Docs/ep.mp4".toUri())!!
        assertTrue(target.connection.useHttps)
    }

    @Test
    fun `a saved https webdav connection builds a webdavs uri`() {
        val saved = connection(NetworkProtocol.WEBDAV).copy(useHttps = true)
        assertEquals("webdavs", NetworkUri.build(saved, "Docs/ep.mp4").scheme)
    }

    @Test
    fun `percent-encoded ad-hoc paths decode back to the client path`() {
        val target = NetworkUri.adHocTargetOf("smb://host/Media/TV%20Shows/ep%20one.mkv".toUri())!!

        assertEquals("Media", target.connection.path)
        assertEquals("TV Shows/ep one.mkv", target.filePath)
    }

    @Test
    fun `ad-hoc connections are not mistaken for saved ones`() {
        val target = NetworkUri.adHocTargetOf("ftp://host/clip.mp4".toUri())!!
        assertEquals(0L, target.connection.id)
        assertNull(NetworkUri.connectionIdOf("ftp://host/clip.mp4".toUri()))
    }

    @Test
    fun `uris that name no file or no host are rejected`() {
        assertNull(NetworkUri.adHocTargetOf("ftp://host".toUri()))
        assertNull(NetworkUri.adHocTargetOf("ftp:///clip.mp4".toUri()))
        assertNull(NetworkUri.adHocTargetOf("http://host/clip.mp4".toUri()))
        assertNull(NetworkUri.adHocTargetOf("content://media/external/video/media/1".toUri()))
    }

    @Test
    fun `a saved uri still resolves through the connection id, not its own parts`() {
        // The saved form has no share segment, so ad-hoc parsing of it would be wrong.
        val uri = NetworkUri.build(connection(NetworkProtocol.SMB), "Shows/ep.mkv")
        assertEquals(3L, NetworkUri.connectionIdOf(uri))
    }

    @Test
    fun `ad-hoc sftp uri can pin the host key so an unknown server is not trusted blindly`() {
        val plain = NetworkUri.adHocTargetOf("sftp://host/clip.mp4".toUri())!!
        assertEquals("", plain.connection.hostKeyFingerprint)

        val pinned = NetworkUri.adHocTargetOf(
            "sftp://host/clip.mp4?fp=SHA256:k6fzWcosN/NvDzUBUEolQT0E%2BjjnJAEcpVgrwJ9B%2BMc".toUri(),
        )!!
        assertEquals(
            "SHA256:k6fzWcosN/NvDzUBUEolQT0E+jjnJAEcpVgrwJ9B+Mc",
            pinned.connection.hostKeyFingerprint,
        )
    }

    private fun connection(protocol: NetworkProtocol) = NetworkConnection(
        id = 3,
        name = "NAS",
        protocol = protocol,
        host = "192.168.1.10",
    )
}