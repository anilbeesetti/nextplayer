package dev.anilbeesetti.nextplayer.core.datastore

import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    val sortBy: SortBy = SortBy.TITLE,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val groupVideosByFolder: Boolean = true,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM
)

enum class SortBy {
    TITLE, LENGTH, PATH, SIZE
}

enum class ThemeConfig(val value: String) {
    SYSTEM("System Default"),
    LIGHT("Off"),
    DARK("On")
}

enum class SortOrder {
    ASCENDING, DESCENDING
}
