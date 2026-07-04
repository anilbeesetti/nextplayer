package dev.anilbeesetti.nextplayer.core.media.network.clients

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SMB2/3 client backed by smbj. [NetworkConnection.path] holds only the share name; browse paths
 * are relative to the share root.
 */
class SmbClient(private val connection: NetworkConnection) : NetworkClient {

    private var client: SMBClient? = null
    private var smbConnection: Connection? = null
    private var session: Session? = null

    private val shareName: String get() = connection.path.trim('/')

    override val rootPath: String = ""

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(shareName.isNotEmpty() && !shareName.contains('/')) {
                "Path must be just the share name (e.g. Media), without any folders."
            }
            // Disable signing/encryption: avoids Key.getEncoded() crashes on Android.
            val config = SmbConfig.builder()
                .withTimeout(30, TimeUnit.SECONDS)
                .withSoTimeout(35, TimeUnit.SECONDS)
                .withDialects(
                    SMB2Dialect.SMB_3_1_1,
                    SMB2Dialect.SMB_3_0_2,
                    SMB2Dialect.SMB_3_0,
                    SMB2Dialect.SMB_2_1,
                    SMB2Dialect.SMB_2_0_2,
                )
                .withDfsEnabled(false)
                .withMultiProtocolNegotiate(true)
                .withSigningRequired(false)
                .withEncryptData(false)
                .build()

            val smbClient = SMBClient(config)
            val conn = smbClient.connect(connection.host, connection.effectivePort)
            val authContext = if (connection.isAnonymous) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(connection.username, connection.password.toCharArray(), null)
            }
            val sess = conn.authenticate(authContext)
            // Verify the share is reachable and a disk share.
            (sess.connectShare(shareName) as? DiskShare)?.use { it.list("") }
                ?: error("Share '$shareName' is not a disk share")

            client = smbClient
            smbConnection = conn
            session = sess
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { session?.close() }
        runCatching { smbConnection?.close() }
        runCatching { client?.close() }
        session = null
        smbConnection = null
        client = null
    }

    override fun isConnected(): Boolean = session != null && smbConnection?.isConnected == true

    override suspend fun listFiles(path: String): Result<List<NetworkFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val sess = session ?: error("Not connected")
            val relative = path.trim('/')
            (sess.connectShare(shareName) as DiskShare).use { share ->
                share.list(smbPath(relative)).mapNotNull { info ->
                    val name = info.fileName
                    if (name == "." || name == ".." || name.endsWith("$")) return@mapNotNull null
                    val isDirectory = info.fileAttributes and 0x10L != 0L // FILE_ATTRIBUTE_DIRECTORY
                    NetworkFile(
                        name = name,
                        path = if (relative.isEmpty()) name else "$relative/$name",
                        isDirectory = isDirectory,
                        size = if (isDirectory) 0 else info.endOfFile,
                        modified = info.lastWriteTime?.toEpochMillis(),
                    )
                }
            }
        }
    }

    override suspend fun fileSize(path: String): Long = withContext(Dispatchers.IO) {
        runCatching {
            val sess = session ?: error("Not connected")
            (sess.connectShare(shareName) as DiskShare).use { share ->
                openReadFile(share, path).use { it.fileInformation.standardInformation.endOfFile }
            }
        }.getOrDefault(-1L)
    }

    override suspend fun openStream(path: String, offset: Long): InputStream = withContext(Dispatchers.IO) {
        if (!isConnected()) connect().getOrThrow()
        val sess = session ?: error("Not connected")
        val share = sess.connectShare(shareName) as DiskShare
        val file = openReadFile(share, path)
        object : InputStream() {
            private var position = offset

            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val read = file.read(b, position, off, len)
                if (read > 0) position += read
                return read
            }

            override fun close() {
                runCatching { file.close() }
                runCatching { share.close() }
            }
        }
    }

    private fun openReadFile(share: DiskShare, path: String) = share.openFile(
        smbPath(path.trim('/')),
        EnumSet.of(AccessMask.GENERIC_READ),
        null,
        EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
        SMB2CreateDisposition.FILE_OPEN,
        null,
    )

    private fun smbPath(relative: String): String = relative.replace('/', '\\')
}
