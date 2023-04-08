package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.datastore.FastSeek
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.datastore.Resume
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakePreferencesRepository : PreferencesRepository {

    val appPreferences = MutableStateFlow(AppPreferences())
    val playerPreferences = MutableStateFlow(PlayerPreferences())

    override val appPreferencesFlow: Flow<AppPreferences>
        get() = appPreferences
    override val playerPreferencesFlow: Flow<PlayerPreferences>
        get() = playerPreferences

    override suspend fun setSortOrder(sortOrder: SortOrder) {
        appPreferences.update { it.copy(sortOrder = sortOrder) }
    }

    override suspend fun setSortBy(sortBy: SortBy) {
        appPreferences.update { it.copy(sortBy = sortBy) }
    }

    override suspend fun setPlaybackResume(resume: Resume) {
        playerPreferences.update { it.copy(resume = resume) }
    }

    override suspend fun shouldRememberPlayerBrightness(value: Boolean) {
        playerPreferences.update { it.copy(rememberPlayerBrightness = value) }
    }

    override suspend fun setPlayerBrightness(value: Float) {
        playerPreferences.update { it.copy(playerBrightness = value) }
    }

    override suspend fun setDoubleTapGesture(gesture: DoubleTapGesture) {
        playerPreferences.update { it.copy(doubleTapGesture = gesture) }
    }

    override suspend fun setFastSeek(seek: FastSeek) {
        playerPreferences.update { it.copy(fastSeek = seek) }
    }
}
