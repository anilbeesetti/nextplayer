package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.anilbeesetti.nextplayer.core.database.entities.NetworkConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkConnectionDao {

    @Upsert
    suspend fun upsert(connection: NetworkConnectionEntity): Long

    @Query("SELECT * FROM network_connection ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<NetworkConnectionEntity>>

    @Query("SELECT * FROM network_connection WHERE id = :id")
    suspend fun getById(id: Long): NetworkConnectionEntity?

    @Query("DELETE FROM network_connection WHERE id = :id")
    suspend fun deleteById(id: Long)
}
