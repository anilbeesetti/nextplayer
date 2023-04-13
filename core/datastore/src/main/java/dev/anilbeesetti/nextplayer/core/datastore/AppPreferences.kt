package dev.anilbeesetti.nextplayer.core.datastore

import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    val sortBy: SortBy = SortBy.TITLE,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val groupVideosByFolder: Boolean = true
)

enum class SortBy {
    TITLE, LENGTH, PATH, SIZE
}

enum class SortOrder {
    ASCENDING, DESCENDING
}
