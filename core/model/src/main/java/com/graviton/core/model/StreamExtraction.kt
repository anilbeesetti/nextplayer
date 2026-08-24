package com.graviton.core.model

/**
 * A URL that the existing Media3 player can stream without downloading the source first.
 */
data class ExtractedStream(
    val sourceUrl: String,
    val playableUrl: String,
    val title: String? = null,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long? = null,
    val formatId: String? = null,
    val extension: String? = null,
    val protocol: String? = null,
    val extracted: Boolean = false,
) {
    val isHls: Boolean
        get() = protocol.equals("m3u8", ignoreCase = true) ||
            protocol.equals("m3u8_native", ignoreCase = true) ||
            extension.equals("m3u8", ignoreCase = true) ||
            playableUrl.contains(".m3u8", ignoreCase = true)

    val isDash: Boolean
        get() = protocol.equals("http_dash_segments", ignoreCase = true) ||
            extension.equals("mpd", ignoreCase = true) ||
            playableUrl.contains(".mpd", ignoreCase = true)
}

data class StreamFormatCandidate(
    val url: String,
    val formatId: String? = null,
    val extension: String? = null,
    val protocol: String? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val height: Int? = null,
    val tbr: Double? = null,
) {
    val hasVideo: Boolean = !vcodec.isNullOrBlank() && !vcodec.equals("none", ignoreCase = true)
    val hasAudio: Boolean = !acodec.isNullOrBlank() && !acodec.equals("none", ignoreCase = true)
    val isProgressive: Boolean = hasVideo && hasAudio
    val isAudioOnly: Boolean = hasAudio && !hasVideo
    val isHttp: Boolean = protocol.isNullOrBlank() ||
        protocol.startsWith("http", ignoreCase = true) ||
        protocol.equals("https", ignoreCase = true)
}

object StreamUrls {
    private val directExtensions = setOf(
        "3gp", "aac", "aiff", "avi", "flac", "flv", "m4a", "m4v", "mkv", "mov", "mp3",
        "mp4", "mpd", "mpeg", "mpg", "ogg", "ogv", "opus", "ts", "wav", "webm", "wma",
        "m3u8",
    )

    fun isHttpUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    fun isLocalProxy(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("http://127.0.0.1") || lower.startsWith("http://localhost")
    }

    fun pathExtension(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')
        val name = path.substringAfterLast('/')
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return ext.takeIf { it.isNotBlank() && it.length <= 5 }
    }

    fun isDirectMediaUrl(url: String): Boolean {
        if (isLocalProxy(url)) return true
        val ext = pathExtension(url) ?: return false
        return ext in directExtensions
    }

    fun needsExtraction(url: String): Boolean {
        if (!isHttpUrl(url)) return false
        if (isLocalProxy(url)) return false
        if (isDirectMediaUrl(url)) return false
        return true
    }
}

object StreamFormatSelector {
    fun select(
        sourceUrl: String,
        title: String?,
        uploader: String?,
        thumbnailUrl: String?,
        durationSeconds: Double?,
        topLevelUrl: String?,
        topLevelExt: String?,
        topLevelProtocol: String?,
        formats: List<StreamFormatCandidate>,
    ): ExtractedStream {
        val preferred = pick(formats)
        val playable = preferred?.url ?: topLevelUrl?.takeIf { it.isNotBlank() }
            ?: error("yt-dlp returned no playable URL for $sourceUrl")
        return ExtractedStream(
            sourceUrl = sourceUrl,
            playableUrl = playable,
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnailUrl,
            durationMs = durationSeconds?.times(1000.0)?.toLong(),
            formatId = preferred?.formatId,
            extension = preferred?.extension ?: topLevelExt,
            protocol = preferred?.protocol ?: topLevelProtocol,
            extracted = !playable.equals(sourceUrl, ignoreCase = true),
        )
    }

    fun pick(formats: List<StreamFormatCandidate>): StreamFormatCandidate? {
        val withUrl = formats.filter { it.url.isNotBlank() }
        if (withUrl.isEmpty()) return null
        val progressive = withUrl.filter { it.isProgressive && it.isHttp }
            .sortedWith(compareByDescending<StreamFormatCandidate> { it.height ?: 0 }.thenByDescending { it.tbr ?: 0.0 })
        progressive.firstOrNull()?.let { return it }

        val hls = withUrl.filter {
            it.extension.equals("m3u8", ignoreCase = true) ||
                it.protocol?.contains("m3u8", ignoreCase = true) == true
        }
        hls.maxByOrNull { it.height ?: 0 }?.let { return it }

        val dash = withUrl.filter {
            it.extension.equals("mpd", ignoreCase = true) ||
                it.protocol?.contains("dash", ignoreCase = true) == true
        }
        dash.maxByOrNull { it.height ?: 0 }?.let { return it }

        val audio = withUrl.filter { it.isAudioOnly && it.isHttp }
            .sortedByDescending { it.tbr ?: 0.0 }
        audio.firstOrNull()?.let { return it }

        return withUrl.maxWithOrNull(
            compareBy<StreamFormatCandidate> { it.height ?: 0 }.thenBy { it.tbr ?: 0.0 },
        )
    }
}
