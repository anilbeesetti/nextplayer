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


    suspend fun updateApplicationPreferences(transform: suspend (ApplicationPreferences) -> ApplicationPreferences)


    suspend fun updatePlayerPreferences(transform: suspend (PlayerPreferences) -> PlayerPreferences)
}
