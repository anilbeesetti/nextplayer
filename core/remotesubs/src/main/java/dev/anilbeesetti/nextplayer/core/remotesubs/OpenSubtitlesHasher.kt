package dev.anilbeesetti.nextplayer.core.remotesubs

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class OpenSubtitlesHasher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val HASH_CHUNK_SIZE = 64 * 1024
    }

    @Throws(IOException::class)
    fun computeHash(file: File): String {
        val size = file.length()
        val chunkSizeForFile = min(HASH_CHUNK_SIZE.toLong(), size)
        FileInputStream(file).channel.use { fileChannel ->
            val head = computeChunkHash(
                fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    0,
                    chunkSizeForFile,
                ),
            )
            val tail = computeChunkHash(
                fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    max(size - HASH_CHUNK_SIZE, 0),
                    chunkSizeForFile,
                ),
            )
            return String.format("%016x", size + head + tail)
        }
    }

    fun computeHash(uri: Uri, length: Long): String {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            inputStream ?: throw IllegalStateException("Unable to open input stream")

            val chunkSize = min(HASH_CHUNK_SIZE.toLong(), length).toInt()
            val chunkBytes = ByteArray(min(2 * HASH_CHUNK_SIZE.toLong(), length).toInt())

            // Read first chunk
            inputStream.readExactly(chunkBytes, 0, chunkSize)

            // Skip to tail chunk if necessary
            val tailChunkPosition = length - chunkSize
            if (tailChunkPosition > chunkSize) {
                inputStream.skip(tailChunkPosition - chunkSize)
            }

            // Read second chunk or remaining data
            inputStream.readExactly(chunkBytes, chunkSize, chunkBytes.size - chunkSize)

            val head = computeChunkHash(ByteBuffer.wrap(chunkBytes, 0, chunkSize))
            val tail = computeChunkHash(ByteBuffer.wrap(chunkBytes, chunkBytes.size - chunkSize, chunkSize))

            return "%016x".format(length + head + tail)
        }
    }

    private fun InputStream.readExactly(buffer: ByteArray, offset: Int, length: Int) {
        var bytesRead = 0
        while (bytesRead < length) {
            val bytesReadThisIteration = read(buffer, offset + bytesRead, length - bytesRead)
            if (bytesReadThisIteration < 0) break // End of stream reached
            bytesRead += bytesReadThisIteration
        }
        if (bytesRead < length) {
            throw IllegalStateException("Unexpected end of stream: Read $bytesRead bytes, expected $length")
        }
    }

    private fun computeChunkHash(buffer: ByteBuffer): Long {
        val longBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
        var hash: Long = 0
        while (longBuffer.hasRemaining()) {
            hash += longBuffer.get()
        }
        return hash
    }
}
