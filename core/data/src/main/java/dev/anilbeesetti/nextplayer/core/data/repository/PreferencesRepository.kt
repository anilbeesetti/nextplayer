package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.FastSeek
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {

    /**
     * Stream of [ApplicationPreferences].
     */
    val applicationPreferences: Flow<ApplicationPreferences>

    /**
     * Stream of [PlayerPreferences].
     */
    val playerPreferences: Flow<PlayerPreferences>

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
     * Sets the group videos by folder of the video items.
     *
     * @param value The group videos by folder to be set.
     */
    suspend fun setGroupVideosByFolder(value: Boolean)

    suspend fun addToExcludedFolders(folder: String)

    suspend fun removeFromExcludedFolders(folder: String)

    /**
     * Sets the theme config.
     *
     * @param themeConfig The theme config to be set.
     */
    suspend fun setThemeConfig(themeConfig: ThemeConfig)

    /**
     * Sets whether the dynamic colors is enabled.
     *
     * @param value Weather the dynamic color to be enabled.
     */
    suspend fun setUseDynamicColors(value: Boolean)

    /**
     * Sets whether the player should remember the brightness level.
     *
     * @param value should remember the brightness level.
     */
    suspend fun shouldRememberPlayerBrightness(value: Boolean)

    /**
     * Sets the playback resume of the video items.
     *
     * @param resume The playback resume to be set.
     */
    suspend fun setPlaybackResume(resume: Resume)

    /**
     * Sets the brightness level of the video items.
     *
     * @param value The brightness level to be set.
     */
    suspend fun setPlayerBrightness(value: Float)

    /**
     * Sets the double tap gesture of the video items.
     *
     * @param gesture The double tap gesture to be set.
     */
    suspend fun setDoubleTapGesture(gesture: DoubleTapGesture)

    /**
     * Sets the fast seek of the video items.
     *
     * @param seek The fast seek to be set.
     */
    suspend fun setFastSeek(seek: FastSeek)

    /**
     * Sets the use of swipe controls to adjust volume and brightness.
     *
     * @param value The use of swipe controls to be set.
     */
    suspend fun setUseSwipeControls(value: Boolean)

    /**
     * Sets the use of seek controls to seek forwards and backwards.
     *
     * @param value The use of seek controls to be set.
     */
    suspend fun setUseSeekControls(value: Boolean)

    /**
     * Sets whether the app should remember the selections for each file like audio track, subtitle track, etc.
     *
     * @param value The use of swipe controls to be set.
     */
    suspend fun setRememberSelections(value: Boolean)

    suspend fun setPreferredAudioLanguage(value: String)

    suspend fun setPreferredSubtitleLanguage(value: String)

    suspend fun setPlayerScreenOrientation(value: ScreenOrientation)
}
