package dev.anilbeesetti.nextplayer.feature.player.service

import androidx.media3.common.MediaItem
import dev.anilbeesetti.nextplayer.feature.player.utils.isExplicitPlaylistItem

/** Receivers auto-advance their queue; implicit folders must honor the autoplay preference. */
internal fun mediaItemsForCast(
    items: List<MediaItem>,
    startIndex: Int,
    autoplay: Boolean,
): List<MediaItem> {
    val selected = items.getOrNull(startIndex.coerceAtLeast(0)) ?: return items
    return if (!autoplay && !selected.isExplicitPlaylistItem) listOf(selected) else items
}
