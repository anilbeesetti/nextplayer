package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItemInput
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import java.io.IOException
import kotlinx.coroutines.flow.Flow

data class PlaylistRefreshResult(
    val playlistId: Long,
    val itemCount: Int,
    val skippedEntries: Int,
)

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    fun observePlaylist(id: Long): Flow<Playlist?>

    suspend fun createEditable(name: String): Long

    suspend fun createLinked(
        name: String,
        type: PlaylistType,
        source: String,
    ): PlaylistRefreshResult

    suspend fun addItems(id: Long, items: List<PlaylistItemInput>): Int

    suspend fun moveItem(id: Long, uriString: String, toIndex: Int)

    suspend fun refresh(id: Long): PlaylistRefreshResult

    suspend fun delete(id: Long)
}

class PlaylistNameConflictException : IllegalArgumentException()

class LinkedPlaylistReadOnlyException : IllegalStateException()

class PlaylistSourceException(cause: Throwable) : IOException(cause)
