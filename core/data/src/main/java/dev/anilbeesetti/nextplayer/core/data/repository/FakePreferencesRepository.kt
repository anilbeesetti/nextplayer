package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakePreferencesRepository: PreferencesRepository {

    val appPreferences = MutableStateFlow(AppPreferences())

    override val preferences: Flow<AppPreferences>
        get() = appPreferences

    override suspend fun setSortOrder(sortOrder: SortOrder) {
        appPreferences.update { it.copy(sortOrder = sortOrder) }
    }

    override suspend fun setSortBy(sortBy: SortBy) {
        appPreferences.update { it.copy(sortBy = sortBy) }
    }
}