package dev.anilbeesetti.nextplayer.core.media.network.sftp

import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

internal typealias OffsetReader = (Long, ByteArray, Int, Int) -> Int

internal class SftpOwnedInputStream(
    offset: Long,
    private val reader: OffsetReader,
    private val remoteFile: Closeable,
    private val sftpClient: Closeable,
    private val sshClient: Closeable,
) : InputStream() {

    private var position = offset
    private val closed = AtomicBoolean(false)

    override fun read(): Int {
        val byte = ByteArray(1)
        return if (read(byte, 0, 1) == -1) -1 else byte[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > buffer.size || length > buffer.size - offset) {
            throw IndexOutOfBoundsException(
                "offset=$offset, length=$length, bufferSize=${buffer.size}",
            )
        }
        if (length == 0) return 0
        val bytesRead = reader(position, buffer, offset, length)
        if (bytesRead > 0) position += bytesRead
        return bytesRead
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { remoteFile.close() }
        runCatching { sftpClient.close() }
        runCatching { sshClient.close() }
    }
}
