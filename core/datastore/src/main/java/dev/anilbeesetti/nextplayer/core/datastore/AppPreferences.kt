package dev.anilbeesetti.nextplayer.core.datastore

import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    val sortBy: SortBy = SortBy.TITLE,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val groupVideosByFolder: Boolean = true,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val useDynamicColors: Boolean = true
)
