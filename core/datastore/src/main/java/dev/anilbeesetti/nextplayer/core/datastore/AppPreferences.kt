package dev.anilbeesetti.nextplayer.core.datastore

import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    val sortBy: SortBy = SortBy.TITLE,
    val sortOrder: SortOrder = SortOrder.ASCENDING
)

enum class SortBy {
    TITLE, DURATION, PATH, RESOLUTION
}

enum class SortOrder {
    ASCENDING, DESCENDING
}
