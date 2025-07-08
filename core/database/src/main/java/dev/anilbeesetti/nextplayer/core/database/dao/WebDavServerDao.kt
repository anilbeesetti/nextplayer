package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.anilbeesetti.nextplayer.core.database.entities.WebDavServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebDavServerDao {
    @Query("SELECT * FROM webdav_servers ORDER BY createdAt DESC")
    fun getAllServers(): Flow<List<WebDavServerEntity>>

    @Query("SELECT * FROM webdav_servers WHERE id = :id")
    suspend fun getServerById(id: String): WebDavServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: WebDavServerEntity)

    @Update
    suspend fun updateServer(server: WebDavServerEntity)

    @Delete
    suspend fun deleteServer(server: WebDavServerEntity)

    @Query("DELETE FROM webdav_servers WHERE id = :id")
    suspend fun deleteServerById(id: String)

    @Query("UPDATE webdav_servers SET isConnected = :isConnected, lastConnected = :lastConnected WHERE id = :id")
    suspend fun updateConnectionStatus(id: String, isConnected: Boolean, lastConnected: Long)
}
