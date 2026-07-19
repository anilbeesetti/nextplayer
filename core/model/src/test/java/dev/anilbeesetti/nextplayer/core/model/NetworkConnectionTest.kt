package dev.anilbeesetti.nextplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkConnectionTest {
    @Test
    fun `SFTP defaults to port 22`() {
        assertEquals(22, NetworkProtocol.SFTP.defaultPort)
    }

    @Test
    fun `network connection defaults to password authentication`() {
        assertEquals(NetworkAuthentication.PASSWORD, NetworkConnection.sample.authentication)
        assertEquals("", NetworkConnection.sample.privateKeyFileName)
        assertEquals("", NetworkConnection.sample.privateKeyPassphrase)
        assertEquals("", NetworkConnection.sample.hostKeyFingerprint)
    }
}
