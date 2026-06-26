package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
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

    @Query("SELECT * FROM hidden_video WHERE vault_path = :vaultPath")
    suspend fun getByVaultPath(vaultPath: String): HiddenVideoEntity?

    @Query("SELECT * FROM hidden_video WHERE original_path = :originalPath LIMIT 1")
    suspend fun getByOriginalPath(originalPath: String): HiddenVideoEntity?

    @Query("SELECT COUNT(*) FROM hidden_video")
    suspend fun count(): Int

    @Delete
    suspend fun delete(hiddenVideo: HiddenVideoEntity)

    @Query("DELETE FROM hidden_video WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
