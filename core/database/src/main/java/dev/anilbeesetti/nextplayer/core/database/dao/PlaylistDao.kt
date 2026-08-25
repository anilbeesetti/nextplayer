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
        SELECT playlist.id, playlist.name, playlist.type, playlist.last_refreshed_at,
            COUNT(playlist_item.uri) AS item_count
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

    @Transaction
    @Query("SELECT * FROM playlist WHERE id = :playlistId")
    suspend fun getPlaylist(playlistId: Long): PlaylistWithItems?

    @Query("SELECT * FROM playlist WHERE id = :playlistId")
    suspend fun getPlaylistEntity(playlistId: Long): PlaylistEntity?

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

    @Query("DELETE FROM playlist_item WHERE playlist_id = :playlistId")
    suspend fun deleteItemsForPlaylist(playlistId: Long)

    @Query(
        """
        UPDATE playlist
        SET last_refreshed_at = :refreshedAt
        WHERE id = :playlistId
        """,
    )
    suspend fun updateLastRefreshedAt(playlistId: Long, refreshedAt: Long)

    @Query("SELECT COUNT(*) FROM playlist WHERE type = :type AND source = :source")
    suspend fun countPlaylistsByTypeAndSource(type: String, source: String): Int

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
    suspend fun createM3UPlaylist(
        playlist: PlaylistEntity,
        items: List<PlaylistItemEntity>,
    ): Long {
        val playlistId = insertPlaylist(playlist)
        insertItems(items.map { it.copy(playlistId = playlistId) })
        return playlistId
    }

    @Transaction
    suspend fun replaceM3UItems(
        playlistId: Long,
        items: List<PlaylistItemEntity>,
        refreshedAt: Long,
    ) {
        require(getPlaylistEntity(playlistId)?.type in LINKED_PLAYLIST_TYPES) {
            "Only linked playlists can be refreshed"
        }
        val playedAtByUri = getItems(playlistId).associate { it.uri to it.lastPlayedAt }
        deleteItemsForPlaylist(playlistId)
        insertItems(
            items.map { item ->
                item.copy(
                    playlistId = playlistId,
                    lastPlayedAt = playedAtByUri[item.uri],
                )
            },
        )
        updateLastRefreshedAt(playlistId, refreshedAt)
    }

    suspend fun requireLocalPlaylist(playlistId: Long) {
        require(getPlaylistEntity(playlistId)?.type == LOCAL_PLAYLIST_TYPE) {
            "Only local playlists can be edited"
        }
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
    suspend fun removeMissingLocalItems(existingUris: Set<String>) {
        val localPlaylistIds = getLocalPlaylistIds().toSet()
        val missingItems = getAllItems().filter {
            it.playlistId in localPlaylistIds && it.uri !in existingUris
        }
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

    @Query("SELECT id FROM playlist WHERE type = 'LOCAL'")
    suspend fun getLocalPlaylistIds(): List<Long>

    companion object {
        private const val LOCAL_PLAYLIST_TYPE = "LOCAL"
        private val LINKED_PLAYLIST_TYPES = setOf("M3U_URL", "M3U_FILE")
    }
}
