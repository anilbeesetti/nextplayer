package dev.anilbeesetti.nextplayer.feature.network.screens.addconnection

import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionFormValidationTest {
    @Test
    fun `legacy protocols require only name and host`() {
        listOf(NetworkProtocol.SMB, NetworkProtocol.FTP, NetworkProtocol.WEBDAV).forEach { protocol ->
            assertFalse(canSaveConnection("", "host", protocol, "", NetworkAuthentication.PASSWORD, false, false))
            assertFalse(canSaveConnection("Name", "", protocol, "", NetworkAuthentication.PASSWORD, false, false))
            assertTrue(canSaveConnection("Name", "host", protocol, "", NetworkAuthentication.PASSWORD, false, false))
        }
    }

    @Test
    fun `SFTP password authentication requires username`() {
        assertFalse(
            canSaveConnection(
                "SFTP",
                "host",
                NetworkProtocol.SFTP,
                "",
                NetworkAuthentication.PASSWORD,
                false,
                false,
            ),
        )
        assertTrue(
            canSaveConnection(
                "SFTP",
                "host",
                NetworkProtocol.SFTP,
                "alice",
                NetworkAuthentication.PASSWORD,
                false,
                false,
            ),
        )
    }

    @Test
    fun `SFTP key authentication requires username and private key`() {
        assertFalse(
            canSaveConnection(
                "SFTP",
                "host",
                NetworkProtocol.SFTP,
                "alice",
                NetworkAuthentication.SSH_KEY,
                false,
                false,
            ),
        )
        assertTrue(
            canSaveConnection(
                "SFTP",
                "host",
                NetworkProtocol.SFTP,
                "alice",
                NetworkAuthentication.SSH_KEY,
                true,
                false,
            ),
        )
    }

    @Test
    fun `testing always disables save`() {
        assertFalse(
            canSaveConnection(
                "Name",
                "host",
                NetworkProtocol.FTP,
                "",
                NetworkAuthentication.PASSWORD,
                false,
                true,
            ),
        )
        assertFalse(
            canSaveConnection(
                "SFTP",
                "host",
                NetworkProtocol.SFTP,
                "alice",
                NetworkAuthentication.SSH_KEY,
                true,
                true,
            ),
        )
    }
}
