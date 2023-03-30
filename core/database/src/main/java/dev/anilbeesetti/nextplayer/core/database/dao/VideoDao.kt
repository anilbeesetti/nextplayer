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

    @Query("UPDATE VideoEntity SET playbackPosition = :position WHERE path = :path")
    suspend fun updatePosition(path: String, position: Long)

    @Query("SELECT * FROM VideoEntity WHERE path = :path")
    suspend fun get(path: String): VideoEntity?
}
