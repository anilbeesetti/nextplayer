package dev.anilbeesetti.nextplayer.feature.iptv

import dev.anilbeesetti.nextplayer.core.model.IptvChannel
import dev.anilbeesetti.nextplayer.core.model.IptvQuality

/**
 * A single row in the channel list. It represents one logical channel which may be available in
 * several quality [variants] (e.g. "BBC One SD/HD/FHD"). When there is only one variant the picker
 * is hidden and tapping plays it directly.
 */
data class ChannelListItem(
    val displayName: String,
    val groupTitle: String?,
    val logoUrl: String?,
    val isLive: Boolean,
    val variants: List<QualityOption>,
) {
    val hasMultipleQualities: Boolean get() = variants.size > 1

    /** Highest available quality — the default when the user taps the row. */
    val defaultVariant: QualityOption get() = variants.first()
}

data class QualityOption(
    val quality: IptvQuality,
    val channel: IptvChannel,
)

/**
 * Collapses a flat channel list into [ChannelListItem]s, merging quality variants that share the
 * same base name (within the same group). Variants are ordered highest-quality first.
 */
fun List<IptvChannel>.toChannelListItems(): List<ChannelListItem> {
    data class Key(val group: String?, val base: String)

    val grouped = LinkedHashMap<Key, MutableList<QualityOption>>()
    for (channel in this) {
        val (quality, baseName) = IptvQuality.fromName(channel.name)
        val key = Key(channel.groupTitle, baseName.lowercase())
        grouped.getOrPut(key) { mutableListOf() }.add(QualityOption(quality, channel))
    }

    return grouped.values.map { options ->
        val sorted = options.sortedByDescending { it.quality.ordinal }
        val representative = sorted.first().channel
        val (_, baseName) = IptvQuality.fromName(representative.name)
        ChannelListItem(
            displayName = baseName.ifBlank { representative.name },
            groupTitle = representative.groupTitle,
            logoUrl = sorted.firstNotNullOfOrNull { it.channel.logoUrl },
            isLive = sorted.any { it.channel.isLive },
            variants = sorted,
        )
    }
}
