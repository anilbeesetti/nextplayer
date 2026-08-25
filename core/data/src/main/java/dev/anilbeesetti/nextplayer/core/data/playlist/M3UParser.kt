package dev.anilbeesetti.nextplayer.core.data.playlist

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.model.M3UPlaylist
import dev.anilbeesetti.nextplayer.core.model.M3UPlaylistItem
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal sealed interface M3USource {
    val fallbackName: String

    data class Remote(
        val uri: URI,
        override val fallbackName: String,
    ) : M3USource

    data class Document(
        val uri: Uri,
        override val fallbackName: String,
    ) : M3USource
}

private data class PendingEntryMetadata(
    val title: String? = null,
    val duration: Int = M3UPlaylistItem.UNKNOWN_DURATION,
    val tvgLogo: String? = null,
    val groupTitle: String? = null,
)

class M3UParser @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.IO)
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun parseUrl(url: String): Result<M3UPlaylist> = withContext(ioDispatcher) {
        resultCatching {
            val sourceUri = URI(url).takeIf { it.isValidNetworkUri(HTTP_SCHEMES) }
                ?: throw IllegalArgumentException("M3U URL must use HTTP or HTTPS")
            val connection = URL(sourceUri.toString()).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
                connection.readTimeout = NETWORK_TIMEOUT_MILLIS
                connection.instanceFollowRedirects = true
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("HTTP $status while reading M3U playlist")
                val finalUri = connection.url.toURI()
                val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseContent(
                    content = content,
                    source = M3USource.Remote(
                        uri = finalUri,
                        fallbackName = finalUri.sourceName(),
                    ),
                ).getOrThrow()
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun parseUri(uri: Uri): Result<M3UPlaylist> = withContext(ioDispatcher) {
        resultCatching {
            require(uri.isReadableDocumentUri()) {
                "M3U file must use a content URI or an absolute file URI"
            }
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: throw IOException("Unable to open M3U document")
            parseContent(
                content = content,
                source = M3USource.Document(
                    uri = uri,
                    fallbackName = context.queryDisplayName(uri)
                        ?: uri.lastPathSegment
                        ?: DEFAULT_PLAYLIST_NAME,
                ),
            ).getOrThrow()
        }
    }

    internal fun parseContent(
        content: String,
        source: M3USource,
    ): Result<M3UPlaylist> = runCatching {
        val normalizedContent = content.removePrefix("\uFEFF")
        require(
            normalizedContent.lineSequence().none {
                it.trim().startsWith("#EXT-X-", ignoreCase = true)
            },
        ) {
            "HLS manifests cannot be imported as M3U playlists"
        }

        val entries = mutableListOf<M3UPlaylistItem>()
        val seenUris = mutableSetOf<String>()
        var playlistName: String? = null
        var pending = PendingEntryMetadata()

        normalizedContent.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#PLAYLIST:", ignoreCase = true) -> {
                    if (playlistName == null) {
                        playlistName = line.substringAfter(':').trim().ifBlank { null }
                    }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> pending = line.parseExtInf()
                line.startsWith("#EXTIMG:", ignoreCase = true) -> {
                    if (pending.tvgLogo == null) {
                        pending = pending.copy(
                            tvgLogo = line.substringAfter(':').trim().ifBlank { null },
                        )
                    }
                }
                line.startsWith('#') -> Unit
                else -> {
                    val resolvedUri = resolveMediaUri(line, source)
                    if (resolvedUri != null && seenUris.add(resolvedUri)) {
                        entries += M3UPlaylistItem(
                            uri = resolvedUri,
                            title = pending.title ?: deriveItemTitle(resolvedUri),
                            tvgLogo = pending.tvgLogo?.let {
                                resolveArtworkUri(it, source)
                            },
                            duration = pending.duration,
                            groupTitle = pending.groupTitle,
                        )
                    }
                    pending = PendingEntryMetadata()
                }
            }
        }

        require(entries.isNotEmpty()) { "M3U playlist contains no playable entries" }
        M3UPlaylist(
            playlistName = playlistName ?: derivePlaylistName(source),
            items = entries,
        )
    }

    private companion object {
        const val NETWORK_TIMEOUT_MILLIS = 15_000
        const val DEFAULT_PLAYLIST_NAME = "M3U Playlist"
        val HTTP_SCHEMES = setOf("http", "https")
    }
}

private val extInfAttribute = Regex(
    """([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""",
)

private fun String.parseExtInf(): PendingEntryMetadata {
    val metadata = substringAfter(':')
    val commaIndex = metadata.firstUnquotedComma()
    val attributeSection = if (commaIndex >= 0) metadata.substring(0, commaIndex) else metadata
    val attributes = attributeSection.parseAttributes()
    val commaTitle = if (commaIndex >= 0) metadata.substring(commaIndex + 1).trim() else ""
    val duration = signedIntegerAtStart
        .find(attributeSection.trim())
        ?.value
        ?.toIntOrNull()
        ?: M3UPlaylistItem.UNKNOWN_DURATION
    return PendingEntryMetadata(
        title = commaTitle.ifBlank { attributes["tvg-name"].orEmpty() }.ifBlank { null },
        duration = duration,
        tvgLogo = attributes["tvg-logo"]?.takeIf(String::isNotBlank)
            ?: attributes["logo"]?.takeIf(String::isNotBlank),
        groupTitle = attributes["group-title"]?.takeIf(String::isNotBlank),
    )
}

