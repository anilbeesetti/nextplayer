package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Only explicitly registered media can be fetched; URLs expire when casting stops. */
@UnstableApi
internal class LocalCastServer(
    context: Context,
    private val dataSourceFactory: DataSource.Factory,
) : NanoHTTPD(0) {
    private val wakeLock = context.getSystemService<PowerManager>()
        ?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NextPlayer:LocalCast",
        )
        ?.apply { setReferenceCounted(false) }

    @Suppress("DEPRECATION")
    private val wifiLock = context.applicationContext.getSystemService<WifiManager>()
        ?.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "NextPlayer:LocalCast",
        )
        ?.apply { setReferenceCounted(false) }
    private data class Resource(val uri: Uri, val mimeType: String)
    private val resources = ConcurrentHashMap<String, Resource>()

    init {
        setAsyncRunner(object : DefaultAsyncRunner() {
            override fun exec(code: ClientHandler) {
                if (running.size >= 8) code.close() else super.exec(code)
            }
        })
    }

    @Synchronized
    fun register(uri: Uri, mimeType: String, host: String): String {
        if (!isAlive) {
            try {
                start(
                    SOCKET_READ_TIMEOUT,
                    true,
                )
                wakeLock?.acquire()
                wifiLock?.acquire()
            } catch (error: Exception) {
                stop()
                throw error
            }
        }
        val resource = Resource(
            uri,
            mimeType,
        )
        val path = resources.entries.firstOrNull { it.value == resource }?.key
            ?: "/${UUID.randomUUID()}".also { resources[it] = resource }
        return "http://$host:$listeningPort$path"
    }

    @Synchronized
    override fun stop() {
        resources.clear()
        try {
            super.stop()
        } finally {
            if (wifiLock?.isHeld == true) wifiLock.release()
            if (wakeLock?.isHeld == true) wakeLock.release()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val resource = resources[session.uri]
            ?: return error(Response.Status.NOT_FOUND)
        if (session.method != Method.GET && session.method != Method.HEAD) {
            return error(Response.Status.METHOD_NOT_ALLOWED).apply {
                addHeader(
                    "Allow",
                    "GET, HEAD",
                )
            }
        }
        var input: DataSourceInputStream? = null
        return try {
            val source = dataSourceFactory.createDataSource()
            val total = try {
                source.open(DataSpec(resource.uri))
            } finally {
                source.close()
            }
            val rangeHeader = session.headers["range"]
            val range = if (rangeHeader == null) {
                null
            } else {
                byteRange(
                    rangeHeader,
                    total,
                )
                    ?: return error(Response.Status.RANGE_NOT_SATISFIABLE).apply {
                        addHeader(
                            "Content-Range",
                            "bytes */${if (total >= 0) total else "*"}",
                        )
                    }
            }
            val start = range?.first ?: 0
            val length = range?.let { it.last - it.first + 1 } ?: total
            val status = if (range != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK
            val stream = if (session.method == Method.HEAD) {
                java.io.ByteArrayInputStream(byteArrayOf())
            } else {
                DataSourceInputStream(
                    dataSourceFactory.createDataSource(),
                    DataSpec.Builder().setUri(resource.uri).setPosition(start).build(),
                ).also {
                    input = it
                    it.open()
                }
            }
            val response = if (length == C.LENGTH_UNSET.toLong()) {
                newChunkedResponse(
                    status,
                    resource.mimeType,
                    stream,
                )
            } else {
                newFixedLengthResponse(
                    status,
                    resource.mimeType,
                    stream,
                    length,
                )
            }
            response.apply {
                addHeader(
                    "Accept-Ranges",
                    if (total >= 0) "bytes" else "none",
                )
                addHeader(
                    "Access-Control-Allow-Origin",
                    "*",
                )
                addHeader(
                    "Cache-Control",
                    "no-store",
                )
                if (range !=
                    null
                ) {
                    addHeader(
                        "Content-Range",
                        "bytes ${range.first}-${range.last}/$total",
                    )
                }
            }
        } catch (_: IOException) {
            input?.close()
            error(Response.Status.NOT_FOUND)
        } catch (_: SecurityException) {
            input?.close()
            error(Response.Status.FORBIDDEN)
        }
    }

    private fun error(status: Response.Status): Response = newFixedLengthResponse(
        status,
        MIME_PLAINTEXT,
        "",
    )
}

/** RFC 9110 single byte ranges, including open-ended and suffix ranges. */
internal fun byteRange(header: String, size: Long): LongRange? {
    if (size <= 0 || !header.startsWith("bytes=")) return null
    val parts = header.removePrefix("bytes=").split('-')
    if (parts.size != 2 || parts.any { part -> part.any { it !in '0'..'9' } }) return null
    if (parts[0].isEmpty()) {
        val suffix = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return (size - suffix.coerceAtMost(size))..(size - 1)
    }
    val start = parts[0].toLongOrNull()?.takeIf { it in 0 until size } ?: return null
    val end = if (parts[1].isEmpty()) size - 1 else parts[1].toLongOrNull() ?: return null
    if (end < start) return null
    return start..end.coerceAtMost(size - 1)
}
