package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A user-defined chapter / bookmark within a video, identified by a [title] and the
 * playback position [timestampMs] it points to. Stored per-video and importable/exportable
 * as a JSON array of these objects.
 */
@Serializable
data class Chapter(
    val title: String,
    val timestampMs: Long,
)

/**
 * Serializes a list of [Chapter]s to/from a JSON string for both DB persistence and
 * file import/export. Kept here in `core:model` so callers do not need a direct
 * kotlinx-serialization dependency. Mirrors the role of `UriListConverter`.
 */
object ChapterListConverter {

    private val json = Json { ignoreUnknownKeys = true }

    fun fromListToString(chapters: List<Chapter>): String {
        if (chapters.isEmpty()) return ""
        return json.encodeToString(chapters)
    }

    fun fromStringToList(value: String): List<Chapter> {
        if (value.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Chapter>>(value) }
            .getOrDefault(emptyList())
    }
}
