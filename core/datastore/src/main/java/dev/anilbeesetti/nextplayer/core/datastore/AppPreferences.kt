package dev.anilbeesetti.nextplayer.core.datastore

import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    val sortBy: SortBy = SortBy.Title,
    val sortOrder: SortOrder = SortOrder.Ascending
)

enum class SortBy {
    Title, Length
}

enum class SortOrder {
    Ascending, Descending
}
