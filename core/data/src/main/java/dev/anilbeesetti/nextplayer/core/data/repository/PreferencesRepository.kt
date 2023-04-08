package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.datastore.Resume
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {

    /**
     * Stream of [AppPreferences].
     */
    val appPreferencesFlow: Flow<AppPreferences>

    /**
     * Stream of [PlayerPreferences].
     */
    val playerPreferencesFlow: Flow<PlayerPreferences>

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

    /**
     * Sets the playback resume of the video items.
     *
     * @param resume The playback resume to be set.
     */
    suspend fun setPlaybackResume(resume: Resume)

    /**
     * Sets the brightness level of the video items.
     *
     * @param value should remember the brightness level.
     */
    suspend fun shouldRememberPlayerBrightness(value: Boolean)

    /**
     * Sets the brightness level of the video items.
     *
     * @param value The brightness level to be set.
     */
    suspend fun setPlayerBrightness(value: Float)
}
