package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchHistory(
    val queries: List<String> = emptyList(),
) {
    companion object {
        const val MAX_HISTORY_SIZE = 5
    }

    fun addQuery(query: String): SearchHistory {
        if (query.isBlank()) return this
        val normalizedQuery = query.trim()
        val updatedQueries = listOf(normalizedQuery) + queries.filter { it != normalizedQuery }
        return copy(queries = updatedQueries.take(MAX_HISTORY_SIZE))
    }

    fun removeQuery(query: String): SearchHistory {
        return copy(queries = queries.filter { it != query })
    }

    fun clear(): SearchHistory {
        return copy(queries = emptyList())
    }
}
