package dev.anilbeesetti.nextplayer.core.data.playlist

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject

class PlaylistEntryResolver @Inject constructor() {
    fun isValidRemoteSource(source: String): Boolean =
        source.toUriOrNull()?.isValidHttpUri() == true

    fun resolveRemote(source: String, raw: String): String? = runCatching {
        val sourceUri = URI(source).takeIf { it.isValidHttpUri() } ?: return null
        val entry = raw.toEntryUriOrNull() ?: return null
        val resolved = if (entry.isAbsolute) entry else sourceUri.resolve(entry)
        resolved.toValidatedEntry()
    }.getOrNull()

    fun resolveDocument(source: String, raw: String): String? {
        val entry = raw.toEntryUriOrNull() ?: return null
        if (entry.isAbsolute) return entry.toValidatedEntry()

        val relativeSegments = entry.normalizedRelativeSegments() ?: return null
        val treeSource = source.toTreeDocumentSourceOrNull() ?: return null
        val parentDocumentId = treeSource.documentId.substringBeforeLast(
            delimiter = '/',
            missingDelimiterValue = "",
        )
        if (!parentDocumentId.isAtOrDescendantOf(treeSource.treeDocumentId)) return null

        val resolvedDocumentId = (listOf(parentDocumentId) + relativeSegments).joinToString("/")
        if (!resolvedDocumentId.isDescendantOf(treeSource.treeDocumentId)) return null

        return "content://${treeSource.authority}/tree/" +
            "${treeSource.treeDocumentId.encodePathSegment()}/document/" +
            resolvedDocumentId.encodePathSegment()
    }

    private fun URI.toValidatedEntry(): String? = when (scheme?.lowercase()) {
        "http", "https" -> toString().takeIf { isValidHttpUri() }
        "content" -> toString().takeIf {
            !isOpaque && !rawAuthority.isNullOrBlank() && path?.trim('/')?.isNotBlank() == true
        }
        "file" -> toString().takeIf {
            !isOpaque && !path.isNullOrBlank() && path.startsWith('/')
        }
        else -> null
    }

    private fun URI.isValidHttpUri(): Boolean =
        scheme?.lowercase() in HTTP_SCHEMES && !isOpaque && !host.isNullOrBlank()

    private fun URI.normalizedRelativeSegments(): List<String>? {
        if (isAbsolute || isOpaque || authority != null || query != null || fragment != null) return null
        val value = path?.takeIf { it.isNotEmpty() && !it.startsWith('/') } ?: return null
        return value.split('/').takeIf { segments ->
            segments.all { segment ->
                segment.isNotEmpty() && segment != "." && segment != ".." && '\\' !in segment
            }
        }
    }

    private fun String.toTreeDocumentSourceOrNull(): TreeDocumentSource? {
        val uri = toUriOrNull()?.takeIf { it.toValidatedEntry() != null } ?: return null
        if (uri.scheme?.lowercase() != "content" || uri.query != null || uri.fragment != null) return null
        val segments = uri.rawPath?.removePrefix("/")?.split('/') ?: return null
        if (segments.size != 4 || segments[0] != "tree" || segments[2] != "document") return null

        val treeDocumentId = segments[1].decodePathSegmentOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return null
        val documentId = segments[3].decodePathSegmentOrNull()
            ?.takeIf { it.isDescendantOf(treeDocumentId) } ?: return null
        return TreeDocumentSource(
            authority = uri.rawAuthority,
            treeDocumentId = treeDocumentId,
            documentId = documentId,
        )
    }

    private fun String.isDescendantOf(parent: String): Boolean = startsWith("$parent/")

    private fun String.isAtOrDescendantOf(parent: String): Boolean =
        this == parent || isDescendantOf(parent)

    private fun String.toEntryUriOrNull(): URI? = runCatching { URI(this) }
        .recoverCatching {
            require(':' !in this && '\\' !in this)
            URI(null, null, this, null)
        }
        .getOrNull()

    private fun String.toUriOrNull(): URI? = runCatching { URI(this) }.getOrNull()

    private fun String.decodePathSegmentOrNull(): String? = runCatching {
        URLDecoder.decode(replace("+", "%2B"), Charsets.UTF_8)
    }.getOrNull()

    private fun String.encodePathSegment(): String =
        URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")

    private data class TreeDocumentSource(
        val authority: String,
        val treeDocumentId: String,
        val documentId: String,
    )

    private companion object {
        val HTTP_SCHEMES = setOf("http", "https")
    }
}
