package dev.anilbeesetti.nextplayer.core.model

data class AppPrefs(
    val sortBy: SortBy,
    val sortOrder: SortOrder,
    val groupVideosByFolder: Boolean,
    val themeConfig: ThemeConfig,
    val useDynamicColors: Boolean
) {

    companion object {
        fun default() = AppPrefs(
            sortBy = SortBy.TITLE,
            sortOrder = SortOrder.ASCENDING,
            groupVideosByFolder = true,
            themeConfig = ThemeConfig.SYSTEM,
            useDynamicColors = true
        )
    }
}