private fun String.parseAttributes(): Map<String, String> =
    extInfAttribute.findAll(this).associate { match ->
        val value = match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
        match.groupValues[1].lowercase(Locale.ROOT) to value
    }

private fun String.firstUnquotedComma(): Int {
    var quote: Char? = null
    forEachIndexed { index, char ->
        when {
            quote == null && (char == '\'' || char == '"') -> quote = char
            quote == char -> quote = null
            quote == null && char == ',' -> return index
        }
    }
    return -1
}

private fun resolveMediaUri(rawValue: String, source: M3USource): String? =
    resolveUri(rawValue, source, MEDIA_SCHEMES)

private fun resolveArtworkUri(rawValue: String, source: M3USource): String? =
    resolveUri(rawValue, source, ARTWORK_SCHEMES)

private fun resolveUri(
    rawValue: String,
    source: M3USource,
    acceptedSchemes: Set<String>,
): String? = runCatching {
    val entry = rawValue.toUriOrNull() ?: return null
    if (!entry.isAbsolute && entry.path.orEmpty().split('/').any { it == "." || it == ".." || '\\' in it }) {
        return null
    }
    val resolved = when {
        entry.isAbsolute -> entry
        source is M3USource.Remote -> source.uri.resolve(entry)
        source is M3USource.Document &&
            source.uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) -> {
            val segments = entry.path?.split('/').orEmpty()
            if (segments.any { it.isEmpty() || it == "." || it == ".." || '\\' in it }) return null
            URI(source.uri.toString()).resolve(entry)
        }
        else -> return null
    }
    resolved.validated(acceptedSchemes)
}.getOrNull()

private fun URI.validated(acceptedSchemes: Set<String>): String? {
    val normalizedScheme = scheme?.lowercase(Locale.ROOT) ?: return null
    if (normalizedScheme !in acceptedSchemes) return null
    if (path.orEmpty().split('/').any { it == "." || it == ".." || '\\' in it }) return null
    return when (normalizedScheme) {
        "http", "https", "rtsp" -> toString().takeIf {
            !isOpaque && !host.isNullOrBlank()
        }
        "content" -> toString().takeIf {
            !isOpaque && !rawAuthority.isNullOrBlank() && path?.trim('/')?.isNotBlank() == true
        }
        "file" -> toString().takeIf {
            !isOpaque &&
                rawAuthority.isNullOrBlank() &&
                !path.isNullOrBlank() &&
                path.startsWith('/')
        }
        else -> null
    }
}

private fun String.toUriOrNull(): URI? = runCatching { URI(this) }
    .recoverCatching {
        require(':' !in this && '\\' !in this)
        URI(null, null, this, null)
    }
    .getOrNull()

private fun URI.isValidNetworkUri(schemes: Set<String>): Boolean =
    scheme?.lowercase(Locale.ROOT) in schemes && !isOpaque && !host.isNullOrBlank()

private fun Uri.isReadableDocumentUri(): Boolean = when {
    scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) ->
        !authority.isNullOrBlank()
    scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) ->
        !path.isNullOrBlank() && path.orEmpty().startsWith('/')
    else -> false
}

private fun Context.queryDisplayName(uri: Uri): String? = runCatching {
    contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
    }
}.getOrNull()?.takeIf(String::isNotBlank)

private fun derivePlaylistName(source: M3USource): String =
    source.fallbackName.cleanPlaylistName().ifBlank { "M3U Playlist" }

private fun URI.sourceName(): String =
    rawPath?.substringAfterLast('/')?.decodePathSegment().orEmpty()
        .ifBlank { host.orEmpty() }
        .ifBlank { "M3U Playlist" }

private fun String.cleanPlaylistName(): String =
    decodePathSegment()
        .replace(Regex("(?i)\\.m3u8?$"), "")
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()

private fun deriveItemTitle(uri: String): String = runCatching {
    val parsed = URI(uri)
    parsed.rawPath
        ?.substringAfterLast('/')
        ?.decodePathSegment()
        ?.substringBeforeLast('.', missingDelimiterValue = parsed.rawPath.substringAfterLast('/').decodePathSegment())
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.trim()
        .orEmpty()
}.getOrDefault("").ifBlank { uri.substringAfterLast('/').take(80) }

private fun String.decodePathSegment(): String = runCatching {
    URLDecoder.decode(replace("+", "%2B"), Charsets.UTF_8.name())
}.getOrDefault(this)

private inline fun <T> resultCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    Result.failure(error)
}

private val MEDIA_SCHEMES = setOf("http", "https", "rtsp", "content", "file")
private val ARTWORK_SCHEMES = setOf("http", "https", "content", "file")
private val signedIntegerAtStart = Regex("""^[+-]?\d+""")
