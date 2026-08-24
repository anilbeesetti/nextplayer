package com.graviton.core.data.stream

import com.graviton.core.model.ExtractedStream
import com.graviton.core.model.StreamFormatCandidate
import com.graviton.core.model.StreamFormatSelector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses `yt-dlp -J` output. Kept free of Android APIs so unit tests can feed fixtures.
 */
object YtDlpJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(sourceUrl: String, raw: String): ExtractedStream {
        val root = json.parseToJsonElement(raw).jsonObject
        val formats = root["formats"]?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val url = item.string("url") ?: return@mapNotNull null
            StreamFormatCandidate(
                url = url,
                formatId = item.string("format_id"),
                extension = item.string("ext"),
                protocol = item.string("protocol"),
                vcodec = item.string("vcodec"),
                acodec = item.string("acodec"),
                height = item.int("height"),
                tbr = item.double("tbr"),
            )
        }
        return StreamFormatSelector.select(
            sourceUrl = sourceUrl,
            title = root.string("title") ?: root.string("fulltitle"),
            uploader = root.string("uploader") ?: root.string("channel"),
            thumbnailUrl = firstThumbnail(root) ?: root.string("thumbnail"),
            durationSeconds = root.double("duration"),
            topLevelUrl = root.string("url"),
            topLevelExt = root.string("ext"),
            topLevelProtocol = root.string("protocol"),
            formats = formats,
        )
    }

    private fun firstThumbnail(root: JsonObject): String? {
        val thumbs = root["thumbnails"]?.jsonArray ?: return null
        var bestUrl: String? = null
        var bestWidth = -1
        thumbs.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val url = item.string("url") ?: return@forEach
            val width = item.int("width") ?: 0
            if (width >= bestWidth) {
                bestWidth = width
                bestUrl = url
            }
        }
        return bestUrl
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

    private val JsonElement.jsonPrimitiveSafe get() = runCatching { jsonPrimitive }.getOrNull()
}
