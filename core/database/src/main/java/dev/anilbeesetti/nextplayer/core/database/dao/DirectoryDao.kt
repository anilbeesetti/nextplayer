package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectoryDao {

    @Upsert
    suspend fun upsert(directory: DirectoryEntity)

    @Upsert
    suspend fun upsertAll(directories: List<DirectoryEntity>)

    @Query("SELECT * FROM directories")
    fun getAll(): Flow<List<DirectoryEntity>>

    @Query("DELETE FROM directories WHERE path in (:paths)")
    suspend fun delete(paths: List<String>)
}
