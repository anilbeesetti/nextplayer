package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity

@Dao
interface VideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(videoEntity: VideoEntity)

    @Query("SELECT * FROM VideoEntity WHERE id = :id")
    suspend fun get(id: Long): VideoEntity?
}
