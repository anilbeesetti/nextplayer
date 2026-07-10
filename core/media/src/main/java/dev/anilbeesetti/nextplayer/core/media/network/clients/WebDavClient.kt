package dev.anilbeesetti.nextplayer.core.media.network.clients

import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * WebDAV client backed by sardine-android for listing and OkHttp for range-based streaming.
 * Browse paths are relative to [NetworkConnection.path] (the base path on the server).
 */
class WebDavClient(private val connection: NetworkConnection) : NetworkClient {

    private var sardine: Sardine? = null
    private val httpClient = OkHttpClient()

    override val rootPath: String = ""

    private val scheme get() = if (connection.useHttps) "https" else "http"

    private fun buildUrl(path: String): String {
        val base = connection.path.trim('/')
        val clean = path.trim('/')
        val authority = "$scheme://${connection.host}:${connection.effectivePort}"
        return when {
            clean.isEmpty() -> if (base.isEmpty()) "$authority/" else "$authority/$base"
            base.isEmpty() -> "$authority/$clean"
            else -> "$authority/$base/$clean"
        }
    }

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpSardine()
            if (!connection.isAnonymous) client.setCredentials(connection.username, connection.password)
            client.exists(buildUrl(""))
            sardine = client
        }
    }

    override suspend fun disconnect() {
        sardine = null
    }

    override fun isConnected(): Boolean = sardine != null

    override suspend fun listFiles(path: String): Result<List<NetworkFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = sardine ?: error("Not connected")
            client.list(buildUrl(path)).drop(1).map { resource ->
                val name = resource.name ?: ""
                NetworkFile(
                    name = name,
                    path = if (path.isBlank()) name else "${path.trimEnd('/')}/$name",
                    isDirectory = resource.isDirectory,
                    size = resource.contentLength ?: 0,
                    modified = resource.modified?.time,
                )
            }
        }
    }

    override suspend fun fileSize(path: String): Long = withContext(Dispatchers.IO) {
        runCatching {
            val client = sardine ?: error("Not connected")
            client.list(buildUrl(path), 0).firstOrNull { !it.isDirectory }?.contentLength ?: -1L
        }.getOrDefault(-1L)
    }

    override suspend fun openStream(path: String, offset: Long): InputStream = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(buildUrl(path))
        if (!connection.isAnonymous) {
            builder.header("Authorization", Credentials.basic(connection.username, connection.password))
        }
        if (offset > 0) builder.header("Range", "bytes=$offset-")
        val response = httpClient.newCall(builder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            error("WebDAV request failed: ${response.code}")
        }
        response.body?.byteStream() ?: run {
            response.close()
            error("Empty WebDAV response body")
        }
    }
}
