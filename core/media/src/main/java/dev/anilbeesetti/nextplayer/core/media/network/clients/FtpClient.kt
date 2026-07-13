package dev.anilbeesetti.nextplayer.core.media.network.clients

import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * FTP client backed by Apache commons-net. Browse paths are absolute paths on the server.
 * Streaming uses a dedicated control connection with `REST` (restart offset) for seeking.
 */
class FtpClient(private val connection: NetworkConnection) : NetworkClient {

    private var ftpClient: FTPClient? = null

    override val rootPath: String = connection.path
        .trim()
        .trim('/')
        .let { path -> if (path.isEmpty()) "/" else "/$path" }

    private fun newClient(): FTPClient = FTPClient().apply {
        controlEncoding = "UTF-8"
        connectTimeout = 15_000
        dataTimeout = 120.seconds.toJavaDuration()
        defaultTimeout = 120_000
    }

    private fun FTPClient.loginAndPrepare(): Boolean {
        connect(connection.host, connection.effectivePort)
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            disconnect()
            return false
        }
        val ok = if (connection.isAnonymous) login("anonymous", "") else login(connection.username, connection.password)
        if (!ok) {
            disconnect()
            return false
        }
        setFileType(FTP.BINARY_FILE_TYPE)
        enterLocalPassiveMode()
        runCatching { sendCommand("OPTS UTF8 ON") }
        bufferSize = 64 * 1024
        return true
    }

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = newClient()
            try {
                check(client.loginAndPrepare()) { "FTP login failed" }
                ftpClient = client
            } catch (error: Throwable) {
                runCatching { if (client.isConnected) client.disconnect() }
                throw error
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        ftpClient?.let { client ->
            runCatching {
                if (client.isConnected) {
                    client.logout()
                    client.disconnect()
                }
            }
        }
        ftpClient = null
    }

    override fun isConnected(): Boolean = ftpClient?.isConnected == true

    override suspend fun listFiles(path: String): Result<List<NetworkFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = ftpClient ?: error("Not connected")
            val dir = path.ifBlank { rootPath }
            val files = client.listFiles(dir)
            check(FTPReply.isPositiveCompletion(client.replyCode)) {
                client.replyString.trim().ifEmpty { "Failed to list FTP directory: $dir" }
            }
            files.mapNotNull { file ->
                if (file.name == "." || file.name == "..") return@mapNotNull null
                NetworkFile(
                    name = file.name,
                    path = "${dir.trimEnd('/')}/${file.name}",
                    isDirectory = file.isDirectory,
                    size = file.size,
                    modified = file.timestamp?.timeInMillis,
                )
            }
        }
    }

    override suspend fun fileSize(path: String): Long = withContext(Dispatchers.IO) {
        runCatching {
            val client = ftpClient ?: error("Not connected")
            // `SIZE` (RFC 3659) is a single round-trip and works in binary mode. Listing the file
            // path directly is unreliable — many servers only LIST directories, returning nothing.
            if (client.sendCommand("SIZE", path) == FTPReply.FILE_STATUS) {
                client.replyString.trim().substringAfterLast(' ').toLongOrNull() ?: -1L
            } else {
                // Fallback: find the entry in its parent directory listing.
                val parent = path.substringBeforeLast('/', "").ifEmpty { "/" }
                val name = path.substringAfterLast('/')
                client.listFiles(parent).firstOrNull { it.name == name && !it.isDirectory }?.size ?: -1L
            }
        }.getOrDefault(-1L)
    }

    override suspend fun openStream(path: String, offset: Long): InputStream = withContext(Dispatchers.IO) {
        // A fresh control connection per stream avoids conflicting with browsing / other streams.
        val streamClient = newClient()
        check(streamClient.loginAndPrepare()) { "FTP login failed" }
        if (offset > 0) streamClient.restartOffset = offset
        val raw = streamClient.retrieveFileStream(path)
            ?: run {
                runCatching { streamClient.disconnect() }
                error("Failed to open FTP stream for $path")
            }
        object : InputStream() {
            override fun read(): Int = raw.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = raw.read(b, off, len)
            override fun available(): Int = raw.available()
            override fun close() {
                runCatching { raw.close() }
                runCatching {
                    if (streamClient.isConnected) {
                        streamClient.completePendingCommand()
                        streamClient.logout()
                        streamClient.disconnect()
                    }
                }
            }
        }
    }
}
