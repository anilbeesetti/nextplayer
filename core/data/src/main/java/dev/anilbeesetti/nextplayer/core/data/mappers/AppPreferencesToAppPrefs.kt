package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.model.AppPrefs

fun AppPreferences.toAppPrefs() = AppPrefs(
    sortBy = sortBy,
    sortOrder = sortOrder,
    groupVideosByFolder = groupVideosByFolder,
    themeConfig = themeConfig,
    useDynamicColors = useDynamicColors
)