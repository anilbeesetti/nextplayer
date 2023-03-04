package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {

    /**
     * Stream of [AppPreferences].
     */
    val preferences: Flow<AppPreferences>

    /**
     * Sets the sort order of the video items.
     *
     * @param sortOrder The sort order to be set.
     */
    suspend fun setSortOrder(sortOrder: SortOrder)

    /**
     * Sets the sort by of the video items.
     *
     * @param sortBy The sort by to be set.
     */
    suspend fun setSortBy(sortBy: SortBy)

}