package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.anilbeesetti.nextplayer.core.database.entities.HiddenVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenVideoDao {

    @Insert
    suspend fun insert(hiddenVideo: HiddenVideoEntity): Long

    @Query("SELECT * FROM hidden_video ORDER BY hidden_at DESC")
    fun getAll(): Flow<List<HiddenVideoEntity>>

    @Query("SELECT * FROM hidden_video WHERE id = :id")
    suspend fun getById(id: Long): HiddenVideoEntity?

    @Query("DELETE FROM hidden_video WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM hidden_video WHERE vault_path IN (:vaultPaths)")
    suspend fun deleteByVaultPaths(vaultPaths: List<String>)
}
