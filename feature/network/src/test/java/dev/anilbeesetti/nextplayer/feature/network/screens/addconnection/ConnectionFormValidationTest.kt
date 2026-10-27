package dev.anilbeesetti.nextplayer.feature.network.screens.addconnection

import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionFormValidationTest {
    @Test
    fun `legacy protocols require only name and host`() {
        listOf(
            NetworkProtocol.SMB,
            NetworkProtocol.FTP,
            NetworkProtocol.WEBDAV,
        ).forEach { protocol ->
            assertFalse(
                canSaveConnection(
                    name = "",
                    host = "host",
                    protocol = protocol,
                    username = "",
                    NetworkAuthentication.PASSWORD,
                    hasPrivateKey = false,
                    isTesting = false,
                ),
            )
            assertFalse(
                canSaveConnection(
                    name = "Name",
                    host = "",
                    protocol = protocol,
                    username = "",
                    NetworkAuthentication.PASSWORD,
                    hasPrivateKey = false,
                    isTesting = false,
                ),
            )
            assertTrue(
                canSaveConnection(
                    name = "Name",
                    host = "host",
                    protocol = protocol,
                    username = "",
                    NetworkAuthentication.PASSWORD,
                    hasPrivateKey = false,
                    isTesting = false,
                ),
            )
        }
    }

    @Test
    fun `SFTP password authentication requires username`() {
        assertFalse(
            canSaveConnection(
                name = "SFTP",
                host = "host",
                NetworkProtocol.SFTP,
                username = "",
                NetworkAuthentication.PASSWORD,
                hasPrivateKey = false,
                isTesting = false,
            ),
        )
        assertTrue(
            canSaveConnection(
                name = "SFTP",
                host = "host",
                NetworkProtocol.SFTP,
                username = "alice",
                NetworkAuthentication.PASSWORD,
                hasPrivateKey = false,
                isTesting = false,
            ),
        )
    }

    @Test
    fun `SFTP key authentication requires username and private key`() {
        assertFalse(
            canSaveConnection(
                name = "SFTP",
                host = "host",
                NetworkProtocol.SFTP,
                username = "alice",
                NetworkAuthentication.SSH_KEY,
                hasPrivateKey = false,
                isTesting = false,
            ),
        )
        assertTrue(
            canSaveConnection(
                name = "SFTP",
                host = "host",
                NetworkProtocol.SFTP,
                username = "alice",
                NetworkAuthentication.SSH_KEY,
                hasPrivateKey = true,
                isTesting = false,
            ),
        )
    }

    @Test
    fun `testing always disables save`() {
        assertFalse(
            canSaveConnection(
                name = "Name",
                host = "host",
                NetworkProtocol.FTP,
                username = "",
                NetworkAuthentication.PASSWORD,
                hasPrivateKey = false,
                isTesting = true,
            ),
        )
        assertFalse(
            canSaveConnection(
                name = "SFTP",
                host = "host",
                NetworkProtocol.SFTP,
                username = "alice",
                NetworkAuthentication.SSH_KEY,
                hasPrivateKey = true,
                isTesting = true,
            ),
        )
    }
}
