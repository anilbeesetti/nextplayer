package com.graviton.core.model

import kotlinx.serialization.Serializable

/**
 * A user saved position inside one media item.
 *
 * Bookmarks live in the existing application preferences store keyed by the media id, so they
 * survive upgrades without a database migration and without a parallel preference system.
 */
@Serializable
data class VideoBookmark(
    val positionMs: Long,
    val label: String = "",
    val createdAt: Long = 0L,
) {
    companion object {
        /** Upper bound per media item, keeping the serialized preferences small. */
        const val MAX_BOOKMARKS_PER_MEDIA = 50
    }
}
