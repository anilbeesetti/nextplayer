package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.anilbeesetti.nextplayer.core.database.entities.AudioStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.database.entities.SubtitleStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.VideoStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.relations.MediumWithInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface MediumDao {

    @Upsert
    suspend fun upsert(medium: MediumEntity)

    @Upsert
    suspend fun upsertAll(media: List<MediumEntity>)

    @Query("SELECT * FROM media WHERE uri = :uri")
    suspend fun get(uri: String): MediumEntity?

    @Query("SELECT * FROM media")
    fun getAll(): Flow<List<MediumEntity>>

    @Query("SELECT * FROM media WHERE parent_path = :directoryPath")
    fun getAllFromDirectory(directoryPath: String): Flow<List<MediumEntity>>

    @Transaction
    @Query("SELECT * FROM media WHERE uri = :uri")
    suspend fun getWithInfo(uri: String): MediumWithInfo?

    @Transaction
    @Query("SELECT * FROM media")
    fun getAllWithInfo(): Flow<List<MediumWithInfo>>

    @Transaction
    @Query("SELECT * FROM media WHERE parent_path = :directoryPath")
    fun getAllWithInfoFromDirectory(directoryPath: String): Flow<List<MediumWithInfo>>

    @Query("DELETE FROM media WHERE uri in (:uris)")
    suspend fun delete(uris: List<String>)

    @Query("UPDATE OR REPLACE media SET playback_position = :position, audio_track_index = :audioTrackIndex, subtitle_track_index = :subtitleTrackIndex, playback_speed = :playbackSpeed, external_subs = :externalSubs, last_played_time = :lastPlayedTime WHERE uri = :uri")
    suspend fun updateMediumState(
        uri: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        playbackSpeed: Float?,
        externalSubs: String,
        lastPlayedTime: Long?,
    )

    @Upsert
    fun upsertVideoStreamInfo(videoStreamInfoEntity: VideoStreamInfoEntity)

    @Upsert
    fun upsertAudioStreamInfo(audioStreamInfoEntity: AudioStreamInfoEntity)

    @Upsert
    fun upsertSubtitleStreamInfo(subtitleStreamInfoEntity: SubtitleStreamInfoEntity)
}
