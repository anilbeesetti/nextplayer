package dev.anilbeesetti.nextplayer.core.media.network.clients

import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.media.network.sftp.HostKeyConfirmationRequired
import dev.anilbeesetti.nextplayer.core.media.network.sftp.HostKeyMismatch
import dev.anilbeesetti.nextplayer.core.media.network.sftp.SftpHostKeyVerifier
import dev.anilbeesetti.nextplayer.core.media.network.sftp.SftpOwnedInputStream
import dev.anilbeesetti.nextplayer.core.media.network.sftp.SftpSecurityProvider
import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import java.io.InputStream
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient

class SftpClient(
    private val connection: NetworkConnection,
    private val sshKeyStore: SshKeyStore,
) : NetworkClient {

    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    override val rootPath: String = connection.path
        .trim()
        .trim('/')
        .let { path -> if (path.isEmpty()) "/" else "/$path" }

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            closeBrowsingClients()
            val ssh = newClient()
            try {
                ssh.connectAndAuthenticate()
                val sftp = ssh.newSFTPClient()
                try {
                    sftp.ls(rootPath)
                    sshClient = ssh
                    sftpClient = sftp
                } catch (error: Throwable) {
                    runCatching { sftp.close() }
                    throw error
                }
            } catch (error: Throwable) {
                runCatching { ssh.close() }
                rethrowStrictHostException(error)
            }
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        closeBrowsingClients()
    }

    override fun isConnected(): Boolean =
        sftpClient != null && sshClient?.isConnected == true && sshClient?.isAuthenticated == true

    override suspend fun listFiles(path: String): Result<List<NetworkFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = sftpClient ?: error("Not connected")
            val directory = path.ifBlank { rootPath }
            client.ls(directory).mapNotNull { entry ->
                if (entry.name == "." || entry.name == "..") return@mapNotNull null
                NetworkFile(
                    name = entry.name,
                    path = "${directory.trimEnd('/')}/${entry.name}",
                    isDirectory = entry.isDirectory,
                    size = entry.attributes.size,
                    modified = entry.attributes.mtime.takeIf { it > 0 }?.times(MILLIS_PER_SECOND),
                )
            }
        }
    }

    override suspend fun fileSize(path: String): Long = withContext(Dispatchers.IO) {
        runCatching {
            val client = sftpClient ?: error("Not connected")
            client.stat(path).size
        }.getOrDefault(-1L)
    }

    override suspend fun openStream(path: String, offset: Long): InputStream = withContext(Dispatchers.IO) {
        val ssh = newClient()
        try {
            ssh.connectAndAuthenticate()
            val sftp = ssh.newSFTPClient()
            try {
                val remoteFile = sftp.open(path, EnumSet.of(OpenMode.READ))
                SftpOwnedInputStream(
                    offset = offset,
                    reader = remoteFile::read,
                    remoteFile = remoteFile,
                    sftpClient = sftp,
                    sshClient = ssh,
                )
            } catch (error: Throwable) {
                runCatching { sftp.close() }
                throw error
            }
        } catch (error: Throwable) {
            runCatching { ssh.close() }
            rethrowStrictHostException(error)
        }
    }

    private fun newClient(): SSHClient {
        SftpSecurityProvider.install()
        return SSHClient().apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            timeout = SOCKET_TIMEOUT_MILLIS
            addHostKeyVerifier(SftpHostKeyVerifier(this@SftpClient.connection.hostKeyFingerprint))
        }
    }

    private fun SSHClient.connectAndAuthenticate() {
        connect(this@SftpClient.connection.host, this@SftpClient.connection.effectivePort)
        when (this@SftpClient.connection.authentication) {
            NetworkAuthentication.PASSWORD -> authPassword(
                this@SftpClient.connection.username,
                this@SftpClient.connection.password,
            )
            NetworkAuthentication.SSH_KEY -> {
                val keyFile = sshKeyStore.resolve(this@SftpClient.connection.privateKeyFileName)
                val keyProvider = loadKeys(
                    keyFile.absolutePath,
                    this@SftpClient.connection.privateKeyPassphrase.toCharArray(),
                )
                authPublickey(this@SftpClient.connection.username, keyProvider)
            }
        }
    }

    private fun closeBrowsingClients() {
        runCatching { sftpClient?.close() }
        runCatching { sshClient?.close() }
        sftpClient = null
        sshClient = null
    }

    private fun rethrowStrictHostException(error: Throwable): Nothing {
        val strictHostException = generateSequence(error) { it.cause }
            .firstOrNull { it is HostKeyConfirmationRequired || it is HostKeyMismatch }
        throw strictHostException ?: error
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val SOCKET_TIMEOUT_MILLIS = 120_000
        const val MILLIS_PER_SECOND = 1_000L
    }
}
