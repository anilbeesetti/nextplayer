package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistItemEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistSummaryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT playlist.id, playlist.name, COUNT(playlist_item.uri) AS item_count
        FROM playlist
        LEFT JOIN playlist_item ON playlist.id = playlist_item.playlist_id
        GROUP BY playlist.id
        ORDER BY playlist.created_at, playlist.id
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummaryEntity>>

    @Transaction
    @Query("SELECT * FROM playlist WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistWithItems?>

    @Query("SELECT * FROM playlist_item WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun getItems(playlistId: Long): List<PlaylistItemEntity>

    @Query("SELECT * FROM playlist_item")
    suspend fun getAllItems(): List<PlaylistItemEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<PlaylistItemEntity>): List<Long>

    @Query("UPDATE playlist SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("DELETE FROM playlist WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_item WHERE playlist_id = :playlistId AND uri = :uri")
    suspend fun deleteItem(playlistId: Long, uri: String)

    @Query(
        """
        UPDATE playlist_item
        SET last_played_at = :playedAt
        WHERE playlist_id = :playlistId AND uri = :uri
        """,
    )
    suspend fun updateLastPlayedAt(playlistId: Long, uri: String, playedAt: Long): Int

    @Delete
    suspend fun deleteItems(items: List<PlaylistItemEntity>)

    @Query("UPDATE playlist_item SET position = position + :offset WHERE playlist_id = :playlistId")
    suspend fun shiftPositions(playlistId: Long, offset: Int)

    @Update
    suspend fun updateItems(items: List<PlaylistItemEntity>)

    @Transaction
    suspend fun createPlaylist(name: String, uris: List<String>): Long {
        val playlistId = insertPlaylist(PlaylistEntity(name = name))
        addItems(playlistId, uris)
        return playlistId
    }

    @Transaction
    suspend fun addItems(playlistId: Long, uris: List<String>): Int {
        val currentItems = getItems(playlistId)
        val knownUris = currentItems.mapTo(mutableSetOf()) { it.uri }
        var nextPosition = currentItems.size
        val newItems = uris.mapNotNull { uri ->
            if (knownUris.add(uri)) {
                PlaylistItemEntity(
                    playlistId = playlistId,
                    uri = uri,
                    position = nextPosition++,
                )
            } else {
                null
            }
        }
        return insertItems(newItems).count { it != -1L }
    }

    @Transaction
    suspend fun removeItem(playlistId: Long, uri: String) {
        deleteItem(playlistId, uri)
        normalizePositions(playlistId)
    }

    @Transaction
    suspend fun markItemPlayed(playlistId: Long, uri: String, playedAt: Long) {
        require(updateLastPlayedAt(playlistId, uri, playedAt) == 1) {
            "Last played URI must belong to the playlist"
        }
    }

    @Transaction
    suspend fun replaceOrder(playlistId: Long, orderedUris: List<String>) {
        val currentItems = getItems(playlistId)
        require(orderedUris.size == orderedUris.distinct().size) {
            "Playlist order must not contain duplicate URIs"
        }
        require(orderedUris.toSet() == currentItems.mapTo(mutableSetOf()) { it.uri }) {
            "Playlist order must contain every current item exactly once"
        }
        if (orderedUris == currentItems.map { it.uri }) return

        shiftPositions(playlistId, currentItems.size + 1)
        val itemsByUri = currentItems.associateBy { it.uri }
        updateItems(
            orderedUris.mapIndexed { position, uri ->
                itemsByUri.getValue(uri).copy(position = position)
            },
        )
    }

    @Transaction
    suspend fun removeMissingItems(existingUris: Set<String>) {
        val missingItems = getAllItems().filter { it.uri !in existingUris }
        if (missingItems.isEmpty()) return

        deleteItems(missingItems)
        missingItems.map { it.playlistId }.distinct().forEach { playlistId ->
            normalizePositions(playlistId)
        }
    }

    private suspend fun normalizePositions(playlistId: Long) {
        val items = getItems(playlistId)
        if (items.withIndex().all { (index, item) -> item.position == index }) return

        shiftPositions(playlistId, items.size + 1)
        updateItems(items.mapIndexed { position, item -> item.copy(position = position) })
    }
}
