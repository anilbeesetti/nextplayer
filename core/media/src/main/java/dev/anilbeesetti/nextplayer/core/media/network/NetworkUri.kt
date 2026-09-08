package dev.anilbeesetti.nextplayer.core.media.network

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol

/**
 * The stable playback URI for a file on a saved [NetworkConnection], e.g.
 * `smb://192.168.1.10/Movies/foo.mkv?cid=3`.
 *
 * The same file always produces the same URI, which matters because the player stores playback
 * position and history keyed by the media id. The connection id is carried in the query rather
 * than inferred from the host, so two connections to the same server stay distinct.
 *
 * The path segments hold [dev.anilbeesetti.nextplayer.core.model.NetworkFile.path] verbatim. That
 * value is opaque to everyone but the client that produced it — SMB and WebDAV paths are relative
 * to the share/base, FTP and SFTP paths are absolute — so [filePathOf] restores the leading slash
 * only for the protocols that use one.
 */
object NetworkUri {

    private const val CONNECTION_ID_PARAM = "cid"

    fun schemeOf(protocol: NetworkProtocol): String = when (protocol) {
        NetworkProtocol.SMB -> "smb"
        NetworkProtocol.FTP -> "ftp"
        NetworkProtocol.SFTP -> "sftp"
        NetworkProtocol.WEBDAV -> "webdav"
    }

    /** Every scheme [build] can produce, for telling network media items from local ones. */
    val schemes: Set<String> = NetworkProtocol.entries.map(::schemeOf).toSet()

    fun isNetworkUri(uri: Uri): Boolean = uri.scheme?.lowercase() in schemes

    fun build(connection: NetworkConnection, filePath: String): Uri = Uri.Builder()
        .scheme(schemeOf(connection.protocol))
        .encodedAuthority(
            if (connection.port != null) "${connection.host}:${connection.port}" else connection.host,
        )
        .apply {
            filePath.split('/').filter(String::isNotEmpty).forEach(::appendPath)
        }
        .appendQueryParameter(CONNECTION_ID_PARAM, connection.id.toString())
        .build()

    fun connectionIdOf(uri: Uri): Long? =
        uri.getQueryParameter(CONNECTION_ID_PARAM)?.toLongOrNull()

    /** Restores the path in the form the [NetworkClient] for [protocol] expects. */
    fun filePathOf(uri: Uri, protocol: NetworkProtocol): String {
        val path = uri.pathSegments.joinToString("/")
        return when (protocol) {
            NetworkProtocol.FTP, NetworkProtocol.SFTP -> "/$path"
            NetworkProtocol.SMB, NetworkProtocol.WEBDAV -> path
        }
    }
}
