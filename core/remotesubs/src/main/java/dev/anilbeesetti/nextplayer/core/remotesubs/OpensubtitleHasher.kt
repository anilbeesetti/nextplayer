package dev.anilbeesetti.nextplayer.core.remotesubs

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
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

    @Throws(IOException::class)
    fun computeHash(uri: Uri, length: Long): String {
        val stream = context.contentResolver.openInputStream(uri)
        val chunkSizeForFile = min(HASH_CHUNK_SIZE.toLong(), length)
            .toInt()

        // buffer that will contain the head and the tail chunk, chunks will overlap if length is smaller than two chunks
        val chunkBytes = ByteArray(min(2 * HASH_CHUNK_SIZE.toLong(), length).toInt())
        val inputStream = DataInputStream(stream)

        // first chunk
        inputStream.readFully(chunkBytes, 0, chunkSizeForFile)
        var position = chunkSizeForFile.toLong()
        val tailChunkPosition = length - chunkSizeForFile

        // seek to position of the tail chunk, or not at all if length is smaller than two chunks
        while (position < tailChunkPosition && inputStream.skip(tailChunkPosition - position)
                .let { position += it; position } >= 0
        );

        // second chunk, or the rest of the data if length is smaller than two chunks
        inputStream.readFully(chunkBytes, chunkSizeForFile, chunkBytes.size - chunkSizeForFile)
        val head = computeChunkHash(ByteBuffer.wrap(chunkBytes, 0, chunkSizeForFile))
        val tail = computeChunkHash(
            ByteBuffer.wrap(
                chunkBytes,
                chunkBytes.size - chunkSizeForFile,
                chunkSizeForFile,
            ),
        )
        inputStream.close()
        stream?.close()
        return String.format("%016x", length + head + tail)
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