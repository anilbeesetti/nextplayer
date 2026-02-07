package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.datastore.datasource.SearchHistoryDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalSearchHistoryRepository @Inject constructor(
    private val searchHistoryDataSource: SearchHistoryDataSource,
) : SearchHistoryRepository {

    override val searchHistory: Flow<List<String>> =
        searchHistoryDataSource.searchHistory.map { it.queries }

    override suspend fun addSearchQuery(query: String) {
        searchHistoryDataSource.update { history ->
            history.addQuery(query)
        }
    }

    override suspend fun removeSearchQuery(query: String) {
        searchHistoryDataSource.update { history ->
            history.removeQuery(query)
        }
    }

    override suspend fun clearHistory() {
        searchHistoryDataSource.update { history ->
            history.clear()
        }
    }
}
