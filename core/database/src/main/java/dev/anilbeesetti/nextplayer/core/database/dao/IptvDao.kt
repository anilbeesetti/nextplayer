package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.anilbeesetti.nextplayer.core.database.entities.IptvChannelEntity
import dev.anilbeesetti.nextplayer.core.database.entities.IptvPlaylistEntity
import kotlinx.coroutines.flow.Flow

/** A playlist row together with the number of channels it currently holds. */
data class IptvPlaylistWithCount(
    @Embedded val playlist: IptvPlaylistEntity,
    val channelCount: Int,
)

@Dao
interface IptvDao {

    @Query(
        """
        SELECT p.*, (SELECT COUNT(*) FROM iptv_channel c WHERE c.playlist_id = p.id) AS channelCount
        FROM iptv_playlist p
        ORDER BY p.added_at DESC
        """,
    )
    fun getPlaylistsWithCount(): Flow<List<IptvPlaylistWithCount>>

    @Query("SELECT * FROM iptv_channel ORDER BY name COLLATE NOCASE ASC")
    fun getAllChannels(): Flow<List<IptvChannelEntity>>

    @Query("SELECT * FROM iptv_channel WHERE playlist_id = :playlistId ORDER BY name COLLATE NOCASE ASC")
    fun getChannelsForPlaylist(playlistId: Long): Flow<List<IptvChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: IptvPlaylistEntity): Long

    @Query("SELECT * FROM iptv_playlist WHERE source = :source LIMIT 1")
    suspend fun getPlaylistBySource(source: String): IptvPlaylistEntity?

    @Insert
    suspend fun insertChannels(channels: List<IptvChannelEntity>)

    @Query("DELETE FROM iptv_channel WHERE playlist_id = :playlistId")
    suspend fun deleteChannelsForPlaylist(playlistId: Long)

    @Query("DELETE FROM iptv_playlist WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    /** Replaces all channels of [playlistId] with [channels] in a single transaction. */
    @Transaction
    suspend fun replaceChannels(playlistId: Long, channels: List<IptvChannelEntity>) {
        deleteChannelsForPlaylist(playlistId)
        insertChannels(channels)
    }
}
