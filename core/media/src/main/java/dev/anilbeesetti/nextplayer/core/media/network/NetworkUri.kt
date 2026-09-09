package dev.anilbeesetti.nextplayer.core.media.network

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol

/**
 * Playback URIs for files reached over SMB/FTP/SFTP/WebDAV.
 *
 * A file on a *saved* connection gets `smb://192.168.1.10/Movies/foo.mkv?cid=3`. The same file
 * always produces the same URI, which matters because the player stores playback position and
 * history keyed by the media id. The connection id is carried in the query rather than inferred
 * from the host, so two connections to the same server stay distinct.
 *
 * A URI *without* `cid` — one the user typed or another app handed over — is resolved by
 * [adHocTargetOf] straight from its own parts, so `ftp://user:pass@host/dir/clip.mp4` plays
 * without saving a connection first. SFTP additionally needs the server's host key pinned as
 * `?fp=SHA256:…`; without it the connection is refused rather than trusted blindly, because
 * nothing along the playback path can stop and ask the user to vouch for an unknown server.
 *
 * Path handling differs per protocol because
 * [dev.anilbeesetti.nextplayer.core.model.NetworkFile.path] is opaque to everyone but the client
 * that produced it: SMB and WebDAV paths are relative to the share/base, FTP and SFTP paths are
 * absolute. Saved URIs hold that value verbatim; ad-hoc URIs additionally carry the SMB share as
 * their first path segment, since there is no saved connection to hold it.
 */
object NetworkUri {

    private const val CONNECTION_ID_PARAM = "cid"
    private const val HOST_KEY_PARAM = "fp"

    private const val SCHEME_SMB = "smb"
    private const val SCHEME_FTP = "ftp"
    private const val SCHEME_SFTP = "sftp"
    private const val SCHEME_WEBDAV = "webdav"
    private const val SCHEME_WEBDAVS = "webdavs"

    /** The connection to play through and the path to open on it. */
    data class Target(val connection: NetworkConnection, val filePath: String)

    /** Every scheme these URIs use, for telling network media items from local ones. */
    val schemes: Set<String> = setOf(
        SCHEME_SMB, SCHEME_FTP, SCHEME_SFTP, SCHEME_WEBDAV, SCHEME_WEBDAVS,
    )

    fun isNetworkUri(uri: Uri): Boolean = uri.scheme?.lowercase() in schemes

    fun schemeOf(connection: NetworkConnection): String = when (connection.protocol) {
        NetworkProtocol.SMB -> SCHEME_SMB
        NetworkProtocol.FTP -> SCHEME_FTP
        NetworkProtocol.SFTP -> SCHEME_SFTP
        NetworkProtocol.WEBDAV -> if (connection.useHttps) SCHEME_WEBDAVS else SCHEME_WEBDAV
    }

    fun build(connection: NetworkConnection, filePath: String): Uri = Uri.Builder()
        .scheme(schemeOf(connection))
        .encodedAuthority(
            if (connection.port != null) "${connection.host}:${connection.port}" else connection.host,
        )
        .apply { filePath.split('/').filter(String::isNotEmpty).forEach(::appendPath) }
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

    /**
     * Builds a throwaway connection for a URI that names its own server, e.g.
     * `smb://user:pass@host/share/dir/clip.mkv`. Credentials come from the user info; leaving it
     * out connects anonymously. Returns null if the URI is not one this can serve.
     */
    fun adHocTargetOf(uri: Uri): Target? {
        val scheme = uri.scheme?.lowercase() ?: return null
        val protocol = when (scheme) {
            SCHEME_SMB -> NetworkProtocol.SMB
            SCHEME_FTP -> NetworkProtocol.FTP
            SCHEME_SFTP -> NetworkProtocol.SFTP
            SCHEME_WEBDAV, SCHEME_WEBDAVS -> NetworkProtocol.WEBDAV
            else -> return null
        }
        val host = uri.host?.takeIf(String::isNotBlank) ?: return null
        val segments = uri.pathSegments.filter(String::isNotEmpty)
        if (segments.isEmpty()) return null

        // SMB addresses a file as <share>/<path>, and the share belongs to the connection. Every
        // other protocol treats the whole path as the file, relative to a root the server picks.
        val root = if (protocol == NetworkProtocol.SMB) segments.first() else ""
        val fileSegments = if (protocol == NetworkProtocol.SMB) segments.drop(1) else segments
        if (fileSegments.isEmpty()) return null

        val userInfo = uri.userInfo?.let(Uri::decode).orEmpty()
        val connection = NetworkConnection(
            id = AD_HOC_ID,
            name = host,
            protocol = protocol,
            host = host,
            port = uri.port.takeIf { it > 0 },
            path = root,
            username = userInfo.substringBefore(':'),
            password = userInfo.substringAfter(':', missingDelimiterValue = ""),
            useHttps = scheme == SCHEME_WEBDAVS,
            hostKeyFingerprint = uri.getQueryParameter(HOST_KEY_PARAM).orEmpty(),
        )
        val filePath = fileSegments.joinToString("/")
        return Target(
            connection = connection,
            filePath = when (protocol) {
                NetworkProtocol.FTP, NetworkProtocol.SFTP -> "/$filePath"
                NetworkProtocol.SMB, NetworkProtocol.WEBDAV -> filePath
            },
        )
    }

    /** Saved connections are row ids, which Room starts at 1, so 0 can only be an ad-hoc one. */
    private const val AD_HOC_ID = 0L
}
