package dev.anilbeesetti.nextplayer.core.model

/**
 * A quality variant of a channel, e.g. the "FHD" version of "BBC One".
 *
 * IPTV playlists rarely expose bitrate ladders inside a single manifest; instead the same channel
 * is listed several times with a quality suffix in its name (SD / HD / FHD / 4K …). We detect that
 * suffix at parse time so the UI can offer a quality picker.
 */
data class IptvQualityVariant(
    val quality: IptvQuality,
    val channel: IptvChannel,
)

/**
 * Known stream quality tiers, ordered from lowest to highest so the UI can sort variants and
 * pick a sensible default (the highest available).
 */
enum class IptvQuality(val label: String) {
    UNKNOWN("Auto"),
    SD("SD"),
    HD("HD"),
    FHD("FHD"),
    UHD("4K"),
    ;

    companion object {
        /**
         * Extracts a quality tier from a channel [name] and returns it alongside the name stripped
         * of the quality token, which is used to group variants of the same channel together.
         */
        fun fromName(name: String): Pair<IptvQuality, String> {
            val tokens = listOf(
                UHD to listOf("4k", "uhd", "2160p", "2160"),
                FHD to listOf("fhd", "1080p", "1080", "full hd"),
                HD to listOf("hd", "720p", "720"),
                SD to listOf("sd", "480p", "360p", "480", "360"),
            )
            val lower = name.lowercase()
            for ((quality, keys) in tokens) {
                val matched = keys.firstOrNull { key ->
                    // Match as a standalone word so "Adhd" doesn't become HD.
                    Regex("(^|[^a-z0-9])${Regex.escape(key)}($|[^a-z0-9])").containsMatchIn(lower)
                }
                if (matched != null) {
                    val baseName = name
                        .replace(Regex("(?i)(^|[^a-z0-9])${Regex.escape(matched)}($|[^a-z0-9])"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .ifEmpty { name }
                    return quality to baseName
                }
            }
            return UNKNOWN to name
        }
    }
}
