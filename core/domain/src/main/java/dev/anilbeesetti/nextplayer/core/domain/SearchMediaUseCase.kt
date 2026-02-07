package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

data class SearchResults(
    val folders: List<Folder> = emptyList(),
    val videos: List<Video> = emptyList(),
) {
    val isEmpty: Boolean
        get() = folders.isEmpty() && videos.isEmpty()

    val totalCount: Int
        get() = folders.size + videos.size
}

fun SearchResults.asRootFolder(): Folder {
    return Folder.rootFolder.copy(
        mediaList = videos,
        folderList = folders,
    )
}

class SearchMediaUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val getSortedFoldersUseCase: GetSortedFoldersUseCase,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(query: String): Flow<SearchResults> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return flowOf(SearchResults())
        }

        return combine(
            getSortedVideosUseCase(),
            getSortedFoldersUseCase(),
        ) { videos, folders ->
            val searchMatcher = SearchMatcher(normalizedQuery)

            // Score and filter folders
            val scoredFolders = folders.mapNotNull { folder ->
                val score = searchMatcher.calculateScore(folder.name, folder.path)
                if (score > 0) folder to score else null
            }.sortedByDescending { it.second }.map { it.first }

            val scoredVideos = videos.mapNotNull { video ->
                val score = searchMatcher.calculateScore(video.nameWithExtension, video.path)
                if (score > 0) video to score else null
            }.sortedByDescending { it.second }.map { it.first }

            SearchResults(
                folders = scoredFolders,
                videos = scoredVideos,
            )
        }.flowOn(defaultDispatcher)
    }
}

/**
 * Intelligent search matcher that supports grep-style multi-word search.
 *
 * Ranking priority (highest to lowest):
 * 1. Exact phrase match (e.g., "stranger 2019" matches "stranger 2019")
 * 2. All words appear in order with gaps (e.g., "stranger 2019" matches "stranger things 2019")
 * 3. All words appear anywhere (e.g., "stranger 2019" matches "2019 stranger things")
 */
private class SearchMatcher(query: String) {
    private val queryLower = query.lowercase()
    private val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

    private val wordsInOrderPattern: Regex? = if (queryWords.size > 1) {
        Regex(queryWords.joinToString(".*") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    } else {
        null
    }

    /**
     * Calculate a relevance score for the given texts.
     * Returns 0 if no match, higher scores for better matches.
     */
    fun calculateScore(vararg texts: String): Int {
        var bestScore = 0

        for (text in texts) {
            val textLower = text.lowercase()
            val score = calculateTextScore(textLower)
            if (score > bestScore) {
                bestScore = score
            }
        }

        return bestScore
    }

    private fun calculateTextScore(textLower: String): Int {
        // Level 1: Exact phrase match (highest priority)
        if (textLower.contains(queryLower)) {
            // Bonus for exact match at word boundary
            val wordBoundaryBonus = if (isWordBoundaryMatch(textLower, queryLower)) 50 else 0
            // Bonus for match at start
            val startBonus = if (textLower.startsWith(queryLower)) 30 else 0
            return 1000 + wordBoundaryBonus + startBonus
        }

        // For single word queries, no further matching needed
        if (queryWords.size <= 1) {
            return 0
        }

        // Level 2: All words appear in order (with gaps allowed)
        // e.g., "stranger 2019" matches "stranger things 2019"
        wordsInOrderPattern?.let { pattern ->
            if (pattern.containsMatchIn(textLower)) {
                return 500
            }
        }

        // Level 3: All words appear somewhere in the text (any order)
        // e.g., "stranger 2019" matches "2019 stranger things"
        val allWordsPresent = queryWords.all { word -> textLower.contains(word) }
        if (allWordsPresent) {
            // Count how many words match at word boundaries for bonus
            val boundaryMatches = queryWords.count { word -> isWordBoundaryMatch(textLower, word) }
            return 200 + (boundaryMatches * 20)
        }

        return 0
    }

    /**
     * Check if the query appears at a word boundary in the text.
     * Word boundaries are: start of string, space, underscore, dash, dot, etc.
     */
    private fun isWordBoundaryMatch(text: String, query: String): Boolean {
        val index = text.indexOf(query)
        if (index == -1) return false

        // Check if match is at start or preceded by word boundary
        val isAtStart = index == 0
        val isPrecededByBoundary = index > 0 && text[index - 1].isWordBoundary()

        return isAtStart || isPrecededByBoundary
    }

    private fun Char.isWordBoundary(): Boolean {
        return this in listOf(' ', '_', '-', '.', '/', '\\', '[', ']', '(', ')')
    }
}
