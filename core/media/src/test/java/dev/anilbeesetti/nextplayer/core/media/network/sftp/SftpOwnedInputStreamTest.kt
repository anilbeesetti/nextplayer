package dev.anilbeesetti.nextplayer.core.media.network.sftp

import java.io.Closeable
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SftpOwnedInputStreamTest {

    @Test
    fun `reads from requested offset and advances by bytes read`() {
        val positions = mutableListOf<Long>()
        val results = ArrayDeque(listOf(3, 2))
        val stream = SftpOwnedInputStream(
            offset = 41L,
            reader = { position, _, _, _ ->
                positions += position
                results.removeFirst()
            },
            remoteFile = Closeable {},
            sftpClient = Closeable {},
            sshClient = Closeable {},
        )

        assertEquals(3, stream.read(ByteArray(8), 1, 4))
        assertEquals(2, stream.read(ByteArray(8), 0, 2))
        assertEquals(listOf(41L, 44L), positions)
    }

    @Test
    fun `end of file is preserved without advancing position`() {
        val positions = mutableListOf<Long>()
        val stream = SftpOwnedInputStream(
            offset = 9L,
            reader = { position, _, _, _ ->
                positions += position
                -1
            },
            remoteFile = Closeable {},
            sftpClient = Closeable {},
            sshClient = Closeable {},
        )

        assertEquals(-1, stream.read(ByteArray(4)))
        assertEquals(-1, stream.read(ByteArray(4)))
        assertEquals(listOf(9L, 9L), positions)
    }

    @Test
    fun `invalid bulk read ranges fail locally without invoking reader`() {
        var readerCalls = 0
        val stream = SftpOwnedInputStream(
            offset = 0L,
            reader = { _, _, _, _ ->
                readerCalls++
                -1
            },
            remoteFile = Closeable {},
            sftpClient = Closeable {},
            sshClient = Closeable {},
        )
        val buffer = ByteArray(4)

        assertThrows(IndexOutOfBoundsException::class.java) { stream.read(buffer, -1, 1) }
        assertThrows(IndexOutOfBoundsException::class.java) { stream.read(buffer, 0, -1) }
        assertThrows(IndexOutOfBoundsException::class.java) { stream.read(buffer, 3, 2) }

        assertEquals(0, readerCalls)
    }

    @Test
    fun `zero length bulk read returns zero without invoking reader at EOF`() {
        var readerCalls = 0
        val stream = SftpOwnedInputStream(
            offset = 0L,
            reader = { _, _, _, _ ->
                readerCalls++
                -1
            },
            remoteFile = Closeable {},
            sftpClient = Closeable {},
            sshClient = Closeable {},
        )

        assertEquals(0, stream.read(ByteArray(4), 4, 0))
        assertEquals(0, readerCalls)
    }

    @Test
    fun `close releases every owner once when an earlier close throws`() {
        var remoteFileCloses = 0
        var sftpClientCloses = 0
        var sshClientCloses = 0
        val stream = SftpOwnedInputStream(
            offset = 0L,
            reader = { _, _, _, _ -> -1 },
            remoteFile = Closeable {
                remoteFileCloses++
                throw IOException("Remote file close failed")
            },
            sftpClient = Closeable { sftpClientCloses++ },
            sshClient = Closeable { sshClientCloses++ },
        )

        stream.close()
        stream.close()

        assertEquals(1, remoteFileCloses)
        assertEquals(1, sftpClientCloses)
        assertEquals(1, sshClientCloses)
    }
}
