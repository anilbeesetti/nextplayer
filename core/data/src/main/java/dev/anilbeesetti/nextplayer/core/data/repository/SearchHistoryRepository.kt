package dev.anilbeesetti.nextplayer.core.data.repository

import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {

    /**
     * Stream of search history queries.
     */
    val searchHistory: Flow<List<String>>

    /**
     * Add a search query to history.
     */
    suspend fun addSearchQuery(query: String)

    /**
     * Remove a search query from history.
     */
    suspend fun removeSearchQuery(query: String)

    /**
     * Clear all search history.
     */
    suspend fun clearHistory()
}
