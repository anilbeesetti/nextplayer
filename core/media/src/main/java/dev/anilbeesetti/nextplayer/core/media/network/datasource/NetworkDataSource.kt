package dev.anilbeesetti.nextplayer.core.media.network.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking

/**
 * Reads a file on a saved network connection (SMB/FTP/SFTP/WebDAV) for the Media3 player.
 *
 * Media3 ships no data source for these protocols, so this adapts [NetworkClient.openStream],
 * which can start at an arbitrary byte offset and therefore supports seeking directly. The player
 * closes and reopens the source at a new offset for each seek.
 */
@UnstableApi
class NetworkDataSource(
    private val sessions: NetworkSessions,
) : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var stream: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        try {
            runBlocking {
                val target = sessions.target(dataSpec.uri)
                val fileSize = target.client.fileSize(target.filePath)

                bytesRemaining = when {
                    dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                    fileSize >= 0 -> (fileSize - dataSpec.position).coerceAtLeast(0)
                    else -> C.LENGTH_UNSET.toLong()
                }
                stream = target.client.openStream(target.filePath, dataSpec.position)
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Failed to open ${dataSpec.uri}", e)
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val readLength = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        val bytesRead = stream?.read(buffer, offset, readLength) ?: return C.RESULT_END_OF_INPUT
        if (bytesRead == -1) return C.RESULT_END_OF_INPUT

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            stream?.close()
        } finally {
            stream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
