package dev.anilbeesetti.nextplayer.core.data.webdav

import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import dev.anilbeesetti.nextplayer.core.model.WebDavFile
import dev.anilbeesetti.nextplayer.core.model.WebDavServer
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber

@Singleton
class SardineWebDavClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true) // Allow following redirects
        .followSslRedirects(true) // Allow SSL redirects
        .retryOnConnectionFailure(true)
        .build()

    private fun createSardine(server: WebDavServer): Sardine {
        return OkHttpSardine(client).apply {
            if (server.username.isNotEmpty()) {
                setCredentials(server.username, server.password)
            }
        }
    }

    suspend fun listFiles(server: WebDavServer, path: String): Result<List<WebDavFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val sardine = createSardine(server)
                val url = buildUrl(server.url, path)
                Timber.d("WebDAV Sardine PROPFIND request URL: $url")
                val resources = sardine.list(url)
                val files = mutableListOf<WebDavFile>()
                for (resource in resources) {
                    // Skip current directory entry - more strict filtering
                    val resourceHref = resource.href.toString()
                    val normalizedResourceHref = resourceHref.trimEnd('/')
                    val normalizedUrl = url.trimEnd('/')
                    // Skip current directory (exact match or just different path ending)
                    if (normalizedResourceHref == normalizedUrl ||
                        normalizedResourceHref == normalizedUrl + "/" ||
                        resourceHref == url
                    ) {
                        Timber.d("Skipping current directory: $resourceHref (matches $url)")
                        continue
                    }
                    // Additional check: if it's a parent directory reference of current path, skip it too
                    val resourcePath = try {
                        java.net.URI(resourceHref).path.trimEnd('/')
                    } catch (e: Exception) {
                        resourceHref.trimEnd('/')
                    }
                    val currentPath = try {
                        java.net.URI(url).path.trimEnd('/')
                    } catch (e: Exception) {
                        path.trimEnd('/')
                    }
                    if (resourcePath == currentPath) {
                        Timber.d("Skipping current directory by path: $resourcePath == $currentPath")
                        continue
                    }
                    val fileName = resource.displayName ?: resource.name ?: resourceHref.substringAfterLast("/").removeSuffix("/")
                    val isDirectory = resource.isDirectory
                    val size = if (isDirectory) 0L else (resource.contentLength ?: 0L)
                    val lastModified = resource.modified?.time ?: 0L
                    val mimeType = if (isDirectory) null else resource.contentType
                    // Use path relative to server root directory, handle base path for servers like AList
                    val filePath = try {
                        val uri = java.net.URI(resourceHref)
                        val path = uri.path

                        // If server URL contains base path (like /dav/), ensure file path is complete
                        val baseUrlPath = try {
                            java.net.URL(server.url).path.trimEnd('/')
                        } catch (e: Exception) {
                            ""
                        }
                        // If base path exists and file path doesn't contain it, add base path
                        if (baseUrlPath.isNotEmpty() && !path.startsWith(baseUrlPath)) {
                            if (path.startsWith("/")) {
                                baseUrlPath + path
                            } else {
                                "$baseUrlPath/$path"
                            }
                        } else {
                            path
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to parse resource href: $resourceHref")
                        resourceHref
                    }
                    files.add(
                        WebDavFile(
                            name = fileName,
                            path = filePath,
                            isDirectory = isDirectory,
                            size = size,
                            lastModified = lastModified,
                            mimeType = mimeType ?: if (!isDirectory) guessMimeType(fileName) else null,
                        ),
                    )
                    Timber.d("Added file: $fileName (isDirectory: $isDirectory, path: $filePath)")
                }
                Timber.d("Parsed ${files.size} files/directories using Sardine")
                Result.success(files)
            } catch (e: Exception) {
                Timber.e(e, "Failed to list WebDAV files using Sardine")
                Result.failure(e)
            }
        }
    }

    suspend fun testConnection(server: WebDavServer): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val sardine = createSardine(server)
                val url = normalizeUrl(server.url)
                // Simple test: try to list root directory
                sardine.list(url, 0) // depth = 0 means only check the directory itself
                Result.success(true)
            } catch (e: Exception) {
                Timber.e(e, "WebDAV Sardine connection test failed")
                Result.failure(e)
            }
        }
    }

    suspend fun getFileStream(server: WebDavServer, filePath: String): Result<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val sardine = createSardine(server)
                val url = buildUrl(server.url, filePath)
                Timber.d("Getting file stream for: $url")
                val inputStream = sardine.get(url)
                Result.success(inputStream)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get file stream for: $filePath")
                Result.failure(e)
            }
        }
    }

    private fun buildUrl(baseUrl: String, path: String): String {
        val normalizedUrl = normalizeUrl(baseUrl)
        val normalizedPath = normalizePath(path)
        val result = if (normalizedPath == "/") {
            normalizedUrl
        } else {
            // Check if the path already contains the base URL's path to avoid duplication
            val baseUrlPath = try {
                java.net.URL(normalizedUrl).path.trimEnd('/')
            } catch (e: Exception) {
                ""
            }

            if (baseUrlPath.isNotEmpty() && normalizedPath.startsWith(baseUrlPath)) {
                // Path already includes the base path, so we need to construct the full URL differently
                // Extract the scheme, host, and port from the base URL
                val baseUrlObj = java.net.URL(normalizedUrl)
                val baseUrlWithoutPath = "${baseUrlObj.protocol}://${baseUrlObj.authority}"
                baseUrlWithoutPath + normalizedPath
            } else {
                normalizedUrl.trimEnd('/') + normalizedPath
            }
        }

        Timber.d("buildUrl: baseUrl=$baseUrl, path=$path -> result=$result")
        return result
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        if (!normalized.endsWith("/")) {
            normalized += "/"
        }
        return normalized
    }

    private fun normalizePath(path: String): String {
        var normalized = path.trim()
        if (!normalized.startsWith("/")) {
            normalized = "/$normalized"
        }
        if (normalized != "/" && normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }
        return normalized
    }

    private fun guessMimeType(fileName: String): String? {
        // Only return MIME type if the file has a valid extension
        if (!fileName.contains(".")) return null
        val extension = fileName.substringAfterLast(".", "").lowercase()
        if (extension.isEmpty()) return null
        return when (extension) {
            // Video formats
            "mp4", "m4v" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "ts" -> "video/mp2t"
            "m2ts" -> "video/mp2t"
            "mts" -> "video/mp2t"
            "vob" -> "video/dvd"
            "ogv" -> "video/ogg"
            // Audio formats
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "wma" -> "audio/x-ms-wma"
            "m4a" -> "audio/mp4"
            // Playlist formats
            "m3u8" -> "application/vnd.apple.mpegurl"
            "m3u" -> "audio/x-mpegurl"
            "pls" -> "audio/x-scpls"
            else -> null
        }
    }
}
