package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediumDao {

    @Upsert
    suspend fun upsert(medium: MediumEntity)

    @Upsert
    suspend fun upsertAll(media: List<MediumEntity>)

    @Query("SELECT * FROM media WHERE path = :path")
    suspend fun get(path: String): MediumEntity?

    @Query("SELECT * FROM media")
    fun getAll(): Flow<List<MediumEntity>>

    @Query("SELECT * FROM media WHERE parent_path = :directoryPath")
    fun getAllFromDirectory(directoryPath: String): Flow<List<MediumEntity>>

    @Query("DELETE FROM media WHERE path in (:paths)")
    suspend fun delete(paths: List<String>)

    @Query("UPDATE OR REPLACE media SET playback_position = :position, audio_track_index = :audioTrackIndex, subtitle_track_index = :subtitleTrackIndex, playback_speed = :playbackSpeed, external_subs = :externalSubs WHERE path = :path")
    suspend fun updateMediumState(
        path: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        playbackSpeed: Float?,
        externalSubs: String
    )
}
