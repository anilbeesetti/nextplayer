package dev.anilbeesetti.nextplayer.core.media.network

import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import java.io.InputStream

/**
 * Client for browsing and reading files over a network protocol (SMB, FTP, WebDAV).
 *
 * Implementations must be able to [openStream] at an arbitrary byte [offset] so the local
 * streaming proxy can satisfy HTTP range requests (seeking) from the media player.
 */
interface NetworkClient {

    /** The path at which browsing should start for this connection. */
    val rootPath: String

    suspend fun connect(): Result<Unit>

    suspend fun disconnect()

    fun isConnected(): Boolean

    suspend fun listFiles(path: String): Result<List<NetworkFile>>

    /** Size in bytes of the file at [path], or `-1` if it can't be determined. */
    suspend fun fileSize(path: String): Long

    /** Opens a read stream starting at [offset] bytes. The caller is responsible for closing it. */
    suspend fun openStream(path: String, offset: Long = 0L): InputStream
}
