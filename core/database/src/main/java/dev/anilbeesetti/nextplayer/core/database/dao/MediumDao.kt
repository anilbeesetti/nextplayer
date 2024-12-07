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

    @Query("SELECT * FROM media WHERE uri = :uri")
    fun getAsFlow(uri: String): Flow<MediumEntity?>

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

    @Query(
        "UPDATE OR REPLACE media SET " +
            "external_subs = :externalSubs, " +
            "video_scale = :videoScale " +
            "WHERE uri = :uri",
    )
    suspend fun updateMediumUiState(
        uri: String,
        externalSubs: String,
        videoScale: Float,
    )

    @Query(
        "UPDATE OR REPLACE media SET " +
            "playback_position = :position, " +
            "audio_track_index = :audioTrackIndex, " +
            "subtitle_track_index = :subtitleTrackIndex, " +
            "playback_speed = :playbackSpeed, " +
            "last_played_time = :lastPlayedTime " +
            "WHERE uri = :uri",
    )
    suspend fun updateMediumState(
        uri: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        playbackSpeed: Float?,
        lastPlayedTime: Long?,
    )

    @Query("UPDATE OR REPLACE media SET playback_position = :position WHERE uri = :uri")
    suspend fun updateMediumPosition(
        uri: String,
        position: Long,
    )

    @Query("UPDATE OR REPLACE media SET playback_speed = :playbackSpeed WHERE uri = :uri")
    suspend fun updateMediumPlaybackSpeed(
        uri: String,
        playbackSpeed: Float,
    )

    @Query("UPDATE OR REPLACE media SET audio_track_index = :audioTrackIndex WHERE uri = :uri")
    suspend fun updateMediumAudioTrack(
        uri: String,
        audioTrackIndex: Int,
    )

    @Query("UPDATE OR REPLACE media SET subtitle_track_index = :subtitleTrackIndex WHERE uri = :uri")
    suspend fun updateMediumSubtitleTrack(
        uri: String,
        subtitleTrackIndex: Int,
    )

    @Query("UPDATE OR REPLACE media SET video_scale = :zoom WHERE uri = :uri")
    suspend fun updateMediumZoom(
        uri: String,
        zoom: Float,
    )

    @Query("UPDATE OR REPLACE media SET last_played_time = :lastPlayedTime WHERE uri = :uri")
    suspend fun updateMediumLastPlayedTime(
        uri: String,
        lastPlayedTime: Long,
    )

    @Query("UPDATE OR REPLACE media SET external_subs = :externalSubs WHERE uri = :mediumUri")
    suspend fun addExternalSubtitle(mediumUri: String, externalSubs: String)

    @Upsert
    fun upsertVideoStreamInfo(videoStreamInfoEntity: VideoStreamInfoEntity)

    @Upsert
    fun upsertAudioStreamInfo(audioStreamInfoEntity: AudioStreamInfoEntity)

    @Upsert
    fun upsertSubtitleStreamInfo(subtitleStreamInfoEntity: SubtitleStreamInfoEntity)
}
