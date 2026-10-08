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
import com.hierynomus.smbj.share.PipeShare
import com.rapid7.client.dcerpc.Interface
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransport
import com.rapid7.helper.smbj.share.NamedPipe
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SMB2/3 client backed by smbj. [NetworkConnection.path] holds a share name or `/` to browse
 * the server's disk shares. Server-root browse paths include the share as their first segment;
 * paths for a named-share connection remain relative to that share.
 */
class SmbClient internal constructor(
    private val connection: NetworkConnection,
    private val createClient: (SmbConfig) -> SMBClient,
    private val listShares: (Session) -> List<NetworkFile>,
) : NetworkClient {

    constructor(connection: NetworkConnection) : this(connection, ::SMBClient, ::listSmbShares)

    private var client: SMBClient? = null
    private var smbConnection: Connection? = null
    private var session: Session? = null

    private val shareName: String get() = connection.path.trim('/')
    private val isServerRoot: Boolean get() = connection.path == "/"

    override val rootPath: String = ""

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(isServerRoot || (shareName.isNotEmpty() && !shareName.contains('/'))) {
                "Path must be / or just the share name (e.g. Media), without any folders."
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
                // Add large buffer sizes (e.g., 8MB)
                .withReadBufferSize(8 * 1024 * 1024)
                .withWriteBufferSize(8 * 1024 * 1024)
                .build()

            val smbClient = createClient(config)
            client = smbClient
            try {
                val conn = smbClient.connect(connection.host, connection.effectivePort)
                smbConnection = conn
                val authContext = if (connection.isAnonymous) {
                    AuthenticationContext.anonymous()
                } else {
                    AuthenticationContext(connection.username, connection.password.toCharArray(), null)
                }
                val sess = conn.authenticate(authContext)
                session = sess
                if (isServerRoot) {
                    // Test enumeration as well as authentication before saving a server root.
                    listShares(sess)
                } else {
                    (sess.connectShare(shareName) as? DiskShare)?.use { it.list("") }
                        ?: error("Share '$shareName' is not a disk share")
                }
            } catch (error: Throwable) {
                closeResources()
                throw error
            }
            Unit
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        closeResources()
    }

    private fun closeResources() {
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
            if (isServerRoot && relative.isEmpty()) return@runCatching listShares(sess)
            val (selectedShare, sharePath) = shareAndPath(path)
            (sess.connectShare(selectedShare) as DiskShare).use { share ->
                share.list(smbPath(sharePath)).mapNotNull { info ->
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
            val (selectedShare, sharePath) = shareAndPath(path)
            (sess.connectShare(selectedShare) as DiskShare).use { share ->
                openReadFile(share, sharePath).use { it.fileInformation.standardInformation.endOfFile }
            }
        }.getOrDefault(-1L)
    }

    override suspend fun openStream(path: String, offset: Long): InputStream = withContext(Dispatchers.IO) {
        if (!isConnected()) connect().getOrThrow()
        val sess = session ?: error("Not connected")
        val (selectedShare, sharePath) = shareAndPath(path)
        val share = sess.connectShare(selectedShare) as DiskShare
        val file = try {
            openReadFile(share, sharePath)
        } catch (error: Throwable) {
            runCatching { share.close() }
            throw error
        }

        val rawStream = object : InputStream() {
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

        // Wrap the stream in a 2MB buffer to drastically reduce SMB network requests
        return@withContext rawStream.buffered(2 * 1024 * 1024)
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

    private fun shareAndPath(path: String): Pair<String, String> {
        val relative = path.trim('/')
        if (!isServerRoot) return shareName to relative
        require(relative.isNotEmpty()) { "Select a share before opening a file." }
        return relative.substringBefore('/') to relative.substringAfter('/', "")
    }
}

private fun listSmbShares(session: Session): List<NetworkFile> =
    (session.connectShare("IPC$") as PipeShare).use { share ->
        NamedPipe(session, share, "srvsvc").use { pipe ->
            val transport = SMBTransport(pipe)
            transport.bind(Interface.SRVSVC_V3_0, Interface.NDR_32BIT_V2)
            ServerService(transport).shares1
                .filter { it.type and 0xFFFF == 0 && !it.netName.endsWith("$") }
                .map { NetworkFile(name = it.netName, path = it.netName, isDirectory = true) }
        }
    }
