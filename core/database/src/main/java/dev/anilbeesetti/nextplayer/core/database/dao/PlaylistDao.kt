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
        SELECT playlist.id, playlist.name, playlist.type,
            COUNT(playlist_item.uri) AS item_count, playlist.last_refreshed_at
        FROM playlist
        LEFT JOIN playlist_item ON playlist.id = playlist_item.playlist_id
        GROUP BY playlist.id
        ORDER BY playlist.created_at, playlist.id
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummaryEntity>>

    @Transaction
    @Query("SELECT * FROM playlist WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistWithItems?>

    @Transaction
    @Query("SELECT * FROM playlist WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistWithItems?

    @Query("SELECT * FROM playlist_item WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun getItems(playlistId: Long): List<PlaylistItemEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemsIgnore(items: List<PlaylistItemEntity>): List<Long>

    @Query("DELETE FROM playlist_item WHERE playlist_id = :playlistId")
    suspend fun deleteItems(playlistId: Long)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist WHERE type = :type AND source = :source")
    suspend fun countPlaylistsByTypeAndSource(type: String, source: String): Int

    @Update
    suspend fun updateItems(items: List<PlaylistItemEntity>)

    @Query("UPDATE playlist_item SET position = position + :offset WHERE playlist_id = :playlistId")
    suspend fun shiftItemPositions(playlistId: Long, offset: Int)

    @Transaction
    suspend fun addItems(playlistId: Long, items: List<PlaylistItemEntity>): Int {
        val currentItems = getItems(playlistId)
        val knownUris = currentItems.mapTo(mutableSetOf()) { it.uri }
        var nextPosition = (currentItems.maxOfOrNull { it.position } ?: -1) + 1
        val newItems = items.mapNotNull { item ->
            if (knownUris.add(item.uri)) {
                item.copy(playlistId = playlistId, position = nextPosition++)
            } else {
                null
            }
        }
        return insertItemsIgnore(newItems).count { it != -1L }
    }

    @Transaction
    suspend fun insertLinkedPlaylist(
        playlist: PlaylistEntity,
        items: List<PlaylistItemEntity>,
    ): Long {
        val playlistId = insertPlaylist(playlist)
        addItems(playlistId, items)
        return playlistId
    }

    @Transaction
    suspend fun replaceItems(
        playlistId: Long,
        items: List<PlaylistItemEntity>,
        refreshedAt: Long,
    ) {
        val playlist = getPlaylist(playlistId)?.playlist ?: return
        deleteItems(playlistId)
        addItems(playlistId, items)
        updatePlaylist(
            playlist.copy(
                updatedAt = refreshedAt,
                lastRefreshedAt = refreshedAt,
            ),
        )
    }

    @Transaction
    suspend fun moveItem(playlistId: Long, uri: String, toIndex: Int) {
        val items = getItems(playlistId)
        val fromIndex = items.indexOfFirst { it.uri == uri }
        if (fromIndex < 0 || items.size < 2) return

        val targetIndex = toIndex.coerceIn(0, items.lastIndex)
        if (fromIndex == targetIndex) return

        val reordered = items.toMutableList().apply {
            add(targetIndex, removeAt(fromIndex))
        }
        shiftItemPositions(playlistId, items.size)
        updateItems(reordered.mapIndexed { index, item -> item.copy(position = index) })
    }
}
